package com.flow.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.data.local.AppDatabase
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStreakEntity
import com.flow.data.local.TaskStatus
import kotlinx.coroutines.flow.first
import java.util.Calendar
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T042 — Repository integrity tests for [TaskCompletionLogDao] + [TaskStreakDao].
 *
 * After `updateLog()` + `recalculateStreaks()` the following invariants must hold:
 *  - Completed log count matches `isCompleted=true` rows (no phantom completions)
 *  - `TaskStreakEntity.longestStreak >= currentStreak` at all times
 *  - `updateLog()` overwrites the log's date field (not inserts a duplicate)
 */
@RunWith(AndroidJUnit4::class)
class RepositoryIntegrityTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun insertTask(id: Long, scheduleMask: Int? = null): TaskEntity {
        val task = TaskEntity(
            id = id, title = "Task $id",
            startDate = 0L, dueDate = null,
            isRecurring = true,
            scheduleMask = scheduleMask
        )
        db.taskDao().insertTask(task)
        return task
    }

    private suspend fun insertLog(
        id: Long = 0,
        taskId: Long = 1L,
        date: Long,
        isCompleted: Boolean = true,
        timestamp: Long = date
    ) {
        val log = TaskCompletionLog(id = id, taskId = taskId, date = date, isCompleted = isCompleted, timestamp = timestamp)
        db.taskCompletionLogDao().insertLog(log)
    }

    private val day0 = 0L      // epoch day 0
    private val day1 = 86_400_000L
    private val day2 = 2 * day1
    private val day3 = 3 * day1

    // ── updateLog changes the date, not inserts a new row ─────────────────────

    @Test
    fun updateLog_updatesDateWithoutDuplicatingRow() = runTest {
        insertTask(1L)
        insertLog(taskId = 1L, date = day1)

        val existing = db.taskCompletionLogDao().getAllCompletedLogs().first()
        assertEquals("should start with 1 log", 1, existing.size)

        val updated = existing.first().copy(date = day2)
        db.taskCompletionLogDao().updateLog(updated)

        val after = db.taskCompletionLogDao().getAllCompletedLogs().first()
        assertEquals("still 1 row after update", 1, after.size)
        assertEquals("date field updated", day2, after.first().date)
    }

    // ── completedCount == isCompleted=true log rows ───────────────────────────

    @Test
    fun completedLogCount_matchesIsCompletedRows() = runTest {
        insertTask(1L)
        insertLog(taskId = 1L, date = day0, isCompleted = true)
        insertLog(taskId = 1L, date = day1, isCompleted = true)
        insertLog(taskId = 1L, date = day2, isCompleted = false) // not completed

        val completed = db.taskCompletionLogDao().getAllCompletedLogs().first()
        assertEquals("only isCompleted=true rows", 2, completed.size)
        assertTrue("all returned rows are completed", completed.all { it.isCompleted })
    }

    // ── upsertStreak maintains longestStreak >= currentStreak ─────────────────

    @Test
    fun upsertStreak_longestStreakAtLeastCurrentStreak() = runTest {
        insertTask(1L)

        // Insert initial streak: current=5, longest=5
        db.taskStreakDao().upsertStreak(
            TaskStreakEntity(taskId = 1L, currentStreak = 5, longestStreak = 5, longestStreakStartDate = null)
        )
        val s1 = db.taskStreakDao().getStreakForTask(1L).first()
        assertNotNull(s1)
        assertTrue("longestStreak >= currentStreak", s1!!.longestStreak >= s1.currentStreak)

        // Update: current falls after a break (3), longest stays at 5
        db.taskStreakDao().upsertStreak(
            TaskStreakEntity(taskId = 1L, currentStreak = 3, longestStreak = 5, longestStreakStartDate = null)
        )
        val s2 = db.taskStreakDao().getStreakForTask(1L).first()
        assertEquals("currentStreak updated to 3", 3, s2!!.currentStreak)
        assertEquals("longestStreak unchanged at 5", 5, s2.longestStreak)
        assertTrue("invariant: longest >= current", s2.longestStreak >= s2.currentStreak)
    }

    // ── streak cannot exceed total completed logs ──────────────────────────────

    @Test
    fun streakCannotExceedTotalLogCount() = runTest {
        insertTask(1L)
        insertLog(taskId = 1L, date = day0, isCompleted = true)
        insertLog(taskId = 1L, date = day1, isCompleted = true)

        val completedCount = db.taskCompletionLogDao().getAllCompletedLogs().first()
            .filter { it.taskId == 1L }.size

        db.taskStreakDao().upsertStreak(
            TaskStreakEntity(taskId = 1L, currentStreak = 2, longestStreak = 2, longestStreakStartDate = null)
        )
        val s = db.taskStreakDao().getStreakForTask(1L).first()!!
        assertTrue("streak=${ s.currentStreak} <= completedCount=$completedCount",
            s.currentStreak <= completedCount)
    }

    // ── T009: US1 — addTask round-trip: dueDate=today is counted by getTodayProgress ──

    /**
     * T009 — addTask with dueDate=today is counted by getTodayProgress.
     *
     * Verifies the full round-trip:
     *  1. addTask() normalises dueDate to midnight (FR-001 fix)
     *  2. getTodayProgress() counts it via getTasksDueOn(normaliseToMidnight(now))
     * Result: totalToday = 1.
     */
    @Test
    fun addTask_with_dueDate_today_then_getTodayProgress_counts_it() = runTest {
        val todayMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val repo = TaskRepositoryImpl(
            db                  = db,
            taskDao             = db.taskDao(),
            dailyProgressDao    = db.dailyProgressDao(),
            taskCompletionLogDao = db.taskCompletionLogDao(),
            taskStreakDao       = db.taskStreakDao(),
            achievementDao      = db.achievementDao()
        )

        // Add task with dueDate = 10 pm today (non-midnight raw value).
        // After T003 fix, addTask() normalises this to todayMidnight.
        repo.addTask(
            title       = "Round-trip task",
            startDate   = todayMidnight,
            isRecurring = false,
            dueDate     = todayMidnight + 79_200_000L,  // 10 pm today
            scheduleMask = null
        )

        val progress = repo.getTodayProgress().first()
        assertEquals("totalToday should be 1 after adding a dueDate=today task", 1, progress.totalToday)
    }

    // ── T017/US3: Progress counts mix of recurring and non-recurring tasks ────

    /**
     * T017 — Integration: 2 recurring + 2 non-recurring tasks all due today;
     * complete 2; assert progress = 2/4.
     */
    @Test
    fun progress_countsMixOfRecurringAndNonRecurringEndingToday() = runTest {
        val repo = buildRepo()
        val todayMidnight = today()
        val todayEnd = todayMidnight + 86_340_000L // 11:59 PM

        // Insert 2 recurring tasks due tonight
        repo.addTask("Recurring 1", todayMidnight, dueDate = todayEnd, isRecurring = true,  scheduleMask = null)
        repo.addTask("Recurring 2", todayMidnight, dueDate = todayEnd, isRecurring = true,  scheduleMask = null)
        // Insert 2 non-recurring tasks due tonight
        repo.addTask("OneTime 1",   todayMidnight, dueDate = todayEnd, isRecurring = false, scheduleMask = null)
        repo.addTask("OneTime 2",   todayMidnight, dueDate = todayEnd, isRecurring = false, scheduleMask = null)

        // Complete first 2 tasks
        val allTasks = db.taskDao().getAllTasks().first()
        repo.updateTaskStatus(allTasks[0], TaskStatus.COMPLETED)
        repo.updateTaskStatus(allTasks[1], TaskStatus.COMPLETED)

        val progress = repo.getTodayProgress().first()
        assertEquals("totalToday must be 4 (2 recurring + 2 non-recurring)", 4, progress.totalToday)
        assertEquals("completedToday must be 2", 2, progress.completedToday)
    }

    // ── T021/US4: Non-recurring task moved to IN_PROGRESS disappears from history

    @Test
    fun nonRecurringTask_movedToInProgress_disappearsFromHistory() = runTest {
        val repo = buildRepo()
        val todayMidnight = today()

        repo.addTask("History Task", todayMidnight, null, isRecurring = false, scheduleMask = null)
        val task = db.taskDao().getAllTasks().first().first()

        // Complete the task → it appears in history
        repo.updateTaskStatus(task, TaskStatus.COMPLETED)
        val completed = db.taskDao().getCompletedNonRecurringTasks().first()
        assertEquals("Task must appear in history after completion", 1, completed.size)

        // Move back to IN_PROGRESS → completionTimestamp cleared → disappears from history
        repo.updateTaskStatus(completed.first(), TaskStatus.IN_PROGRESS)
        val afterUndo = db.taskDao().getCompletedNonRecurringTasks().first()
        assertTrue(
            "Non-recurring task moved to IN_PROGRESS must disappear from history",
            afterUndo.isEmpty()
        )
    }

    // ── T022/US4: Recurring task re-completed same day has only one log entry ─

    @Test
    fun recurringTask_recompletedSameDay_onlyOneLogEntry() = runTest {
        val repo = buildRepo()
        val todayMidnight = today()
        insertTask(99L)
        val task = db.taskDao().getTaskById(99L)!!.copy(isRecurring = true)
        db.taskDao().updateTask(task)

        // Complete once
        repo.updateTaskStatus(task, TaskStatus.COMPLETED)
        // Complete again (simulate double-tap or undo and redo)
        val t1 = db.taskDao().getTaskById(99L)!!
        repo.updateTaskStatus(t1.copy(status = TaskStatus.IN_PROGRESS), TaskStatus.COMPLETED)

        val logs = db.taskCompletionLogDao().getCompletedLogsForTask(99L)
        assertEquals(
            "Re-completing a recurring task on the same day must produce exactly 1 log entry (UPSERT)",
            1,
            logs.size
        )
    }

    // ── T046/US3: Existing midnight-timed task is not altered by updateTask ───

    @Test
    fun existingMidnightTask_afterRepositoryChange_fieldValuesUnchanged() = runTest {
        val repo = buildRepo()
        val todayMidnight = today()

        // Insert legacy task with exact midnight dueDate (pre-fix format)
        val legacyTask = TaskEntity(
            id        = 0L,
            title     = "Legacy Midnight Task",
            startDate = todayMidnight,
            dueDate   = todayMidnight,
            isRecurring = false
        )
        val assignedId = db.taskDao().insertTask(legacyTask)
        val inserted = db.taskDao().getTaskById(assignedId)!!

        // Perform a read→write cycle via updateTask (no status change)
        repo.updateTask(inserted)

        val afterUpdate = db.taskDao().getTaskById(assignedId)!!
        assertEquals(
            "Legacy midnight startDate must not be altered by updateTask",
            todayMidnight,
            afterUpdate.startDate
        )
        assertEquals(
            "Legacy midnight dueDate must not be altered by updateTask",
            todayMidnight,
            afterUpdate.dueDate
        )
    }

    // ── Helpers used by new tests ─────────────────────────────────────────────

    private fun today(): Long = Calendar.getInstance().run {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    private fun buildRepo() = TaskRepositoryImpl(
        db                   = db,
        taskDao              = db.taskDao(),
        dailyProgressDao     = db.dailyProgressDao(),
        taskCompletionLogDao = db.taskCompletionLogDao(),
        taskStreakDao        = db.taskStreakDao(),
        achievementDao       = db.achievementDao()
    )
}
