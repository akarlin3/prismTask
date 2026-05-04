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
 * PeriodicWorkRequest target that emits a [AutomationEvent.TimeTick] at
 * 15-minute clock-aligned slots (00, 15, 30, 45 past the hour). The
 * scheduler in [com.averycorp.prismtask.PrismTaskApplication] supplies
 * `setInitialDelay = computeAlignedDelayMs(now)` so the first tick lands
 * on the next slot boundary; subsequent fires inherit the alignment from
 * WorkManager's periodic-work bookkeeping (modulo doze-mode flex).
 *
 * Engine matcher requires `tick.hour == trigger.hour && tick.minute ==
 * trigger.minute`, so a rule's minute MUST be 0/15/30/45 to fire under
 * this scheduling. See `docs/audits/AUTOMATION_VALIDATION_T2_T4_AUDIT.md`
 * Part D option (ii) for the design rationale and the trade-off vs the
 * window-tolerance alternative.
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

    companion object {
        const val INTERVAL_MIN: Int = 15

        /**
         * Milliseconds from [nowMs] until the next wall-clock minute that
         * is a multiple of [intervalMin] in the device timezone — i.e. the
         * delay to pass to [androidx.work.PeriodicWorkRequest.Builder.setInitialDelay]
         * so the first fire lands on a clock-aligned slot.
         *
         * If [nowMs] is exactly on a slot boundary, returns one full
         * [intervalMin] (next slot) rather than zero — zero would
         * cause WorkManager to fire immediately and then again at +15
         * min, defeating the alignment.
         *
         * [intervalMin] must divide 60 evenly (1, 2, 3, 4, 5, 6, 10, 12,
         * 15, 20, 30, 60). 15 is the production value.
         */
        fun computeAlignedDelayMs(nowMs: Long, intervalMin: Int = INTERVAL_MIN): Long {
            require(intervalMin in 1..60 && 60 % intervalMin == 0) {
                "intervalMin must divide 60 evenly: was $intervalMin"
            }
            val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
            val minute = cal.get(Calendar.MINUTE)
            val second = cal.get(Calendar.SECOND)
            val ms = cal.get(Calendar.MILLISECOND)
            val minutesPastSlot = minute % intervalMin
            val msPastSlot = ((minutesPastSlot * 60L + second) * 1000L) + ms
            val intervalMs = intervalMin * 60_000L
            val delay = intervalMs - msPastSlot
            return if (delay == 0L) intervalMs else delay
        }
    }
}
