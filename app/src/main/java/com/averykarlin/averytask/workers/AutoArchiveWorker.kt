package com.averykarlin.averytask.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.averykarlin.averytask.data.preferences.ArchivePreferences
import com.averykarlin.averytask.data.repository.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AutoArchiveWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepository: TaskRepository,
    private val archivePreferences: ArchivePreferences
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val days = archivePreferences.getAutoArchiveDays().first()
        if (days > 0) {
            taskRepository.autoArchiveOldCompleted(days)
        }
        return Result.success()
    }
}
