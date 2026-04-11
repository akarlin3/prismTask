package com.averycorp.prismtask.domain.model

/**
 * User-selectable swipe actions on task cards.
 * Stored as name() strings in UserPreferencesDataStore.
 */
enum class SwipeAction {
    COMPLETE,
    DELETE,
    RESCHEDULE,
    ARCHIVE,
    MOVE_TO_PROJECT,
    FLAG,
    NONE;

    companion object {
        fun fromName(name: String?): SwipeAction =
            values().firstOrNull { it.name == name } ?: COMPLETE
    }
}

/**
 * User-selectable start of week.
 */
enum class StartOfWeek {
    MONDAY,
    SUNDAY,
    SATURDAY;

    companion object {
        fun fromName(name: String?): StartOfWeek =
            values().firstOrNull { it.name == name } ?: MONDAY
    }
}

/**
 * Auto-set due date options for new tasks.
 */
enum class AutoDueDate {
    NONE,
    TODAY,
    TOMORROW;

    companion object {
        fun fromName(name: String?): AutoDueDate =
            values().firstOrNull { it.name == name } ?: NONE
    }
}
