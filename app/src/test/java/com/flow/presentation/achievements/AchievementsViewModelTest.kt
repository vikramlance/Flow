package com.flow.presentation.achievements

import com.flow.data.local.AchievementEntity
import com.flow.data.local.AchievementType
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

/**
 * T011 / C-003 — Unit tests for [AchievementsViewModel].
 *
 * (a) Verifies [toggleHowItWorks] inverts [AchievementsUiState.isHowItWorksExpanded].
 * (b) Verifies a list emitted from [FakeTaskRepository.achievementsFlow] populates
 *     [AchievementsUiState.earned].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeTaskRepository
    private lateinit var viewModel: AchievementsViewModel

    @Before
    fun setUp() {
        fakeRepo = FakeTaskRepository()
        viewModel = AchievementsViewModel(fakeRepo)
    }

    // ── (a) toggleHowItWorks ─────────────────────────────────────────────────

    @Test
    fun toggleHowItWorks_startsCollapsed() {
        assertFalse(
            "isHowItWorksExpanded should be false on init",
            viewModel.uiState.value.isHowItWorksExpanded
        )
    }

    @Test
    fun toggleHowItWorks_expandsOnFirstCall() = runTest {
        viewModel.toggleHowItWorks()
        assertTrue(
            "isHowItWorksExpanded should be true after first toggle",
            viewModel.uiState.value.isHowItWorksExpanded
        )
    }

    @Test
    fun toggleHowItWorks_collapsesOnSecondCall() = runTest {
        viewModel.toggleHowItWorks()
        viewModel.toggleHowItWorks()
        assertFalse(
            "isHowItWorksExpanded should be false after second toggle",
            viewModel.uiState.value.isHowItWorksExpanded
        )
    }

    // ── (b) earned achievements flow ─────────────────────────────────────────

    @Test
    fun earnedAchievements_populatesFromRepository() = runTest {
        val sub = launch { viewModel.uiState.collect {} }

        val expected = listOf(
            AchievementEntity(
                id       = 1L,
                type     = AchievementType.STREAK_10,
                earnedAt = System.currentTimeMillis()
            ),
            AchievementEntity(
                id       = 2L,
                type     = AchievementType.EARLY_FINISH,
                earnedAt = System.currentTimeMillis() - 10_000L
            )
        )

        fakeRepo.achievementsFlow.value = expected
        advanceUntilIdle()

        assertEquals(
            "uiState.earned must reflect the list emitted by the repository flow",
            expected,
            viewModel.uiState.value.earned
        )

        sub.cancel()
    }

    @Test
    fun earnedAchievements_startsEmpty() = runTest {
        advanceUntilIdle()
        assertTrue(
            "uiState.earned must be empty when repository emits empty list",
            viewModel.uiState.value.earned.isEmpty()
        )
    }
}
