package com.averycorp.averytask.smoke

import android.content.Context
import androidx.room.Room
import com.averycorp.averytask.data.local.database.AveryTaskDatabase
import com.averycorp.averytask.di.DatabaseModule
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
    fun provideTestDatabase(@ApplicationContext context: Context): AveryTaskDatabase =
        Room.inMemoryDatabaseBuilder(context, AveryTaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    fun provideTaskDao(database: AveryTaskDatabase) = database.taskDao()

    @Provides
    fun provideProjectDao(database: AveryTaskDatabase) = database.projectDao()

    @Provides
    fun provideTagDao(database: AveryTaskDatabase) = database.tagDao()

    @Provides
    fun provideAttachmentDao(database: AveryTaskDatabase) = database.attachmentDao()

    @Provides
    fun provideUsageLogDao(database: AveryTaskDatabase) = database.usageLogDao()

    @Provides
    fun provideSyncMetadataDao(database: AveryTaskDatabase) = database.syncMetadataDao()

    @Provides
    fun provideCalendarSyncDao(database: AveryTaskDatabase) = database.calendarSyncDao()

    @Provides
    fun provideHabitDao(database: AveryTaskDatabase) = database.habitDao()

    @Provides
    fun provideHabitCompletionDao(database: AveryTaskDatabase) = database.habitCompletionDao()

    @Provides
    fun provideLeisureDao(database: AveryTaskDatabase) = database.leisureDao()

    @Provides
    fun provideSchoolworkDao(database: AveryTaskDatabase) = database.schoolworkDao()

    @Provides
    fun provideSelfCareDao(database: AveryTaskDatabase) = database.selfCareDao()

    @Provides
    fun provideTaskTemplateDao(database: AveryTaskDatabase) = database.taskTemplateDao()

    @Provides
    @Singleton
    fun provideGson(): com.google.gson.Gson = com.google.gson.Gson()
}
