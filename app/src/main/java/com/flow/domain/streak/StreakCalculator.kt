package com.flow.domain.streak

import com.flow.domain.streak.RecurrenceSchedule.Companion.orDaily
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure, Android-free streak calculator for recurring tasks.
 *
 * ## Concepts
 *
 * **Scheduled day** — a calendar day on which the task is expected to be completed
 * according to its [RecurrenceSchedule]:
 * - [RecurrenceSchedule.Daily]  → every calendar day is scheduled.
 * - [RecurrenceSchedule.Weekly] → only the specified [java.time.DayOfWeek] values are scheduled.
 *
 * Completing a task on a *non-scheduled* day is ignored by the calculator.
 * Skipping a non-scheduled day never breaks a streak.
 *
 * **Grace for today** — if [today] is a scheduled day that hasn't been completed yet,
 * the calculator treats it as "still open" (the day hasn't ended) and counts backwards
 * from the previous scheduled day.  This prevents a streak from appearing broken
 * mid-afternoon when the user hasn't ticked the task off yet.
 *
 * ## Complexity
 * - Time: O(D + C) where D = number of calendar days from the earliest completion
 *   to today, C = number of completion entries.  For a 3-year habit D ≈ 1095 — trivial.
 * - Space: O(C) for the completion set.
 *
 * ## No Android dependencies
 * Uses only `java.time` (available on the JVM and on Android API 26+, or via
 * desugaring).  Safe to test with plain JUnit on the JVM.
 *
 * ## Usage
 * ```kotlin
 * val result = StreakCalculator.compute(
 *     completionDates = task.logs.filter { it.isCompleted }.map { it.date },
 *     schedule        = task.recurrenceSchedule.orDaily(),
 *     today           = LocalDate.now(),
 * )
 * println("current=${result.currentStreak}  best=${result.longestStreak}")
 * ```
 */
object StreakCalculator {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Compute [StreakResult.currentStreak] and [StreakResult.longestStreak] for a
     * recurring task.
     *
     * @param completionDates
     *   Midnight-normalised epoch-millisecond values for days where the task was
     *   marked *completed* (`TaskCompletionLog.isCompleted == true`).
     *   Duplicates and unsorted order are both acceptable; they are handled internally.
     *   Pass an empty list if no completions exist.
     *
     * @param schedule
     *   The task's recurrence schedule.  `null` is treated as [RecurrenceSchedule.Daily]
     *   (see [RecurrenceSchedule.Companion.orDaily]).
     *
     * @param today
     *   Reference date for "today".  Defaults to [LocalDate.now].
     *   **Inject a fixed value in tests** to make them deterministic.
     *
     * @param zone
     *   Time-zone used when converting epoch-ms values to [LocalDate].
     *   Defaults to [ZoneId.systemDefault].  In most apps the device zone is correct;
     *   pass a fixed zone (e.g. `ZoneOffset.UTC`) in server-side or test contexts.
     *
     * @return [StreakResult.ZERO] when [completionDates] is empty.
     */
    fun compute(
        completionDates: List<Long>,
        schedule: RecurrenceSchedule?,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): StreakResult {
        val effectiveSchedule: RecurrenceSchedule = schedule.orDaily()

        // Convert epoch-ms list → deduplicated LocalDate set for O(1) membership checks.
        val completedDays: Set<LocalDate> = completionDates
            .mapTo(linkedSetOf()) { ms -> ms.toLocalDate(zone) }

        if (completedDays.isEmpty()) return StreakResult.ZERO

        val earliest: LocalDate = completedDays.min()

        val current = computeCurrentStreak(
            today         = today,
            completedDays = completedDays,
            schedule      = effectiveSchedule,
            earliest      = earliest,
        )

        val longest = computeLongestStreak(
            from          = earliest,
            to            = today,
            completedDays = completedDays,
            schedule      = effectiveSchedule,
        )

        // longest must always be >= current (both algorithms are consistent, but
        // guard against floating-point-style surprises from edge cases).
        return StreakResult(
            currentStreak          = current,
            longestStreak          = maxOf(current, longest.first),
            longestStreakStartDate  = longest.second?.let { it.atStartOfDay(zone).toInstant().toEpochMilli() },
        )
    }

    // -------------------------------------------------------------------------
    // currentStreak
    // -------------------------------------------------------------------------

    /**
     * Walk backwards from [today] through scheduled days counting consecutive
     * completed days.
     *
     * ### Grace period for today
     * If [today] is a scheduled day and has *not* been completed, the walk starts
     * from `today - 1` (today is still open).  If [today] is not a scheduled day
     * at all (e.g. Tuesday on a Mon/Wed/Fri schedule), the walk simply backs up
     * until it reaches the first scheduled day, which is Monday.
     *
     * ### Termination
     * The loop moves back one calendar day at a time.  It terminates when:
     * 1. A scheduled day is found that is NOT in [completedDays] → streak broken.
     * 2. [day] moves before [earliest] → no more completions can exist.
     */
    private fun computeCurrentStreak(
        today: LocalDate,
        completedDays: Set<LocalDate>,
        schedule: RecurrenceSchedule,
        earliest: LocalDate,
    ): Int {
        // Apply grace: if today is scheduled but not yet completed, pretend we are
        // counting from yesterday (the day is still open).
        val startDay: LocalDate = if (schedule.isScheduled(today.dayOfWeek) && today !in completedDays) {
            today.minusDays(1)
        } else {
            today
        }

        var streak = 0
        var day = startDay

        // Walk backwards one calendar day at a time.
        while (!day.isBefore(earliest)) {
            if (schedule.isScheduled(day.dayOfWeek)) {
                if (day in completedDays) {
                    streak++
                } else {
                    // Scheduled day was missed → streak is over.
                    break
                }
            }
            // Non-scheduled days are silently skipped (do not break the streak).
            day = day.minusDays(1)
        }

        return streak
    }

    // -------------------------------------------------------------------------
    // longestStreak
    // -------------------------------------------------------------------------

    /**
     * Scan all scheduled days from [from] to [to] (inclusive) in ascending order
     * and return the length of the longest unbroken run of completed scheduled days.
     *
     * Non-scheduled days are transparent: they neither contribute to nor reset the
     * running counter.  Only a scheduled day that is absent from [completedDays]
     * resets the counter to zero.
     */
    private fun computeLongestStreak(
        from: LocalDate,
        to: LocalDate,
        completedDays: Set<LocalDate>,
        schedule: RecurrenceSchedule,
    ): Pair<Int, LocalDate?> {
        var longest = 0
        var current = 0
        var currentStart: LocalDate? = null
        var longestStart: LocalDate? = null
        var day = from

        while (!day.isAfter(to)) {
            if (schedule.isScheduled(day.dayOfWeek)) {
                if (day in completedDays) {
                    if (current == 0) currentStart = day
                    current++
                    if (current > longest) {
                        longest = current
                        longestStart = currentStart
                    }
                } else {
                    current = 0  // missed a scheduled day
                    currentStart = null
                }
            }
            day = day.plusDays(1)
        }

        return Pair(longest, longestStart)
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private fun Long.toLocalDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
