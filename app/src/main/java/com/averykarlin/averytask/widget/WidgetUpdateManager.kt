package com.averykarlin.averytask.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun updateAllWidgets() {
        TodayWidget().updateAll(context)
        HabitStreakWidget().updateAll(context)
    }

    suspend fun updateTodayWidget() {
        TodayWidget().updateAll(context)
    }

    suspend fun updateHabitWidget() {
        HabitStreakWidget().updateAll(context)
    }
}
