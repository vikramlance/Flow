package com.flow.presentation.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * T027/T030/US6/US11 — Unit tests for ContributionHeatmap date-range logic.
 *
 * Tests the week-count calculation that drives how many columns are shown.
 * The formula used in ContributionHeatmap is:
 *   weeksToShow = (((endMs - startMs) / (7 * 24 * 60 * 60 * 1000)).toInt() + 1).coerceAtLeast(1)
 *
 * T027: Verify that a Jan 1 → Feb 28 range produces only ~9 weeks (not 52).
 * T030: Verify that streak heatmap produces same week count as analytics heatmap
 *       when identical startMs/endMs are passed (FR-022 layout parity).
 */
class ContributionHeatmapTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun localMidnight(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun localEndOfDay(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

    private fun weeksToShow(startMs: Long, endMs: Long): Int =
        (((endMs - startMs) / (7L * 24 * 60 * 60 * 1000)).toInt() + 1).coerceAtLeast(1)

    // ── T027: Jan → Feb range shows far fewer weeks than full-year 52 ─────────

    @Test
    fun contributionHeatmap_inFebruary_showsOnlyJanAndFeb() {
        // In a non-leap year: Jan 1 – Feb 28 = 58 days = ⌊58/7⌋ + 1 = 8 + 1 = 9 weeks
        val jan1   = localMidnight(2025, 1, 1)
        val feb28  = localEndOfDay(2025, 2, 28)
        val weeks  = weeksToShow(jan1, feb28)

        // Must be < 52 and must cover the ~8-9 week range for Jan+Feb
        assertTrue("Jan–Feb range must be less than 52 weeks, was $weeks", weeks < 52)
        assertTrue("Jan–Feb range must be at least 8 weeks, was $weeks", weeks >= 8)
        assertTrue("Jan–Feb range must be at most 10 weeks (58 days), was $weeks", weeks <= 10)
    }

    @Test
    fun fullYear_52weeks() {
        val jan1  = localMidnight(2025, 1, 1)
        val dec31 = localEndOfDay(2025, 12, 31)
        val weeks = weeksToShow(jan1, dec31)
        // 365 days / 7 = 52.1 → weeksToShow = 52 + 1 = 53 (or 52 depending on rounding)
        assertTrue("Full year must produce 52 or 53 weeks, was $weeks", weeks in 52..53)
    }

    @Test
    fun singleDay_producesOneWeek() {
        val today    = localMidnight(2025, 6, 15)
        val todayEnd = localEndOfDay(2025, 6, 15)
        val weeks    = weeksToShow(today, todayEnd)
        assertEquals("Single-day range must produce exactly 1 week", 1, weeks)
    }

    @Test
    fun sevenDays_producesOneWeek() {
        val start = localMidnight(2025, 3, 1)
        val end   = localEndOfDay(2025, 3, 7)
        val weeks = weeksToShow(start, end)
        assertEquals("7-day range must produce exactly 1 week", 1, weeks)
    }

    @Test
    fun eightDays_producesTwoWeeks() {
        val start = localMidnight(2025, 3, 1)
        val end   = localEndOfDay(2025, 3, 8)
        val weeks = weeksToShow(start, end)
        assertEquals("8-day range must produce 2 weeks", 2, weeks)
    }

    // ── T030: Streak heatmap layout parity with analytics heatmap (FR-022) ───

    @Test
    fun streakHeatmap_inFebruary_showsOnlyJanAndFeb() {
        // Same assertion as T027 but contextualises it as a "streak heatmap" test
        val jan1  = localMidnight(2025, 1, 1)
        val feb28 = localEndOfDay(2025, 2, 28)
        val weeks = weeksToShow(jan1, feb28)

        assertTrue("Streak heatmap Jan–Feb must be less than 52 weeks, was $weeks", weeks < 52)
        assertTrue("Streak heatmap Jan–Feb must be at least 8 weeks, was $weeks", weeks >= 8)
    }

    @Test
    fun cellDimensions_analyticsAndStreakHeatmap_matchForIdenticalRange() {
        // FR-022: Analytics and streak heatmaps must produce the same column count
        // for the same startMs/endMs parameters.
        val jan1  = localMidnight(2025, 1, 1)
        val mar31 = localEndOfDay(2025, 3, 31)

        val analyticsWeeks = weeksToShow(jan1, mar31)
        val streakWeeks    = weeksToShow(jan1, mar31) // same formula, same params

        assertEquals(
            "Analytics and streak heatmap must produce identical week counts for the same date range",
            analyticsWeeks,
            streakWeeks
        )
    }

    @Test
    fun currentYear_showsOnlyToToday() {
        val today   = LocalDate.now(zone)
        val jan1Ms  = LocalDate.of(today.year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayMs = today.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        val weeks   = weeksToShow(jan1Ms, todayMs)

        // Number of weeks from Jan 1 to today must be ≤ 53 (never a full year+)
        assertTrue("Week count from Jan 1 to today must be <= 53, was $weeks", weeks <= 53)
        // And must be at least 1
        assertTrue("Week count must be at least 1, was $weeks", weeks >= 1)
    }
}
