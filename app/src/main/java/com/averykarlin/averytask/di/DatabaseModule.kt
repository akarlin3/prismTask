package com.averykarlin.averytask.di

import android.content.Context
import androidx.room.Room
import com.averykarlin.averytask.data.local.dao.AttachmentDao
import com.averykarlin.averytask.data.local.dao.CalendarSyncDao
import com.averykarlin.averytask.data.local.dao.HabitCompletionDao
import com.averykarlin.averytask.data.local.dao.HabitDao
import com.averykarlin.averytask.data.local.dao.LeisureDao
import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.SyncMetadataDao
import com.averykarlin.averytask.data.local.dao.TagDao
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.dao.UsageLogDao
import com.averykarlin.averytask.data.local.database.AveryTaskDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AveryTaskDatabase =
        Room.databaseBuilder(
            context,
            AveryTaskDatabase::class.java,
            "averytask.db"
        )
            .addMigrations(AveryTaskDatabase.MIGRATION_1_2, AveryTaskDatabase.MIGRATION_2_3, AveryTaskDatabase.MIGRATION_3_4, AveryTaskDatabase.MIGRATION_4_5, AveryTaskDatabase.MIGRATION_5_6, AveryTaskDatabase.MIGRATION_6_7, AveryTaskDatabase.MIGRATION_7_8)
            .build()

    @Provides
    fun provideTaskDao(database: AveryTaskDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideProjectDao(database: AveryTaskDatabase): ProjectDao = database.projectDao()

    @Provides
    fun provideTagDao(database: AveryTaskDatabase): TagDao = database.tagDao()

    @Provides
    fun provideAttachmentDao(database: AveryTaskDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideUsageLogDao(database: AveryTaskDatabase): UsageLogDao = database.usageLogDao()

    @Provides
    fun provideSyncMetadataDao(database: AveryTaskDatabase): SyncMetadataDao = database.syncMetadataDao()

    @Provides
    fun provideCalendarSyncDao(database: AveryTaskDatabase): CalendarSyncDao = database.calendarSyncDao()

    @Provides
    fun provideHabitDao(database: AveryTaskDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideHabitCompletionDao(database: AveryTaskDatabase): HabitCompletionDao = database.habitCompletionDao()

    @Provides
    fun provideLeisureDao(database: AveryTaskDatabase): LeisureDao = database.leisureDao()
}
