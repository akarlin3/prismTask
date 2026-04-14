package com.averycorp.prismtask.ui.screens.debug

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.notifications.ExactAlarmHelper
import com.averycorp.prismtask.notifications.NotificationHelper
import com.averycorp.prismtask.notifications.PomodoroTimerService
import com.averycorp.prismtask.notifications.ReminderBroadcastReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Temporary diagnostic ViewModel for debugging notification delivery issues
 * on Samsung S25 Ultra and similar devices. Collects permission state, channel
 * configuration, and exercises each notification path directly.
 *
 * TODO: remove before release.
 */
@HiltViewModel
class NotificationDiagnosticViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context
    ) : ViewModel() {
        private val _state = MutableStateFlow(DiagnosticState())
        val state: StateFlow<DiagnosticState> = _state.asStateFlow()

        private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
        val logEntries: StateFlow<List<LogEntry>> = _logEntries.asStateFlow()

        private val _countdownSeconds = MutableStateFlow<Int?>(null)
        val countdownSeconds: StateFlow<Int?> = _countdownSeconds.asStateFlow()

        private var countdownJob: Job? = null

        init {
            runChecks()
        }

        fun runChecks() {
            val context = appContext

            val postNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val exactAlarms = ExactAlarmHelper.canScheduleExact(context)

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val batteryOptIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

            val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

            val channels = readChannels(context)

            _state.value = DiagnosticState(
                postNotificationsGranted = postNotificationsGranted,
                postNotificationsApplicable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                exactAlarmsAllowed = exactAlarms,
                batteryOptimizationIgnored = batteryOptIgnored,
                notificationsEnabled = notificationsEnabled,
                channels = channels,
                sdkInt = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL
            )

            log("Ran diagnostic checks — SDK=${Build.VERSION.SDK_INT}, device=${Build.MANUFACTURER} ${Build.MODEL}")
            log(
                "POST_NOTIFICATIONS=$postNotificationsGranted, exactAlarms=$exactAlarms, batteryOptIgnored=$batteryOptIgnored, notificationsEnabled=$notificationsEnabled, channels=${channels.size}"
            )
        }

        private fun readChannels(context: Context): List<ChannelInfo> {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return emptyList()
            // Ensure our channels exist so they show up in the list.
            NotificationHelper.createNotificationChannel(context)
            PomodoroTimerService.createChannels(context)

            val appBlocked = !NotificationManagerCompat.from(context).areNotificationsEnabled()
            return manager.notificationChannels.map { ch ->
                ChannelInfo(
                    id = ch.id,
                    name = ch.name?.toString().orEmpty(),
                    importance = ch.importance,
                    importanceLabel = importanceLabel(ch.importance),
                    blocked = ch.importance == NotificationManager.IMPORTANCE_NONE || appBlocked
                )
            }
        }

        fun fireTestNotification() {
            try {
                NotificationHelper.showTaskReminder(
                    context = appContext,
                    taskId = 999999L,
                    taskTitle = "Test notification",
                    taskDescription = "If you see this, notifications work!"
                )
                log("Fired test notification (taskId=999999) via NotificationHelper.showTaskReminder")
            } catch (e: Exception) {
                Log.e(TAG, "Test notification failed", e)
                log("Test notification FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        fun scheduleAlarmIn30Seconds() {
            try {
                val triggerTime = System.currentTimeMillis() + 30_000L
                val intent = Intent(appContext, ReminderBroadcastReceiver::class.java).apply {
                    putExtra("taskId", 999998L)
                    putExtra("taskTitle", "30-second alarm test")
                    putExtra("taskDescription", "Scheduled from diagnostic screen")
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    999998,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                ExactAlarmHelper.scheduleExact(appContext, triggerTime, pendingIntent)
                val formatted = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(triggerTime))
                Log.d(TAG, "Scheduled diagnostic alarm for $formatted")
                log("Scheduled alarm for $formatted (30s from now) via ExactAlarmHelper")
                startCountdown(30)
            } catch (e: Exception) {
                Log.e(TAG, "Scheduling alarm failed", e)
                log("Schedule alarm FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        private fun startCountdown(seconds: Int) {
            countdownJob?.cancel()
            countdownJob = viewModelScope.launch {
                var remaining = seconds
                _countdownSeconds.value = remaining
                while (remaining > 0) {
                    delay(1000)
                    remaining -= 1
                    _countdownSeconds.value = remaining
                }
                _countdownSeconds.value = null
                log("Countdown complete — alarm should have fired by now")
            }
        }

        fun startForegroundServiceTest() {
            try {
                PomodoroTimerService.start(
                    context = appContext,
                    durationSeconds = 60,
                    sessionIndex = 0,
                    sessionType = PomodoroTimerService.SESSION_TYPE_WORK
                )
                log("Started PomodoroTimerService for 60 seconds (foreground service test)")
            } catch (e: Exception) {
                Log.e(TAG, "Foreground service start failed", e)
                log("Foreground service FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        fun openAppNotificationSettings() {
            try {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                log("Opened app notification settings")
            } catch (e: Exception) {
                Log.e(TAG, "Could not open notification settings", e)
                log("Open settings FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        private fun log(message: String) {
            val entry = LogEntry(
                timestampMillis = System.currentTimeMillis(),
                message = message
            )
            _logEntries.value = (_logEntries.value + entry).takeLast(200)
        }

        fun clearLog() {
            _logEntries.value = emptyList()
        }

        override fun onCleared() {
            countdownJob?.cancel()
            super.onCleared()
        }

        private fun importanceLabel(importance: Int): String = when (importance) {
            NotificationManager.IMPORTANCE_NONE -> "None (blocked)"
            NotificationManager.IMPORTANCE_MIN -> "Min"
            NotificationManager.IMPORTANCE_LOW -> "Low"
            NotificationManager.IMPORTANCE_DEFAULT -> "Default"
            NotificationManager.IMPORTANCE_HIGH -> "High"
            NotificationManager.IMPORTANCE_MAX -> "Max"
            else -> "Unknown ($importance)"
        }

        companion object {
            private const val TAG = "NotifDiagnostic"
        }
    }

data class DiagnosticState(
    val postNotificationsGranted: Boolean = false,
    val postNotificationsApplicable: Boolean = false,
    val exactAlarmsAllowed: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val channels: List<ChannelInfo> = emptyList(),
    val sdkInt: Int = 0,
    val manufacturer: String = "",
    val model: String = ""
)

data class ChannelInfo(
    val id: String,
    val name: String,
    val importance: Int,
    val importanceLabel: String,
    val blocked: Boolean
)

data class LogEntry(
    val timestampMillis: Long,
    val message: String
)
