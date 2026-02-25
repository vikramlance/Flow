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
