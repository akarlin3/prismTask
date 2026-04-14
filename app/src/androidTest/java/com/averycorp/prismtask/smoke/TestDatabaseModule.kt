package com.averycorp.prismtask.smoke

import android.content.Context
import androidx.room.Room
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideTestDatabase(
        @ApplicationContext context: Context
    ): PrismTaskDatabase =
        Room
            .inMemoryDatabaseBuilder(context, PrismTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    fun provideTaskDao(database: PrismTaskDatabase) = database.taskDao()

    @Provides
    fun provideProjectDao(database: PrismTaskDatabase) = database.projectDao()

    @Provides
    fun provideTagDao(database: PrismTaskDatabase) = database.tagDao()

    @Provides
    fun provideAttachmentDao(database: PrismTaskDatabase) = database.attachmentDao()

    @Provides
    fun provideUsageLogDao(database: PrismTaskDatabase) = database.usageLogDao()

    @Provides
    fun provideSyncMetadataDao(database: PrismTaskDatabase) = database.syncMetadataDao()

    @Provides
    fun provideCalendarSyncDao(database: PrismTaskDatabase) = database.calendarSyncDao()

    @Provides
    fun provideHabitDao(database: PrismTaskDatabase) = database.habitDao()

    @Provides
    fun provideHabitCompletionDao(database: PrismTaskDatabase) = database.habitCompletionDao()

    @Provides
    fun provideLeisureDao(database: PrismTaskDatabase) = database.leisureDao()

    @Provides
    fun provideSchoolworkDao(database: PrismTaskDatabase) = database.schoolworkDao()

    @Provides
    fun provideSelfCareDao(database: PrismTaskDatabase) = database.selfCareDao()

    @Provides
    fun provideTaskTemplateDao(database: PrismTaskDatabase) = database.taskTemplateDao()

    @Provides
    @Singleton
    fun provideGson(): com.google.gson.Gson = com.google.gson.Gson()
}
