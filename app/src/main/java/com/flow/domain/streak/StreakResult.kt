package com.flow.domain.streak

/**
 * The result of a streak computation for a single recurring task.
 *
 * @property currentStreak
 *   Number of consecutive **scheduled days** completed up to and including today.
 *   - Today is treated as a "grace" day: if today is a scheduled day that has not
 *     yet been completed, it is skipped (the day is still open) and the streak is
 *     counted from the previous scheduled day backwards.
 *   - Once a scheduled day in the past is found that was NOT completed, counting
 *     stops and `currentStreak` reflects only the completed portion.
 *   - Zero if no scheduled day in the recent past was completed.
 *
 * @property longestStreak
 *   The longest unbroken run of consecutive scheduled days ever completed.
 *   Always >= [currentStreak].
 */
data class StreakResult(
    val currentStreak: Int,
    val longestStreak: Int,
    /** Midnight-epoch of the first day in the longest streak window; null when no completions. */
    val longestStreakStartDate: Long? = null,
) {
    init {
        require(currentStreak >= 0) { "currentStreak must be >= 0" }
        require(longestStreak >= 0) { "longestStreak must be >= 0" }
        require(longestStreak >= currentStreak) {
            "longestStreak ($longestStreak) must be >= currentStreak ($currentStreak)"
        }
    }

    companion object {
        val ZERO = StreakResult(currentStreak = 0, longestStreak = 0, longestStreakStartDate = null)
    }
}
