package com.flow.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * T021-test / US3 — RED test asserting that [EditTaskDialog] default-time helpers
 * correctly initialise the dueDate and startDate state when the task values are
 * absent (null dueDate; zero/epoch startDate).
 *
 * Targets two `internal` pure functions to be extracted in T021:
 *   - `resolveEditDialogDueDate(taskDueDate: Long?): Long?`
 *   - `resolveEditDialogStartDate(taskStartDate: Long): Long`
 *
 * These functions will be extracted so the initialization logic is unit-testable
 * without running the Compose runtime.
 *
 * Expected to be RED until T021 creates both functions.
 */
class EditTaskDialogDefaultTimeTest {

    // ── (a) null dueDate → default to 11:59 PM today ─────────────────────────

    @Test
    fun resolveEditDialogDueDate_whenNull_returnsEndOfToday() {
        val result = resolveEditDialogDueDate(null)

        val cal = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(
            "T021: null dueDate must initialise to HOUR_OF_DAY = 23",
            23,
            cal.get(Calendar.HOUR_OF_DAY)
        )
        assertEquals(
            "T021: null dueDate must initialise to MINUTE = 59",
            59,
            cal.get(Calendar.MINUTE)
        )
    }

    @Test
    fun resolveEditDialogDueDate_whenNonNull_passesThrough() {
        val explicit = System.currentTimeMillis() + 7_200_000L   // 2 hours from now
        val result   = resolveEditDialogDueDate(explicit)
        assertEquals(
            "T021: non-null dueDate must pass through unchanged",
            explicit,
            result
        )
    }

    // ── (b) startDate == 0L (epoch) → default to now ─────────────────────────

    @Test
    fun resolveEditDialogStartDate_whenZero_returnsNearNow() {
        val before = System.currentTimeMillis()
        val result = resolveEditDialogStartDate(0L)
        val after  = System.currentTimeMillis()

        assertTrue(
            "T021: startDate=0L must initialise to a time within the current 5-second window",
            result in before..(after + 5_000L)
        )
    }

    @Test
    fun resolveEditDialogStartDate_whenNonZero_passesThrough() {
        val explicit = System.currentTimeMillis() - 3_600_000L   // 1 hour ago
        val result   = resolveEditDialogStartDate(explicit)
        assertEquals(
            "T021: non-zero startDate must pass through unchanged",
            explicit,
            result
        )
    }
}
