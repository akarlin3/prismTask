# Daily Essentials Audit — 2026-04-16

## Summary
Daily-essentials domains (habits, self-care, courses/assignments, leisure, medication) each have their own Room entity + DAO layered under Today via independent `StateFlow`s on `TodayViewModel` — there is no single unified `TodayUiState` or virtual-task abstraction.
"Due today" filtering is done in-memory in repositories/ViewModels; no DAO currently exposes a day-bound query for habits, assignments, or self-care.
Today layout is already preference-driven (`DashboardPreferences` + `TodaySectionId` + `TodayLayoutResolver`), so adding a new "Daily Essentials" section fits cleanly by extending the enum — but rendering essentials as a unified card list is greenfield UX.

## 1. Habits scheduling
**Entity**: `HabitEntity` (table `habits`)
Columns: `id:Long`, `name:String`, `description:String?`, `target_frequency:Int`, `frequency_period:String` ("daily"), `active_days:String?`, `color:String`, `icon:String`, `reminder_time:Long?`, `sort_order:Int`, `is_archived:Boolean`, `category:String?`, `create_daily_task:Boolean`, `reminder_interval_millis:Long?`, `reminder_times_per_day:Int`, `has_logging:Boolean`, `track_booking:Boolean`, `track_previous_period:Boolean`, `is_bookable:Boolean`, `is_booked:Boolean`, `booked_date:Long?`, `booked_note:String?`, `show_streak:Boolean`, `nag_suppression_override_enabled:Boolean`, `nag_suppression_days_override:Int`, `created_at:Long`, `updated_at:Long`.

**Frequency model**: `targetFrequency:Int` + `frequencyPeriod:String` ("daily"/etc.) + `activeDays:String?` (serialized days, likely CSV of day indices).

**Category**: freeform nullable `category:String?` — no enum. `getAllCategories()` query returns distinct values.

**HabitDao "due today" query**: NOT PRESENT. Only `getAllHabits`, `getActiveHabits`, `getActiveHabitsOnce`, `getHabitsWithIntervalReminder`. Due-today filtering is done in-memory (caller filters by `activeDays`).

**habit_completions columns**: `id:Long`, `habit_id:Long` (FK→habits, CASCADE), `completed_date:Long`, `completed_at:Long`, `notes:String?`.

## 2. Self-care routines and steps
**Parent**: No DB "routine" entity — routines are enumerated as `routine_type:String` values ("morning"/"bedtime"/"medication"/"housework"). Routine metadata (tiers, phases, built-in steps) lives in `domain/model/SelfCareRoutine.kt` as an object (`SelfCareRoutines`).

**Step entity**: `SelfCareStepEntity` (table `self_care_steps`)
Columns: `id:Long`, `step_id:String`, `routine_type:String`, `label:String`, `duration:String`, `tier:String`, `note:String`, `phase:String`, `sort_order:Int` (ordering field), `reminder_delay_millis:Long?`, `time_of_day:String` (default "morning"), `medication_name:String?` (FK-by-name to MedicationRefill).
No hard FK to a routine parent — join is by `routine_type` string.

**self_care_logs columns**: `id:Long`, `routine_type:String`, `date:Long`, `selected_tier:String` ("survival"/"solid"/"full"/etc.), `completed_steps:String` (JSON array), `tiers_by_time:String` (JSON object), `is_complete:Boolean`, `started_at:Long?`, `created_at:Long`. Unique index on (`routine_type`, `date`). One log row per routine per day tracks which steps were completed and at which tier.

**Morning/bedtime distinction**: Via `routine_type:String` value ("morning" vs "bedtime"). No enum — raw string convention.

**DAO queries**: `getLogForDate(routineType, date)`, `getLogForDateOnce`, `getStepsForRoutine`, `getStepsForRoutineOnce`, `getLogsForRoutine`, `getStepByMedicationName`. No "routines due now / by time_of_day" query — caller filters steps by `timeOfDay` in memory.

## 3. Courses + assignments (schoolwork)
**CourseEntity** (`courses`): `id:Long`, `name:String`, `code:String`, `color:Int`, `icon:String`, `active:Boolean`, `sort_order:Int`, `created_at:Long`.

**AssignmentEntity** (`assignments`): `id:Long`, `course_id:Long` (FK→courses, CASCADE), `title:String`, `due_date:Long?`, `completed:Boolean`, `completed_at:Long?`, `notes:String?`, `created_at:Long`.

**StudyLogEntity** (`study_logs`, unique on `date`): `id:Long`, `date:Long`, `course_pick:Long?`, `study_done:Boolean`, `assignment_pick:Long?`, `assignment_done:Boolean`, `started_at:Long?`, `created_at:Long`. (Marked "legacy, kept for migration compatibility" in DAO.)

**CourseCompletionEntity** (`course_completions`, unique on `date`+`course_id`): `id:Long`, `date:Long`, `course_id:Long` (FK→courses, CASCADE), `completed:Boolean`, `completed_at:Long?`, `created_at:Long`. Modern per-course daily completion record.

