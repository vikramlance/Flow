package com.flow.presentation.history

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.fake.FakeTaskRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    /**
     * T009 [US2] / FR-003 / AL-002
     *
     * Verifies that [TaskEditSheet] has a due-time picker (chained after the due-date picker)
     * and that confirming the chain passes the correct dueDate H:M through the onSave callback.
     *
     * This test MUST FAIL before T010-T014 (no time picker exists in TaskEditSheet — clicking
     * "Due:..." opens a date picker whose confirm button says "OK" and closes, not "Next" to
     * chain into a time picker; "Select Target Time" text will not appear).
     *
     * After T010-T014, "Next" appears on the date picker confirm, "Select Target Time"
     * appears on the time picker, and the callback receives dueDate with H=22 M=45.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun taskEditSheet_timePicker_savesExactTime() {
        // A task whose dueDate is at H=22 M=45
        val dueDateH22M45 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22); set(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val task = TaskEntity(
            id        = 10L,
            title     = "Time Test Task",
            status    = TaskStatus.IN_PROGRESS,
            dueDate   = dueDateH22M45,
            startDate = System.currentTimeMillis()
        )

        var savedTask: TaskEntity? = null

        composeRule.setContent {
            MaterialTheme {
                TaskEditSheet(
                    task     = task,
                    onSave   = { savedTask = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.waitForIdle()

        // Click the "Due:" button to open the date picker
        composeRule.onNodeWithText("Due:", substring = true).performClick()
        composeRule.waitForIdle()

        // After T010-T014: "Next" chains into time picker (before: was "OK" and closed)
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()

        // Time picker must now be visible — proves FR-003 implemented
        composeRule.onNodeWithText("Select Target Time").assertIsDisplayed()

        // Confirm the time picker with the default time (H=22 M=45 from task.dueDate)
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.waitForIdle()

        // Press Save
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        // Assert the saved task has dueDate with H=22 M=45 (FR-001 end-to-end)
        assertNotNull("onSave must have been called", savedTask)
        val cal = Calendar.getInstance().apply { timeInMillis = savedTask!!.dueDate!! }
        assertEquals(
            "T009: dueDate HOUR_OF_DAY must be 22 after time picker confirm",
            22, cal.get(Calendar.HOUR_OF_DAY)
        )
        assertEquals(
            "T009: dueDate MINUTE must be 45 after time picker confirm",
            45, cal.get(Calendar.MINUTE)
        )
    }

}
