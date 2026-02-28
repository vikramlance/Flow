package com.flow.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Converts a UTC midnight epoch (as returned by [DatePickerState.selectedDateMillis])
 * to the equivalent local-calendar midnight.
 *
 * Android's DatePickerDialog returns the selected date as midnight UTC regardless
 * of the device timezone. In UTC-N zones this means the selected "Feb 22" arrives as
 * "Feb 21 22:00 local", and normalising that time to local midnight gives Feb 21 —
 * one day off. This function undoes the UTC encoding before applying local midnight.
 *
 * Algorithm:
 *  1. Interpret [utcMidnight] in the UTC timezone to recover the calendar fields
 *     (year, month, day) the user selected.
 *  2. Rebuild those same calendar fields at local midnight (00:00:00.000 device tz).
 *
 * @param utcMidnight  Epoch millis where the selected calendar date is represented as
 *                     midnight UTC (e.g. the value from [DatePickerState.selectedDateMillis]).
 * @return             Epoch millis for 00:00:00.000 of that same calendar day in the
 *                     device's local timezone.
 */
fun utcDateToLocalMidnight(utcMidnight: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMidnight
    }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR,         utcCal.get(Calendar.YEAR))
        set(Calendar.MONTH,        utcCal.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY,  0)
        set(Calendar.MINUTE,       0)
        set(Calendar.SECOND,       0)
        set(Calendar.MILLISECOND,  0)
    }.timeInMillis
}

/**
 * T005 / C-001 — Returns epoch millis for **today** at 23:59:00.000 local time.
 * Called once at dialog-open time via `remember { defaultEndTime() }`.
 *
 * @return Epoch millis >= local midnight today and < local midnight tomorrow.
 */
fun defaultEndTime(): Long = endTimeForDate(System.currentTimeMillis())

/**
 * T005 / C-001 — Returns epoch millis for the same calendar day as [dateMillis] at
 * 23:59:00.000 local time.
 * Used when the user selects a different date — the time component stays at end-of-day.
 *
 * @param dateMillis Any millis value whose calendar date is used; time-of-day is ignored.
 * @return Epoch millis on the same calendar date as [dateMillis] at 23:59.
 */
fun endTimeForDate(dateMillis: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE,      59)
        set(Calendar.SECOND,       0)
        set(Calendar.MILLISECOND,  0)
    }.timeInMillis
}

/**
 * T005 / C-007 — Returns epoch millis combining the calendar **date** from [datePart]
 * with the hour/minute/second components from [timePart].
 *
 * Used in [AddTaskDialog]'s start-date picker so that choosing a different start date
 * preserves the clock time captured when the dialog was first opened.
 *
 * @param datePart  Millis whose year/month/day are used.
 * @param timePart  Millis whose hour/minute/second are used.
 * @return Epoch millis on [datePart]'s calendar date at [timePart]'s time-of-day.
 */
internal fun mergeDateTime(datePart: Long, timePart: Long): Long {
    val dateCal = Calendar.getInstance().apply { timeInMillis = datePart }
    val timeCal = Calendar.getInstance().apply { timeInMillis = timePart }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR,         dateCal.get(Calendar.YEAR))
        set(Calendar.MONTH,        dateCal.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY,  timeCal.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE,       timeCal.get(Calendar.MINUTE))
        set(Calendar.SECOND,       timeCal.get(Calendar.SECOND))
        set(Calendar.MILLISECOND,  0)
    }.timeInMillis
}
