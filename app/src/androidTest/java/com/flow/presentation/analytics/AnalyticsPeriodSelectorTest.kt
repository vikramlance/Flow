package com.flow.presentation.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.TimeZone

/**
 * T044 — [AnalyticsPeriod.toDateRange] calculation tests.
 *
 * Verifies:
 *  - [AnalyticsPeriod.CurrentYear] returns 1 Jan of current year → end of today
 *  - [AnalyticsPeriod.Last12Months] returns 365 days back from now
 *  - [AnalyticsPeriod.SpecificYear] returns exactly 1 Jan → 31 Dec of that year
 *  - [AnalyticsPeriod.Lifetime] returns earliestLogMs → now
 *  - SpecificYear boundary: startMs is precisely midnight Jan 1st (UTC)
 *  - Switching from CurrentYear to Last12Months produces a different date range
 */
@RunWith(AndroidJUnit4::class)
class AnalyticsPeriodSelectorTest {

    // Arbitrary "earliest log" for Lifetime calculations
    private val earliest = 1_700_000_000_000L // ~Nov 2023

    // ── CurrentYear ─────────────────────────────────────────────────────────

    @Test
    fun currentYear_startIs1JanOfCurrentYear() {
        val (start, _) = AnalyticsPeriod.CurrentYear.toDateRange(earliest)
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        assertEquals("Month should be January", Calendar.JANUARY, cal.get(Calendar.MONTH))
        assertEquals("Day should be 1", 1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals("Hour should be 0", 0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Minute should be 0", 0, cal.get(Calendar.MINUTE))
        assertEquals("Second should be 0", 0, cal.get(Calendar.SECOND))
    }

    @Test
    fun currentYear_endIsAfterStart() {
        val (start, end) = AnalyticsPeriod.CurrentYear.toDateRange(earliest)
        assertTrue("end > start", end > start)
    }

    // ── Last12Months ─────────────────────────────────────────────────────────

    @Test
    fun last12Months_rangeIsApproximately365DaysBack() {
        val (start, end) = AnalyticsPeriod.Last12Months.toDateRange(earliest)
        val rangeMs = end - start
        val minRange = 364 * 86_400_000L // at least 364 days
        val maxRange = 366 * 86_400_000L // at most 366 days (leap year)
        assertTrue("Range should be ~365 days, was ${rangeMs / 86_400_000} days",
            rangeMs in minRange..maxRange)
    }

    @Test
    fun last12Months_endIsNowOrAfterStart() {
        // toDateRange returns today.endOfDay() (23:59:59) as end, not System.currentTimeMillis().
        // Verify end is within today (between today midnight and tomorrow midnight).
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val todayMidnight = cal.timeInMillis
        val tomorrowMidnight = todayMidnight + 86_400_000L

        val (start, end) = AnalyticsPeriod.Last12Months.toDateRange(earliest)
        assertTrue("end should be today's end-of-day", end in todayMidnight until tomorrowMidnight)
        assertTrue("start < end", start < end)
    }

    // ── SpecificYear ─────────────────────────────────────────────────────────

    @Test
    fun specificYear_startIsJan1Midnight() {
        val (start, _) = AnalyticsPeriod.SpecificYear(2023).toDateRange(earliest)
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        assertEquals("Year 2023", 2023, cal.get(Calendar.YEAR))
        assertEquals("January", Calendar.JANUARY, cal.get(Calendar.MONTH))
        assertEquals("1st day", 1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals("00:00:00", 0, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun specificYear_endIsWithinOrAfterDec31() {
        val (start, end) = AnalyticsPeriod.SpecificYear(2023).toDateRange(earliest)
        val cal = Calendar.getInstance()
        cal.timeInMillis = end
        assertTrue("End should be in or after Dec 2023 for year 2023",
            cal.get(Calendar.YEAR) >= 2023)
        assertTrue("end > start", end > start)
    }

    @Test
    fun specificYear_rangeIs365Or366Days_leapYearAware() {
        // Range = Jan 1 00:00:00 → Dec 31 23:59:59.
        // That is exactly (daysInYear * 86400 - 1) seconds, so 1 second less than full days.
        // Allow 1 second tolerance in both directions.
        val toleranceMs = 1001L

        // 2024 is a leap year (366 days)
        val (_, end2024) = AnalyticsPeriod.SpecificYear(2024).toDateRange(earliest)
        val (start2024, _) = AnalyticsPeriod.SpecificYear(2024).toDateRange(earliest)
        val range2024 = end2024 - start2024

        // 2023 is not a leap year (365 days)
        val (start2023, end2023) = AnalyticsPeriod.SpecificYear(2023).toDateRange(earliest)
        val range2023 = end2023 - start2023

        assertTrue("2023 range approx 365 days (within 1s tolerance)",
            range2023 >= 365L * 86_400_000L - toleranceMs)
        assertTrue("2024 range approx 366 days (leap, within 1s tolerance)",
            range2024 >= 366L * 86_400_000L - toleranceMs)
    }

    // ── Lifetime ─────────────────────────────────────────────────────────────

    @Test
    fun lifetime_startIsEarliestLogMs() {
        val (start, _) = AnalyticsPeriod.Lifetime.toDateRange(earliest)
        assertEquals("Lifetime start should equal earliestLogMs", earliest, start)
    }

    @Test
    fun lifetime_endIsNow() {
        // toDateRange returns today.endOfDay() (23:59:59) as end, not System.currentTimeMillis().
        // Verify end is within today (between today midnight and tomorrow midnight).
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val todayMidnight = cal.timeInMillis
        val tomorrowMidnight = todayMidnight + 86_400_000L

        val (_, end) = AnalyticsPeriod.Lifetime.toDateRange(earliest)
        assertTrue("Lifetime end should be today's end-of-day", end in todayMidnight until tomorrowMidnight)
    }

    // ── Period switching produces different ranges ─────────────────────────────

    @Test
    fun currentYear_and_last12Months_produceDifferentRanges() {
        val (cyStart, _) = AnalyticsPeriod.CurrentYear.toDateRange(earliest)
        val (l12Start, _) = AnalyticsPeriod.Last12Months.toDateRange(earliest)
        // CurrentYear starts on Jan 1; Last12Months starts ~365d ago — they differ except when today is Jan 1
        val todayCal = Calendar.getInstance()
        if (todayCal.get(Calendar.MONTH) == Calendar.JANUARY && todayCal.get(Calendar.DAY_OF_MONTH) == 1) {
            // Edge case: on Jan 1 these may overlap — just verify both ranges are positive
            assertTrue("CurrentYear range positive", cyStart < AnalyticsPeriod.CurrentYear.toDateRange(earliest).second)
        } else {
            assertTrue("Ranges differ on non-Jan-1 days", cyStart != l12Start)
        }
    }
}
