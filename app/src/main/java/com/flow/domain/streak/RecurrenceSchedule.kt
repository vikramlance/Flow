package com.flow.domain.streak

import java.time.DayOfWeek

/**
 * Describes which calendar days a recurring task is scheduled to occur.
 *
 * - [Daily]  – every calendar day is a scheduled day.
 * - [Weekly] – only the specified subset of [DayOfWeek] values are scheduled days.
 *              Completing the task on a non-scheduled day is ignored by the streak
 *              calculator (it neither extends nor resets the streak).
 *
 * ### Null-schedule fallback
 * A task whose `recurrenceSchedule` field is `null` is treated as [Daily].
 * Use the [orDaily] extension to apply this convention at call sites:
 * ```kotlin
 * val effective: RecurrenceSchedule = task.recurrenceSchedule.orDaily()
 * ```
 */
sealed class RecurrenceSchedule {

    /** Every calendar day is a scheduled day. */
    object Daily : RecurrenceSchedule() {
        override fun toString() = "Daily"
    }

    /**
     * Only [days] are scheduled days.
     *
     * Example — Monday / Wednesday / Friday:
     * ```kotlin
     * Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
     * ```
     *
     * @throws IllegalArgumentException if [days] is empty.
     */
    data class Weekly(val days: Set<DayOfWeek>) : RecurrenceSchedule() {
        init {
            require(days.isNotEmpty()) {
                "Weekly schedule must contain at least one DayOfWeek."
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns `true` when [dayOfWeek] is a scheduled day according to this schedule.
     *
     * Always `true` for [Daily]; for [Weekly] only when [dayOfWeek] is in [Weekly.days].
     */
    fun isScheduled(dayOfWeek: DayOfWeek): Boolean = when (this) {
        is Daily  -> true
        is Weekly -> dayOfWeek in days
    }

    companion object {
        /**
         * Null-safety helper: returns the receiver if non-null, otherwise [Daily].
         *
         * Usage:
         * ```kotlin
         * val schedule = task.recurrenceSchedule.orDaily()
         * ```
         */
        fun RecurrenceSchedule?.orDaily(): RecurrenceSchedule = this ?: Daily

        /**
         * Convenience factory that parses a comma-separated string of
         * [DayOfWeek] names (e.g. `"MONDAY,WEDNESDAY,FRIDAY"`) into a
         * [Weekly] schedule.  Returns [Daily] for `"DAILY"` or a blank string.
         *
         * Useful when the value is stored as a plain string in the database.
         */
        fun fromString(value: String?): RecurrenceSchedule {
            if (value.isNullOrBlank() || value.trim().uppercase() == "DAILY") return Daily
            val days = value.split(",")
                .map { it.trim().uppercase() }
                .mapTo(mutableSetOf()) { DayOfWeek.valueOf(it) }
            return Weekly(days)
        }
    }
}
