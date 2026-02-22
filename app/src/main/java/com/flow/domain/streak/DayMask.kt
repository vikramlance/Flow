package com.flow.domain.streak

import java.time.DayOfWeek

/**
 * T002 — Bitmask helpers for recurring-task schedule encoding.
 *
 * Bit layout: bit 0 = Monday, bit 1 = Tuesday, …, bit 6 = Sunday.
 * A null mask means "every day" (DAILY behaviour).
 */
object DayMask {
    const val MON = 0b0000001
    const val TUE = 0b0000010
    const val WED = 0b0000100
    const val THU = 0b0001000
    const val FRI = 0b0010000
    const val SAT = 0b0100000
    const val SUN = 0b1000000
    const val ALL = 0b1111111

    /** Returns the bitmask value for a given [DayOfWeek]. */
    fun fromDayOfWeek(day: DayOfWeek): Int = when (day) {
        DayOfWeek.MONDAY    -> MON
        DayOfWeek.TUESDAY   -> TUE
        DayOfWeek.WEDNESDAY -> WED
        DayOfWeek.THURSDAY  -> THU
        DayOfWeek.FRIDAY    -> FRI
        DayOfWeek.SATURDAY  -> SAT
        DayOfWeek.SUNDAY    -> SUN
    }

    /**
     * Returns true if [scheduleMask] contains the bit for [day].
     * A null mask (DAILY) always returns true.
     */
    fun isScheduled(scheduleMask: Int?, day: DayOfWeek): Boolean {
        if (scheduleMask == null) return true
        return (scheduleMask and fromDayOfWeek(day)) != 0
    }

    /**
     * Converts a nullable bitmask to a [RecurrenceSchedule].
     * null → [RecurrenceSchedule.Daily]; non-null → [RecurrenceSchedule.Weekly]
     * with the corresponding [DayOfWeek] set.
     */
    fun toRecurrenceSchedule(scheduleMask: Int?): RecurrenceSchedule {
        if (scheduleMask == null) return RecurrenceSchedule.Daily
        val days = DayOfWeek.entries.filter { (scheduleMask and fromDayOfWeek(it)) != 0 }.toSet()
        return if (days.isEmpty()) RecurrenceSchedule.Daily
        else RecurrenceSchedule.Weekly(days)
    }

    /**
     * Converts a [RecurrenceSchedule] back to a nullable bitmask.
     * [RecurrenceSchedule.Daily] → null; [RecurrenceSchedule.Weekly] → the bitmask.
     */
    fun fromRecurrenceSchedule(schedule: RecurrenceSchedule): Int? = when (schedule) {
        is RecurrenceSchedule.Daily  -> null
        is RecurrenceSchedule.Weekly -> schedule.days.fold(0) { acc, day -> acc or fromDayOfWeek(day) }
    }
}

