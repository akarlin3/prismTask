package com.averycorp.prismtask.di

import com.averycorp.prismtask.domain.automation.AutomationActionHandler
import com.averycorp.prismtask.domain.automation.ConditionEvaluator
import com.averycorp.prismtask.domain.automation.handlers.AiCompleteActionHandler
import com.averycorp.prismtask.domain.automation.handlers.AiSummarizeActionHandler
import com.averycorp.prismtask.domain.automation.handlers.ApplyBatchActionHandler
import com.averycorp.prismtask.domain.automation.handlers.LogActionHandler
import com.averycorp.prismtask.domain.automation.handlers.MutateHabitActionHandler
import com.averycorp.prismtask.domain.automation.handlers.MutateMedicationActionHandler
import com.averycorp.prismtask.domain.automation.handlers.MutateTaskActionHandler
import com.averycorp.prismtask.domain.automation.handlers.NotifyActionHandler
import com.averycorp.prismtask.domain.automation.handlers.ScheduleTimerActionHandler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Hilt wiring for the automation engine. Each handler is multibound into
 * the engine's `Set<AutomationActionHandler>` parameter via `@IntoSet`.
 * Adding a new action type means: implement [AutomationActionHandler],
 * add a `@Binds @IntoSet` line below.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AutomationModule {

    @Binds
    @IntoSet
    abstract fun bindNotify(impl: NotifyActionHandler): AutomationActionHandler

    @Binds
    @IntoSet
    abstract fun bindMutateTask(impl: MutateTaskActionHandler): AutomationActionHandler

    @Binds
    @IntoSet
    abstract fun bindMutateHabit(impl: MutateHabitActionHandler): AutomationActionHandler

    @Binds
    @IntoSet
    abstract fun bindMutateMedication(impl: MutateMedicationActionHandler): AutomationActionHandler

    @Binds
    @IntoSet
    abstract fun bindScheduleTimer(impl: ScheduleTimerActionHandler): AutomationActionHandler

    @Binds
    @IntoSet
    abstract fun bindApplyBatch(impl: ApplyBatchActionHandler): AutomationActionHandler

    @Binds
    @IntoSet
    abstract fun bindAiComplete(impl: AiCompleteActionHandler): AutomationActionHandler

    @Binds
    @IntoSet
    abstract fun bindAiSummarize(impl: AiSummarizeActionHandler): AutomationActionHandler

    @Binds
    @IntoSet
    abstract fun bindLog(impl: LogActionHandler): AutomationActionHandler

    companion object {
        @Provides
        @Singleton
        fun provideConditionEvaluator(): ConditionEvaluator = ConditionEvaluator()
    }
}
