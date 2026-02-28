package com.flow.presentation.home

import com.flow.data.local.TaskEntity
import com.flow.fake.FakeSettingsRepository
import com.flow.fake.FakeTaskRepository
import com.flow.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * T026-test / US4 — RED tests for [EditTaskDialog] recurring schedule wiring.
 *
 * Two concerns:
 *   (a) Pure function [resolveInitialScheduleMask] — converts a stored null mask to
 *       127 (all-days) for backward compatibility when the dialog opens (FR-018).
 *   (b) ViewModel sanity — [HomeViewModel.updateTask] passes the scheduleMask through
 *       to the repository unchanged, and does not touch any completion logs.
 *
 * Tests (a) are RED until T026 creates [resolveInitialScheduleMask] in HomeScreen.kt.
 * Tests (b) are GREEN immediately (existing ViewModel behaviour).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditTaskDialogScheduleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo    : FakeTaskRepository
    private lateinit var fakeSettings: FakeSettingsRepository
    private lateinit var viewModel   : HomeViewModel

    @Before
    fun setUp() {
        fakeRepo    = FakeTaskRepository()
        fakeSettings = FakeSettingsRepository()
        fakeRepo.allTasksFlow.value = emptyList()
        viewModel = HomeViewModel(fakeRepo, fakeSettings)
    }

    // ── (a) resolveInitialScheduleMask — pure function tests (RED) ────────────

    @Test
    fun resolveInitialScheduleMask_whenNull_returns127() {
        assertEquals(
            "T026: null scheduleMask must initialise to 127 (all days — FR-018 backward compat)",
            127,
            resolveInitialScheduleMask(null)
        )
    }

    @Test
    fun resolveInitialScheduleMask_whenNonNull_passesThrough() {
        val monWed = 0b0000101  // Mon (bit 0) + Wed (bit 2) = 5
        assertEquals(
            "T026: non-null scheduleMask must pass through unchanged",
            monWed,
            resolveInitialScheduleMask(monWed)
        )
    }

    // ── (b) ViewModel — scheduleMask preserved through updateTask ─────────────

    @Test
    fun updateTask_scheduleMaskPreservedInRepository() = runTest {
        val original = TaskEntity(id = 1L, title = "Habit", isRecurring = true, scheduleMask = 127)
        fakeRepo.allTasksFlow.value = listOf(original)

        val job = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Edit: Mon+Wed only (scheduleMask = 5)
        val monWed = 0b0000101
        viewModel.updateTask(original.copy(scheduleMask = monWed))
        advanceUntilIdle()

        val stored = fakeRepo.allTasksFlow.value.find { it.id == 1L }
        assertEquals(
            "T026: updateTask must preserve the caller-supplied scheduleMask",
            monWed,
            stored?.scheduleMask
        )
        job.cancel()
    }

    @Test
    fun updateTask_doesNotModifyCompletionLogs() = runTest {
        // Seed a completed task with a log
        val task = TaskEntity(id = 2L, title = "Streak", isRecurring = true, scheduleMask = 5)
        fakeRepo.allTasksFlow.value = listOf(task)

        val initialLogCount = fakeRepo.completedLogsFlow.value.size

        val job = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Editing the task should NOT create or delete any completion logs
        viewModel.updateTask(task.copy(scheduleMask = 31))
        advanceUntilIdle()

        assertEquals(
            "T026: updateTask must NOT modify TaskCompletionLog rows",
            initialLogCount,
            fakeRepo.completedLogsFlow.value.size
        )
        job.cancel()
    }
}
