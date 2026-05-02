package com.averycorp.prismtask.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.averycorp.prismtask.domain.automation.AutomationEvent
import com.averycorp.prismtask.domain.automation.AutomationEventBus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

/**
 * PeriodicWorkRequest target that emits a [AutomationEvent.TimeTick]
 * every 15 minutes (the WorkManager minimum periodic interval). The
 * engine's matcher checks `tick.hour == trigger.hour && tick.minute == trigger.minute`,
 * so a [AutomationTrigger.TimeOfDay] only fires when the minute lines up
 * within one tick. This is acceptable for v1 — sub-15-minute precision
 * for time-based rules adds AlarmManager complexity that doesn't pay off
 * before the rule edit screen lands.
 */
@HiltWorker
class AutomationTimeTickWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bus: AutomationEventBus
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val cal = Calendar.getInstance()
        bus.emit(
            AutomationEvent.TimeTick(
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE)
            )
        )
        return Result.success()
    }
}
