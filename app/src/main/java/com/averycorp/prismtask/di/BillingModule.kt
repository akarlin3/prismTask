package com.averycorp.prismtask.di

import com.averycorp.prismtask.data.billing.BillingManager
import com.averycorp.prismtask.data.preferences.ProStatusPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    // BillingManager and ProStatusPreferences are @Singleton with @Inject constructors,
    // so Hilt provides them automatically. This module exists as a placeholder for
    // any future billing-related bindings (e.g., server-side verification).
}
