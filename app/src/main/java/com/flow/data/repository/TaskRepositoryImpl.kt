package com.flow.data.repository

import androidx.room.withTransaction
import com.flow.data.local.AchievementDao
import com.flow.data.local.AchievementEntity
import com.flow.data.local.AchievementType
import com.flow.data.local.AppDatabase
import com.flow.data.local.DailyProgressDao
import com.flow.data.local.DailyProgressEntity
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskCompletionLogDao
import com.flow.data.local.TaskDao
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.data.local.TaskStreakDao
import com.flow.data.local.TaskStreakEntity
import com.flow.domain.streak.DayMask
import com.flow.domain.streak.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val taskDao: TaskDao,
    private val dailyProgressDao: DailyProgressDao,
    private val taskCompletionLogDao: TaskCompletionLogDao,
    private val taskStreakDao: TaskStreakDao,
    private val achievementDao: AchievementDao
) : TaskRepository {

    // ── Reactive queries ──────────────────────────────────────────────────

    override fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    override fun getHistory(): Flow<List<DailyProgressEntity>> = dailyProgressDao.getAllHistory()

    override fun getCompletedTaskCount(): Flow<Int> = taskDao.getCompletedTaskCount()

    override fun getTaskStreak(taskId: Long): Flow<Int> =
        taskCompletionLogDao.getLogsForTask(taskId).map { logs ->
            calculateStreakFromLogs(logs)
        }

    override fun getTaskHistory(taskId: Long): Flow<List<TaskCompletionLog>> =
        taskCompletionLogDao.getLogsForTask(taskId)

    // ── FR-001: today progress counts tasks due anywhere within today ──────

    override fun getTodayProgress(): Flow<TodayProgressState> {
        val today    = normaliseToMidnight(System.currentTimeMillis())
        val todayEnd = today + 86_399_999L  // T018/US3: inclusive end-of-day
        return taskDao.getTasksDueInRange(today, todayEnd).map { tasks ->
            val total     = tasks.size
            val completed = tasks.count { it.isCompleted }
            TodayProgressState(totalToday = total, completedToday = completed)
        }
    }

    override fun getTodayProgressRatio(): Flow<Float> = getTodayProgress().map { it.ratio }

    // ── Heatmap ─────────────────────────────────────────────────────────

    override fun getHeatMapData(startMs: Long, endMs: Long): Flow<Map<Long, Int>> =
        taskCompletionLogDao.getLogsBetween(startMs, endMs).map { logs ->
            logs.groupBy { it.date }.mapValues { (_, v) -> v.size }
        }

    override fun getHeatMapData(): Flow<Map<Long, Int>> =
        dailyProgressDao.getAllHistory().map { rows ->
            rows.associate { it.date to it.tasksCompletedCount }
        }

    // ── Forest ──────────────────────────────────────────────────────────

    override fun getForestData(startMs: Long, endMs: Long): Flow<Map<Long, List<String>>> =
        taskCompletionLogDao.getRecurringLogsBetween(startMs, endMs).map { entries ->
            entries.groupBy { it.completionDate }.mapValues { (_, v) ->
                v.map { it.taskTitle }.distinct()
            }
        }

    // ── Home screen ──────────────────────────────────────────────────────

    override fun getHomeScreenTasks(): Flow<List<TaskEntity>> {
        val todayStart    = normaliseToMidnight(System.currentTimeMillis())
        val tomorrowStart = todayStart + 86_400_000L
        return taskDao.getHomeScreenTasks(todayStart, tomorrowStart)
    }

    override fun getAllCompletedRecurringLogs(): Flow<List<TaskCompletionLog>> =
        taskCompletionLogDao.getAllCompletedLogs()

    override fun getCompletedNonRecurringTasks(): Flow<List<TaskEntity>> =
        taskDao.getCompletedNonRecurringTasks()

    // ── Streak & achievement reactive ───────────────────────────────────

    override fun getStreakForTask(taskId: Long): Flow<TaskStreakEntity?> =
        taskStreakDao.getStreakForTask(taskId)

    override fun getAchievements(): Flow<List<AchievementEntity>> =
        achievementDao.getAll()

    // ── Commands ──────────────────────────────────────────────────────────

    override suspend fun addTask(
        title: String,
        startDate: Long,
        dueDate: Long?,
        isRecurring: Boolean,
        scheduleMask: Int?
    ) {
        // T046: Validate scheduleMask — must be null (every day) or in 1..127 (valid day-bit combination)
        val validMask = when {
            scheduleMask == null -> null
            scheduleMask in 1..127 -> scheduleMask
            else -> null // silently clamp invalid values to “every day”
        }
        // T019/US3: Preserve caller-supplied times — AddTaskDialog supplies defaultEndTime()
        // (T020) for dueDate and dialog-open time for startDate.
        // refreshRecurringTasks() handles the 12:01 AM / 11:59 PM reset independently.
        val resolvedStart = startDate
        val resolvedDue   = dueDate
        taskDao.insertTask(
            TaskEntity(
                title        = title,
                startDate    = resolvedStart,
                dueDate      = resolvedDue,
                isRecurring  = isRecurring,
                scheduleMask = if (isRecurring) validMask else null
            )
        )
    }

    override suspend fun updateTask(task: TaskEntity) {
        val existing = taskDao.getTaskById(task.id) ?: return
        // T023/US4: Only preserve completionTimestamp when task is COMPLETED;
        // for any other status clear the timestamp so history has no ghost entries.
        val resolvedTimestamp = if (task.status == TaskStatus.COMPLETED)
            existing.completionTimestamp
        else
            null
        taskDao.updateTask(
            task.copy(
                completionTimestamp = resolvedTimestamp,
                dueDate             = task.dueDate?.let { normaliseToMidnight(it) }
            )
        )
    }

    override suspend fun updateTaskStatus(task: TaskEntity, newStatus: TaskStatus) {
        // T024/US4: COMPLETED → IN_PROGRESS is now a valid transition (clears timestamp)
        val validTransition = when (task.status) {
            TaskStatus.TODO        -> newStatus == TaskStatus.IN_PROGRESS || newStatus == TaskStatus.COMPLETED
            TaskStatus.IN_PROGRESS -> newStatus == TaskStatus.COMPLETED || newStatus == TaskStatus.TODO
            TaskStatus.COMPLETED   -> newStatus == TaskStatus.TODO || newStatus == TaskStatus.IN_PROGRESS
        }
        if (!validTransition) return

        val justCompleted = newStatus == TaskStatus.COMPLETED
        // T024/US4: Clear timestamp when task moves OUT of COMPLETED state (both TODO and IN_PROGRESS)
        val leavingCompleted = task.status == TaskStatus.COMPLETED && newStatus != TaskStatus.COMPLETED

        val newTimestamp = when {
            justCompleted && task.completionTimestamp == null -> System.currentTimeMillis()
            leavingCompleted                                  -> null
            else                                              -> task.completionTimestamp
        }

        taskDao.updateTask(task.copy(status = newStatus, completionTimestamp = newTimestamp))

        if (task.isRecurring) {
            val today = normaliseToMidnight(System.currentTimeMillis())
            // T022/US4: UPSERT pattern — check if a log already exists for today
            // before inserting. Since TaskCompletionLog uses auto-generated PK with no
            // unique constraint on (taskId, date), a plain INSERT always creates a new
            // row. Using check-then-update prevents duplicate entries when a recurring
            // task is completed, reverted, and completed again on the same calendar day.
            val existingLog = taskCompletionLogDao.getLogForTaskDate(task.id, today)
            if (existingLog != null) {
                taskCompletionLogDao.updateLog(
                    existingLog.copy(
                        isCompleted = justCompleted,
                        timestamp   = System.currentTimeMillis()
                    )
                )
            } else {
                taskCompletionLogDao.insertLog(
                    TaskCompletionLog(taskId = task.id, date = today, isCompleted = justCompleted)
                )
            }
            if (justCompleted) {
                recalculateStreaks(task.id)
                checkAndAwardAchievements(task.id)
            }
        }

        // T031: global achievement check on every completion
        if (justCompleted) checkAndAwardAchievements(null)

        upsertDailyProgress(normaliseToMidnight(System.currentTimeMillis()))
    }

    override suspend fun deleteTask(task: TaskEntity) {
        db.withTransaction {
            taskCompletionLogDao.deleteLogsForTask(task.id)
            taskStreakDao.deleteByTaskId(task.id)
            taskDao.deleteTask(task)
        }
        upsertDailyProgress(normaliseToMidnight(System.currentTimeMillis()))
    }

    override suspend fun getTaskById(id: Long): TaskEntity? = taskDao.getTaskById(id)

    override suspend fun updateLog(log: TaskCompletionLog) {
        taskCompletionLogDao.updateLog(log)
    }

    override suspend fun recalculateStreaks(taskId: Long) {
        val task = taskDao.getTaskById(taskId) ?: return
        val logs = taskCompletionLogDao.getCompletedLogsForTask(taskId)
        val schedule = DayMask.toRecurrenceSchedule(task.scheduleMask)
        val result   = StreakCalculator.compute(
            completionDates = logs.map { it.date },
            schedule        = schedule,
            today           = LocalDate.now()
        )
        taskStreakDao.upsertStreak(
            TaskStreakEntity(
                taskId                = taskId,
                currentStreak         = result.currentStreak,
                longestStreak         = result.longestStreak,
                longestStreakStartDate = result.longestStreakStartDate,
                lastUpdated           = System.currentTimeMillis()
            )
        )
    }

    override suspend fun checkAndAwardAchievements(taskId: Long?) {
        db.withTransaction {
            val now = System.currentTimeMillis()

            if (taskId != null) {
                val streak = taskStreakDao.getStreakForTask(taskId).firstOrNull()
                val best   = streak?.longestStreak ?: 0
                mapOf(
                    AchievementType.STREAK_10  to 10,
                    AchievementType.STREAK_30  to 30,
                    AchievementType.STREAK_100 to 100
                ).forEach { (type, threshold) ->
                    if (best >= threshold) {
                        achievementDao.insertOnConflictIgnore(
                            AchievementEntity(type = type, taskId = taskId, earnedAt = now)
                        )
                    }
                }
                val task = taskDao.getTaskById(taskId)
                val ts   = task?.completionTimestamp
                val due  = task?.dueDate
                if (ts != null && due != null && ts < due) {
                    achievementDao.insertOnConflictIgnore(
                        AchievementEntity(type = AchievementType.EARLY_FINISH, taskId = taskId, earnedAt = now)
                    )
                }
            }

            val onTimeCount = taskDao.getCompletedOnTimeCount()
            if (onTimeCount >= 10) {
                achievementDao.insertOnConflictIgnore(
                    AchievementEntity(type = AchievementType.ON_TIME_10, taskId = null, earnedAt = now)
                )
            }

            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val yearStart   = normaliseToMidnight(
                Calendar.getInstance().apply { set(currentYear, Calendar.JANUARY, 1) }.timeInMillis
            )
            val yearEnd     = getEndOfDay(
                normaliseToMidnight(
                    Calendar.getInstance().apply { set(currentYear, Calendar.DECEMBER, 31) }.timeInMillis
                )
            )
            val distinctDays = taskCompletionLogDao.getLogsBetween(yearStart, yearEnd)
                .firstOrNull()
                ?.filter { it.isCompleted }
                ?.map { it.date }
                ?.toSet()
                ?.size ?: 0
            if (distinctDays >= 365) {
                achievementDao.insertOnConflictIgnore(
                    AchievementEntity(
                        type        = AchievementType.YEAR_FINISHER,
                        taskId      = null,
                        earnedAt    = now,
                        periodLabel = currentYear.toString()
                    )
                )
            }
        }
    }

    // ── Aggregates ────────────────────────────────────────────────────────

    override suspend fun calculateCurrentStreak(): Int {
        val history = dailyProgressDao.getAllHistory().firstOrNull() ?: return 0
        var streak   = 0
        var expected = normaliseToMidnight(System.currentTimeMillis())
        for (day in history) {
            if (day.date == expected && day.tasksCompletedCount > 0) {
                streak++
                expected -= 86_400_000L
            } else if (day.date < expected) break
        }
        return streak
    }

    // ── T016: refreshRecurringTasks with scheduleMask ─────────────────────

    override suspend fun refreshRecurringTasks() {
        val allTasks = taskDao.getAllTasks().firstOrNull() ?: return
        val today       = normaliseToMidnight(System.currentTimeMillis())
        val calDow      = Calendar.getInstance().apply { timeInMillis = today }.get(Calendar.DAY_OF_WEEK)
        val todayBit    = calDowToDayMaskBit(calDow)

        allTasks.filter { it.isRecurring && it.status == TaskStatus.COMPLETED }.forEach { task ->
            val completedDay = task.completionTimestamp?.let { normaliseToMidnight(it) } ?: 0L
            if (completedDay < today) {
                val mask = task.scheduleMask
                if (mask != null && (mask and todayBit) == 0) return@forEach
                // T013/US1: Apply correct 12:01 AM / 11:59 PM time boundaries on refresh
                taskDao.updateTask(
                    task.copy(
                        status              = TaskStatus.TODO,
                        completionTimestamp = null,
                        startDate           = today + 60_000L,
                        dueDate             = today + 86_340_000L
                    )
                )
            }
        }
    }

    override suspend fun getCompletedOnTimeCount(): Int = taskDao.getCompletedOnTimeCount()

    override suspend fun getMissedDeadlineCount(): Int =
        taskDao.getMissedDeadlineCount(System.currentTimeMillis())

    override suspend fun getBestStreak(): Int {
        val recurringTasks = taskDao.getAllTasks().firstOrNull()?.filter { it.isRecurring } ?: return 0
        return recurringTasks.maxOfOrNull { task ->
            val logs = taskCompletionLogDao.getLogsForTask(task.id).firstOrNull() ?: emptyList()
            calculateBestStreakFromLogs(logs)
        } ?: 0
    }

    override suspend fun getLifetimeStats(): LifetimeStats {
        val total     = taskDao.getCompletedTaskCount().firstOrNull() ?: 0
        val onTime    = taskDao.getCompletedOnTimeCount()
        val onTimePct = if (total == 0) 0f else onTime / total.toFloat()
        val best      = getBestStreak()
        val habits    = taskDao.getAllTasks().firstOrNull()?.count { it.isRecurring } ?: 0
        return LifetimeStats(
            totalCompleted    = total,
            onTimeCompleted   = onTime,
            onTimePct         = onTimePct,
            longestStreak     = best,
            uniqueHabitsCount = habits
        )
    }

    override suspend fun getCurrentYearStats(): CurrentYearStats {
        val year  = Calendar.getInstance().get(Calendar.YEAR)
        val jan1  = normaliseToMidnight(
            Calendar.getInstance().apply { set(year, Calendar.JANUARY, 1) }.timeInMillis
        )
        val dec31 = getEndOfDay(
            normaliseToMidnight(
                Calendar.getInstance().apply { set(year, Calendar.DECEMBER, 31) }.timeInMillis
            )
        )
        val logsThisYear      = taskCompletionLogDao.getLogsBetween(jan1, dec31)
            .firstOrNull()?.filter { it.isCompleted } ?: emptyList()
        val completedThisYear = logsThisYear.size

        val allTasks = taskDao.getAllTasks().firstOrNull() ?: emptyList()
        val eligible = allTasks.filter { t ->
            !t.isRecurring && t.completionTimestamp != null && t.completionTimestamp in jan1..dec31
        }
        val onTime = eligible.count { t ->
            t.completionTimestamp != null && t.dueDate != null && t.completionTimestamp <= t.dueDate
        }
        val onTimeRate = if (eligible.isEmpty()) 0f else onTime / eligible.size.toFloat()

        return CurrentYearStats(
            completedThisYear  = completedThisYear,
            onTimeRateThisYear = onTimeRate,
            bestStreakThisYear  = getBestStreak()
        )
    }

    override suspend fun getEarliestCompletionDate(): Long? =
        taskCompletionLogDao.getEarliestCompletionDate()

    // ── Private helpers ───────────────────────────────────────────────────

    private suspend fun upsertDailyProgress(todayMidnight: Long) {
        val allTasks = taskDao.getAllTasks().firstOrNull() ?: emptyList()
        val todayEnd = getEndOfDay(todayMidnight)
        val activeTasks = allTasks.filter { it.startDate <= todayEnd }
        dailyProgressDao.insertOrUpdateProgress(
            DailyProgressEntity(
                date                = todayMidnight,
                tasksCompletedCount = activeTasks.count { it.status == TaskStatus.COMPLETED },
                tasksTotalCount     = activeTasks.size
            )
        )
    }

    private fun calculateStreakFromLogs(logs: List<TaskCompletionLog>): Int {
        val completed = logs.filter { it.isCompleted }.sortedByDescending { it.date }
        if (completed.isEmpty()) return 0
        val today     = normaliseToMidnight(System.currentTimeMillis())
        val yesterday = today - 86_400_000L
        if (completed.first().date != today && completed.first().date != yesterday) return 0
        var streak   = 0
        var expected = completed.first().date
        for (log in completed) {
            if (log.date == expected) { streak++; expected -= 86_400_000L } else break
        }
        return streak
    }

    private fun calculateBestStreakFromLogs(logs: List<TaskCompletionLog>): Int {
        val dates = logs.filter { it.isCompleted }.map { it.date }.sorted()
        if (dates.isEmpty()) return 0
        var best = 0; var current = 0; var prev = -1L
        for (date in dates) {
            current = if (prev == -1L || date - prev == 86_400_000L) current + 1 else 1
            if (current > best) best = current
            prev = date
        }
        return best
    }

    private fun normaliseToMidnight(timestamp: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun getEndOfDay(midnight: Long): Long = midnight + 86_399_999L

    private fun calDowToDayMaskBit(calDow: Int): Int = when (calDow) {
        Calendar.MONDAY    -> DayMask.MON
        Calendar.TUESDAY   -> DayMask.TUE
        Calendar.WEDNESDAY -> DayMask.WED
        Calendar.THURSDAY  -> DayMask.THU
        Calendar.FRIDAY    -> DayMask.FRI
        Calendar.SATURDAY  -> DayMask.SAT
        Calendar.SUNDAY    -> DayMask.SUN
        else               -> DayMask.ALL
    }
}
