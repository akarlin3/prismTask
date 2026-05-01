package com.averycorp.prismtask.di

import com.averycorp.prismtask.core.time.SystemTimeProvider
import com.averycorp.prismtask.core.time.TimeProvider
import com.averycorp.prismtask.data.preferences.StartOfDay
import com.averycorp.prismtask.data.preferences.StartOfDayProvider
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    companion object {
        @Provides
        @Singleton
        fun provideStartOfDayProvider(
            prefs: TaskBehaviorPreferences
        ): StartOfDayProvider = object : StartOfDayProvider {
            override suspend fun current(): StartOfDay = prefs.getStartOfDay().first()
        }

        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
