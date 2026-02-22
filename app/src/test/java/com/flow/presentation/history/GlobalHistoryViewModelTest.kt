package com.flow.presentation.history

import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskEntity
import com.flow.fake.FakeTaskRepository
import com.flow.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

/**
 * TU04 — Unit tests for [GlobalHistoryViewModel].
 * Verifies filteredItems logic: showAll, selectedDate, viewMode, filterMode.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeTaskRepository

    // Fixed midnight timestamps for test isolation
    private val today = midnight(0)
    private val yesterday = midnight(-1)
    private val twoDaysAgo = midnight(-2)

    @Before
    fun setUp() {
        fakeRepo = FakeTaskRepository()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun midnight(offsetDays: Int): Long {
        return Calendar.getInstance().run {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, offsetDays)
            timeInMillis
        }
    }

    private fun makeLog(taskId: Long, dayMidnight: Long, ts: Long = dayMidnight + 1000) =
        TaskCompletionLog(id = ts, taskId = taskId, date = dayMidnight, isCompleted = true, timestamp = ts)

    private fun makeRecurringTask(id: Long, title: String, dueDate: Long? = null) =
        TaskEntity(id = id, title = title, isRecurring = true, dueDate = dueDate)

    private fun makeNonRecurringTask(id: Long, title: String, completedAt: Long, dueDate: Long? = null) =
        TaskEntity(
            id = id, title = title, isRecurring = false, dueDate = dueDate,
            completionTimestamp = completedAt
        )

    private suspend fun buildVm(): GlobalHistoryViewModel {
        return GlobalHistoryViewModel(fakeRepo)
    }

    // ── TU04-a: showAll returns all items chronologically ────────────────────

    @Test
    fun `showAll=true returns all items most-recent-first`() = runTest {
        val task1 = makeRecurringTask(1L, "Daily Task")
        val task2 = makeNonRecurringTask(2L, "One-off Task", completedAt = yesterday + 3000)
        val log1 = makeLog(1L, today, today + 5000)
        val log2 = makeLog(1L, yesterday, yesterday + 500)

        fakeRepo.allTasksFlow.value = listOf(task1, task2)
        fakeRepo.completedLogsFlow.value = listOf(log1, log2)
        fakeRepo.completedNonRecurringFlow.value = listOf(task2)

        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.toggleShowAll()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.showAll)
        val items = vm.filteredItems(state)
        // showAll returns ALL items, most-recent-first
        assertEquals(3, items.size)
        assertTrue(items[0].completedAtMs >= items[1].completedAtMs)
        assertTrue(items[1].completedAtMs >= items[2].completedAtMs)
        job.cancel()
    }

    // ── TU04-b: selectedDate filters to that day ─────────────────────────────

    @Test
    fun `selectDate filters to matching day only`() = runTest {
        val task = makeRecurringTask(1L, "Recurring")
        val logToday = makeLog(1L, today, today + 1000)
        val logYesterday = makeLog(1L, yesterday, yesterday + 1000)

        fakeRepo.allTasksFlow.value = listOf(task)
        fakeRepo.completedLogsFlow.value = listOf(logToday, logYesterday)

        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.selectDate(today)
        advanceUntilIdle()

        val items = vm.filteredItems(vm.uiState.value)
        assertEquals(1, items.size)
        assertEquals(today, items[0].completedDayMidnight)
        job.cancel()
    }

    @Test
    fun `selectDate(null) shows all items`() = runTest {
        val task = makeRecurringTask(1L, "T")
        val logs = listOf(makeLog(1L, today), makeLog(1L, yesterday))

        fakeRepo.allTasksFlow.value = listOf(task)
        fakeRepo.completedLogsFlow.value = logs

        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.selectDate(today)
        advanceUntilIdle()
        vm.selectDate(null)
        advanceUntilIdle()

        val items = vm.filteredItems(vm.uiState.value)
        assertEquals(2, items.size)
        job.cancel()
    }

    // ── TU04-c: DATE_GROUPED view mode ───────────────────────────────────────

    @Test
    fun `default viewMode is DATE_GROUPED`() = runTest {
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(HistoryViewMode.DATE_GROUPED, vm.uiState.value.viewMode)
        job.cancel()
    }

    @Test
    fun `setViewMode to CHRONOLOGICAL is reflected in uiState`() = runTest {
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.setViewMode(HistoryViewMode.CHRONOLOGICAL)
        advanceUntilIdle()

        assertEquals(HistoryViewMode.CHRONOLOGICAL, vm.uiState.value.viewMode)
        job.cancel()
    }

    // ── TU04-d: filterMode TARGET_DATE groups by dueDate ─────────────────────

    @Test
    fun `filterMode TARGET_DATE filters by targetDate not completedDay`() = runTest {
        // Task completed today but its due date was yesterday
        val task = makeRecurringTask(1L, "Task", dueDate = yesterday)
        val log = makeLog(1L, today, today + 1000)  // completed today

        fakeRepo.allTasksFlow.value = listOf(task)
        fakeRepo.completedLogsFlow.value = listOf(log)

        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.setFilterMode(HistoryFilterMode.TARGET_DATE)
        vm.selectDate(yesterday)   // filter to yesterday's target date
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(HistoryFilterMode.TARGET_DATE, state.filterMode)
        val items = vm.filteredItems(state)
        // Should find the task because its targetDate is yesterday
        assertEquals(1, items.size)
        assertEquals("Task", items[0].taskTitle)
        job.cancel()
    }

    // ── TU04-e: datesWithData populated correctly ─────────────────────────────

    @Test
    fun `datesWithData contains midnight timestamps of all completed items`() = runTest {
        val task = makeRecurringTask(1L, "T")
        val logs = listOf(makeLog(1L, today), makeLog(1L, yesterday), makeLog(1L, twoDaysAgo))

        fakeRepo.allTasksFlow.value = listOf(task)
        fakeRepo.completedLogsFlow.value = logs

        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        val dates = vm.uiState.value.datesWithData
        assertTrue(dates.contains(today))
        assertTrue(dates.contains(yesterday))
        assertTrue(dates.contains(twoDaysAgo))
        job.cancel()
    }

    // ── TU04-f: non-recurring tasks appear as HistoryItems ───────────────────

    @Test
    fun `non-recurring completed task appears in history`() = runTest {
        val task = makeNonRecurringTask(10L, "One-Off Task", completedAt = today + 2000)
        fakeRepo.allTasksFlow.value = listOf(task)
        fakeRepo.completedNonRecurringFlow.value = listOf(task)

        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        val items = vm.filteredItems(vm.uiState.value)
        assertEquals(1, items.size)
        assertEquals("One-Off Task", items[0].taskTitle)
        assertFalse(items[0].isRecurring)
        job.cancel()
    }

    // ── TU04-g: toggleShowAll resets selectedDate ────────────────────────────

    @Test
    fun `toggleShowAll clears selectedDate and switches to CHRONOLOGICAL`() = runTest {
        val vm = buildVm()
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        vm.selectDate(today)
        advanceUntilIdle()

        vm.toggleShowAll()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.showAll)
        assertEquals(null, state.selectedDateMidnight)
        assertEquals(HistoryViewMode.CHRONOLOGICAL, state.viewMode)
        job.cancel()
    }
}
