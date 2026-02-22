package com.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.data.local.AppDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * TI01 — A task remains visible on the Home screen after its status is advanced.
 *
 * Strategy:
 *  - Clear the DB in @Before so [HomeViewModel.checkAndPopulateDummyData] always seeds the
 *    5 known dummy tasks on first collection.
 *  - "Morning Jog 🏃‍♂️" is recurring, so it always appears in getHomeScreenTasks.
 *  - Tap once  → TODO  → IN_PROGRESS  — still visible (recurring, not completed)
 *  - Tap again → IN_PROGRESS → COMPLETED — still visible (recurring tasks always shown)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TaskPersistenceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var db: AppDatabase

    @Before
    fun setUp() {
        hiltRule.inject()
        // Wipe the DB so dummy tasks are always freshly seeded by the ViewModel
        runBlocking { db.clearAllTables() }
    }

    @Test
    fun task_remainsVisible_afterStatusAdvancedToInProgress() {
        // Wait for home screen title
        composeRule.onNodeWithText("Flow").assertIsDisplayed()

        // Morning Jog is a recurring dummy task — always in the home list
        val taskNode = composeRule.onNodeWithText("Morning Jog", substring = true)
        taskNode.assertIsDisplayed()

        // Advance status: TODO → IN_PROGRESS
        taskNode.performClick()

        // Must still be visible
        composeRule.onNodeWithText("Morning Jog", substring = true).assertIsDisplayed()
    }

    @Test
    fun recurringTask_remainsVisible_afterCompletion() {
        composeRule.onNodeWithText("Flow").assertIsDisplayed()

        val taskNode = composeRule.onNodeWithText("Morning Jog", substring = true)
        taskNode.assertIsDisplayed()

        // Advance: TODO → IN_PROGRESS → COMPLETED (two taps)
        taskNode.performClick()
        composeRule.onNodeWithText("Morning Jog", substring = true).performClick()

        // Recurring completed tasks are always included in getHomeScreenTasks
        composeRule.onNodeWithText("Morning Jog", substring = true).assertIsDisplayed()
    }
}
