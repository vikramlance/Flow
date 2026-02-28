package com.flow.data.repository

import com.flow.data.local.AchievementDao
import com.flow.data.local.AppDatabase
import com.flow.data.local.DailyProgressDao
import com.flow.data.local.DailyProgressEntity
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskCompletionLogDao
import com.flow.data.local.TaskDao
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.data.local.TaskStreakDao
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * T010/T011/T014/T019/T020/US1/US2/US4 — Unit tests for [TaskRepositoryImpl].
 *
 * Uses MockK to isolate the repository from Room DAO implementations.
 *
 * Covers:
 *  - T010/US1: addTask(isRecurring=true) sets startDate=midnight+60000, dueDate=midnight+86340000
 *  - T011/US1: refreshRecurringTasks applies 12:01AM / 11:59PM boundaries
 *  - T014/US2: addTask(isRecurring=false, dueDate=null) defaults dueDate to midnight+86340000
 *  - T019/US4: updateTask(non-completed) clears completionTimestamp
 *  - T020/US4: updateTaskStatus(COMPLETED → IN_PROGRESS) is valid and clears timestamp
 */
class TaskRepositoryImplTest {

    // ── Mocks ────────────────────────────────────────────────────────────────

    private val db                  : AppDatabase           = mockk(relaxed = true)
    private val taskDao             : TaskDao                = mockk(relaxed = true)
    private val dailyProgressDao    : DailyProgressDao       = mockk(relaxed = true)
    private val taskCompletionLogDao: TaskCompletionLogDao   = mockk(relaxed = true)
    private val taskStreakDao       : TaskStreakDao           = mockk(relaxed = true)
    private val achievementDao      : AchievementDao         = mockk(relaxed = true)

