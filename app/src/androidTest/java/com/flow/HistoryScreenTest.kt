package com.flow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.data.local.AppDatabase
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import javax.inject.Inject

/**
 * TI03 — Navigating to the History screen and back.
 *
 * Verifies:
 *  - Tapping the "History" icon on the Home screen navigates to GlobalHistoryScreen.
 *  - The History screen's TopAppBar displays the title "History".
 *  - Tapping the back arrow returns to the Home screen.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var db: AppDatabase

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { db.clearAllTables() }
    }

    @Test
    fun tappingHistoryIcon_navigatesToHistoryScreen() {
        // Home screen must be ready
        composeRule.onNodeWithText("Flow").assertIsDisplayed()

        // Tap the History icon in the TopAppBar
        composeRule.onNodeWithContentDescription("History").performClick()

        // History screen TopAppBar title
        composeRule.onNodeWithText("History").assertIsDisplayed()
    }

    @Test
    fun historyScreen_backButton_navigatesHome() {
        composeRule.onNodeWithText("Flow").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("History").performClick()
        composeRule.onNodeWithText("History").assertIsDisplayed()

        // Tap the ArrowBack icon
        composeRule.onNodeWithContentDescription("Back").performClick()

        // Back on the Home screen
        composeRule.onNodeWithText("Flow").assertIsDisplayed()
    }

    @Test
    fun historyScreen_isInitiallyEmpty_orShowsItems_afterNavigation() {
        composeRule.onNodeWithText("Flow").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("History").performClick()

        // History screen is visible regardless of content
        composeRule.onNodeWithText("History").assertIsDisplayed()

        // No crash — the screen loaded successfully
        composeRule.waitForIdle()
    }

    // ── T041: Recurring task status change from history disappears from list ──

    /**
     * T041 [US2] — Verify FR-006 end-to-end on device:
     *  1. A recurring task completed today appears in the history list.
     *  2. Long-pressing → "Edit task" → selecting "TODO" → Save makes it
     *     disappear from the history list immediately.
     *  3. Navigating back to the Home screen confirms the task is still visible
     *     there (recurring tasks are always shown on the home screen).
     */
    @Test
    fun recurringTask_statusChangedFromHistory_disappearsFromList() {
        val taskTitle = "T041 Recurring Habit Test"
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Seed: recurring task in COMPLETED state + matching task_log
        val taskId: Long
        runBlocking {
            taskId = db.taskDao().insertTask(
                TaskEntity(
                    title               = taskTitle,
                    isRecurring         = true,
                    startDate           = today - 86_400_000L,  // started yesterday
                    dueDate             = today + 86_340_000L,  // due 11:59 PM today
                    status              = TaskStatus.COMPLETED,
                    completionTimestamp = today + 3_600_000L    // completed 1h after midnight
                )
            )
            db.taskCompletionLogDao().insertLog(
                TaskCompletionLog(
                    taskId      = taskId,
                    date        = today,
                    timestamp   = today + 3_600_000L,
                    isCompleted = true
                )
            )
        }

        // Navigate to History screen
        composeRule.onNodeWithText("Flow").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("History").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("History").assertIsDisplayed()

        // Task must appear in the history list
        composeRule.onNodeWithText(taskTitle).assertIsDisplayed()

        // Long-press the task row to open the action sheet (recurring items)
        composeRule.onNodeWithText(taskTitle).performTouchInput {
            down(0, center)
            advanceEventTime(1000L)  // well beyond the long-press timeout (~500 ms)
            up(0)
        }
        composeRule.waitForIdle()

        // Action sheet offers "Edit task"
        composeRule.onNodeWithText("Edit task").assertIsDisplayed()
        composeRule.onNodeWithText("Edit task").performClick()
        composeRule.waitForIdle()

        // TaskEditSheet — click the "TODO" status chip to revert completion
        composeRule.onNodeWithText("TODO").assertIsDisplayed()
        composeRule.onNodeWithText("TODO").performClick()
        composeRule.waitForIdle()

        // Confirm save
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()

        // ── Assert 1: Task no longer in the history list ────────────────────
        // History shows only completed items; changing to TODO must remove it
        composeRule.onNodeWithText(taskTitle).assertDoesNotExist()

        // ── Assert 2: Task appears on Home screen (recurring, always shown) ─
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Flow").assertIsDisplayed()
        composeRule.onNodeWithText(taskTitle).assertIsDisplayed()
    }
}
