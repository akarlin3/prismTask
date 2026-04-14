package com.averycorp.prismtask.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.averycorp.prismtask.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that runs a Pomodoro focus/break countdown. A plain
 * `viewModelScope` coroutine is cancelled as soon as the app is backgrounded,
 * which means the ViewModel's old timer loop never fired the completion
 * notification. Running the countdown inside a foreground service keeps the
 * process alive and guarantees the ongoing + completion notifications are
 * delivered even if the user switches apps or locks the screen.
 *
 * The service emits two kinds of broadcast to the in-app UI so the ViewModel
 * can keep [com.averycorp.prismtask.ui.screens.pomodoro.SmartPomodoroViewModel]
 * state in sync:
 *  - [ACTION_TICK] every second with [EXTRA_SECONDS_REMAINING]
 *  - [ACTION_COMPLETE] once when the countdown reaches zero
 */
class PomodoroTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var secondsRemaining: Int = 0
    private var sessionIndex: Int = 0
    private var sessionType: String = SESSION_TYPE_WORK

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCountdown(intent)
            ACTION_STOP -> {
                stopCountdown()
                stopSelf()
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startCountdown(intent: Intent) {
        createChannels(this)
        secondsRemaining = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0)
        sessionIndex = intent.getIntExtra(EXTRA_SESSION_INDEX, 0)
        sessionType = intent.getStringExtra(EXTRA_SESSION_TYPE) ?: SESSION_TYPE_WORK

        val notification = buildOngoingNotification(secondsRemaining)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_ONGOING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_ONGOING, notification)
        }

        tickJob?.cancel()
        tickJob = serviceScope.launch {
            // Emit an initial tick so the UI picks up the starting value even
            // if the user backgrounded the app before the first second
            // elapsed.
            broadcastTick(secondsRemaining)
            while (secondsRemaining > 0) {
                delay(1000)
                secondsRemaining -= 1
                updateOngoingNotification(secondsRemaining)
                broadcastTick(secondsRemaining)
            }
            onCountdownComplete()
        }
    }

    private fun stopCountdown() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun onCountdownComplete() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.cancel(NOTIFICATION_ID_ONGOING)

        val completion = buildCompletionNotification()
        manager?.notify(NOTIFICATION_ID_COMPLETE, completion)

        sendBroadcast(Intent(ACTION_COMPLETE).apply {
            setPackage(packageName)
            putExtra(EXTRA_SESSION_INDEX, sessionIndex)
            putExtra(EXTRA_SESSION_TYPE, sessionType)
        })

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateOngoingNotification(seconds: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID_ONGOING, buildOngoingNotification(seconds))
    }

    private fun buildOngoingNotification(seconds: Int): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PomodoroTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (sessionType) {
            SESSION_TYPE_BREAK -> "Break"
            SESSION_TYPE_LONG_BREAK -> "Long Break"
            else -> "Focus Session"
        }
        val content = "$title \u2014 ${formatRemaining(seconds)} remaining"

        return NotificationCompat.Builder(this, CHANNEL_ID_ONGOING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(tapPending)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPending
            )
            .build()
    }

    private fun buildCompletionNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            this,
            2,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (sessionType) {
            SESSION_TYPE_BREAK, SESSION_TYPE_LONG_BREAK -> "Break Complete!"
            else -> "Session Complete!"
        }
        val body = when (sessionType) {
            SESSION_TYPE_BREAK, SESSION_TYPE_LONG_BREAK -> "Ready to get back to focus?"
            else -> "Nice work \u2014 time for a break."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID_COMPLETE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()
    }

    private fun broadcastTick(seconds: Int) {
        sendBroadcast(Intent(ACTION_TICK).apply {
            setPackage(packageName)
            putExtra(EXTRA_SECONDS_REMAINING, seconds)
            putExtra(EXTRA_SESSION_INDEX, sessionIndex)
            putExtra(EXTRA_SESSION_TYPE, sessionType)
        })
    }

    override fun onDestroy() {
        tickJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.averycorp.prismtask.pomodoro.START"
        const val ACTION_STOP = "com.averycorp.prismtask.pomodoro.STOP"
        const val ACTION_TICK = "com.averycorp.prismtask.pomodoro.TICK"
        const val ACTION_COMPLETE = "com.averycorp.prismtask.pomodoro.COMPLETE"

        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        const val EXTRA_SESSION_INDEX = "session_index"
        const val EXTRA_SESSION_TYPE = "session_type"
        const val EXTRA_SECONDS_REMAINING = "seconds_remaining"

        const val SESSION_TYPE_WORK = "WORK"
        const val SESSION_TYPE_BREAK = "BREAK"
        const val SESSION_TYPE_LONG_BREAK = "LONG_BREAK"

        const val CHANNEL_ID_ONGOING = "pomodoro_timer"
        private const val CHANNEL_NAME_ONGOING = "Pomodoro Timer"
        const val CHANNEL_ID_COMPLETE = "pomodoro_timer_alerts"
        private const val CHANNEL_NAME_COMPLETE = "Pomodoro Alerts"

        private const val NOTIFICATION_ID_ONGOING = 9_001
        private const val NOTIFICATION_ID_COMPLETE = 9_002

        fun start(
            context: Context,
            durationSeconds: Int,
            sessionIndex: Int,
            sessionType: String
        ) {
            try {
                createChannels(context)
                val intent = Intent(context, PomodoroTimerService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                    putExtra(EXTRA_SESSION_INDEX, sessionIndex)
                    putExtra(EXTRA_SESSION_TYPE, sessionType)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Foreground-service restrictions or mocked-context in tests.
                // The ViewModel's in-app timer state still updates; we just
                // won't get the ongoing notification in these edge cases.
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, PomodoroTimerService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {
                // Service may already be stopped, or context unavailable.
            }
        }

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val ongoing = NotificationChannel(
                CHANNEL_ID_ONGOING,
                CHANNEL_NAME_ONGOING,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing Pomodoro session countdown"
                setSound(null, null)
                enableVibration(false)
            }
            val complete = NotificationChannel(
                CHANNEL_ID_COMPLETE,
                CHANNEL_NAME_COMPLETE,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a Pomodoro session or break ends"
            }
            manager.createNotificationChannel(ongoing)
            manager.createNotificationChannel(complete)
        }

        private fun formatRemaining(totalSeconds: Int): String {
            val mins = totalSeconds / 60
            val secs = totalSeconds % 60
            return "%02d:%02d".format(mins, secs)
        }
    }
}
