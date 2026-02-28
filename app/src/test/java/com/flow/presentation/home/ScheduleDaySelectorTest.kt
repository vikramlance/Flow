package com.flow.presentation.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T023 / US4 — RED tests for the [ScheduleDaySelector] pure helper functions.
 *
 * Tests target three `internal` functions to be created in T024:
 *   - [applySchedulePreset] — maps a [SchedulePreset] to its canonical bitmask
 *   - [toggleDayBit]         — XOR-toggles a single day bit in a mask
 *   - [isScheduleMaskValid]  — validates that a mask encodes at least one selected day
 *
 * Expected to be RED (compile error) until T024 Step 1 creates the functions and enum.
 */
class ScheduleDaySelectorTest {

    // ── applySchedulePreset ────────────────────────────────────────────────────

    @Test
    fun applySchedulePreset_allDays_returns127() {
        assertEquals(
            "ALL_DAYS preset must yield bitmask 127 (all 7 bits set)",
            127,
            applySchedulePreset(SchedulePreset.ALL_DAYS)
        )
    }

    @Test
    fun applySchedulePreset_weekdays_returns31() {
        assertEquals(
            "WEEKDAYS preset must yield bitmask 31 (bits 0–4: Mon–Fri)",
            31,
            applySchedulePreset(SchedulePreset.WEEKDAYS)
        )
    }

    @Test
    fun applySchedulePreset_weekends_returns96() {
        assertEquals(
            "WEEKENDS preset must yield bitmask 96 (bits 5–6: Sat+Sun)",
            96,
            applySchedulePreset(SchedulePreset.WEEKENDS)
        )
    }

    // ── toggleDayBit ──────────────────────────────────────────────────────────

    @Test
    fun toggleDayBit_allDaysToggleMonday_returns126() {
        assertEquals(
            "Toggling bit-0 (Monday) on mask=127 must return 126",
            126,
            toggleDayBit(127, 0)
        )
    }

    @Test
    fun toggleDayBit_offDayToggleAdds() {
        // mask=0b0000000 (no days), toggle bit-0 → 0b0000001 = 1
        assertEquals(
            "Toggling bit-0 on mask=0 must set the bit (0→1)",
            1,
            toggleDayBit(0, 0)
        )
    }

    @Test
    fun toggleDayBit_existingBitToggleRemoves() {
        // mask=0b0000001 (Monday only), toggle bit-0 → 0b0000000 = 0
        assertEquals(
            "Toggling the only set bit must return 0",
            0,
            toggleDayBit(1, 0)
        )
    }

    // ── isScheduleMaskValid ───────────────────────────────────────────────────

    @Test
    fun isScheduleMaskValid_zeroReturnsFalse() {
        assertFalse(
            "mask=0 means no day selected — must be invalid",
            isScheduleMaskValid(0)
        )
    }

    @Test
    fun isScheduleMaskValid_nullReturnsTrue() {
        assertTrue(
            "null mask means 'every day' (FR-018) — must be treated as valid",
            isScheduleMaskValid(null)
        )
    }

    @Test
    fun isScheduleMaskValid_127ReturnsTrue() {
        assertTrue(
            "mask=127 (all days) must be valid",
            isScheduleMaskValid(127)
        )
    }
}
