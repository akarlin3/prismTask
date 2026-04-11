package com.averycorp.prismtask.di

import android.content.Context
import androidx.room.Room
import com.averycorp.prismtask.data.local.dao.AttachmentDao
import com.averycorp.prismtask.data.local.dao.CalendarSyncDao
import com.averycorp.prismtask.data.local.dao.HabitCompletionDao
import com.averycorp.prismtask.data.local.dao.HabitDao
import com.averycorp.prismtask.data.local.dao.HabitLogDao
import com.averycorp.prismtask.data.local.dao.LeisureDao
import com.averycorp.prismtask.data.local.dao.ProjectDao
import com.averycorp.prismtask.data.local.dao.SchoolworkDao
import com.averycorp.prismtask.data.local.dao.SelfCareDao
import com.averycorp.prismtask.data.local.dao.SyncMetadataDao
import com.averycorp.prismtask.data.local.dao.TagDao
import com.averycorp.prismtask.data.local.dao.TaskDao
import com.averycorp.prismtask.data.local.dao.TaskTemplateDao
import com.averycorp.prismtask.data.local.dao.UsageLogDao
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): PrismTaskDatabase =
        Room.databaseBuilder(
            context,
            PrismTaskDatabase::class.java,
            "averytask.db"
        )
            .addMigrations(PrismTaskDatabase.MIGRATION_1_2, PrismTaskDatabase.MIGRATION_2_3, PrismTaskDatabase.MIGRATION_3_4, PrismTaskDatabase.MIGRATION_4_5, PrismTaskDatabase.MIGRATION_5_6, PrismTaskDatabase.MIGRATION_6_7, PrismTaskDatabase.MIGRATION_7_8, PrismTaskDatabase.MIGRATION_8_9, PrismTaskDatabase.MIGRATION_9_10, PrismTaskDatabase.MIGRATION_10_11, PrismTaskDatabase.MIGRATION_11_12, PrismTaskDatabase.MIGRATION_12_13, PrismTaskDatabase.MIGRATION_13_14, PrismTaskDatabase.MIGRATION_14_15, PrismTaskDatabase.MIGRATION_15_16, PrismTaskDatabase.MIGRATION_16_17, PrismTaskDatabase.MIGRATION_17_18, PrismTaskDatabase.MIGRATION_18_19, PrismTaskDatabase.MIGRATION_19_20, PrismTaskDatabase.MIGRATION_20_21, PrismTaskDatabase.MIGRATION_21_22, PrismTaskDatabase.MIGRATION_22_23, PrismTaskDatabase.MIGRATION_23_24, PrismTaskDatabase.MIGRATION_24_25, PrismTaskDatabase.MIGRATION_25_26, PrismTaskDatabase.MIGRATION_26_27)
            .build()

    @Provides
    fun provideTaskDao(database: PrismTaskDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideProjectDao(database: PrismTaskDatabase): ProjectDao = database.projectDao()

    @Provides
    fun provideTagDao(database: PrismTaskDatabase): TagDao = database.tagDao()

    @Provides
    fun provideAttachmentDao(database: PrismTaskDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideUsageLogDao(database: PrismTaskDatabase): UsageLogDao = database.usageLogDao()

    @Provides
    fun provideSyncMetadataDao(database: PrismTaskDatabase): SyncMetadataDao = database.syncMetadataDao()

    @Provides
    fun provideCalendarSyncDao(database: PrismTaskDatabase): CalendarSyncDao = database.calendarSyncDao()

    @Provides
    fun provideHabitDao(database: PrismTaskDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideHabitCompletionDao(database: PrismTaskDatabase): HabitCompletionDao = database.habitCompletionDao()

    @Provides
    fun provideHabitLogDao(database: PrismTaskDatabase): HabitLogDao = database.habitLogDao()

    @Provides
    fun provideLeisureDao(database: PrismTaskDatabase): LeisureDao = database.leisureDao()

    @Provides
    fun provideSchoolworkDao(database: PrismTaskDatabase): SchoolworkDao = database.schoolworkDao()

    @Provides
    fun provideSelfCareDao(database: PrismTaskDatabase): SelfCareDao = database.selfCareDao()

    @Provides
    fun provideTaskTemplateDao(database: PrismTaskDatabase): TaskTemplateDao = database.taskTemplateDao()
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
