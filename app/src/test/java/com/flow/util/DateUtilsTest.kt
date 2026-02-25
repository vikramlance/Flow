package com.flow.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * T002 — Unit tests for [utcDateToLocalMidnight].
 *
 * Verifies the UTC-midnight → local-midnight conversion works correctly in
 * UTC+, UTC-, and UTC=0 timezones. Tests explicitly set [TimeZone.setDefault]
 * and restore the original timezone in [tearDown].
 */
class DateUtilsTest {

    private lateinit var originalTz: TimeZone

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Epoch millis for Feb 22 midnight UTC (2024 is a leap year, but any year works). */
    private val feb22UtcMidnight: Long = run {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.FEBRUARY, 22, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun localMidnightFor(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ─────────────────────────────────────────────────────────────────────────
    // UTC+ zone — picker returns Feb 22 UTC midnight, user is ahead of UTC
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun utcDateToLocalMidnight_utcPlusZone_returnsFeb22LocalMidnight() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo")) // UTC+9
        val result = utcDateToLocalMidnight(feb22UtcMidnight)
        val expected = localMidnightFor(2024, Calendar.FEBRUARY, 22)
        assertEquals("UTC+9: should return Feb 22 local midnight", expected, result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTC- zone — without this fix, raw normalisation gives Feb 21 (off-by-one)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun utcDateToLocalMidnight_utcMinusZone_returnsFeb22LocalMidnight_notFeb21() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York")) // UTC-5 / UTC-4
        val result = utcDateToLocalMidnight(feb22UtcMidnight)
        val expected = localMidnightFor(2024, Calendar.FEBRUARY, 22)
        assertEquals(
            "UTC-5: should return Feb 22 local midnight (not Feb 21 off-by-one)",
            expected, result
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTC=0 — identity case
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun utcDateToLocalMidnight_utcZone_returnsIdentical() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val result = utcDateToLocalMidnight(feb22UtcMidnight)
        assertEquals("UTC: result should equal input", feb22UtcMidnight, result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Idempotency — if the input is already a local midnight, re-applying
    // utcDateToLocalMidnight with UTC as device tz must be a no-op
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun utcDateToLocalMidnight_alreadyLocalMidnight_idempotentInUtcZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val localMidnight = localMidnightFor(2024, Calendar.FEBRUARY, 22)
        val result = utcDateToLocalMidnight(localMidnight)
        assertEquals("Idempotent: applying twice should give same result", localMidnight, result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LA-specific: picker for Mar 5 in UTC-8 should land on Mar 5 local midnight
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun utcDateToLocalMidnight_mar5_utcMinus8_returnsMar5LocalMidnight() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles")) // UTC-8
        val mar5UtcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2024, Calendar.MARCH, 5, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val result = utcDateToLocalMidnight(mar5UtcMidnight)
        val expected = localMidnightFor(2024, Calendar.MARCH, 5)
        assertEquals("UTC-8: Mar 5 UTC midnight → Mar 5 local midnight", expected, result)
    }
}