**"Due today" query on AssignmentDao**: NOT PRESENT. Only `getActiveAssignments()` (all incomplete ordered by `due_date`) — caller filters. No day-bound query.

**Linkage to habits**: None. No FK between courses/assignments and `habits`, no shared category enum.

## 4. Leisure
**LeisureLogEntity** (`leisure_logs`, unique on `date`): `id:Long`, `date:Long`, `music_pick:String?`, `music_done:Boolean`, `flex_pick:String?`, `flex_done:Boolean`, `started_at:Long?`, `created_at:Long`. Picks are freeform string IDs — no FK to catalog.

**Catalog**: No DB catalog table. Built-in options are hardcoded in `LeisureViewModel.kt` as `DEFAULT_INSTRUMENTS` and `DEFAULT_FLEX_OPTIONS` (both `List<LeisureOption>`). Examples: music → bass/guitar/drums/piano/singing; flex → read/gaming/cook/watch/boardgame. User-added activities stored in `LeisurePreferences` DataStore (`leisure_prefs`) as `CustomLeisureActivity(id,label,icon)` JSON arrays keyed `custom_music_activities` / `custom_flex_activities`. Final list = defaults + custom.

**Composables (picker reuse)**: `ui/screens/leisure/components/LeisureComponents.kt` → `LeisureOption(id,label,icon)`, `OptionCard`, and the options-grid composable at line ~191.

## 5. Medication
**Public API of `MedicationReminderScheduler`**:
- `scheduleNext(habitId, habitName, habitDescription, completedAt, intervalMillis, doseNumber, totalDoses)`
- `scheduleAtSpecificTime(timeIndex, triggerTimeMillis, habitName)`
- `cancelSpecificTime(timeIndex)` / `cancel(habitId)`
- `suspend fun scheduleSpecificTimes()` / `suspend fun rescheduleAll()`
- `suspend fun getFollowUpTimeIfSuppressed(habit: HabitEntity): LocalDateTime?`
- `scheduleDelayedHabitFollowUp(habitId, habitName, fireAt)` / `cancelFollowUp(habitId)`

**Data source**: The scheduler treats each dose as a `HabitEntity` (uses `HabitDao.getHabitsWithIntervalReminder()` + `HabitCompletionDao`). Schedule mode comes from `MedicationPreferences` DataStore (`MedicationScheduleMode.SPECIFIC_TIMES` vs interval). `SelfCareStepEntity` (routine_type="medication") is what the UI lists as doses.

**"Due today" check**: No single method. `rescheduleAll()` iterates habits with interval reminders, comparing `completionDao.getCompletionCountForDateOnce(habitId, todayStart)` to `habit.reminderTimesPerDay`.

**"Taken today" check**: `HabitCompletionDao.getCompletionCountForDateOnce(habitId, dateStart)` — compared to `reminderTimesPerDay`. Also `getLastCompletionOnce(habitId)`.

**Separate medication entity**: `MedicationRefillEntity` (table `medication_refills`, unique on `medication_name`) for refill data only — NOT for scheduling. Columns: `id:Long`, `medication_name:String`, `pill_count:Int`, `pills_per_dose:Int`, `doses_per_day:Int`, `last_refill_date:Long?`, `pharmacy_name:String?`, `pharmacy_phone:String?`, `reminder_days_before:Int`, `created_at:Long`, `updated_at:Long`. Linked to `SelfCareStepEntity.medication_name` by string match.

## 6. Today screen structure
**Top-level sections** (from `TodaySectionId` enum in `domain/model/TodaySection.kt`):
- `PROGRESS` (Progress Header), `OVERDUE`, `TODAY_TASKS`, `HABITS` (Habit Chips), `CALENDAR_EVENTS`, `PLANNED`, `FLAGGED`, `COMPLETED`, `AI_BRIEFING` (PRO-gated).

**ViewModel state shape**: `TodayViewModel` exposes MANY independent `StateFlow`s — NOT a single `TodayUiState`. Key flows: `overdueTasks`, `todayTasks`, `plannedTasks`, `completedToday`, `allTodayItems`, `totalTodayCount`, `completedTodayCount`, `sectionOrder`, `hiddenSections`, `collapsedSections`, `progressStyle`, `balanceState`, `burnoutResult`, `workLifeBalancePrefs`, `currentNudge`, `showCheckInPrompt`, `checkInGreeting`, `uiTier`, `currentSort`.

**Habit chips composable**: `HabitChipRow` in `today/components/TodayHabitChips.kt` (skeleton: `HabitChipRowSkeleton`). Rendered around `TodayScreen.kt:496`.

**Section ordering**: Preference-driven. Persisted via `DashboardPreferences` (`sectionOrderFlow`, `hiddenSectionsFlow`, `collapsedSectionsFlow`). Composed by `TodayLayoutResolver.resolve(userOrder, hiddenKeys, currentTier) → List<TodaySection>`. `TodayScreen` iterates resolved list; unknown keys are dropped, new sections appended at end.

