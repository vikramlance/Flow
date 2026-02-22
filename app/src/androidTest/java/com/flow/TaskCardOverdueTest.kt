package com.flow

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.presentation.home.TaskItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * TI02 — Overdue task card shows the "Overdue" countdown label and an orange 2dp border.
 *
 * The border colour itself is a visual property not easily asserted in semantics, so we
 * confirm the overdue state is recognised by checking the label text that is produced
 * only when [isOverdue] is true inside [TaskItem].
 */
@RunWith(AndroidJUnit4::class)
class TaskCardOverdueTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildYesterdayMidnight(): Long =
        Calendar.getInstance().run {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -1)
            timeInMillis
        }

    @Test
    fun overdueTask_displaysOverdueLabel() {
        val overdueTask = TaskEntity(
            id = 1L,
            title = "Overdue Task",
            status = TaskStatus.TODO,
            dueDate = buildYesterdayMidnight() + 1_000L,   // 1 second after yesterday midnight
            isRecurring = false,
            createdAt = System.currentTimeMillis() - 86_400_000L
        )

        composeRule.setContent {
            MaterialTheme {
                TaskItem(
                    task = overdueTask,
                    streakCount = 0,
                    onStatusChange = {},
                    onEdit = {},
                    onShowStreak = {}
                )
            }
        }

        // The label is either "Overdue!" or "Overdue by Xd" — both contain "Overdue"
        composeRule.onNodeWithText("Overdue", substring = true).assertIsDisplayed()
    }

    @Test
    fun futureTask_doesNotShowOverdueLabel() {
        val futureTask = TaskEntity(
            id = 2L,
            title = "Future Task",
            status = TaskStatus.TODO,
            dueDate = System.currentTimeMillis() + 10 * 86_400_000L,  // 10 days from now
            isRecurring = false,
            createdAt = System.currentTimeMillis() - 1_000L
        )

        composeRule.setContent {
            MaterialTheme {
                TaskItem(
                    task = futureTask,
                    streakCount = 0,
                    onStatusChange = {},
                    onEdit = {},
                    onShowStreak = {}
                )
            }
        }

        // No "Overdue" text should be present
        composeRule.onNodeWithText("Overdue", substring = true).assertDoesNotExist()
    }

    @Test
    fun completedTask_doesNotShowOverdueLabel() {
        val completedTask = TaskEntity(
            id = 3L,
            title = "Completed Task",
            status = TaskStatus.COMPLETED,
            dueDate = buildYesterdayMidnight() + 1_000L,  // past due but completed
            isRecurring = false,
            createdAt = System.currentTimeMillis() - 86_400_000L
        )

        composeRule.setContent {
            MaterialTheme {
                TaskItem(
                    task = completedTask,
                    streakCount = 0,
                    onStatusChange = {},
                    onEdit = {},
                    onShowStreak = {}
                )
            }
        }

        // Completed overdue tasks should NOT show the overdue label (isCompleted check in TaskItem)
        composeRule.onNodeWithText("Overdue", substring = true).assertDoesNotExist()
    }
}
