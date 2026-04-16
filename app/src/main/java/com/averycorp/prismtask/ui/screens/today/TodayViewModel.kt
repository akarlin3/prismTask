package com.averycorp.prismtask.ui.screens.today

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.data.preferences.DashboardPreferences
import com.averycorp.prismtask.data.preferences.HabitListPreferences
import com.averycorp.prismtask.data.preferences.MorningCheckInPreferences
import com.averycorp.prismtask.data.preferences.SortPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.data.preferences.WorkLifeBalancePrefs
import com.averycorp.prismtask.data.repository.HabitRepository
import com.averycorp.prismtask.data.repository.HabitWithStatus
import com.averycorp.prismtask.data.repository.LeisureRepository
import com.averycorp.prismtask.data.repository.MedicationRefillRepository
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.SchoolworkRepository
import com.averycorp.prismtask.data.repository.SelfCareRepository
import com.averycorp.prismtask.data.repository.TagRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.data.repository.TaskTemplateRepository
import com.averycorp.prismtask.domain.usecase.BalanceConfig
import com.averycorp.prismtask.domain.usecase.BalanceState
import com.averycorp.prismtask.domain.usecase.BalanceTracker
import com.averycorp.prismtask.domain.usecase.BurnoutResult
import com.averycorp.prismtask.domain.usecase.BurnoutScorer
import com.averycorp.prismtask.domain.usecase.SelfCareNudge
import com.averycorp.prismtask.domain.usecase.SelfCareNudgeEngine
import com.averycorp.prismtask.ui.components.QuickRescheduleFormatter
import com.averycorp.prismtask.util.DayBoundary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel
@Inject
constructor(
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val taskDao: TaskDao,
    private val habitRepository: HabitRepository,
    private val projectRepository: ProjectRepository,
    private val templateRepository: TaskTemplateRepository,
    private val dashboardPreferences: DashboardPreferences,
    private val habitListPreferences: HabitListPreferences,
    private val taskBehaviorPreferences: TaskBehaviorPreferences,
    private val sortPreferences: SortPreferences,
    private val proFeatureGate: com.averycorp.prismtask.domain.usecase.ProFeatureGate,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val checkInLogRepository: com.averycorp.prismtask.data.repository.CheckInLogRepository,
    private val medicationRefillRepository: MedicationRefillRepository,
    private val morningCheckInPreferences: MorningCheckInPreferences
) : ViewModel() {
    /**
     * True when the morning check-in banner should render on the Today
     * screen. Derived reactively from three signals (so it flips off the
     * instant any of them changes):
     *  1. [MorningCheckInPreferences.featureEnabled] — user hasn't turned the feature off.
     *  2. Banner hasn't been dismissed earlier today (stored per-day in DataStore).
     *  3. The user hasn't already completed a check-in today and we're still
     *     before the configured prompt hour.
     */
    private val _showCheckInPrompt = MutableStateFlow(false)
    val showCheckInPrompt: StateFlow<Boolean> = _showCheckInPrompt

    /** Greeting that flips from "Good morning!" to "Good afternoon!" after noon. */
    private val _checkInGreeting = MutableStateFlow("Good Morning!")
    val checkInGreeting: StateFlow<String> = _checkInGreeting

    /**
     * True for a few seconds after the user finishes a check-in so the
     * Today screen can render a short "Check-in complete ✓" chip that
     * fades out on its own.
     */
    private val _showCompletionChip = MutableStateFlow(false)
    val showCompletionChip: StateFlow<Boolean> = _showCompletionChip

    /** Remembers whether a check-in already existed when the VM loaded,
     *  so we only show the completion chip for a completion done in this
     *  session (not one the user did yesterday or earlier in the day). */
    private var wasCheckedInAtLoad: Boolean? = null

    fun dismissCheckInPrompt() {
        _showCheckInPrompt.value = false
        viewModelScope.launch {
            try {
                morningCheckInPreferences.dismissBannerToday()
            } catch (
                e: Exception
            ) {
                Log.e("TodayVM", "Failed to persist check-in dismissal", e)
            }
        }
    }

    /** Called by the UI after the completion chip animation is done. */
    fun clearCompletionChip() {
        _showCompletionChip.value = false
    }

    init {
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics
                .getInstance()
                .setCustomKey("screen", "TodayScreen")
        } catch (_: Exception) {
        }

        _checkInGreeting.value = computeGreeting()

        // Reactive banner-visibility pipeline. Recomputes whenever the user
        // dismisses, the feature toggle flips, or a check-in log arrives
        // for today (e.g. the user just finished MorningCheckInScreen).
        viewModelScope.launch {
            try {
                combine(
                    morningCheckInPreferences.featureEnabled(),
                    morningCheckInPreferences.bannerDismissedDate(),
                    checkInLogRepository.observeAll()
                ) { enabled, dismissedDate, logs ->
                    val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
                    val todayStart = DayBoundary.startOfCurrentDay(dayStartHour)
                    val todayIso = java.time.LocalDate
                        .now()
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    val alreadyCheckedInToday = logs.any { it.date >= todayStart }
                    val hour = java.util.Calendar
                        .getInstance()
                        .get(java.util.Calendar.HOUR_OF_DAY)
                    val beforePromptHour = hour < 11
                    val dismissedToday = dismissedDate == todayIso

                    // Track first-seen state so we can distinguish "already
                    // completed before opening Today" from "completed just
                    // now in this session".
                    if (wasCheckedInAtLoad == null) {
                        wasCheckedInAtLoad = alreadyCheckedInToday
                    } else if (!wasCheckedInAtLoad!! && alreadyCheckedInToday && !_showCompletionChip.value) {
                        _showCompletionChip.value = true
                        launch {
                            delay(3000L)
                            _showCompletionChip.value = false
                        }
                    }

                    enabled && beforePromptHour && !alreadyCheckedInToday && !dismissedToday
                }.distinctUntilChanged().collect { shouldShow ->
                    _showCheckInPrompt.value = shouldShow
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to observe check-in banner state", e)
            }
        }
    }

    private fun computeGreeting(): String {
        val hour = java.util.Calendar
            .getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)
        return if (hour < 12) "Good Morning!" else "Good Afternoon!"
    }

    private fun buildCheckInSummary(
        taskCount: Int,
        habitCount: Int,
        hasMedications: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (taskCount > 0) {
            parts += if (taskCount == 1) "1 task" else "$taskCount tasks"
        }
        if (habitCount > 0) {
            parts += if (habitCount == 1) "1 habit" else "$habitCount habits"
        }
        if (hasMedications) parts += "medications"
        val stitched = when (parts.size) {
            0 -> return "Kick off the day with a quick check-in."
            1 -> parts[0]
            2 -> "${parts[0]} and ${parts[1]}"
            else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
        }
        return "You have $stitched to check in on."
    }

    private val balanceTracker: BalanceTracker = BalanceTracker()
    private val burnoutScorer: BurnoutScorer = BurnoutScorer()
    private val nudgeEngine: SelfCareNudgeEngine = SelfCareNudgeEngine()

    private val _currentNudge = MutableStateFlow<SelfCareNudge?>(null)
    val currentNudge: StateFlow<SelfCareNudge?> = _currentNudge
    private var lastShownNudgeId: String? = null
    private val dismissedNudgesToday = mutableSetOf<String>()

    fun dismissNudge() {
        _currentNudge.value?.let { dismissedNudgesToday.add(it.id) }
        _currentNudge.value = null
    }

    fun snoozeNudge() {
        _currentNudge.value = null
    }

    fun nudgeDidIt() {
        viewModelScope.launch {
            try {
                taskRepository.addTask(
                    title = "Self-care break",
                    lifeCategory = com.averycorp.prismtask.domain.model.LifeCategory.SELF_CARE.name
                )
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to add self-care task", e)
            }
            _currentNudge.value?.let { dismissedNudgesToday.add(it.id) }
            _currentNudge.value = null
        }
    }

    private fun refreshNudge(balance: BalanceState, burnout: BurnoutResult) {
        val selfCareRatio = balance.currentRatios[
            com.averycorp.prismtask.domain.model.LifeCategory.SELF_CARE
        ] ?: 0f
        val selfCareTarget = (workLifeBalancePrefs.value.selfCareTarget / 100f)
        val hour = java.util.Calendar
            .getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)
        val next = nudgeEngine.select(
            burnoutScore = burnout.score,
            selfCareRatio = selfCareRatio,
            selfCareTarget = selfCareTarget,
            hourOfDay = hour,
            lastShownId = lastShownNudgeId
        )
        if (next != null && next.id !in dismissedNudgesToday) {
            _currentNudge.value = next
            lastShownNudgeId = next.id
        }
    }

    /**
     * Work-Life Balance preferences: target ratios, toggles, overload threshold.
     * Exposed to the UI so it can read `showBalanceBar` before rendering the section.
     */
    val workLifeBalancePrefs: StateFlow<WorkLifeBalancePrefs> =
        userPreferencesDataStore.workLifeBalanceFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkLifeBalancePrefs())

    /**
     * Live [BalanceState] derived from the user's full task pool and current
     * WLB configuration. The Today screen balance bar, overload banner, and
     * future burnout scorer (V2) all subscribe to this.
     */
    val balanceState: StateFlow<BalanceState> =
        combine(
            taskRepository.getAllTasks(),
            workLifeBalancePrefs
        ) { allTasks, prefs ->
            val config = BalanceConfig(
                workTarget = prefs.workTarget / 100f,
                personalTarget = prefs.personalTarget / 100f,
                selfCareTarget = prefs.selfCareTarget / 100f,
                healthTarget = prefs.healthTarget / 100f,
                overloadThreshold = prefs.overloadThresholdPct / 100f
            )
            balanceTracker.compute(allTasks, config)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BalanceState.EMPTY)

    /**
     * Composite burnout score (v1.4.0 V2). Derived from the same task pool +
     * WLB prefs as the balance state so the UI can render both side-by-side
     * without a second query.
     */
    val burnoutResult: StateFlow<BurnoutResult> =
        combine(
            taskRepository.getAllTasks(),
            workLifeBalancePrefs,
            balanceState
        ) { allTasks, prefs, balance ->
            val workRatio = balance.currentRatios[com.averycorp.prismtask.domain.model.LifeCategory.WORK] ?: 0f
            val result = burnoutScorer.computeFromTasks(
                tasks = allTasks,
                workRatio = workRatio,
                workTarget = prefs.workTarget / 100f
            )
            refreshNudge(balance, result)
            result
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BurnoutResult.EMPTY)

    val isPro: Boolean
        get() = proFeatureGate.isPro()

    /**
     * Persisted sort mode for the Today screen. Screens that don't yet have
     * their own sort selector still expose this so future UI can read/write
     * the same key without a second migration.
     */
    val currentSort: StateFlow<String> =
        sortPreferences
            .observeSortMode(SortPreferences.ScreenKeys.TODAY)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortPreferences.SortModes.DEFAULT)

    fun onChangeSort(sortMode: String) {
        viewModelScope.launch {
            sortPreferences.setSortMode(SortPreferences.ScreenKeys.TODAY, sortMode)
        }
    }

    val snackbarHostState = SnackbarHostState()

    /**
     * Reactive "start of current day" timestamp. Re-emits whenever the user
     * changes their day-start hour in settings, so that downstream task/habit
     * queries automatically refresh and reset to the new day's window.
     */
    private val dayStart: StateFlow<Long> = taskBehaviorPreferences
        .getDayStartHour()
        .map { DayBoundary.startOfCurrentDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayBoundary.startOfCurrentDay(0))

    val sectionOrder: StateFlow<List<String>> = dashboardPreferences
        .getSectionOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardPreferences.DEFAULT_ORDER)

    val hiddenSections: StateFlow<Set<String>> = dashboardPreferences
        .getHiddenSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val progressStyle: StateFlow<String> = dashboardPreferences
        .getProgressStyle()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ring")

    val collapsedSections: StateFlow<Set<String>> = dashboardPreferences
        .getCollapsedSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardPreferences.DEFAULT_COLLAPSED)

    fun onToggleSectionCollapsed(sectionKey: String) {
        viewModelScope.launch {
            val isCollapsed = collapsedSections.value.contains(sectionKey)
            dashboardPreferences.setSectionCollapsed(sectionKey, !isCollapsed)
        }
    }

    private suspend fun currentStartOfToday(): Long =
        DayBoundary.startOfCurrentDay(taskBehaviorPreferences.getDayStartHour().first())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch {
            try {
                taskDao.clearExpiredPlans(currentStartOfToday())
            } catch (
                e: Exception
            ) {
                Log.e("TodayVM", "Failed to clear expired plans", e)
            }
        }
        viewModelScope.launch {
            try {
                // Wait for first emission from a key data flow, then mark loading done
                dayStart.flatMapLatest { start -> taskDao.getTodayTasks(start, start + DayBoundary.DAY_MILLIS) }.first()
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed initial data load", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    val overdueTasks: StateFlow<List<TaskEntity>> = dayStart
        .flatMapLatest { start ->
            taskDao.getOverdueRootTasks(start)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTasks: StateFlow<List<TaskEntity>> = dayStart
        .flatMapLatest { start ->
            taskDao.getTodayTasks(start, start + DayBoundary.DAY_MILLIS)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val plannedTasks: StateFlow<List<TaskEntity>> = dayStart
        .flatMapLatest { start ->
            taskDao.getPlannedForToday(start, start + DayBoundary.DAY_MILLIS)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedToday: StateFlow<List<TaskEntity>> = dayStart
        .flatMapLatest { start ->
            taskDao.getCompletedToday(start)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTodayItems: StateFlow<List<TaskEntity>> =
        combine(todayTasks, plannedTasks) { today, planned ->
            today + planned
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTodayCount: StateFlow<Int> = allTodayItems
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val completedTodayCount: StateFlow<Int> = completedToday
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val progressPercent: StateFlow<Float> =
        combine(totalTodayCount, completedTodayCount) { total, completed ->
            if (total + completed == 0) 0f else completed.toFloat() / (total + completed).toFloat()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // Tag map for task items
    private val allDisplayTasks = combine(overdueTasks, todayTasks, plannedTasks, completedToday) { o, t, p, c ->
        o + t + p + c
    }

    val taskTagsMap: StateFlow<Map<Long, List<TagEntity>>> = allDisplayTasks
        .flatMapLatest { tasks ->
            val ids = tasks.map { it.id }
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val flows = ids.map { id -> tagRepository.getTagsForTask(id).map { tags -> id to tags } }
                combine(flows) { pairs -> pairs.toMap() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Habits
    private val selfCareEnabled: StateFlow<Boolean> = habitListPreferences
        .isSelfCareEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val medicationEnabled: StateFlow<Boolean> = habitListPreferences
        .isMedicationEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val schoolEnabled: StateFlow<Boolean> = habitListPreferences
        .isSchoolEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val leisureEnabled: StateFlow<Boolean> = habitListPreferences
        .isLeisureEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val houseworkEnabled: StateFlow<Boolean> = habitListPreferences
        .isHouseworkEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val allTodayHabits: StateFlow<List<HabitWithStatus>> = combine(
        habitRepository.getHabitsWithTodayStatus(),
        selfCareEnabled,
        medicationEnabled,
        schoolEnabled,
        leisureEnabled,
        houseworkEnabled
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val habits = values[0] as List<HabitWithStatus>
        val selfCareOn = values[1] as Boolean
        val medicationOn = values[2] as Boolean
        val schoolOn = values[3] as Boolean
        val leisureOn = values[4] as Boolean
        val houseworkOn = values[5] as Boolean
        val disabledNames = mutableSetOf<String>()
        if (!selfCareOn) {
            disabledNames.add(SelfCareRepository.MORNING_HABIT_NAME)
            disabledNames.add(SelfCareRepository.BEDTIME_HABIT_NAME)
        }
        if (!medicationOn) disabledNames.add(SelfCareRepository.MEDICATION_HABIT_NAME)
        if (!houseworkOn) disabledNames.add(SelfCareRepository.HOUSEWORK_HABIT_NAME)
        if (!schoolOn) disabledNames.add(SchoolworkRepository.SCHOOL_HABIT_NAME)
        if (!leisureOn) disabledNames.add(LeisureRepository.LEISURE_HABIT_NAME)
        habits
            .filter { it.habit.name !in disabledNames }
            .sortedBy { it.habit.sortOrder }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Daily habit chips — exclude bookable habits (they have their own sections)
    val todayHabits: StateFlow<List<HabitWithStatus>> = allTodayHabits
        .map { list -> list.filter { !it.habit.isBookable } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Count-aware subtitle for the morning check-in banner. Updates live as
     * the user completes tasks / habits or takes medications, and elides
     * zero counts so the banner never reads "0 tasks".
     */
    val checkInSummaryFlow: StateFlow<String> =
        combine(
            combine(todayTasks, plannedTasks) { today, planned ->
                today.count { !it.isCompleted } + planned.count { !it.isCompleted }
            },
            todayHabits.map { list -> list.count { !it.isCompletedToday } },
            medicationRefillRepository.observeAll().map { it.isNotEmpty() }
        ) { taskCount, habitCount, hasMeds ->
            buildCheckInSummary(
                taskCount = taskCount,
                habitCount = habitCount,
                hasMedications = hasMeds
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Bookable habits booked for today
    val scheduledTodayHabits: StateFlow<List<HabitWithStatus>> = combine(
        habitRepository.getHabitsWithFullStatus(),
        dayStart
    ) { habits, start ->
        val endOfDay = start + com.averycorp.prismtask.util.DayBoundary.DAY_MILLIS
        habits.filter { hws ->
            hws.habit.isBookable &&
                hws.habit.isBooked &&
                hws.habit.bookedDate != null &&
                hws.habit.bookedDate in start until endOfDay
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bookable habits that are overdue (last log > expected interval)
    val overdueBookableHabits: StateFlow<List<HabitWithStatus>> = habitRepository
        .getHabitsWithFullStatus()
        .map { habits ->
            habits.filter { hws ->
                if (!hws.habit.isBookable) return@filter false
                val lastDone = hws.lastLogDate ?: return@filter true
                val periodDays = when (hws.habit.frequencyPeriod) {
                    "weekly" -> 7L
                    "fortnightly" -> 14L
                    "monthly" -> 30L
                    "bimonthly" -> 60L
                    "quarterly" -> 90L
                    else -> return@filter false
                }
                val elapsed = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(
                    System.currentTimeMillis() - lastDone
                )
                elapsed > periodDays
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitCompletedCount: StateFlow<Int> = todayHabits
        .map { habits ->
            habits.count { it.isCompletedToday }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val habitTotalCount: StateFlow<Int> = todayHabits
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allHabitsCompletedToday: StateFlow<Boolean> = todayHabits
        .map { habits ->
            habits.isEmpty() || habits.all { it.isCompletedToday }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Combined progress (tasks + habits)
    val combinedTotal: StateFlow<Int> = combine(totalTodayCount, completedTodayCount, todayHabits) { taskTotal, taskDone, habits ->
        taskTotal + taskDone + habits.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val combinedCompleted: StateFlow<Int> = combine(completedTodayCount, habitCompletedCount) { taskDone, habitDone ->
        taskDone + habitDone
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val combinedProgress: StateFlow<Float> = combine(combinedTotal, combinedCompleted) { total, completed ->
        if (total == 0) 0f else completed.toFloat() / total.toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    fun onToggleHabitCompletion(habitId: Long, isCurrentlyCompleted: Boolean) {
        viewModelScope.launch {
            try {
                if (isCurrentlyCompleted) {
                    habitRepository.uncompleteHabit(habitId, System.currentTimeMillis())
                } else {
                    habitRepository.completeHabit(habitId, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to toggle habit", e)
            }
        }
    }

    // Plan for Today
    val tasksNotInToday: StateFlow<List<TaskEntity>> = dayStart
        .flatMapLatest { start ->
            taskDao.getTasksNotInToday(start, start + DayBoundary.DAY_MILLIS)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = projectRepository
        .getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reactive "start of today" exposed for UI (e.g. quick-add plannedDate override).
    val startOfToday: StateFlow<Long> = dayStart

    private val _showPlanSheet = MutableStateFlow(false)
    val showPlanSheet: StateFlow<Boolean> = _showPlanSheet

    fun onShowPlanSheet() {
        _showPlanSheet.value = true
    }

    fun onDismissPlanSheet() {
        _showPlanSheet.value = false
    }

    fun onPlanForToday(taskId: Long) {
        viewModelScope.launch {
            try {
                taskDao.setPlanDate(taskId, currentStartOfToday())
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to plan task", e)
            }
        }
    }

    fun onPlanForToday(taskIds: List<Long>) {
        viewModelScope.launch {
            try {
                val start = currentStartOfToday()
                taskIds.forEach { id -> taskDao.setPlanDate(id, start) }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to plan tasks", e)
            }
        }
    }

    fun onPlanAllOverdue() {
        viewModelScope.launch {
            try {
                val start = currentStartOfToday()
                overdueTasks.value.forEach { task -> taskDao.setPlanDate(task.id, start) }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to plan overdue tasks", e)
            }
        }
    }

    fun onRemoveFromToday(taskId: Long) {
        viewModelScope.launch {
            try {
                taskDao.setPlanDate(taskId, null)
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to remove task from today", e)
            }
        }
    }

    fun onToggleComplete(taskId: Long, isCurrentlyCompleted: Boolean) {
        viewModelScope.launch {
            try {
                if (isCurrentlyCompleted) {
                    taskRepository.uncompleteTask(taskId)
                } else {
                    taskRepository.completeTask(taskId)
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to toggle complete", e)
            }
        }
    }

    fun onCompleteWithUndo(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.completeTask(taskId)
                val result = snackbarHostState.showSnackbar(
                    message = "Task completed",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.uncompleteTask(taskId)
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to complete task", e)
            }
        }
    }

    fun onDeleteTaskWithUndo(taskId: Long) {
        viewModelScope.launch {
            try {
                val savedTask = taskRepository.getTaskByIdOnce(taskId) ?: return@launch
                taskRepository.deleteTask(taskId)
                val result = snackbarHostState.showSnackbar(
                    message = "Task deleted",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.insertTask(savedTask)
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to delete task", e)
            }
        }
    }

    fun onRescheduleTask(taskId: Long, newDueDate: Long?) {
        viewModelScope.launch {
            try {
                val previous = taskRepository.getTaskByIdOnce(taskId)?.dueDate
                taskRepository.rescheduleTask(taskId, newDueDate)
                val label = QuickRescheduleFormatter.describe(newDueDate)
                val result = snackbarHostState.showSnackbar(
                    message = "Rescheduled to $label",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.rescheduleTask(taskId, previous)
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to reschedule", e)
            }
        }
    }

    fun onPlanTaskForToday(taskId: Long) {
        viewModelScope.launch {
            try {
                taskRepository.planTaskForToday(taskId)
                snackbarHostState.showSnackbar(
                    message = "Planned for today",
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to plan for today", e)
            }
        }
    }

    fun onDuplicateTask(taskId: Long) {
        viewModelScope.launch {
            try {
                val newId = taskRepository.duplicateTask(taskId, includeSubtasks = false)
                if (newId <= 0L) {
                    snackbarHostState.showSnackbar("Something went wrong")
                    return@launch
                }
                snackbarHostState.showSnackbar(
                    message = "Task Duplicated",
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to duplicate task", e)
            }
        }
    }

    /**
     * Reactive task-count map (projectId -> root-task count) that backs the
     * move-to-project sheet on the Today screen. Uses all tasks currently
     * on screen (overdue + today + planned) so the counts reflect the
     * scope the user is actually interacting with.
     */
    val taskCountByProject: StateFlow<Map<Long, Int>> = allDisplayTasks
        .map { tasks ->
            tasks
                .groupingBy { it.projectId }
                .eachCount()
                .mapNotNull { (id, count) -> id?.let { it to count } }
                .toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Moves a single task into [newProjectId] (or null to clear the
     * project). Mirrors the task-list version with a per-task undo
     * snackbar. [cascadeSubtasks] propagates to subtasks when true.
     */
    fun onMoveToProject(
        taskId: Long,
        newProjectId: Long?,
        cascadeSubtasks: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val task = taskRepository.getTaskByIdOnce(taskId) ?: return@launch
                val previousParentProjectId = task.projectId
                val previousSubtaskProjects: Map<Long, Long?> = if (cascadeSubtasks) {
                    taskRepository.getSubtasks(taskId).first().associate { it.id to it.projectId }
                } else {
                    emptyMap()
                }

                taskRepository.moveToProject(taskId, newProjectId, cascadeSubtasks)

                val projectName = newProjectId?.let { id ->
                    projects.value.find { it.id == id }?.name
                } ?: "No Project"
                val result = snackbarHostState.showSnackbar(
                    message = "Moved '${task.title}' to $projectName",
                    actionLabel = "UNDO",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    taskRepository.moveToProject(taskId, previousParentProjectId, false)
                    previousSubtaskProjects
                        .entries
                        .groupBy { it.value }
                        .forEach { (origProjectId, entries) ->
                            taskRepository.batchMoveToProject(
                                entries.map { it.key },
                                origProjectId
                            )
                        }
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to move task to project", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    /**
     * Creates a new project on-the-fly from the move sheet and then moves
     * the given task into it.
     */
    fun onCreateProjectAndMoveTask(
        taskId: Long,
        name: String,
        cascadeSubtasks: Boolean = false
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val newId = projectRepository.addProject(name.trim())
                onMoveToProject(taskId, newId, cascadeSubtasks)
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to create project", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }

    fun showSnackbar(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        viewModelScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onAction?.invoke()
            }
        }
    }

    suspend fun getSubtaskCount(taskId: Long): Int =
        taskRepository.getSubtasks(taskId).first().size

    // Rollover
    fun onRolloverToTomorrow(taskIds: List<Long>) {
        viewModelScope.launch {
            try {
                val tomorrow = currentStartOfToday() + DayBoundary.DAY_MILLIS
                taskIds.forEach { id ->
                    taskDao.updateDueDate(id, tomorrow)
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to rollover tasks", e)
            }
        }
    }

    fun onClearDueDates(taskIds: List<Long>) {
        viewModelScope.launch {
            try {
                taskIds.forEach { id ->
                    taskDao.updateDueDate(id, null)
                }
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to clear due dates", e)
            }
        }
    }

    /**
     * Top 4 most-used templates for the Plan-for-Today sheet chip row.
     * `getAllTemplates()` already orders by `usage_count DESC`, so a
     * straight `take(4)` gives us the most frequently used ones.
     */
    val topTemplates: StateFlow<List<TaskTemplateEntity>> = templateRepository
        .getAllTemplates()
        .map { it.take(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Creates a task from a template and immediately plans it for today so
     * it shows up on the current day's dashboard. Surfaces a snackbar so
     * users can confirm the chip tap registered.
     */
    fun onCreateTaskFromTemplateForToday(templateId: Long) {
        viewModelScope.launch {
            try {
                val today = currentStartOfToday()
                val newTaskId = templateRepository.createTaskFromTemplate(
                    templateId = templateId,
                    dueDateOverride = today,
                    quickUse = true
                )
                // Pin to today's dashboard via planDate too — if the template
                // has no due date, dueDateOverride still sets it, so this is
                // a safety net for templates that already carry their own
                // recurrence / schedule.
                taskDao.setPlanDate(newTaskId, today)
                val title = taskRepository.getTaskByIdOnce(newTaskId)?.title.orEmpty()
                snackbarHostState.showSnackbar(
                    message = "Added '${title.ifBlank { "task" }}' for today",
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                Log.e("TodayVM", "Failed to create task from template", e)
                snackbarHostState.showSnackbar("Something went wrong")
            }
        }
    }
}
