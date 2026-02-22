package com.flow.presentation.analytics

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * T013 — UI-layer value type representing the selected analytics view window.
 *
 * ⚠ Architecture boundary: This type MUST NOT be passed below the ViewModel.
 * Call [toDateRange] in the ViewModel to convert to (startMs, endMs) before
 * invoking any repository method.
 */
sealed class AnalyticsPeriod {
    /** 1 Jan – 31 Dec of the current calendar year. */
    object CurrentYear : AnalyticsPeriod()

    /** Rolling window: today minus 364 days (inclusive) through today. */
    object Last12Months : AnalyticsPeriod()

    /** Specific past calendar year, 1 Jan – 31 Dec year [year]. */
    data class SpecificYear(val year: Int) : AnalyticsPeriod()

    /** From the earliest recorded completion date through today. */
    object Lifetime : AnalyticsPeriod()
}

/**
 * Converts this period to a `(startMs, endMs)` epoch-millisecond range.
 *
 * @param earliestLogMs Used only for [AnalyticsPeriod.Lifetime]; represents the
 *                      earliest completion log date. Pass 0 if no data exists.
 * @param zone Device time-zone used for calendar arithmetic.
 */
fun AnalyticsPeriod.toDateRange(
    earliestLogMs: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Pair<Long, Long> {
    val today = LocalDate.now(zone)

    fun LocalDate.midnight(): Long =
        atStartOfDay(zone).toInstant().toEpochMilli()

    fun LocalDate.endOfDay(): Long =
        atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

    return when (this) {
        is AnalyticsPeriod.CurrentYear -> {
            val jan1 = LocalDate.of(today.year, 1, 1)
            val dec31 = YearMonth.of(today.year, 12).atEndOfMonth()
            jan1.midnight() to dec31.endOfDay()
        }
        is AnalyticsPeriod.Last12Months -> {
            val start = today.minusDays(364)
            start.midnight() to today.endOfDay()
        }
        is AnalyticsPeriod.SpecificYear -> {
            val jan1 = LocalDate.of(year, 1, 1)
            val dec31 = YearMonth.of(year, 12).atEndOfMonth()
            jan1.midnight() to dec31.endOfDay()
        }
        is AnalyticsPeriod.Lifetime -> {
            val startMs = if (earliestLogMs > 0L) earliestLogMs else today.minusYears(1).midnight()
            startMs to today.endOfDay()
        }
    }
}
