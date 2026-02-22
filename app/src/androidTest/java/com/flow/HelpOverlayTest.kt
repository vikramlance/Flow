package com.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flow.presentation.home.HelpOverlay
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TI04 — HelpOverlay renders help content.
 * TI05 — "Got it!" dismisses the overlay.
 */
@RunWith(AndroidJUnit4::class)
class HelpOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    // TI04 — overlay displays its header and button
    @Test
    fun helpOverlay_showsTitle_andGotItButton() {
        composeRule.setContent {
            HelpOverlay(onDismiss = {})
        }
        composeRule.onNodeWithText("How to use Flow", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Got it!").assertIsDisplayed()
    }

    @Test
    fun helpOverlay_showsTips() {
        composeRule.setContent {
            HelpOverlay(onDismiss = {})
        }
        // At least one tip should be visible
        composeRule.onNodeWithText("Tap a task card", substring = true).assertIsDisplayed()
    }

    // TI05 — tapping "Got it!" invokes onDismiss
    @Test
    fun helpOverlay_gotItButton_invokesOnDismiss() {
        var dismissed = false
        composeRule.setContent {
            HelpOverlay(onDismiss = { dismissed = true })
        }
        composeRule.onNodeWithText("Got it!").performClick()
        assertTrue("onDismiss should have been called", dismissed)
    }
}
