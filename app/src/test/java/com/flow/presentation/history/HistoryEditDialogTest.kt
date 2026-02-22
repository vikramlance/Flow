package com.flow.presentation.history

import com.flow.data.local.TaskCompletionLog
import com.flow.fake.FakeTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T043 — [GlobalHistoryViewModel] edit-log unit tests.
 *
 * Verifies:
 *  (a) Valid date edit dismisses dialog (no error)
 *  (b) Future completion date blocked with error message
 *  (c) Unknown taskId blocked with error message
 *  (d) dismissEditLog clears all dialog state
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryEditDialogTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeTaskRepository
    private lateinit var viewModel: GlobalHistoryViewModel

    private val todayMidnight: Long
        get() {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeTaskRepository()
        viewModel = GlobalHistoryViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── (a) Date edit persists ────────────────────────────────────────────────

    @Test
    fun updateLogEntry_validDate_savesAndDismissesDialog() = runTest {
        // FakeRepo assigns id=1L to first inserted task
        repo.addTask("Recurring habit", System.currentTimeMillis() - 10 * 86_400_000L, null, true)
        val taskId = 1L
        val yesterday = todayMidnight - 86_400_000L
        val log = TaskCompletionLog(id = 1L, taskId = taskId, date = yesterday, isCompleted = true)

        viewModel.openEditLog(log)
        advanceUntilIdle()
        assertEquals("editingLog should be set", log, viewModel.uiState.value.editingLog)

        val twoDaysAgo = todayMidnight - 2 * 86_400_000L
        viewModel.updateLogEntry(log.copy(date = twoDaysAgo))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("dialog dismissed after save", state.editingLog)
        assertNull("no error after valid save", state.editError)
    }

    // ── (b) Future completion date is blocked ─────────────────────────────────

    @Test
    fun updateLogEntry_futureDate_setsEditError() = runTest {
        repo.addTask("Recurring habit", System.currentTimeMillis() - 10 * 86_400_000L, null, true)
        val taskId = 1L
        val log = TaskCompletionLog(id = 1L, taskId = taskId, date = todayMidnight - 86_400_000L, isCompleted = true)

        viewModel.openEditLog(log)
        advanceUntilIdle()

        val tomorrow = todayMidnight + 86_400_000L
        viewModel.updateLogEntry(log.copy(date = tomorrow))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("editError must be set for future date", state.editError)
        assertTrue("error mentions future", state.editError!!.contains("future", ignoreCase = true))
        assertNotNull("dialog stays open on error", state.editingLog)
    }

    // ── (c) Missing task ID returns error ─────────────────────────────────────

    @Test
    fun updateLogEntry_unknownTaskId_setsEditError() = runTest {
        val orphanLog = TaskCompletionLog(
            id = 99L, taskId = 9999L,
            date = todayMidnight - 86_400_000L, isCompleted = true
        )

        viewModel.openEditLog(orphanLog)
        advanceUntilIdle()

        viewModel.updateLogEntry(orphanLog)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("editError must be set for missing task", state.editError)
        assertNotNull("dialog stays open on error", state.editingLog)
    }

    // ── (d) dismissEditLog clears state ───────────────────────────────────────

    @Test
    fun dismissEditLog_clearsAllDialogState() = runTest {
        val log = TaskCompletionLog(id = 1L, taskId = 1L, date = todayMidnight - 86_400_000L, isCompleted = true)
        viewModel.openEditLog(log)
        advanceUntilIdle()

        viewModel.dismissEditLog()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull("editingLog cleared", state.editingLog)
        assertNull("editError cleared", state.editError)
    }
}
