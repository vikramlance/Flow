package com.flow.navigation

object Routes {
    const val HOME = "home"
    const val ANALYTICS = "analytics"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val TASK_STREAK = "task_streak/{taskId}"
    const val ACHIEVEMENTS = "achievements"

    fun taskStreak(taskId: Long) = "task_streak/$taskId"
}
