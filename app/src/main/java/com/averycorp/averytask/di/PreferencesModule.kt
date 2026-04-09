package com.averycorp.averytask.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.averycorp.averytask.data.preferences.SortPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.sortDataStore: DataStore<Preferences> by preferencesDataStore(name = "sort_prefs")

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideSortPreferences(@ApplicationContext context: Context): SortPreferences =
        SortPreferences(context.sortDataStore)
}