    private lateinit var repository: TaskRepositoryImpl

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun todayMidnight(): Long = Calendar.getInstance().run {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    @Before
    fun setUp() {
        val realRepo = TaskRepositoryImpl(
            db                   = db,
            taskDao              = taskDao,
            dailyProgressDao     = dailyProgressDao,
            taskCompletionLogDao = taskCompletionLogDao,
            taskStreakDao        = taskStreakDao,
            achievementDao       = achievementDao
        )
        // Spy the repository so we can stub checkAndAwardAchievements, which
        // calls db.withTransaction internally — a Room extension function that
        // cannot be isolated in pure JVM unit tests without Robolectric.
        repository = spyk(realRepo, recordPrivateCalls = false)
        coEvery { repository.checkAndAwardAchievements(any()) } just Runs
        // Default stubs for upsertDailyProgress (called by updateTaskStatus)
        coEvery { taskDao.getAllTasks() } returns flowOf(emptyList())
        coEvery { dailyProgressDao.insertOrUpdateProgress(any()) } returns Unit
    }

    // ── T010/US1: Recurring task time boundaries ──────────────────────────────

    @Test
    fun addRecurringTask_startsAt1201am_endsAt1159pm() = runTest {
        val capturedTask = slot<TaskEntity>()
        coEvery { taskDao.insertTask(capture(capturedTask)) } returns 1L

        repository.addTask(
            title        = "Daily Habit",
            startDate    = System.currentTimeMillis(),
            dueDate      = null,
            isRecurring  = true,
            scheduleMask = null
        )

        val midnight = todayMidnight()
        assertEquals(
            "Recurring task startDate must be midnight + 60 000 ms (12:01 AM)",
            midnight + 60_000L,
            capturedTask.captured.startDate
        )
        assertEquals(
            "Recurring task dueDate must be midnight + 86 340 000 ms (11:59 PM)",
            midnight + 86_340_000L,
            capturedTask.captured.dueDate
        )
    }

    @Test
    fun addRecurringTask_scheduleMaskPreserved() = runTest {
        val capturedTask = slot<TaskEntity>()
        coEvery { taskDao.insertTask(capture(capturedTask)) } returns 1L

        repository.addTask("Habit", System.currentTimeMillis(), null, isRecurring = true, scheduleMask = 0b0011110) // Mon-Thu

        assertEquals(0b0011110, capturedTask.captured.scheduleMask)
    }

    // ── T011/US1: refreshRecurringTasks applies correct boundaries ────────────

    @Test
    fun refreshRecurringTasks_appliesCorrectTimeBoundaries() = runTest {
        val yesterday = todayMidnight() - 86_400_000L
        val completedTask = TaskEntity(
            id                  = 1L,
            title               = "Completed Habit",
            isRecurring         = true,
            status              = TaskStatus.COMPLETED,
            completionTimestamp = yesterday + 1000L // completed yesterday
        )
        coEvery { taskDao.getAllTasks() } returns flowOf(listOf(completedTask))

        val capturedUpdate = slot<TaskEntity>()
        coEvery { taskDao.updateTask(capture(capturedUpdate)) } returns Unit

        repository.refreshRecurringTasks()

        val midnight = todayMidnight()
        assertEquals(
            "Refreshed recurring task startDate must be midnight + 60 000 ms",
            midnight + 60_000L,
            capturedUpdate.captured.startDate
        )
        assertEquals(
            "Refreshed recurring task dueDate must be midnight + 86 340 000 ms",
            midnight + 86_340_000L,
            capturedUpdate.captured.dueDate
        )
        assertEquals(TaskStatus.TODO, capturedUpdate.captured.status)
        assertNull(capturedUpdate.captured.completionTimestamp)
    }

    @Test
    fun refreshRecurringTasks_completedToday_notRefreshed() = runTest {
        val today = todayMidnight()
        val todayTask = TaskEntity(
            id                  = 2L,
            title               = "Already done today",
            isRecurring         = true,
            status              = TaskStatus.COMPLETED,
            completionTimestamp = today + 1000L // completed today, not yesterday
        )
        coEvery { taskDao.getAllTasks() } returns flowOf(listOf(todayTask))

        repository.refreshRecurringTasks()

        coVerify(exactly = 0) { taskDao.updateTask(any()) }
    }

    // ── T014/US2: Non-recurring null dueDate defaults to end of today ─────────

    @Test
    fun addNonRecurringTask_nullDueDate_defaultsToEndOfToday() = runTest {
        val capturedTask = slot<TaskEntity>()
        coEvery { taskDao.insertTask(capture(capturedTask)) } returns 2L

        repository.addTask(
            title        = "One-time task",
            startDate    = System.currentTimeMillis(),
            dueDate      = null,
            isRecurring  = false,
            scheduleMask = null
        )

        val midnight = todayMidnight()
        assertEquals(
            "Non-recurring task with null dueDate must default to midnight + 86 340 000 ms (11:59 PM)",
            midnight + 86_340_000L,
            capturedTask.captured.dueDate
        )
    }

    @Test
    fun addNonRecurringTask_explicitDueDate_preserved() = runTest {
        val capturedTask = slot<TaskEntity>()
        coEvery { taskDao.insertTask(capture(capturedTask)) } returns 3L

        val tomorrow = todayMidnight() + 86_400_000L
        repository.addTask(
            title        = "Task with explicit due date",
            startDate    = System.currentTimeMillis(),
            dueDate      = tomorrow,
            isRecurring  = false,
            scheduleMask = null
        )

        assertEquals(
            "Explicit dueDate must be preserved (normalised to midnight of that day)",
            todayMidnight() + 86_400_000L,
            capturedTask.captured.dueDate
        )
    }

    // ── T019/US4: updateTask clears completionTimestamp when not COMPLETED ────

    @Test
    fun updateTask_whenStatusNotCompleted_clearsCompletionTimestamp() = runTest {
        val existingTask = TaskEntity(
            id                  = 10L,
            title               = "In Progress Task",
            status              = TaskStatus.IN_PROGRESS,
            completionTimestamp = System.currentTimeMillis() // was once completed
        )
        coEvery { taskDao.getTaskById(10L) } returns existingTask

        val capturedUpdate = slot<TaskEntity>()
        coEvery { taskDao.updateTask(capture(capturedUpdate)) } returns Unit

        // Call updateTask with IN_PROGRESS status
        repository.updateTask(existingTask.copy(status = TaskStatus.IN_PROGRESS))

        assertNull(
            "updateTask on IN_PROGRESS task must NOT carry forward completionTimestamp",
            capturedUpdate.captured.completionTimestamp
        )
    }

    @Test
    fun updateTask_whenStatusCompleted_preservesExistingTimestamp() = runTest {
        val ts = 1_700_000_000_000L
        val existingTask = TaskEntity(
            id                  = 11L,
            title               = "Completed Task",
            status              = TaskStatus.COMPLETED,
            completionTimestamp = ts
        )
        coEvery { taskDao.getTaskById(11L) } returns existingTask

        val capturedUpdate = slot<TaskEntity>()
        coEvery { taskDao.updateTask(capture(capturedUpdate)) } returns Unit

        repository.updateTask(existingTask) // same task, COMPLETED status

        assertEquals(
            "updateTask on COMPLETED task must preserve existing completionTimestamp",
            ts,
            capturedUpdate.captured.completionTimestamp
        )
    }

    // ── T020/US4: updateTaskStatus COMPLETED → IN_PROGRESS is allowed ─────────

    @Test
    fun updateTaskStatus_completedToInProgress_isAllowedAndClearsTimestamp() = runTest {
        val ts = 1_700_000_000_000L
        val completedTask = TaskEntity(
            id                  = 20L,
            title               = "Completed → back to In Progress",
            status              = TaskStatus.COMPLETED,
            completionTimestamp = ts,
            isRecurring         = false
        )

        val capturedUpdate = slot<TaskEntity>()
        coEvery { taskDao.updateTask(capture(capturedUpdate)) } returns Unit

        repository.updateTaskStatus(completedTask, TaskStatus.IN_PROGRESS)

        // Transition must be permitted (updateTask must have been called)
        coVerify(exactly = 1) { taskDao.updateTask(any()) }

        assertEquals(TaskStatus.IN_PROGRESS, capturedUpdate.captured.status)
        assertNull(
            "COMPLETED → IN_PROGRESS must clear completionTimestamp",
            capturedUpdate.captured.completionTimestamp
        )
    }

    @Test
    fun updateTaskStatus_completedToTodo_isAllowedAndClearsTimestamp() = runTest {
        val ts = 1_700_000_000_000L
        val completedTask = TaskEntity(
            id                  = 21L,
            title               = "Undo completion",
            status              = TaskStatus.COMPLETED,
            completionTimestamp = ts,
            isRecurring         = false
        )

        val capturedUpdate = slot<TaskEntity>()
        coEvery { taskDao.updateTask(capture(capturedUpdate)) } returns Unit

        repository.updateTaskStatus(completedTask, TaskStatus.TODO)

        assertEquals(TaskStatus.TODO, capturedUpdate.captured.status)
        assertNull(
            "COMPLETED → TODO must clear completionTimestamp",
            capturedUpdate.captured.completionTimestamp
        )
    }

    @Test
    fun updateTaskStatus_inProgressToCompleted_setsTimestamp() = runTest {
        val inProgressTask = TaskEntity(
            id                  = 22L,
            title               = "Complete it",
            status              = TaskStatus.IN_PROGRESS,
            completionTimestamp = null,
            isRecurring         = false
        )

        val capturedUpdate = slot<TaskEntity>()
        coEvery { taskDao.updateTask(capture(capturedUpdate)) } returns Unit

        repository.updateTaskStatus(inProgressTask, TaskStatus.COMPLETED)

        assertEquals(TaskStatus.COMPLETED, capturedUpdate.captured.status)
        // completionTimestamp should be set to current time (not null)
        assert(capturedUpdate.captured.completionTimestamp != null) {
            "IN_PROGRESS → COMPLETED must set completionTimestamp"
        }
    }

    // ── T016/US3: getTodayProgress counts recurring tasks ending at 11:59 PM ──

    /**
     * T016: getTodayProgress() must count recurring tasks whose dueDate falls
     * anywhere within today's range (including 23:59:59), not just midnight.
     *
     * FR-001: progress bar counts all tasks due within today's date range.
     */
    @Test
    fun getTodayProgress_includesRecurringTasksEndingAt1159pm() = runTest {
        val today = todayMidnight()
        val end1159 = today + 86_340_000L  // 23:59:00

        val recurringCompleted = TaskEntity(
            id          = 50L,
            title       = "Evening Recurring",
            status      = TaskStatus.COMPLETED,
            isRecurring = true,
            dueDate     = end1159,
            completionTimestamp = today + 1000L
        )

        // Stub getTasksDueInRange to return the 11:59 PM task for any range query
        every { taskDao.getTasksDueInRange(any(), any()) } returns flowOf(listOf(recurringCompleted))

        val progress = repository.getTodayProgress().first()

        assertEquals("Total should be 1 (recurring task ending at 23:59)", 1, progress.totalToday)
        assertEquals("Completed should be 1", 1, progress.completedToday)
    }}