## 7. DataStore prefs related to Today
Files in `data/preferences/` relevant to Today / leisure / daily routines:

- `DashboardPreferences.kt` — Today layout. Keys: `section_order`, `hidden_sections`, `progress_style`, `collapsed_sections`.
- `MorningCheckInPreferences.kt` — check-in banner. Keys: `banner_dismissed_date`, `feature_enabled`.
- `MedicationPreferences.kt` — medication scheduling. Keys: `reminder_interval_minutes`, `schedule_mode`, `specific_times`.
- `LeisurePreferences.kt` — custom leisure activities (DataStore name `leisure_prefs`). Keys: `custom_music_activities`, `custom_flex_activities`.
- `NotificationPreferences.kt` — habit nags, daily digest (referenced by scheduler for `getHabitNagSuppressionDaysOnce`).
- `TaskBehaviorPreferences.kt` — `dayStartHour` (used by Today day-boundary calc).
- `HabitListPreferences.kt`, `TemplatePreferences.kt`, `ShakePreferences.kt`, `NdPreferences.kt`, `CoachingPreferences.kt` — tangentially related (habit card display, ND gates, coaching nudges).

## 8. Virtual card pattern precedent
**No existing virtual-task-card pattern.** Habits render as chips (`HabitChipRow` in `today/components/TodayHabitChips.kt`), using `HabitWithStatus` from the repository — not as `TaskEntity`-like cards. Calendar events, medication, self-care, leisure each have their own dedicated section/screen with bespoke UI. No adapter/wrapper that shapes a non-task into a `TaskEntity` or shared card composable.

Closest precedents for reuse if a shared-looking card is wanted:
- `today/components/TodaySwipeableTaskItem.kt` — real-task swipe row.
- `habits/components/SelfCareRoutineCard.kt` — routine-as-card in the habits tab.

New UX would be required to render habits/self-care/med/leisure as a unified "daily essentials" card list.

## Implementation notes
**Ready to use as-is**:
- `TodaySectionId` enum + `TodayLayoutResolver` + `DashboardPreferences` — add a new section key (e.g. `DAILY_ESSENTIALS`) and it slots into the existing layout system without migration.
- `SelfCareDao.getLogForDate(routineType, date)` + `SelfCareDao.getStepsForRoutine(routineType)` — per-day routine state is ready to drive a card.
- `HabitDao.getActiveHabits()` + `HabitCompletionDao.getCompletionCountForDateOnce(habitId, dayStart)` — enough to derive "habits due & done today".
- `SchoolworkDao.getActiveAssignments()` + `getCompletionsForDate(date)` — per-day course/assignment completion state.
- `LeisureDao.getLogForDate(date)` — per-day music/flex picks.
- `MedicationRefillDao` + `RefillCalculator` — refill days-left projections.
- `LeisureOption`/`OptionCard` composables + `SelfCareRoutines` catalog — reusable picker primitives.

**Needs a new DAO query**:
- `HabitDao`: "habits scheduled for this weekday" (currently caller parses `active_days` CSV in-memory). A `@Query` that filters by `active_days LIKE '%<day>%' AND is_archived = 0` would be cleaner.
- `AssignmentDao` (inside `SchoolworkDao`): "assignments due in [from, to)" — currently only `getActiveAssignments()` all-at-once.
- `SelfCareDao`: "steps where `time_of_day` matches the current slot (morning/afternoon/evening/night)" — currently filtered in memory.

**Might need a migration**:
- Only if a new "essentials completion" rollup table is desired to aggregate habit/self-care/leisure/medication done-state per day. The existing per-domain tables are sufficient if the UI aggregates in memory — no migration needed for a pure-read aggregation screen.
- If a sort-order or pinning of essentials is desired across heterogeneous sources, a thin `daily_essentials_pin` table (type, foreign_id, sort_order) would be a new migration.

**Half-built / risky**:
- `StudyLogEntity` is marked "legacy, kept for migration compatibility" — don't build new features on it; use `CourseCompletionEntity` instead.
- `SelfCareStepEntity.medication_name` is a by-name link (no FK) — collisions possible if a user renames a refill row without updating the step.
- Medication "scheduling" is split between `HabitEntity` (interval-based reminders) and `SelfCareStepEntity` (routine_type="medication" UI) and `MedicationPreferences` (specific times) — three sources of truth. Any "is medication due today" logic must consult all three.
- `HabitEntity.activeDays` is a `String?` with undocumented format (likely CSV of day indices) — needs to be inspected where written/parsed before trusting it.
- No generic "today's daily essentials" aggregator exists — `TodayViewModel` currently exposes only task-oriented flows. A new aggregator will need to combine `HabitRepository`, `SelfCareRepository`, `LeisureRepository`, `SchoolworkRepository`, and `MedicationRefillRepository`, respecting `TaskBehaviorPreferences.dayStartHour` for the day boundary.
