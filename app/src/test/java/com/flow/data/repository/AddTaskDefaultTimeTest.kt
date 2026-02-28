package com.flow.data.repository

import com.flow.data.local.AchievementDao
import com.flow.data.local.AppDatabase
import com.flow.data.local.DailyProgressDao
import com.flow.data.local.TaskCompletionLogDao
import com.flow.data.local.TaskDao
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.data.local.TaskStreakDao
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * T018 / C-002 — Tests that [TaskRepositoryImpl.addTask] does NOT override the
 * caller-supplied [startDate] for recurring tasks (fix for US3 — Default Task Times).
 *
 * The first-time creation path MUST preserve the caller's startDate exactly.
 * Only [refreshRecurringTasks] should reset the start time to 12:01 AM.
 *
 * Expected to be RED until T019 removes the isRecurring time-override block.
 */
class AddTaskDefaultTimeTest {

    private val db                  : AppDatabase          = mockk(relaxed = true)
    private val taskDao             : TaskDao               = mockk(relaxed = true)
    private val dailyProgressDao    : DailyProgressDao      = mockk(relaxed = true)
    private val taskCompletionLogDao: TaskCompletionLogDao  = mockk(relaxed = true)
    private val taskStreakDao       : TaskStreakDao          = mockk(relaxed = true)
    private val achievementDao      : AchievementDao        = mockk(relaxed = true)

    private lateinit var repository: TaskRepositoryImpl

    /** 2:37 PM today expressed in epoch millis. */
    private fun todayAt1437(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 14)
        set(Calendar.MINUTE, 37)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun todayMidnight(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

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
        repository = spyk(realRepo, recordPrivateCalls = false)
        coEvery { repository.checkAndAwardAchievements(any()) } just Runs
        coEvery { taskDao.getAllTasks() } returns flowOf(emptyList())
        coEvery { dailyProgressDao.insertOrUpdateProgress(any()) } returns Unit
    }

    // ── (a) addTask recurring — startDate must NOT be overridden ─────────────

    @Test
    fun addRecurringTask_startDateIsPreserved_notOverriddenTo1201am() = runTest {
        val capturedTask = slot<TaskEntity>()
        coEvery { taskDao.insertTask(capture(capturedTask)) } returns 1L

        val inputStartDate = todayAt1437()   // 2:37 PM

        repository.addTask(
            title        = "Daily Habit",
            startDate    = inputStartDate,
            dueDate      = null,
            isRecurring  = true,
            scheduleMask = null
        )

        assertEquals(
            "addTask(isRecurring=true) MUST NOT override startDate to 12:01 AM. " +
            "startDate should equal the caller-supplied value (C-002).",
            inputStartDate,
            capturedTask.captured.startDate
        )
    }

    // ── (b) refreshRecurringTasks — startDate MUST be set to 12:01 AM ─────────

    @Test
    fun refreshRecurringTasks_resetsStartDateTo1201am() = runTest {
        val yesterday = todayMidnight() - 86_400_000L
        val completedTask = TaskEntity(
            id                  = 1L,
            title               = "Daily Habit",
            isRecurring         = true,
            status              = TaskStatus.COMPLETED,
            completionTimestamp = yesterday + 1000L
        )
        coEvery { taskDao.getAllTasks() } returns flowOf(listOf(completedTask))

        val capturedUpdate = slot<TaskEntity>()
        coEvery { taskDao.updateTask(capture(capturedUpdate)) } returns Unit

        repository.refreshRecurringTasks()

        val midnight = todayMidnight()
        assertEquals(
            "refreshRecurringTasks MUST reset startDate to midnight+60000 (12:01 AM)",
            midnight + 60_000L,
            capturedUpdate.captured.startDate
        )
    }

    // ── (c) addTask non-recurring — startDate must NOT be modified ────────────

    @Test
    fun addNonRecurringTask_startDateIsPreserved() = runTest {
        val capturedTask = slot<TaskEntity>()
        coEvery { taskDao.insertTask(capture(capturedTask)) } returns 1L

        val inputStartDate = todayAt1437()   // 2:37 PM

        repository.addTask(
            title        = "One-time Task",
            startDate    = inputStartDate,
            dueDate      = null,
            isRecurring  = false,
            scheduleMask = null
        )

        assertEquals(
            "addTask(isRecurring=false) MUST NOT normalise startDate to midnight. " +
            "The time component must be preserved (C-002).",
            inputStartDate,
            capturedTask.captured.startDate
        )
    }
}
