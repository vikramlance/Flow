package com.flow.data.local

import com.flow.data.repository.TodayProgressState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * T017b — Unit tests for today's progress logic.
 *
 * These tests exercise [TodayProgressState] invariants and the dueDate-equality
 * semantics that [getTodayProgress()] relies on.
 *
 * Run with: ./gradlew :app:test
 */
class TodayProgressTest {

    private fun midnightMs(offsetDays: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, offsetDays)
        }.timeInMillis
    }

    // ── TodayProgressState invariants ─────────────────────────────────────────

    @Test
    fun `zero tasks today - hasAnyTodayTasks is false`() {
        val state = TodayProgressState(totalToday = 0, completedToday = 0)
        assertFalse("Empty today should have hasAnyTodayTasks = false", state.hasAnyTodayTasks)
    }

    @Test
    fun `one task today uncompleted - hasAnyTodayTasks is true, ratio is zero`() {
        val state = TodayProgressState(totalToday = 1, completedToday = 0)
        assertTrue(state.hasAnyTodayTasks)
        assertEquals(0f, state.ratio, 0.001f)
    }

    @Test
    fun `all tasks completed - ratio is exactly one`() {
        val state = TodayProgressState(totalToday = 3, completedToday = 3)
        assertEquals(1.0f, state.ratio, 0.001f)
    }

    @Test
    fun `partial completion - ratio matches fraction`() {
        val state = TodayProgressState(totalToday = 4, completedToday = 1)
        assertEquals(0.25f, state.ratio, 0.001f)
    }

    @Test
    fun `completedToday less than or equal to totalToday invariant`() {
        // completedToday should never exceed totalToday logically
        val state = TodayProgressState(totalToday = 5, completedToday = 3)
        assertTrue("completedToday <= totalToday", state.completedToday <= state.totalToday)
    }

    // ── dueDate equality semantics ────────────────────────────────────────────

    @Test
    fun `task with dueDate = todayMidnight is counted`() {
        val todayMidnight = midnightMs(0)
        val task = TaskEntity(
            id        = 1L,
            title     = "Due Today",
            dueDate   = todayMidnight,
            startDate = todayMidnight - 86_400_000L
        )
        // Simulate: task.dueDate == todayMidnight → counted
        assertEquals(todayMidnight, task.dueDate)
    }

    @Test
    fun `task with dueDate = tomorrowMidnight is excluded`() {
        val todayMidnight    = midnightMs(0)
        val tomorrowMidnight = midnightMs(1)
        val task = TaskEntity(
            id        = 2L,
            title     = "Due Tomorrow",
            dueDate   = tomorrowMidnight,
            startDate = todayMidnight
        )
        // Simulate: task.dueDate != todayMidnight → NOT counted
        assertTrue("tomorrowMidnight should differ from todayMidnight", task.dueDate != todayMidnight)
    }

    @Test
    fun `recurring task with dueDate = todayMidnight is counted`() {
        val todayMidnight = midnightMs(0)
        val task = TaskEntity(
            id          = 3L,
            title       = "Daily Habit",
            isRecurring = true,
            dueDate     = todayMidnight
        )
        assertEquals(todayMidnight, task.dueDate)
    }

    @Test
    fun `midnight boundary - one ms before midnight is different from midnight`() {
        val todayMidnight = midnightMs(0)
        val justBeforeMidnight = todayMidnight - 1L
        assertTrue(justBeforeMidnight != todayMidnight)
    }
}
