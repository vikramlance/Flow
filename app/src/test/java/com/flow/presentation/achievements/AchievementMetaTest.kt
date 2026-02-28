package com.flow.presentation.achievements

import com.flow.data.local.AchievementType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T001 — RED test for [AchievementMeta].
 * Verifies descriptions, visibility classification, and emoji for all 6 achievement types.
 */
class AchievementMetaTest {

    @Test
    fun allTypesHaveNonEmptyDescription() {
        for (type in AchievementType.values()) {
            val desc = AchievementMeta.descriptions[type]
            assertNotNull("Description must exist for $type", desc)
            assertFalse("Description must not be empty for $type", desc!!.isEmpty())
        }
    }

    @Test
    fun yearFinisherIsHidden() {
        assertEquals(
            AchievementVisibility.HIDDEN,
            AchievementMeta.visibilityOf(AchievementType.YEAR_FINISHER)
        )
    }

    @Test
    fun otherFiveAreVisible() {
        val visible = listOf(
            AchievementType.STREAK_10,
            AchievementType.STREAK_30,
            AchievementType.STREAK_100,
            AchievementType.ON_TIME_10,
            AchievementType.EARLY_FINISH
        )
        for (type in visible) {
            assertEquals(
                "Expected VISIBLE for $type",
                AchievementVisibility.VISIBLE,
                AchievementMeta.visibilityOf(type)
            )
        }
    }

    @Test
    fun achievementEmojiNonEmptyForAllTypes() {
        for (type in AchievementType.values()) {
            val emoji = AchievementMeta.achievementEmoji(type)
            assertFalse("achievementEmoji($type) must not be empty", emoji.isEmpty())
            val hasEmojiCodePoint = emoji.codePoints().anyMatch { cp -> cp > 127 }
            assertTrue(
                "achievementEmoji($type) must contain a non-ASCII emoji code point",
                hasEmojiCodePoint
            )
        }
    }

    @Test
    fun achievementNameNonEmptyForAllTypes() {
        for (type in AchievementType.values()) {
            val name = AchievementMeta.achievementName(type)
            assertFalse("achievementName($type) must not be empty", name.isEmpty())
        }
    }
}
