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
import java.util.Calendar

/**
 * TU01 — Instrumented unit tests for [TaskDao].
 *
 * Verifies:
 * (a) [TaskDao.getHomeScreenTasks] returns correct tasks per the 4 visibility rules
 * (b) [TaskDao.getCompletedNonRecurringTasks] returns only completed non-recurring tasks
 * (c) [TaskDao.getAllTasks] sort order is [createdAt ASC]
 */
@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TaskDao

    // Midnight timestamps used across tests
    private val todayStart: Long = buildMidnight(0)
    private val tomorrowStart: Long = todayStart + 86_400_000L
    private val yesterdayStart: Long = buildMidnight(-1)
    private val tomorrowEnd: Long = buildMidnight(2)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.taskDao()
    }

    @After
    fun tearDown() = db.close()

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildMidnight(offsetDays: Int): Long =
        Calendar.getInstance().run {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, offsetDays)
            timeInMillis
        }

    private suspend fun insert(vararg tasks: TaskEntity): List<Long> =
        tasks.map { dao.insertTask(it) }

    // ── getAllTasks sort order ─────────────────────────────────────────────────

    @Test
    fun getAllTasks_sortsBy_createdAt_ASC() = runTest {
        val t1 = TaskEntity(title = "First", createdAt = 1000L)
        val t2 = TaskEntity(title = "Second", createdAt = 2000L)
        val t3 = TaskEntity(title = "Third", createdAt = 3000L)
        insert(t3, t1, t2) // Insert out of order

        val result = dao.getAllTasks().first()
        assertEquals("First", result[0].title)
        assertEquals("Second", result[1].title)
        assertEquals("Third", result[2].title)
    }

    // ── getHomeScreenTasks — Rule 1: recurring always shown ───────────────────

    @Test
    fun getHomeScreenTasks_recurringTask_alwaysIncluded() = runTest {
        insert(TaskEntity(title = "Recurring", isRecurring = true))
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertEquals(1, result.size)
        assertEquals("Recurring", result[0].title)
    }

    @Test
    fun getHomeScreenTasks_completedRecurringTask_stillIncluded() = runTest {
        insert(TaskEntity(title = "Done Recurring", isRecurring = true, status = TaskStatus.COMPLETED))
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertEquals(1, result.size)
    }

    // ── getHomeScreenTasks — Rule 2: dated tasks due today ───────────────────

    @Test
    fun getHomeScreenTasks_taskDueToday_isIncluded() = runTest {
        insert(TaskEntity(title = "Due Today", dueDate = todayStart + 3600_000L))
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertEquals(1, result.size)
        assertEquals("Due Today", result[0].title)
    }

    @Test
    fun getHomeScreenTasks_taskDueTomorrow_notIncluded() = runTest {
        insert(TaskEntity(title = "Due Tomorrow", dueDate = tomorrowStart + 1000L))
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertTrue(result.isEmpty())
    }

    // ── getHomeScreenTasks — Rule 3: overdue dated tasks (not completed) ──────

    @Test
    fun getHomeScreenTasks_overdueTask_isIncluded() = runTest {
        insert(TaskEntity(title = "Overdue", dueDate = yesterdayStart + 1000L, status = TaskStatus.TODO))
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertEquals(1, result.size)
        assertEquals("Overdue", result[0].title)
    }

    @Test
    fun getHomeScreenTasks_completedOverdueTask_notIncluded() = runTest {
        insert(
            TaskEntity(
                title = "Completed Overdue",
                dueDate = yesterdayStart + 1000L,
                status = TaskStatus.COMPLETED,
                completionTimestamp = yesterdayStart + 2000L
            )
        )
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertTrue(result.isEmpty())
    }

    // ── getHomeScreenTasks — Rule 4: general undated non-recurring tasks ──────

    @Test
    fun getHomeScreenTasks_undatedIncompletNonRecurring_isIncluded() = runTest {
        insert(TaskEntity(title = "Undated TODO", isRecurring = false, dueDate = null))
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertEquals(1, result.size)
    }

    @Test
    fun getHomeScreenTasks_undatedCompletedTodayNonRecurring_isIncluded() = runTest {
        insert(
            TaskEntity(
                title = "Completed Today",
                isRecurring = false,
                dueDate = null,
                status = TaskStatus.COMPLETED,
                completionTimestamp = todayStart + 1000L
            )
        )
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertEquals(1, result.size)
    }

    @Test
    fun getHomeScreenTasks_undatedCompletedYesterdayNonRecurring_notIncluded() = runTest {
        insert(
            TaskEntity(
                title = "Done Yesterday",
                isRecurring = false,
                dueDate = null,
                status = TaskStatus.COMPLETED,
                completionTimestamp = yesterdayStart + 1000L
            )
        )
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertTrue(result.isEmpty())
    }

    // ── getHomeScreenTasks — sort order ──────────────────────────────────────

    @Test
    fun getHomeScreenTasks_sortedBy_createdAt_ASC() = runTest {
        insert(
            TaskEntity(title = "B", isRecurring = true, createdAt = 2000L),
            TaskEntity(title = "A", isRecurring = true, createdAt = 1000L),
            TaskEntity(title = "C", isRecurring = true, createdAt = 3000L)
        )
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertEquals(listOf("A", "B", "C"), result.map { it.title })
    }

    // ── getCompletedNonRecurringTasks ─────────────────────────────────────────

    @Test
    fun getCompletedNonRecurringTasks_returnsOnlyCompletedNonRecurring() = runTest {
        insert(
            TaskEntity(title = "Done One-Off",  isRecurring = false, completionTimestamp = todayStart + 1000L),
            TaskEntity(title = "Incomplete",    isRecurring = false),                              // no completionTimestamp
            TaskEntity(title = "Done Recurring", isRecurring = true, completionTimestamp = todayStart + 2000L)
        )
        val result = dao.getCompletedNonRecurringTasks().first()
        assertEquals(1, result.size)
        assertEquals("Done One-Off", result[0].title)
    }

    @Test
    fun getCompletedNonRecurringTasks_orderedBy_completionTimestamp_DESC() = runTest {
        insert(
            TaskEntity(title = "Older", isRecurring = false, completionTimestamp = todayStart + 500L),
            TaskEntity(title = "Newer", isRecurring = false, completionTimestamp = todayStart + 999L)
        )
        val result = dao.getCompletedNonRecurringTasks().first()
        assertEquals("Newer", result[0].title)
        assertEquals("Older", result[1].title)
    }
}
