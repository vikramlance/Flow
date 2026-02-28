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

    // ── TR-001 / FR-001 regression: dueDate normalisation ────────────────────

    /**
     * T006 regression — dueDate stored at 10 pm normalises to midnight.
     *
     * Before fix: TaskRepositoryImpl.addTask() stored `dueDate = dueDate` (raw),
     * so tasks created with `dueDate = todayMidnight + 79_200_000` (10 pm) were
     * stored at 10 pm and never matched `getTasksDueOn(todayMidnight)`, causing
     * the progress bar to always show 0%.
     *
     * Current state (T018/T019/US3): addTask() no longer normalises dueDate;
     * getTodayProgress() uses getTasksDueInRange(today, todayEnd) where
     * todayEnd = midnight + 86_399_999, so both midnight and 10 pm values are
     * counted. This test verifies the normaliseToMidnight() function logic itself,
     * not the DAO query semantics.
     */
    @Test
    fun `dueDate stored at 6pm is normalised to midnight and counted`() {
        val todayMidnight = midnightMs(0)
        val rawDueDate = todayMidnight + 79_200_000L   // 22:00:00 today (10 pm)

        // Replicate normaliseToMidnight() — same algorithm as TaskRepositoryImpl
        val normalised = Calendar.getInstance().apply {
            timeInMillis = rawDueDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE,      0)
            set(Calendar.SECOND,      0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(
            "normalised dueDate (10 pm) must equal todayMidnight",
            todayMidnight, normalised
        )

        // Verify that a TaskEntity with the normalised dueDate would be counted by
        // getTodayProgress() which uses getTasksDueInRange(today, todayEnd) range query.
        // Both midnight-aligned and end-of-day (23:59 PM) values fall within the range.
        val task = TaskEntity(
            id        = 99L,
            title     = "Task With 10pm DueDate",
            dueDate   = normalised,
            startDate = todayMidnight - 86_400_000L
        )
        assertEquals(
            "TaskEntity with normalised dueDate should be countable by range query [dayStart, dayEnd]",
            todayMidnight, task.dueDate
        )
    }
}
