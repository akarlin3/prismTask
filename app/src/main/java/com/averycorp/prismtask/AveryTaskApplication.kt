package com.averycorp.prismtask

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import com.averycorp.prismtask.data.repository.LeisureRepository
import com.averycorp.prismtask.data.repository.SchoolworkRepository
import com.averycorp.prismtask.data.repository.SelfCareRepository
import com.averycorp.prismtask.data.seed.TemplateSeeder
import com.averycorp.prismtask.workers.AutoArchiveWorker
import com.averycorp.prismtask.workers.DailyResetWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AveryTaskApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var schoolworkRepository: SchoolworkRepository

    @Inject
    lateinit var leisureRepository: LeisureRepository

    @Inject
    lateinit var selfCareRepository: SelfCareRepository

    @Inject
    lateinit var taskBehaviorPreferences: TaskBehaviorPreferences

    @Inject
    lateinit var templateSeeder: TemplateSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleAutoArchive()
        scheduleDailyReset()
        seedBuiltInHabits()
        seedBuiltInTemplates()
    }

    /**
     * Inserts the six built-in task templates on first launch. Gated by a
     * `templates_seeded` flag in [com.averycorp.prismtask.data.preferences.TemplatePreferences]
     * so it runs exactly once per install and never resurrects deleted defaults.
     */
    private fun seedBuiltInTemplates() {
        appScope.launch {
            templateSeeder.seedIfNeeded()
        }
    }

    /**
     * Schedules the daily reset worker to fire at the configured day-start
     * hour, and re-schedules whenever the user changes the setting so the
     * change takes effect immediately.
     */
    private fun scheduleDailyReset() {
        appScope.launch {
            taskBehaviorPreferences.getDayStartHour().collectLatest { hour ->
                DailyResetWorker.schedule(this@AveryTaskApplication, hour)
            }
        }
    }

    private fun seedBuiltInHabits() {
        appScope.launch {
            schoolworkRepository.ensureHabitExists()
            leisureRepository.ensureHabitExists()
            selfCareRepository.ensureHabitsExist()
        }
    }

    private fun scheduleAutoArchive() {
        val workRequest = PeriodicWorkRequestBuilder<AutoArchiveWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "auto_archive",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
