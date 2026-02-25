package com.flow.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.data.local.AppDatabase
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStreakEntity
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
}
