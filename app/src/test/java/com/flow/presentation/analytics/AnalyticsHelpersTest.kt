package com.flow.presentation.analytics

import com.flow.data.local.AchievementType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T007/US8 — Unit tests for [achievementEmoji].
 *
 * Verifies Principle VII of the constitution: Unicode emoji MUST always render as
 * their intended visual symbols. Empty-string fallbacks for emoji are prohibited.
 *
 * Requires [achievementEmoji] to be `internal` (changed in T007 implementation).
 */
class AnalyticsHelpersTest {

    @Test
    fun achievementEmoji_returnsNonEmptyUnicodeForAllSixTypes() {
        val types = AchievementType.values()
        for (type in types) {
            val emoji = achievementEmoji(type)
            assertFalse(
                "achievementEmoji($type) must not be empty — Principle VII violation",
                emoji.isEmpty()
            )
            // Verify the string contains at least one code point that is NOT a
            // ASCII printable character (i.e., it is a true multi-byte emoji).
            val hasEmojiCodePoint = emoji.codePoints().anyMatch { cp -> cp > 127 }
            assertTrue(
                "achievementEmoji($type) must contain a non-ASCII emoji code point, got: '$emoji'",
                hasEmojiCodePoint
            )
        }
    }

    @Test
    fun achievementEmoji_streak10_isBuddingSprout() {
        val result = achievementEmoji(AchievementType.STREAK_10)
        // 🌱 U+1F331 (SEEDLING)
        assertTrue(result.contains("\uD83C\uDF31"))
    }

    @Test
    fun achievementEmoji_streak30_isTree() {
        val result = achievementEmoji(AchievementType.STREAK_30)
        // 🌳 U+1F333 (DECIDUOUS TREE)
        assertTrue(result.contains("\uD83C\uDF33"))
    }

    @Test
    fun achievementEmoji_streak100_isTrophy() {
        val result = achievementEmoji(AchievementType.STREAK_100)
        // 🏆 U+1F3C6 (TROPHY)
        assertTrue(result.contains("\uD83C\uDFC6"))
    }

    @Test
    fun achievementEmoji_onTime10_isTimerClock() {
        val result = achievementEmoji(AchievementType.ON_TIME_10)
        // ⏱️ U+23F1 U+FE0F (STOPWATCH)
        assertTrue(result.contains("\u23F1"))
    }

    @Test
    fun achievementEmoji_earlyFinish_isLightningBolt() {
        val result = achievementEmoji(AchievementType.EARLY_FINISH)
        // ⚡ U+26A1 (HIGH VOLTAGE SIGN)
        assertTrue(result.contains("\u26A1"))
    }

    @Test
    fun achievementEmoji_yearFinisher_isDartboard() {
        val result = achievementEmoji(AchievementType.YEAR_FINISHER)
        // 🎯 U+1F3AF (DIRECT HIT)
        assertTrue(result.contains("\uD83C\uDFAF"))
    }
}
