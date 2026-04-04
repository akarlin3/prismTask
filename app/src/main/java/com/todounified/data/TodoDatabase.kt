package com.todounified.data

import android.content.Context
import androidx.room.*

@Database(
    entities = [TaskList::class, Task::class, ImportedTab::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile private var INSTANCE: TodoDatabase? = null

        fun getDatabase(context: Context): TodoDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TodoDatabase::class.java,
                    "todo_unified_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
