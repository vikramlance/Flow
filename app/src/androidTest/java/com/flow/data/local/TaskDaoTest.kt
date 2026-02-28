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
    fun getHomeScreenTasks_taskDueTomorrow_isIncluded() = runTest {
        // FR-003: upcoming tasks (dueDate >= tomorrowStart) should appear on home screen
        insert(TaskEntity(title = "Due Tomorrow", dueDate = tomorrowStart + 1000L))
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertEquals(1, result.size)
        assertEquals("Due Tomorrow", result[0].title)
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

    // ── T007: FR-003 — future-dated task in getHomeScreenTasks ───────────────

    /**
     * T007 regression — getHomeScreenTasks returns future-dated incomplete task.
     *
     * Before fix: SQL had no clause for dueDate >= tomorrowStart, so tasks due
     * the day after tomorrow were invisible on the Home screen.
     *
     * After fix: the new OR clause picks them up.
     */
    @Test
    fun getHomeScreenTasks_returnsFutureDatedIncompleteTask() = runTest {
        // Task due the day after tomorrow — should appear in the home list
        insert(TaskEntity(title = "Future Task", dueDate = tomorrowStart + 86_400_000L, status = TaskStatus.TODO))
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertEquals(1, result.size)
        assertEquals("Future Task", result[0].title)
    }

    @Test
    fun getHomeScreenTasks_completedFutureTask_notIncluded() = runTest {
        insert(
            TaskEntity(
                title = "Completed Future",
                dueDate = tomorrowStart + 86_400_000L,
                status = TaskStatus.COMPLETED,
                completionTimestamp = todayStart + 1000L
            )
        )
        val result = dao.getHomeScreenTasks(todayStart, tomorrowStart).first()
        assertTrue(result.isEmpty())
    }

    // ── T018: FR-004 — future task excluded from todayProgress ───────────────

    /**
     * T018 regression — future-dated task not counted in today's progress.
     *
     * getTasksDueOn() uses exact == todayMidnight comparison; a task with
     * dueDate = tomorrowMidnight must NOT appear in the today count.
     */
    @Test
    fun future_task_not_counted_in_todayProgress() = runTest {
        insert(TaskEntity(title = "Due Tomorrow", dueDate = tomorrowStart))
        val result = dao.getTasksDueOn(todayStart).first()
        assertTrue("Future task must not be counted in today's progress", result.isEmpty())
    }

    // ── T005/US3: getTasksDueInRange — task at 11:59 PM is included ───────────

    /**
     * T005 regression — task with dueDate at today 23:59 must be in range.
     *
     * Before fix: getTodayProgress() used getTasksDueOn(midnight) which required
     * exact midnight timestamp; a task at 11:59 PM was invisible.
     *
     * After fix: getTasksDueInRange(today 00:00, today 23:59:59) includes it.
     */
    @Test
    fun getTasksDueInRange_returnsTaskWithDueDateAt1159pm() = runTest {
        val today1159pm = todayStart + 86_340_000L // 23:59:00
        insert(TaskEntity(title = "11:59 PM Task", dueDate = today1159pm))

        val result = dao.getTasksDueInRange(todayStart, todayStart + 86_399_999L).first()
        assertEquals(
            "getTasksDueInRange must include task with dueDate = today 23:59",
            1,
            result.size
        )
        assertEquals("11:59 PM Task", result[0].title)
    }

    // ── T006/US3: getTasksDueInRange — task due tomorrow is excluded ──────────

    @Test
    fun getTasksDueInRange_excludesTaskDueTomorrow() = runTest {
        insert(TaskEntity(title = "Tomorrow Task", dueDate = tomorrowStart))

        val result = dao.getTasksDueInRange(todayStart, todayStart + 86_399_999L).first()
        assertTrue(
            "getTasksDueInRange must NOT include tomorrow's task",
            result.isEmpty()
        )
    }

    @Test
    fun getTasksDueInRange_multipleTasksOnSameDay_allReturned() = runTest {
        val t1 = todayStart + 60_000L        // 12:01 AM
        val t2 = todayStart + 43_200_000L    // 12:00 PM
        val t3 = todayStart + 86_340_000L    // 11:59 PM
        insert(
            TaskEntity(title = "Morning", dueDate = t1),
            TaskEntity(title = "Noon",    dueDate = t2),
            TaskEntity(title = "Night",   dueDate = t3)
        )

        val result = dao.getTasksDueInRange(todayStart, todayStart + 86_399_999L).first()
        assertEquals("All 3 same-day tasks must be included in range query", 3, result.size)
    }
}
