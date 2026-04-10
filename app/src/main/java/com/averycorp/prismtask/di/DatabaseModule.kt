package com.averycorp.prismtask.di

import android.content.Context
import androidx.room.Room
import com.averycorp.prismtask.data.local.dao.AttachmentDao
import com.averycorp.prismtask.data.local.dao.CalendarSyncDao
import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.LeisureDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.SchoolworkDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.dao.TaskTemplateDao
import com.averycorp.prismtask.data.local.dao.UsageLogDao
import com.averycorp.prismtask.data.local.database.AveryTaskDatabase
import com.averycorp.prismtask.data.seed.TemplatePreferencesSeededFlagStore
import com.averycorp.prismtask.data.seed.TemplateSeeder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.gson.Gson
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AveryTaskDatabase =
        Room.databaseBuilder(
            context,
            AveryTaskDatabase::class.java,
            "averytask.db"
        )
            .addMigrations(AveryTaskDatabase.MIGRATION_1_2, AveryTaskDatabase.MIGRATION_2_3, AveryTaskDatabase.MIGRATION_3_4, AveryTaskDatabase.MIGRATION_4_5, AveryTaskDatabase.MIGRATION_5_6, AveryTaskDatabase.MIGRATION_6_7, AveryTaskDatabase.MIGRATION_7_8, AveryTaskDatabase.MIGRATION_8_9, AveryTaskDatabase.MIGRATION_9_10, AveryTaskDatabase.MIGRATION_10_11, AveryTaskDatabase.MIGRATION_11_12, AveryTaskDatabase.MIGRATION_12_13, AveryTaskDatabase.MIGRATION_13_14, AveryTaskDatabase.MIGRATION_14_15, AveryTaskDatabase.MIGRATION_15_16, AveryTaskDatabase.MIGRATION_16_17, AveryTaskDatabase.MIGRATION_17_18, AveryTaskDatabase.MIGRATION_18_19, AveryTaskDatabase.MIGRATION_19_20, AveryTaskDatabase.MIGRATION_20_21, AveryTaskDatabase.MIGRATION_21_22, AveryTaskDatabase.MIGRATION_22_23, AveryTaskDatabase.MIGRATION_23_24, AveryTaskDatabase.MIGRATION_24_25)
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

    @Provides
    fun provideSchoolworkDao(database: AveryTaskDatabase): SchoolworkDao = database.schoolworkDao()

    @Provides
    fun provideSelfCareDao(database: AveryTaskDatabase): SelfCareDao = database.selfCareDao()

    @Provides
    fun provideTaskTemplateDao(database: AveryTaskDatabase): TaskTemplateDao = database.taskTemplateDao()
}

/**
 * Binds the production [TemplateSeeder.SeededFlagStore] implementation
 * ([TemplatePreferencesSeededFlagStore]) into the Hilt graph. Split into a
 * separate abstract module because `@Binds` can't live alongside `@Provides`
 * in an `object` module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TemplateSeederModule {
    @Binds
    @Singleton
    abstract fun bindSeededFlagStore(
        impl: TemplatePreferencesSeededFlagStore
    ): TemplateSeeder.SeededFlagStore
}
