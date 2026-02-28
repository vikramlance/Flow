package com.flow.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * T002 — RED test for [defaultEndTime] and [endTimeForDate].
 */
class DateUtilsDefaultTimeTest {

    @Test
    fun defaultEndTime_isToday2359() {
        val result = defaultEndTime()
        val cal = Calendar.getInstance().apply { timeInMillis = result }
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))

        // Must be on today's calendar date
        val today = Calendar.getInstance()
        assertEquals(today.get(Calendar.YEAR),         cal.get(Calendar.YEAR))
        assertEquals(today.get(Calendar.DAY_OF_YEAR),  cal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun endTimeForDate_sameDateAs_input_hour2359() {
        val inputMs = System.currentTimeMillis()
        val result = endTimeForDate(inputMs)
        val inputCal  = Calendar.getInstance().apply { timeInMillis = inputMs }
        val resultCal = Calendar.getInstance().apply { timeInMillis = result }

        assertEquals(inputCal.get(Calendar.YEAR),        resultCal.get(Calendar.YEAR))
        assertEquals(inputCal.get(Calendar.DAY_OF_YEAR), resultCal.get(Calendar.DAY_OF_YEAR))
        assertEquals(23, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun endTimeForDate_arbitraryDate_hour2359() {
        // Use a fixed date: 2026-01-15 at some arbitrary millis
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 15, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val result = endTimeForDate(cal.timeInMillis)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, resultCal.get(Calendar.MONTH))
        assertEquals(15, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun mergeDateTime_combinesDatePartAndTimePart() {
        val datePart = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 10, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val timePart = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 14, 37, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val result = mergeDateTime(datePart, timePart)
        val resultCal = Calendar.getInstance().apply { timeInMillis = result }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, resultCal.get(Calendar.MONTH))
        assertEquals(10, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(37, resultCal.get(Calendar.MINUTE))
    }
}
