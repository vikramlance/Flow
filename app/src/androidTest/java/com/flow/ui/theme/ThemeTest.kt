package com.flow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T036/US9 — Compose test verifying [FlowTheme] uses `DarkBackground` (#121212) when
 * `dynamicColor = false`.
 *
 * Passing `dynamicColor = false` explicitly makes the assertion valid on ANY API level
 * without platform-level guards. The test verifies the color scheme background matches
 * the canonical `DarkBackground` constant defined in Color.kt.
 *
 * Note: Although the tasks spec listed this as "Tier 1", it requires Compose infrastructure
 * which is Android-only; hence it is placed in the instrumented test suite (Tier 2).
 * This is an intentional deviation to satisfy FR-019 within the existing project toolchain.
 */
@RunWith(AndroidJUnit4::class)
class ThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theme_dynamicColorDisabled_backgroundIsCanonicalDarkSurface() {
        var capturedBackground: Color = Color.Unspecified
        composeRule.setContent {
            FlowTheme(dynamicColor = false, darkTheme = true) {
                capturedBackground = MaterialTheme.colorScheme.background
            }
        }
        // DarkBackground = Color(0xFF121212) defined in Color.kt
        assertEquals(
            "FlowTheme with dynamicColor=false must use DarkBackground #121212",
            Color(0xFF121212),
            capturedBackground
        )
    }

    @Test
    fun theme_defaultDynamicColor_isFalse() {
        // When dynamicColor defaults to false, the dark background must be #121212
        // — the same result as explicitly passing dynamicColor = false.
        var capturedBackground = Color.Unspecified
        composeRule.setContent {
            // Intentionally using only the default dynamicColor parameter (omitting it).
            // If the default were `true` on API 31+, the background would differ from DarkBackground.
            FlowTheme(darkTheme = true) {
                capturedBackground = MaterialTheme.colorScheme.background
            }
        }
        assertEquals(
            "Default dynamicColor must equal false — dark background must be #121212",
            Color(0xFF121212),
            capturedBackground
        )
    }
}
