package com.todounified.data

import androidx.room.*

// ── Enums ──

enum class Priority { URGENT, HIGH, MEDIUM, LOW }

// ── Type converters ──

class Converters {
    @TypeConverter fun fromStringList(value: String?): List<String> =
        value?.split("|||")?.filter { it.isNotEmpty() } ?: emptyList()

    @TypeConverter fun toStringList(list: List<String>): String =
        list.joinToString("|||")

    @TypeConverter fun fromPriority(value: Priority): String = value.name
    @TypeConverter fun toPriority(value: String): Priority =
        try { Priority.valueOf(value) } catch (_: Exception) { Priority.MEDIUM }
}

// ── Entities ──

@Entity(tableName = "task_lists")
data class TaskList(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = TaskList::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("listId")]
)
data class Task(
    @PrimaryKey val id: String,
    val listId: String,
    val title: String,
    val done: Boolean = false,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "imported_tabs")
data class ImportedTab(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val fileName: String,
    val description: String = "",
    val originalStructure: String = "",
    val renderHtml: String = "",
    val sourceCode: String = "",
    val extractedTasksJson: String = "[]",
    val importedAt: Long = System.currentTimeMillis()
)

// ── Relations ──

data class TaskListWithTasks(
    @Embedded val list: TaskList,
    @Relation(parentColumn = "id", entityColumn = "listId")
    val tasks: List<Task>
)
