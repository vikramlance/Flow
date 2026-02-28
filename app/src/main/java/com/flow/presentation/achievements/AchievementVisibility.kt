package com.flow.presentation.achievements

/**
 * T003 — Visibility classification for achievement types on the Achievements screen.
 *
 * [VISIBLE] achievements are shown to the user before earning (name and criteria visible).
 * [HIDDEN] achievements show only a locked placeholder ("??? — Keep going!") until earned.
 */
enum class AchievementVisibility {
    /** Shown to user before earning — name and criteria visible. */
    VISIBLE,
    /** Hidden until earned — displays "??? — Keep going!" placeholder; criteria not revealed. */
    HIDDEN
}
