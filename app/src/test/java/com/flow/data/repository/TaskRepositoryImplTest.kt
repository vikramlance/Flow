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
    fun addRecurringTask_preservesCallerStartDate() = runTest {
        // T019/US3: addTask no longer overrides startDate to 12:01 AM.
        // The dialog (T020) is responsible for supplying the desired default time.
        val capturedTask = slot<TaskEntity>()
        coEvery { taskDao.insertTask(capture(capturedTask)) } returns 1L

        val inputStartDate = System.currentTimeMillis()
        repository.addTask(
            title        = "Daily Habit",
            startDate    = inputStartDate,
            dueDate      = null,
            isRecurring  = true,
            scheduleMask = null
        )

        assertEquals(
            "T019: addTask(isRecurring=true) must preserve caller-supplied startDate",
            inputStartDate,
            capturedTask.captured.startDate
        )
        assertNull(
            "T019: addTask(isRecurring=true, dueDate=null) must store null dueDate",
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
    fun addNonRecurringTask_nullDueDate_storesNull() = runTest {
        // T019/US3: addTask no longer synthesizes a default 11:59 PM dueDate.
        // The dialog (T020) supplies defaultEndTime() before calling addTask.
        val capturedTask = slot<TaskEntity>()
        coEvery { taskDao.insertTask(capture(capturedTask)) } returns 2L

        repository.addTask(
            title        = "One-time task",
            startDate    = System.currentTimeMillis(),
            dueDate      = null,
            isRecurring  = false,
            scheduleMask = null
        )

        assertNull(
            "T019: addTask(dueDate=null) must pass null through — dialog is responsible for default",
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
            "T019: Explicit dueDate must be passed through unchanged",
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
    }

    // ── T005 [RED] / FR-001 / US1: updateTask must not normalise dueDate ──────

    /**
     * T005 [RED] — updateTask_preservesDueDateTimeExact
     *
     * FR-001: TaskRepositoryImpl.updateTask MUST NOT normalise dueDate to midnight.
     * The DAO must receive exactly the dueDate the caller passed.
     *
     * This test MUST FAIL before T007 because the real TaskRepositoryImpl calls
     * `task.dueDate?.let { normaliseToMidnight(it) }` before invoking taskDao.updateTask,
     * stripping H=15 M=30 to 00:00.
     *
     * After T007 (removing the normaliseToMidnight call), this test MUST PASS.
     */
    @Test
    fun updateTask_preservesDueDateTimeExact() = runTest {
        // Arrange: dueDate at H=15 M=30
        val dueDateH15M30 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val task = TaskEntity(
            id        = 99L,
            title     = "Timed Task",
            status    = TaskStatus.IN_PROGRESS,
            dueDate   = dueDateH15M30,
            startDate = System.currentTimeMillis()
        )

        // Stub getTaskById so updateTask can look up the existing record
        coEvery { taskDao.getTaskById(99L) } returns task

        // Capture what updateTask passes to the DAO
        val capturedTask = slot<TaskEntity>()
        coEvery { taskDao.updateTask(capture(capturedTask)) } returns Unit

        // Act
        repository.updateTask(task)

        // Assert: DAO must receive dueDate with H=15 M=30 intact
        val captured = capturedTask.captured
        val cal = Calendar.getInstance().apply { timeInMillis = captured.dueDate!! }
        assertEquals(
            "T005 FR-001: updateTask must pass HOUR_OF_DAY=15 to DAO (was ${cal.get(Calendar.HOUR_OF_DAY)})",
            15, cal.get(Calendar.HOUR_OF_DAY)
        )
        assertEquals(
            "T005 FR-001: updateTask must pass MINUTE=30 to DAO (was ${cal.get(Calendar.MINUTE)})",
            30, cal.get(Calendar.MINUTE)
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // T019–T024 (US1 — Analytics Completed Count) & T043–T045 (US3 — Missed Count)
    // ──────────────────────────────────────────────────────────────────────────

    private val today: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun log(
        id: Long        = 0L,
        taskId: Long    = 1L,
        date: Long      = today,
        timestamp: Long = today + 3_600_000L,
        isCompleted: Boolean = true
    ) = TaskCompletionLog(id = id, taskId = taskId, date = date, timestamp = timestamp, isCompleted = isCompleted)

    private fun nonRecurringTask(
        id: Long,
        completedAt: Long?,
        dueDate: Long? = null,
        status: TaskStatus = if (completedAt != null) TaskStatus.COMPLETED else TaskStatus.TODO
    ) = TaskEntity(
        id = id, title = "Task $id",
        isRecurring = false,
        status = status,
        completionTimestamp = completedAt,
        dueDate = dueDate
    )

    // ── T019: getHeatMapData merges recurring logs + non-recurring timestamps ─

    @Test
    fun getHeatMapData_includesNonRecurringCompletions() = runTest {
        val startMs = today - 7 * 86_400_000L
        val endMs   = today

        val logs = listOf(
            log(id = 1L, taskId = 1L, date = today),
            log(id = 2L, taskId = 2L, date = today),
            log(id = 3L, taskId = 3L, date = today)
        )
        // 2 non-recurring completions with timestamps normalised to 'today'
        val timestamps = listOf(today + 1_000L, today + 2_000L)

        every { taskCompletionLogDao.getLogsBetween(startMs, endMs) } returns flowOf(logs)
        every { taskDao.getCompletedNonRecurringInRange(startMs, endMs) } returns flowOf(timestamps)

        val heatMap = repository.getHeatMapData(startMs, endMs).first()

        assertEquals("heatMap[today] = 3 recurring + 2 non-recurring = 5", 5, heatMap[today])
    }

    // ── T020: getHeatMapData returns empty map when no completions ────────────

    @Test
    fun getHeatMapData_zeroWhenNoCompletions() = runTest {
        val startMs = today - 7 * 86_400_000L
        val endMs   = today

        every { taskCompletionLogDao.getLogsBetween(startMs, endMs) } returns flowOf(emptyList())
        every { taskDao.getCompletedNonRecurringInRange(startMs, endMs) } returns flowOf(emptyList())

        val heatMap = repository.getHeatMapData(startMs, endMs).first()

        assertEquals("Empty map expected when both sources return nothing",
                     emptyMap<Long, Int>(), heatMap)
    }

    // ── T021: getCompletedOnTimeCount sums both sources ───────────────────────

    @Test
    fun getCompletedOnTimeCount_sumsBothSources() = runTest {
        coEvery { taskDao.getCompletedOnTimeCount() } returns 2
        coEvery { taskCompletionLogDao.getRecurringOnTimeCount() } returns 3

        val result = repository.getCompletedOnTimeCount()

        assertEquals("Non-recurring(2) + recurring(3) on-time must equal 5", 5, result)
    }

    // ── T022: getLifetimeStats totalCompleted includes recurring logs ─────────

    @Test
    fun getLifetimeStats_totalIncludesRecurringLogs() = runTest {
        // 4 recurring completed log entries
        coEvery { taskCompletionLogDao.getTotalCompletedLogCount() } returns 4

        // 2 non-recurring COMPLETED tasks + 1 recurring (excluded from non-recurring total)
        val tasks = listOf(
            nonRecurringTask(1L, completedAt = today - 1_000L),
            nonRecurringTask(2L, completedAt = today - 2_000L),
            TaskEntity(id = 3L, title = "Recurring", isRecurring = true,
                       status = TaskStatus.COMPLETED, completionTimestamp = today - 3_000L)
        )
        every { taskDao.getAllTasks() } returns flowOf(tasks)

        // Stub internal on-time calls so getBestStreak chain doesn't fail
        coEvery { taskDao.getCompletedOnTimeCount() } returns 0
        coEvery { taskCompletionLogDao.getRecurringOnTimeCount() } returns 0
        every { taskCompletionLogDao.getLogsForTask(any()) } returns flowOf(emptyList())

        val stats = repository.getLifetimeStats()

        assertEquals("totalCompleted = 4 (logs) + 2 (non-recurring tasks) = 6",
                     6, stats.totalCompleted)
    }

    // ── T023: getCurrentYearStats completedThisYear includes non-recurring ────

    @Test
    fun getCurrentYearStats_completedThisYearIncludesNonRecurring() = runTest {
        val year    = Calendar.getInstance().get(Calendar.YEAR)
        val jan1    = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val midYear = jan1 + 180L * 86_400_000L  // ~mid-year

        // 3 recurring completed logs within this year
        val logsThisYear = listOf(
            log(id = 1L, taskId = 1L, date = jan1,    timestamp = jan1 + 1_000L),
            log(id = 2L, taskId = 2L, date = midYear, timestamp = midYear + 1_000L),
            log(id = 3L, taskId = 3L, date = midYear, timestamp = midYear + 2_000L)
        )
        every { taskCompletionLogDao.getLogsBetween(any(), any()) } returns flowOf(logsThisYear)

        // 2 non-recurring tasks completed within this year
        val tasks = listOf(
            nonRecurringTask(10L, completedAt = jan1 + 5_000L),
            nonRecurringTask(11L, completedAt = midYear + 5_000L)
        )
        every { taskDao.getAllTasks() } returns flowOf(tasks)
        every { taskCompletionLogDao.getLogsForTask(any()) } returns flowOf(emptyList())
        coEvery { taskDao.getCompletedOnTimeCount() } returns 0
        coEvery { taskCompletionLogDao.getRecurringOnTimeCount() } returns 0

        val stats = repository.getCurrentYearStats()

        assertEquals("completedThisYear = 3 (logs) + 2 (non-recurring) = 5",
                     5, stats.completedThisYear)
    }

    // ── T024: getEarliestCompletionDate returns minimum of both sources ───────

    @Test
    fun getEarliestCompletionDate_returnsMinOfBothSources() = runTest {
        coEvery { taskCompletionLogDao.getEarliestCompletionDate() } returns 1_000L
        coEvery { taskDao.getEarliestNonRecurringCompletionDate() } returns 50L

        val result = repository.getEarliestCompletionDate()

        assertEquals("Must return the minimum of both sources (50)", 50L, result)
    }

    @Test
    fun getEarliestCompletionDate_logEarlierThanNonRecurring() = runTest {
        coEvery { taskCompletionLogDao.getEarliestCompletionDate() } returns 100L
        coEvery { taskDao.getEarliestNonRecurringCompletionDate() } returns 5_000L

        val result = repository.getEarliestCompletionDate()

        assertEquals("Must return the log's earlier date (100)", 100L, result)
    }

    @Test
    fun getEarliestCompletionDate_returnsNullWhenBothEmpty() = runTest {
        coEvery { taskCompletionLogDao.getEarliestCompletionDate() } returns null
        coEvery { taskDao.getEarliestNonRecurringCompletionDate() } returns null

        val result = repository.getEarliestCompletionDate()

        assertNull("Must return null when both sources are empty", result)
    }

    // ── T043: getMissedDeadlineCount sums both sources ────────────────────────

    @Test
    fun getMissedDeadlineCount_sumsBothSources() = runTest {
        coEvery { taskDao.getMissedDeadlineCount(any()) } returns 2
        coEvery { taskCompletionLogDao.getRecurringMissedCount(any()) } returns 3

        val result = repository.getMissedDeadlineCount()

        assertEquals("Non-recurring(2) + recurring(3) missed = 5", 5, result)
    }

    // ── T044: getMissedDeadlineCount — only recurring logged ─────────────────

    @Test
    fun getMissedDeadlineCount_onlyRecurringMissed_countedFromLogs() = runTest {
        coEvery { taskDao.getMissedDeadlineCount(any()) } returns 0
        coEvery { taskCompletionLogDao.getRecurringMissedCount(any()) } returns 3

        val result = repository.getMissedDeadlineCount()

        assertEquals("0 non-recurring + 3 recurring missed = 3", 3, result)
    }

    // ── T045: getMissedDeadlineCount regression — non-recurring-only ─────────

    @Test
    fun getMissedDeadlineCount_onlyNonRecurring_unchanged() = runTest {
        coEvery { taskDao.getMissedDeadlineCount(any()) } returns 2
        coEvery { taskCompletionLogDao.getRecurringMissedCount(any()) } returns 0

        val result = repository.getMissedDeadlineCount()

        assertEquals("2 non-recurring + 0 recurring missed = 2 (regression)", 2, result)
    }
}