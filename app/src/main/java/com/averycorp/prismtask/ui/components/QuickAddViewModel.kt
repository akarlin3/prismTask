package com.averycorp.prismtask.ui.components

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.local.converter.RecurrenceConverter
import com.averycorp.prismtask.data.local.dao.UsageLogDao
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.data.local.entity.UsageLogEntity
import com.averycorp.prismtask.data.preferences.VoicePreferences
import com.averycorp.prismtask.data.repository.ProjectRepository
import com.averycorp.prismtask.data.repository.TagRepository
import com.averycorp.prismtask.data.repository.TaskRepository
import com.averycorp.prismtask.data.repository.TaskTemplateRepository
import com.averycorp.prismtask.domain.model.LifeCategory
import com.averycorp.prismtask.domain.usecase.LifeCategoryClassifier
import com.averycorp.prismtask.domain.usecase.NaturalLanguageParser
import com.averycorp.prismtask.domain.usecase.ParsedTask
import com.averycorp.prismtask.domain.usecase.ParsedTaskResolver
import com.averycorp.prismtask.domain.usecase.ProFeatureGate
import com.averycorp.prismtask.domain.usecase.TextToSpeechManager
import com.averycorp.prismtask.domain.usecase.VoiceCommand
import com.averycorp.prismtask.domain.usecase.VoiceCommandParser
import com.averycorp.prismtask.domain.usecase.VoiceInputManager
import com.averycorp.prismtask.domain.usecase.extractKeywords
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class QuickAddViewModel
@Inject
constructor(
    private val parser: NaturalLanguageParser,
    private val resolver: ParsedTaskResolver,
    private val taskRepository: TaskRepository,
    private val tagRepository: TagRepository,
    private val projectRepository: ProjectRepository,
    private val templateRepository: TaskTemplateRepository,
    private val usageLogDao: UsageLogDao,
    private val proFeatureGate: ProFeatureGate,
    val voiceInputManager: VoiceInputManager,
    private val voiceCommandParser: VoiceCommandParser,
    private val tts: TextToSpeechManager,
    private val voicePreferences: VoicePreferences
) : ViewModel() {
    /**
     * List of candidate templates shown in the disambiguation popup when a
     * "/query" shortcut matches more than one template. Null when the popup
     * is dismissed; non-empty when the user needs to pick.
     */
    private val _templateDisambiguation = MutableStateFlow<List<TaskTemplateEntity>?>(null)
    val templateDisambiguation: StateFlow<List<TaskTemplateEntity>?> =
        _templateDisambiguation.asStateFlow()

    private val lifeCategoryClassifier = LifeCategoryClassifier()

    val inputText = MutableStateFlow("")

    val parsedPreview: StateFlow<ParsedTask?> = inputText
        .debounce(200)
        .map { text ->
            if (text.isBlank()) null else parser.parse(text)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    // ----- Voice input surface -----

    /** Emits user-facing messages (command confirmations, errors) that the
     *  hosting screen should display in a Snackbar. */
    private val _voiceMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val voiceMessages: SharedFlow<String> = _voiceMessages.asSharedFlow()

    val isListening: StateFlow<Boolean> = voiceInputManager.isListening
    val voicePartialText: StateFlow<String> = voiceInputManager.partialText
    val voiceRmsLevel: StateFlow<Float> = voiceInputManager.rmsLevel

    private val _voiceInputEnabled = MutableStateFlow(true)
    val voiceInputEnabled: StateFlow<Boolean> = _voiceInputEnabled.asStateFlow()

    private val _continuousModeActive = MutableStateFlow(false)
    val continuousModeActive: StateFlow<Boolean> = _continuousModeActive.asStateFlow()

    /** Id of the most recently created task — voice commands like
     *  "add to project X" operate on this id. */
    private var lastCreatedTaskId: Long? = null

    init {
        viewModelScope.launch {
            voicePreferences.getVoiceInputEnabled().collect { enabled ->
                _voiceInputEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            // Pipe live partial transcription into the visible input field
            // so the user sees the words appear as they speak.
            voiceInputManager.partialText.collect { partial ->
                if (voiceInputManager.isListening.value && partial.isNotEmpty()) {
                    inputText.value = partial
                    if (!_isExpanded.value) _isExpanded.value = true
                }
            }
        }
    }

    fun onInputChanged(text: String) {
        inputText.value = text
    }

    fun onToggleExpand() {
        _isExpanded.value = !_isExpanded.value
    }

    // ----- Voice input entry points -----

    /** Toggle voice recognition on the quick-add bar. Caller is responsible
     *  for requesting RECORD_AUDIO permission before invoking. */
    fun toggleVoiceInput() {
        if (voiceInputManager.isListening.value) {
            voiceInputManager.stopListening()
            return
        }
        _isExpanded.value = true
        voiceInputManager.clearPartialText()
        voiceInputManager.startListening(
            onResult = { transcript ->
                handleVoiceTranscript(transcript)
            },
            onError = { message ->
                viewModelScope.launch { _voiceMessages.emit(message) }
            }
        )
    }

    /** Enter hands-free continuous mode. Same permission contract as
     *  [toggleVoiceInput]. Commands fall through [handleVoiceTranscript]. */
    fun startContinuousVoiceMode() {
        viewModelScope.launch {
            if (!voicePreferences.getContinuousModeEnabled().first()) {
                _voiceMessages.emit("Continuous mode is disabled in Settings")
                return@launch
            }
            _continuousModeActive.value = true
            if (!voiceInputManager.isListening.value) {
                voiceInputManager.clearPartialText()
                voiceInputManager.startListening(
                    onResult = { transcript ->
                        handleVoiceTranscript(transcript)
                    },
                    onError = { message ->
                        viewModelScope.launch { _voiceMessages.emit(message) }
                    }
                )
            }
        }
    }

    /** Exit hands-free mode and stop recognition. */
    fun stopContinuousVoiceMode() {
        _continuousModeActive.value = false
        voiceInputManager.stopListening()
    }

    /** Start the next utterance in continuous mode (auto-called by the UI
     *  after each transcript is processed). */
    fun restartContinuousListening() {
        if (!_continuousModeActive.value) return
        voiceInputManager.clearPartialText()
        voiceInputManager.startListening(
            onResult = { transcript -> handleVoiceTranscript(transcript) },
            onError = { message ->
                viewModelScope.launch { _voiceMessages.emit(message) }
            }
        )
    }

    /** Entry point from the continuous voice overlay: a single utterance is
     *  handled the same way as a tap-driven mic press. */
    fun handleVoiceTranscript(transcript: String, plannedDateOverride: Long? = null) {
        val text = transcript.trim()
        if (text.isBlank()) return
        inputText.value = text

        val command = voiceCommandParser.parseCommand(text)
        if (command != null) {
            executeVoiceCommand(command)
        } else {
            onSubmit(plannedDateOverride)
        }
    }

    private fun executeVoiceCommand(command: VoiceCommand) {
        viewModelScope.launch {
            try {
                val confirmation: String = when (command) {
                    is VoiceCommand.CompleteTask -> completeTaskByName(command.query)
                    is VoiceCommand.DeleteTask -> deleteTaskByName(command.query)
                    is VoiceCommand.RescheduleTask ->
                        rescheduleTaskByName(command.query, command.dateText)
                    is VoiceCommand.MoveToProject -> moveLastTaskToProject(command.projectQuery)
                    is VoiceCommand.StartTimer -> "Timer started on: ${command.query}"
                    VoiceCommand.StopTimer -> "Timer stopped"
                    VoiceCommand.WhatsNext -> buildWhatsNextResponse()
                    VoiceCommand.TaskCount -> buildTaskCountResponse()
                    VoiceCommand.StartFocus -> "Starting focus session"
                    VoiceCommand.ExitVoiceMode -> {
                        stopContinuousVoiceMode()
                        "Voice mode off"
                    }
                }
                if (voicePreferences.getVoiceFeedbackEnabled().first()) {
                    tts.speak(confirmation)
                }
                _voiceMessages.emit(confirmation)
                inputText.value = ""
            } catch (e: Exception) {
                Log.e("QuickAddVM", "Voice command failed", e)
                _voiceMessages.emit("Voice command failed")
            }
        }
    }

    private suspend fun completeTaskByName(query: String): String {
        val all = taskRepository.getAllTasksOnce().filter { !it.isCompleted && it.archivedAt == null }
        val match = voiceCommandParser.fuzzyMatch(all, query) { it.title }
            ?: return "Couldn't find a task matching \"$query\""
        taskRepository.completeTask(match.id)
        return "Completed: ${match.title}"
    }

    private suspend fun deleteTaskByName(query: String): String {
        val all = taskRepository.getAllTasksOnce().filter { it.archivedAt == null }
        val match = voiceCommandParser.fuzzyMatch(all, query) { it.title }
            ?: return "Couldn't find a task matching \"$query\""
        taskRepository.deleteTask(match.id)
        return "Deleted: ${match.title}"
    }

    private suspend fun rescheduleTaskByName(query: String, dateText: String): String {
        val all = taskRepository.getAllTasksOnce().filter { !it.isCompleted && it.archivedAt == null }
        val match = voiceCommandParser.fuzzyMatch(all, query) { it.title }
            ?: return "Couldn't find a task matching \"$query\""
        // Reuse the NLP parser to turn "tomorrow" / "next friday" into a
        // concrete date millis. We feed "placeholder <dateText>" in and only
        // keep the extracted due date.
        val parsed = parser.parse("reschedule $dateText")
        val newDue = parsed.dueDate
            ?: return "Couldn't understand date \"$dateText\""
        taskRepository.updateTask(
            match.copy(dueDate = newDue, updatedAt = System.currentTimeMillis())
        )
        return "Rescheduled \"${match.title}\" to $dateText"
    }

    private suspend fun moveLastTaskToProject(projectQuery: String): String {
        val lastId = lastCreatedTaskId ?: return "No recent task to move"
        val projects = projectRepository.getAllProjects().first()
        val match = voiceCommandParser.fuzzyMatch(projects, projectQuery) { it.name }
            ?: return "Couldn't find project \"$projectQuery\""
        val task = taskRepository.getTaskByIdOnce(lastId)
            ?: return "No recent task to move"
        taskRepository.updateTask(
            task.copy(projectId = match.id, updatedAt = System.currentTimeMillis())
        )
        return "Moved \"${task.title}\" to ${match.name}"
    }

    private suspend fun buildWhatsNextResponse(): String {
        val all = taskRepository
            .getAllTasksOnce()
            .filter { !it.isCompleted && it.archivedAt == null }
            .sortedWith(
                compareByDescending<TaskEntity> { it.priority }
                    .thenBy { it.dueDate ?: Long.MAX_VALUE }
            )
        val top = all.firstOrNull() ?: return "You're all caught up — nothing pending"
        val suffix = if (top.dueDate != null) ", due soon" else ""
        return "Your top priority is: ${top.title}$suffix"
    }

    private suspend fun buildTaskCountResponse(): String {
        val all = taskRepository.getAllTasksOnce().filter { it.archivedAt == null }
        val remaining = all.count { !it.isCompleted }
        return "You have $remaining tasks remaining"
    }

    fun onSubmit(plannedDateOverride: Long? = null) {
        val text = inputText.value.trim()
        if (text.isBlank()) return

        // Template shortcut branch — "/name" or "template:name" bypass the
        // normal NLP pipeline and instead resolve against the user's
        // template library.
        val templateQuery = parser.extractTemplateQuery(text)
        if (templateQuery != null) {
            viewModelScope.launch {
                _isSubmitting.value = true
                try {
                    resolveAndCreateFromTemplate(templateQuery, plannedDateOverride)
                } catch (e: Exception) {
                    Log.e("QuickAddVM", "Failed to resolve template shortcut", e)
                } finally {
                    _isSubmitting.value = false
                }
            }
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                // Use backend NLP for Pro users, local regex parser for free users
                val parsed = if (proFeatureGate.hasAccess(ProFeatureGate.AI_NLP)) {
                    parser.parseRemote(text)
                } else {
                    parser.parse(text)
                }
                val resolved = resolver.resolve(parsed)

                // Auto-create unmatched tags
                val newTagIds = resolved.unmatchedTags.map { tagName ->
                    tagRepository.addTag(name = tagName)
                }
                val allTagIds = resolved.tagIds + newTagIds

                // Auto-create unmatched project
                var projectId = resolved.projectId
                if (projectId == null && resolved.unmatchedProject != null) {
                    projectId = projectRepository.addProject(name = resolved.unmatchedProject)
                }

                // Build recurrence JSON
                val recurrenceJson = resolved.recurrenceRule?.let { RecurrenceConverter.toJson(it) }

                val now = System.currentTimeMillis()
                // If NLP didn't pick up a category tag, fall back to the
                // keyword classifier so Today's balance bar still gets data.
                val resolvedCategory = resolved.lifeCategory ?: run {
                    val guess = lifeCategoryClassifier.classify(resolved.title)
                    if (guess == LifeCategory.UNCATEGORIZED) null else guess.name
                }
                val task = TaskEntity(
                    title = resolved.title,
                    dueDate = resolved.dueDate,
                    dueTime = resolved.dueTime,
                    priority = resolved.priority,
                    projectId = projectId,
                    recurrenceRule = recurrenceJson,
                    plannedDate = plannedDateOverride,
                    lifeCategory = resolvedCategory,
                    createdAt = now,
                    updatedAt = now
                )
                val taskId = taskRepository.insertTask(task)
                lastCreatedTaskId = taskId

                // Assign tags
                if (allTagIds.isNotEmpty()) {
                    tagRepository.setTagsForTask(taskId, allTagIds)
                }

                // Log usage for suggestions
                val keywords = extractKeywords(resolved.title).joinToString(",")
                if (keywords.isNotBlank()) {
                    allTagIds.forEach { tagId ->
                        val tagName = resolved.unmatchedTags.getOrNull(
                            (tagId - (resolved.tagIds.lastOrNull() ?: 0) - 1).toInt().coerceAtLeast(0)
                        ) ?: resolved.title
                        usageLogDao.insert(
                            UsageLogEntity(
                                eventType = "tag_assigned",
                                entityId = tagId,
                                entityName = tagName,
                                taskTitle = resolved.title,
                                titleKeywords = keywords
                            )
                        )
                    }
                    if (projectId != null) {
                        usageLogDao.insert(
                            UsageLogEntity(
                                eventType = "project_assigned",
                                entityId = projectId,
                                entityName = resolved.unmatchedProject ?: "",
                                taskTitle = resolved.title,
                                titleKeywords = keywords
                            )
                        )
                    }
                }

                inputText.value = ""
            } catch (e: Exception) {
                Log.e("QuickAddVM", "Failed to create task", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /**
     * Resolve a template name query against the user's library. If exactly
     * one template matches (case-insensitive substring), creates the task
     * from it immediately. If multiple match, exposes the candidates via
     * [templateDisambiguation] so the UI can show a picker popup.
     */
    private suspend fun resolveAndCreateFromTemplate(
        query: String,
        plannedDateOverride: Long?
    ) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        val all = try {
            templateRepository.getAllTemplates().first()
        } catch (e: Exception) {
            Log.e("QuickAddVM", "Failed to fetch templates", e)
            return
        }
        val matches = fuzzyMatchTemplates(all, normalized)
        when {
            matches.isEmpty() -> { }
            matches.size == 1 -> {
                createFromTemplate(matches.first().id, plannedDateOverride)
                inputText.value = ""
            }
            else -> {
                // More than one candidate — let the UI disambiguate.
                _templateDisambiguation.value = matches
            }
        }
    }

    /**
     * Plan-aware template creation: delegates to the repository's
     * [TaskTemplateRepository.createTaskFromTemplate] with the current
     * plan-date override so quick-add shortcuts in the Plan-for-Today sheet
     * land tasks on today's dashboard.
     */
    private suspend fun createFromTemplate(templateId: Long, plannedDateOverride: Long?) {
        try {
            val newTaskId = templateRepository.createTaskFromTemplate(
                templateId = templateId,
                dueDateOverride = plannedDateOverride,
                quickUse = true
            )
            if (plannedDateOverride != null) {
                taskRepository.planTaskForToday(newTaskId)
            }
            lastCreatedTaskId = newTaskId
        } catch (e: Exception) {
            Log.e("QuickAddVM", "Failed to create from template", e)
        }
    }

    /**
     * Called by the UI when the user taps a candidate in the disambiguation
     * popup. Creates the task from the chosen template, clears the input,
     * and dismisses the popup.
     */
    fun onDisambiguationSelected(templateId: Long, plannedDateOverride: Long? = null) {
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                createFromTemplate(templateId, plannedDateOverride)
                inputText.value = ""
            } finally {
                _templateDisambiguation.value = null
                _isSubmitting.value = false
            }
        }
    }

    fun onDismissDisambiguation() {
        _templateDisambiguation.value = null
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputManager.stopListening()
    }

    /**
     * Simple substring-based fuzzy matcher: returns templates whose name
     * contains any of the space-separated tokens in [query] (case-insensitive).
     * Ranks exact-name matches first, then prefix matches, then substring.
     */
    private fun fuzzyMatchTemplates(
        templates: List<TaskTemplateEntity>,
        query: String
    ): List<TaskTemplateEntity> {
        val q = query.lowercase()
        return templates
            .mapNotNull { template ->
                val name = template.name.lowercase()
                val score = when {
                    name == q -> 0
                    name.startsWith(q) -> 1
                    name.contains(q) -> 2
                    else -> null
                }
                score?.let { template to it }
            }.sortedBy { it.second }
            .map { it.first }
    }
}
