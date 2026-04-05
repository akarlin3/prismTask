package com.averykarlin.averytask.di

import android.content.Context
import androidx.room.Room
import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.TaskDao
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
        ).build()

    @Provides
    fun provideTaskDao(database: AveryTaskDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideProjectDao(database: AveryTaskDatabase): ProjectDao = database.projectDao()
}
