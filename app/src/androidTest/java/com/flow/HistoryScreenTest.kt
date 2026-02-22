package com.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
}
