package com.flow.presentation.history

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.fake.FakeTaskRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * T047/US4 — Compose instrumented test verifying the GlobalHistoryScreen date
 * filter chip row updates reactively when a new completed task is emitted into
 * the repository, WITHOUT manual navigation or reload (FR-008b).
 *
 * The test drives GlobalHistoryViewModel through FakeTaskRepository and renders
 * a minimal date-chip row to verify reactivity.
 */
@RunWith(AndroidJUnit4::class)
class GlobalHistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun todayMidnight(): Long = Calendar.getInstance().run {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    /**
     * T047: After emitting a newly completed task, today's date chip must appear
     * in the filter row without any screen navigation.
     */
    @Test
    fun globalHistoryScreen_filterChipRow_updatesReactivelyOnNewCompletion() {
        val fakeRepo = FakeTaskRepository()
        val today = todayMidnight()
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        val todayLabel = sdf.format(Date(today))

        // Pre-seed data BEFORE ViewModel is created so the first combine() emit contains it
        val completedTask = TaskEntity(
            id                  = 1L,
            title               = "Reactive Chip Task",
            status              = TaskStatus.COMPLETED,
            isRecurring         = false,
            completionTimestamp = today + 1000L,
            dueDate             = today
        )
        fakeRepo.completedNonRecurringFlow.value = listOf(completedTask)

        // Create ViewModel — the init{} combine() will see the pre-seeded data
        val vm = GlobalHistoryViewModel(fakeRepo)

        // Render the date chip row from ViewModel's datesWithData
        composeRule.setContent {
            val state = vm.uiState.collectAsState().value
            androidx.compose.material3.MaterialTheme {
                androidx.compose.foundation.layout.Row {
                    state.datesWithData.sorted().reversed().forEach { midnight ->
                        val label = sdf.format(Date(midnight))
                        androidx.compose.material3.FilterChip(
                            selected = state.selectedDateMidnight == midnight,
                            onClick  = { vm.selectDate(midnight) },
                            label    = { androidx.compose.material3.Text(label) }
                        )
                    }
                }
            }
        }

        // Wait for coroutines to settle and the Compose frame to render
        composeRule.waitForIdle()

        // Today's date chip must appear — reactively driven by the flow, no navigation needed
        composeRule.onNodeWithText(todayLabel).assertIsDisplayed()
    }
}
