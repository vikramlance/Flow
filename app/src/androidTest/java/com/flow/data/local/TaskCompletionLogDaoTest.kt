package com.flow.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TU02 — Instrumented unit tests for [TaskCompletionLogDao].
 *
 * Verifies getAllCompletedLogs() returns only completed entries, ordered
 * by date DESC, then timestamp DESC.
 */
@RunWith(AndroidJUnit4::class)
class TaskCompletionLogDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TaskCompletionLogDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.taskCompletionLogDao()
    }

    @After
    fun tearDown() = db.close()

    private fun log(
        taskId: Long = 1L,
        date: Long = 1_000_000L,
        timestamp: Long = 1_000_000L,
        isCompleted: Boolean = true
    ) = TaskCompletionLog(
        taskId = taskId,
        date = date,
        timestamp = timestamp,
        isCompleted = isCompleted
    )

    // ── basic insert + retrieve ───────────────────────────────────────────────

    @Test
    fun getAllCompletedLogs_returnsInsertedLog() = runTest {
        dao.insertLog(log(taskId = 42L))
        val result = dao.getAllCompletedLogs().first()
        assertEquals(1, result.size)
        assertEquals(42L, result[0].taskId)
    }

    // ── isCompleted filter ────────────────────────────────────────────────────

    @Test
    fun getAllCompletedLogs_excludesNotCompleted() = runTest {
        dao.insertLog(log(taskId = 10L, isCompleted = true))
        dao.insertLog(log(taskId = 20L, isCompleted = false))
        val result = dao.getAllCompletedLogs().first()
        assertEquals(1, result.size)
        assertEquals(10L, result[0].taskId)
    }

    @Test
    fun getAllCompletedLogs_noLogsAtAll_returnsEmpty() = runTest {
        val result = dao.getAllCompletedLogs().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun getAllCompletedLogs_allNotCompleted_returnsEmpty() = runTest {
        dao.insertLog(log(isCompleted = false))
        dao.insertLog(log(isCompleted = false))
        assertTrue(dao.getAllCompletedLogs().first().isEmpty())
    }

    // ── ordering: date DESC, timestamp DESC ──────────────────────────────────

    @Test
    fun getAllCompletedLogs_orderedBy_dateDesc_timestampDesc() = runTest {
        // Day 2, timestamp A (latest)
        dao.insertLog(log(taskId = 1L, date = 2_000_000L, timestamp = 2_100_000L))
        // Day 2, timestamp B (earlier)
        dao.insertLog(log(taskId = 2L, date = 2_000_000L, timestamp = 2_000_000L))
        // Day 1
        dao.insertLog(log(taskId = 3L, date = 1_000_000L, timestamp = 1_000_000L))

        val result = dao.getAllCompletedLogs().first()
        // Ordered by date DESC, timestamp DESC
        assertEquals(2_100_000L, result[0].timestamp)
        assertEquals(2_000_000L, result[1].timestamp)
        assertEquals(1_000_000L, result[2].timestamp)
    }

    @Test
    fun getAllCompletedLogs_sameDay_orderedBy_timestampDesc() = runTest {
        dao.insertLog(log(taskId = 1L, date = 5_000_000L, timestamp = 5_100_000L))
        dao.insertLog(log(taskId = 2L, date = 5_000_000L, timestamp = 5_200_000L))
        val result = dao.getAllCompletedLogs().first()
        assertEquals(5_200_000L, result[0].timestamp)
        assertEquals(5_100_000L, result[1].timestamp)
    }

    @Test
    fun getAllCompletedLogs_multipleTaskIds_allReturned() = runTest {
        dao.insertLog(log(taskId = 10L))
        dao.insertLog(log(taskId = 20L))
        dao.insertLog(log(taskId = 30L))
        assertEquals(3, dao.getAllCompletedLogs().first().size)
    }

    // ── T007: getRecurringOnTimeCount ─────────────────────────────────────────

    @Test
    fun getRecurringOnTimeCount_countsLogsWithinScheduledDay() = runTest {
        // On-time: timestamp <= date + 86340000 (23h 59m)
        dao.insertLog(log(date = 1_000_000L, timestamp = 1_000_000L + 86_339_000L, isCompleted = true))  // just before cutoff
        dao.insertLog(log(date = 2_000_000L, timestamp = 2_000_000L + 1_000L,      isCompleted = true))  // early
        dao.insertLog(log(date = 3_000_000L, timestamp = 3_000_000L + 86_340_000L, isCompleted = true))  // exactly at cutoff
        // Late: timestamp > date + 86340000
        dao.insertLog(log(date = 4_000_000L, timestamp = 4_000_000L + 86_341_000L, isCompleted = true))  // 1 ms late
        dao.insertLog(log(date = 5_000_000L, timestamp = 5_000_000L + 100_000_000L, isCompleted = true)) // very late
        // Incomplete: should not count regardless of timing
        dao.insertLog(log(date = 6_000_000L, timestamp = 6_000_000L + 1_000L,      isCompleted = false))

        val count = dao.getRecurringOnTimeCount()
        assertEquals("3 on-time completed logs expected", 3, count)
    }

    // ── T008: getRecurringMissedCount ─────────────────────────────────────────

    @Test
    fun getRecurringMissedCount_countsPastIncompleteLogs() = runTest {
        val todayMidnight = System.currentTimeMillis().let { now ->
            now - (now % 86_400_000L)  // rough midnight; exact value used in production
        }
        val past1 = todayMidnight - 86_400_000L       // yesterday
        val past2 = todayMidnight - 2 * 86_400_000L  // two days ago
        val past3 = todayMidnight - 3 * 86_400_000L  // three days ago

        // 3 past incomplete (should be counted as missed)
        dao.insertLog(log(date = past1, isCompleted = false))
        dao.insertLog(log(date = past2, isCompleted = false))
        dao.insertLog(log(date = past3, isCompleted = false))
        // 2 today incomplete (date == todayMidnight — NOT past, not missed yet)
        dao.insertLog(log(date = todayMidnight, isCompleted = false))
        dao.insertLog(log(date = todayMidnight, isCompleted = false))
        // Past completed (should not count as missed)
        dao.insertLog(log(date = past1, isCompleted = true))

        val count = dao.getRecurringMissedCount(todayMidnight)
        assertEquals("3 past-incomplete logs should be counted as missed", 3, count)
    }

    // ── T009: getTotalCompletedLogCount ───────────────────────────────────────

    @Test
    fun getTotalCompletedLogCount_returnsAllCompletedLogs() = runTest {
        // 5 completed
        repeat(5) { i -> dao.insertLog(log(taskId = i.toLong() + 1, isCompleted = true)) }
        // 2 incomplete
        dao.insertLog(log(taskId = 10L, isCompleted = false))
        dao.insertLog(log(taskId = 11L, isCompleted = false))

        val count = dao.getTotalCompletedLogCount()
        assertEquals("5 completed logs expected", 5, count)
    }
}
