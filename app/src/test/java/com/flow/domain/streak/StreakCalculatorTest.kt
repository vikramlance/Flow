package com.flow.domain.streak

import com.flow.domain.streak.RecurrenceSchedule.Companion.orDaily
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Unit tests for [StreakCalculator].
 *
 * All tests inject a fixed [today] and use [ZoneOffset.UTC] so they are fully
 * deterministic and require no Android runtime.
 *
 * Run with:  ./gradlew :app:test  (JVM unit-test task)
 */
class StreakCalculatorTest {

    // Convenience: convert LocalDate → midnight UTC epoch-ms (as stored in task_logs.date)
    private fun LocalDate.toEpochMs(): Long =
        atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    // Convenience: build a list of consecutive midnight-ms values
    private fun dates(vararg localDates: LocalDate): List<Long> =
        localDates.map { it.toEpochMs() }

    private val TODAY = LocalDate.of(2026, 2, 22)  // fixed reference date

    // -------------------------------------------------------------------------
    // Null / empty cases
    // -------------------------------------------------------------------------

    @Test
    fun `empty completions returns ZERO`() {
        val result = StreakCalculator.compute(
            completionDates = emptyList(),
            schedule = null,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(StreakResult.ZERO, result)
    }

    @Test
    fun `null schedule is treated as DAILY`() {
        // Three consecutive days completed; null schedule should behave like Daily.
        val logs = dates(TODAY.minusDays(2), TODAY.minusDays(1), TODAY)
        val resultNull = StreakCalculator.compute(logs, schedule = null,  today = TODAY, zone = ZoneOffset.UTC)
        val resultDaily = StreakCalculator.compute(logs, schedule = RecurrenceSchedule.Daily, today = TODAY, zone = ZoneOffset.UTC)
        assertEquals(resultDaily, resultNull)
    }

    // -------------------------------------------------------------------------
    // DAILY schedule — currentStreak
    // -------------------------------------------------------------------------

    @Test
    fun `DAILY - completed today - streak is 1`() {
        val result = StreakCalculator.compute(
            completionDates = dates(TODAY),
            schedule = RecurrenceSchedule.Daily,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
    }

    @Test
    fun `DAILY - completed yesterday only - streak is 1 (today still open)`() {
        // Today not completed yet (grace), yesterday done → current = 1
        val result = StreakCalculator.compute(
            completionDates = dates(TODAY.minusDays(1)),
            schedule = RecurrenceSchedule.Daily,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
    }

    @Test
    fun `DAILY - 5 consecutive days ending yesterday - streak is 5 (today grace)`() {
        val logs = (1L..5L).map { TODAY.minusDays(it).toEpochMs() }
        val result = StreakCalculator.compute(
            completionDates = logs,
            schedule = RecurrenceSchedule.Daily,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(5, result.currentStreak)
        assertEquals(5, result.longestStreak)
    }

    @Test
    fun `DAILY - 5 consecutive days including today - streak is 5`() {
        val logs = (0L..4L).map { TODAY.minusDays(it).toEpochMs() }
        val result = StreakCalculator.compute(
            completionDates = logs,
            schedule = RecurrenceSchedule.Daily,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(5, result.currentStreak)
        assertEquals(5, result.longestStreak)
    }

    @Test
    fun `DAILY - gap 2 days ago resets current streak to 0`() {
        // Completed today and yesterday, but NOT 2 days ago, then again 3 days ago.
        // current streak: today(1) + yesterday(1) = 2, then gap at -2 stops it.
        // longest : the older run of 3 (days -3,-4,-5) might differ.
        val logs = dates(
            TODAY,
            TODAY.minusDays(1),
            // gap at TODAY.minusDays(2)
            TODAY.minusDays(3),
            TODAY.minusDays(4),
            TODAY.minusDays(5),
        )
        val result = StreakCalculator.compute(
            completionDates = logs,
            schedule = RecurrenceSchedule.Daily,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(2, result.currentStreak)
        assertEquals(3, result.longestStreak)
    }

    @Test
    fun `DAILY - completed only 7 days ago - current streak is 0`() {
        // Old completion, nothing recent.
        val result = StreakCalculator.compute(
            completionDates = dates(TODAY.minusDays(7)),
            schedule = RecurrenceSchedule.Daily,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(0, result.currentStreak)
        assertEquals(1, result.longestStreak)
    }

    @Test
    fun `DAILY - duplicate completion dates are deduplicated`() {
        // Same day submitted multiple times should only count once.
        val logs = listOf(TODAY.toEpochMs(), TODAY.toEpochMs(), TODAY.toEpochMs())
        val result = StreakCalculator.compute(
            completionDates = logs,
            schedule = RecurrenceSchedule.Daily,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
    }

    // -------------------------------------------------------------------------
    // DAILY schedule — longestStreak
    // -------------------------------------------------------------------------

    @Test
    fun `DAILY - longest streak in the middle of history`() {
        // Run of 4 in the past is the longest, current = 2
        val logs = dates(
            TODAY,
            TODAY.minusDays(1),
            // gap at -2
            TODAY.minusDays(10),
            TODAY.minusDays(11),
            TODAY.minusDays(12),
            TODAY.minusDays(13),
            // gap at -14
            TODAY.minusDays(20),
        )
        val result = StreakCalculator.compute(
            completionDates = logs,
            schedule = RecurrenceSchedule.Daily,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(2, result.currentStreak)
        assertEquals(4, result.longestStreak)
    }

    // -------------------------------------------------------------------------
    // WEEKLY schedule — Mon / Wed / Fri
    // -------------------------------------------------------------------------
    //
    // TODAY = 2026-02-22 (Sunday).
    // Schedule = MWF.
    // Most-recent scheduled day before/on TODAY is Friday 2026-02-20.

    private val MWF = RecurrenceSchedule.Weekly(
        setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    )

    // Helper to get the most recent MWF day at / before a given date
    private fun prevMwfDay(from: LocalDate): LocalDate {
        var d = from
        while (d.dayOfWeek !in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) {
            d = d.minusDays(1)
        }
        return d
    }

    @Test
    fun `WEEKLY MWF - today is Sunday - non-scheduled day - counts from Friday`() {
        // TODAY is Sunday (non-scheduled). Last scheduled = Friday.
        // Complete Friday, Wednesday, Monday → streak = 3
        val friday    = LocalDate.of(2026, 2, 20)
        val wednesday = LocalDate.of(2026, 2, 18)
        val monday    = LocalDate.of(2026, 2, 16)
        val result = StreakCalculator.compute(
            completionDates = dates(friday, wednesday, monday),
            schedule = MWF,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.longestStreak)
    }

    @Test
    fun `WEEKLY MWF - miss Wednesday - streak resets to 1`() {
        // Complete Friday.  Skip Wednesday.  Complete Monday.
        // currentStreak starting from last scheduled (Friday): 1 then gap at Wed → stops.
        val friday    = LocalDate.of(2026, 2, 20)
        val monday    = LocalDate.of(2026, 2, 16)
        val result = StreakCalculator.compute(
            completionDates = dates(friday, monday),
            schedule = MWF,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
    }

    @Test
    fun `WEEKLY MWF - completing on Tuesday has no effect`() {
        // Tuesday is not a scheduled day.  Completing it must not extend the streak.
        val tuesday   = LocalDate.of(2026, 2, 17)  // Tuesday (not in MWF)
        val friday    = LocalDate.of(2026, 2, 20)  // scheduled
        val result = StreakCalculator.compute(
            completionDates = dates(friday, tuesday),
            schedule = MWF,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        // streak = 1 (only Friday counted; Wednesday was missed so we stop after Friday)
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
    }

    @Test
    fun `WEEKLY MWF - grace today is Monday scheduled and not completed yet`() {
        // TODAY = Monday 2026-02-23 (adjust for this test only)
        val testToday = LocalDate.of(2026, 2, 23)  // Monday
        val friday    = LocalDate.of(2026, 2, 20)  // previous scheduled day
        val wednesday = LocalDate.of(2026, 2, 18)
        // Monday not completed yet → grace → count from Friday backwards
        val result = StreakCalculator.compute(
            completionDates = dates(friday, wednesday),
            schedule = MWF,
            today = testToday,
            zone = ZoneOffset.UTC,
        )
        assertEquals(2, result.currentStreak)  // Friday + Wednesday
    }

    @Test
    fun `WEEKLY MWF - completed all 3 days last week and 3 days this week`() {
        // This week: Mon 16, Wed 18, Fri 20; last week: Mon 9, Wed 11, Fri 13
        val logs = dates(
            LocalDate.of(2026, 2, 20), // Fri
            LocalDate.of(2026, 2, 18), // Wed
            LocalDate.of(2026, 2, 16), // Mon
            LocalDate.of(2026, 2, 13), // Fri
            LocalDate.of(2026, 2, 11), // Wed
            LocalDate.of(2026, 2,  9), // Mon
        )
        val result = StreakCalculator.compute(
            completionDates = logs,
            schedule = MWF,
            today = TODAY, // Sunday 22 Feb
            zone = ZoneOffset.UTC,
        )
        assertEquals(6, result.currentStreak)
        assertEquals(6, result.longestStreak)
    }

    // -------------------------------------------------------------------------
    // WEEKLY single-day schedule (e.g. every Sunday)
    // -------------------------------------------------------------------------

    @Test
    fun `WEEKLY Sunday only - completed last 3 Sundays - streak is 3`() {
        val sundaySchedule = RecurrenceSchedule.Weekly(setOf(DayOfWeek.SUNDAY))
        // TODAY = Sunday 2026-02-22 — completed today + 2 prior Sundays
        val logs = dates(
            TODAY,
            TODAY.minusWeeks(1),
            TODAY.minusWeeks(2),
        )
        val result = StreakCalculator.compute(
            completionDates = logs,
            schedule = sundaySchedule,
            today = TODAY,
            zone = ZoneOffset.UTC,
        )
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.longestStreak)
    }

    @Test
    fun `WEEKLY Sunday only - missed last Sunday - current streak is 0`() {
        val sundaySchedule = RecurrenceSchedule.Weekly(setOf(DayOfWeek.SUNDAY))
        val logs = dates(
            TODAY.minusWeeks(2),  // two Sundays ago (completed)
            TODAY.minusWeeks(3),
        )
        val result = StreakCalculator.compute(
            completionDates = logs,
            schedule = sundaySchedule,
            today = TODAY,   // today IS Sunday, not completed → grace → back to last week → missed → 0
            zone = ZoneOffset.UTC,
        )
        assertEquals(0, result.currentStreak)
        assertEquals(2, result.longestStreak)
    }

    // -------------------------------------------------------------------------
    // RecurrenceSchedule helpers
    // -------------------------------------------------------------------------

    @Test
    fun `fromString DAILY returns Daily`() {
        assertEquals(RecurrenceSchedule.Daily, RecurrenceSchedule.fromString("DAILY"))
        assertEquals(RecurrenceSchedule.Daily, RecurrenceSchedule.fromString(null))
        assertEquals(RecurrenceSchedule.Daily, RecurrenceSchedule.fromString(""))
    }

    @Test
    fun `fromString parses MWF correctly`() {
        val schedule = RecurrenceSchedule.fromString("MONDAY,WEDNESDAY,FRIDAY")
        assertEquals(MWF, schedule)
    }

    @Test
    fun `orDaily returns Daily for null`() {
        val s: RecurrenceSchedule? = null
        assertEquals(RecurrenceSchedule.Daily, s.orDaily())
    }

    @Test
    fun `orDaily returns the schedule when non-null`() {
        assertEquals(MWF, MWF.orDaily())
    }
}
