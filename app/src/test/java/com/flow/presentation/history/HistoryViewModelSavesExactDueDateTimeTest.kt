package com.flow.presentation.history

import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.fake.FakeTaskRepository
import com.flow.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

/**
 * T004 [RED] / US1 / FR-001 / FR-002 / FR-007 / FR-008
 *
 * Verifies that [GlobalHistoryViewModel.saveEditTask] passes dueDate through to
 * [FakeTaskRepository] without stripping the time component via normaliseToMidnight.
 *
 * These tests MUST FAIL before T006 because [GlobalHistoryViewModel.saveEditTask]
 * calls `normaliseToMidnight(it)` on dueDate before invoking [repository.updateTask],
 * causing [FakeTaskRepository] to store midnight instead of H=22 M=45.
 *
 * After T006 (removing the normaliseToMidnight call from saveEditTask), all three
 * tests MUST PASS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelSavesExactDueDateTimeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeTaskRepository
    private lateinit var vm: GlobalHistoryViewModel

    // A fixed dueDate at H=22 M=45 on an arbitrary date (today, 2026-02-28).
    private val dueDateWith22h45m: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 22)
        set(Calendar.MINUTE, 45)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun makeTask(
        id: Long = 1L,
        dueDate: Long? = dueDateWith22h45m,
        isRecurring: Boolean = false
    ) = TaskEntity(
        id          = id,
        title       = "Test Task",
        isRecurring = isRecurring,
        status      = TaskStatus.IN_PROGRESS,
        dueDate     = dueDate,
        startDate   = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        fakeRepo = FakeTaskRepository()
        vm = GlobalHistoryViewModel(fakeRepo)
    }

    // ── Test 1: exact time preserved ─────────────────────────────────────────

    /**
     * FR-001 / FR-002: saveEditTask must not normalise dueDate to midnight.
     * The FakeRepository must store exactly H=22 M=45.
     */
    @Test
    fun globalHistoryViewModel_saveEditTask_preservesDueDateTimeExact() = runTest {
        val task = makeTask()
        fakeRepo.allTasksFlow.value = listOf(task)

        vm.saveEditTask(task)
        advanceUntilIdle()

        val stored = fakeRepo.allTasksFlow.value.find { it.id == task.id }
            ?: error("Task not found in FakeRepository after save")

        val cal = Calendar.getInstance().apply { timeInMillis = stored.dueDate!! }
        assertEquals(
            "T004 FR-002: saveEditTask must preserve HOUR_OF_DAY (expected 22, was ${cal.get(Calendar.HOUR_OF_DAY)})",
            22, cal.get(Calendar.HOUR_OF_DAY)
        )
        assertEquals(
            "T004 FR-002: saveEditTask must preserve MINUTE (expected 45, was ${cal.get(Calendar.MINUTE)})",
            45, cal.get(Calendar.MINUTE)
        )
    }

    // ── Test 2: date-only change preserves existing H:M (FR-007) ─────────────

    /**
     * FR-007: When user changes only the date (not the time), the existing H:M
     * must be preserved after save. Simulates: user edits task — date changes,
     * dueDate stays at H=22 M=45 because the time picker was not touched.
     */
    @Test
    fun globalHistoryViewModel_saveEditTask_preservesTimeOnDateOnlyChange() = runTest {
        // Original task: dueDate at H=22 M=45 on day-0
        val original = makeTask(id = 2L)
        fakeRepo.allTasksFlow.value = listOf(original)

        // Simulate "date-only change": tomorrow at same H=22 M=45 (UI uses mergeDateTime
        // which keeps the time; we pass the result directly to saveEditTask)
        val newDueDate = Calendar.getInstance().apply {
            timeInMillis = dueDateWith22h45m
            add(Calendar.DAY_OF_YEAR, 1)  // one day forward, time unchanged
        }.timeInMillis

        val updated = original.copy(dueDate = newDueDate)
        vm.saveEditTask(updated)
        advanceUntilIdle()

        val stored = fakeRepo.allTasksFlow.value.find { it.id == original.id }
            ?: error("Task not found in FakeRepository after save")

        val cal = Calendar.getInstance().apply { timeInMillis = stored.dueDate!! }
        assertEquals(
            "T004 FR-007: date-only change must preserve HOUR_OF_DAY (expected 22)",
            22, cal.get(Calendar.HOUR_OF_DAY)
        )
        assertEquals(
            "T004 FR-007: date-only change must preserve MINUTE (expected 45)",
            45, cal.get(Calendar.MINUTE)
        )
    }

    // ── Test 3: recurring task dueDate time preserved (FR-008) ─────────────

    /**
     * FR-008: The fix must apply equally to recurring and non-recurring tasks.
     * A recurring task edited via saveEditTask must also preserve H:M.
     */
    @Test
    fun globalHistoryViewModel_saveEditTask_preservesDueDateTime_recurringTask() = runTest {
        val recurringTask = makeTask(id = 3L, isRecurring = true)
        fakeRepo.allTasksFlow.value = listOf(recurringTask)

        vm.saveEditTask(recurringTask)
        advanceUntilIdle()

        val stored = fakeRepo.allTasksFlow.value.find { it.id == recurringTask.id }
            ?: error("Recurring task not found in FakeRepository after save")

        val cal = Calendar.getInstance().apply { timeInMillis = stored.dueDate!! }
        assertEquals(
            "T004 FR-008: recurring task saveEditTask must preserve HOUR_OF_DAY (expected 22)",
            22, cal.get(Calendar.HOUR_OF_DAY)
        )
        assertEquals(
            "T004 FR-008: recurring task saveEditTask must preserve MINUTE (expected 45)",
            45, cal.get(Calendar.MINUTE)
        )
    }
}
