package com.flow.presentation.home

import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T018 — Unit tests for [urgencyLevel] extension on [TaskEntity].
 *
 * Tests the four urgency levels based on elapsed % = (today - startDate) / (dueDate - startDate).
 * All thresholds: GREEN < 50 %, YELLOW 50–80 %, ORANGE ≥ 80 %; NONE for edge cases.
 *
 * Run with: ./gradlew :app:test
 */
class UrgencyLevelTest {

    /** Build a task with known start/due dates and compute urgency given an explicit "today". */
    private fun urgencyAt(startMs: Long, dueMs: Long, nowMs: Long, status: TaskStatus = TaskStatus.TODO): UrgencyLevel {
        val task = TaskEntity(
            id        = 1L,
            title     = "Test",
            startDate = startMs,
            dueDate   = dueMs,
            status    = status
        )
        return task.urgencyLevel(today = nowMs)
    }

    private val baseMs = 1_700_000_000_000L          // arbitrary fixed epoch
    private val day    = 86_400_000L                  // 1 day in ms

    // total window = 10 days
    private val start = baseMs
    private val due   = baseMs + 10 * day

    // ── Boundary tests ────────────────────────────────────────────────────────

    @Test
    fun `15 percent elapsed returns GREEN`() {
        val now = start + (day * 1.5).toLong()   // ~15 %
        assertEquals(UrgencyLevel.GREEN, urgencyAt(start, due, now))
    }

    @Test
    fun `49 percent elapsed returns GREEN`() {
        val now = start + (day * 4.9).toLong()
        assertEquals(UrgencyLevel.GREEN, urgencyAt(start, due, now))
    }

    @Test
    fun `50 percent elapsed returns YELLOW`() {
        val now = start + 5 * day                // exactly 50 %
        assertEquals(UrgencyLevel.YELLOW, urgencyAt(start, due, now))
    }

    @Test
    fun `75 percent elapsed returns YELLOW`() {
        val now = start + (day * 7.5).toLong()   // 75 %
        assertEquals(UrgencyLevel.YELLOW, urgencyAt(start, due, now))
    }

    @Test
    fun `80 percent elapsed returns ORANGE`() {
        val now = start + 8 * day                // exactly 80 %
        assertEquals(UrgencyLevel.ORANGE, urgencyAt(start, due, now))
    }

    @Test
    fun `85 percent elapsed returns ORANGE`() {
        val now = start + (day * 8.5).toLong()   // 85 %
        assertEquals(UrgencyLevel.ORANGE, urgencyAt(start, due, now))
    }

    // ── Edge cases → NONE ─────────────────────────────────────────────────────

    @Test
    fun `completed task returns NONE`() {
        val now = start + 5 * day
        assertEquals(UrgencyLevel.NONE, urgencyAt(start, due, now, status = TaskStatus.COMPLETED))
    }

    @Test
    fun `no dueDate returns NONE`() {
        val task = TaskEntity(id = 2L, title = "No due date", startDate = start, dueDate = null)
        assertEquals(UrgencyLevel.NONE, task.urgencyLevel(today = start + 3 * day))
    }

    @Test
    fun `overdue task returns NONE`() {
        val now = due + day     // past due date
        assertEquals(UrgencyLevel.NONE, urgencyAt(start, due, now))
    }

    @Test
    fun `startDate equals dueDate returns ORANGE`() {
        val sameDay = baseMs + 5 * day
        val now     = sameDay - 1L     // just before due (not overdue), but window = 0
        assertEquals(UrgencyLevel.ORANGE, urgencyAt(sameDay, sameDay, now))
    }

    @Test
    fun `task before startDate returns GREEN`() {
        val now = start - day           // today is before start
        assertEquals(UrgencyLevel.GREEN, urgencyAt(start, due, now))
    }

    @Test
    fun `in progress task still gets urgency color`() {
        val now = start + 9 * day      // 90 % elapsed, IN_PROGRESS
        assertEquals(UrgencyLevel.ORANGE, urgencyAt(start, due, now, status = TaskStatus.IN_PROGRESS))
    }
}
