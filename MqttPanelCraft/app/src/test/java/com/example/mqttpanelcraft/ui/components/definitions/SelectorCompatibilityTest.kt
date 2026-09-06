package com.example.mqttpanelcraft.ui.components.definitions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorCompatibilityTest {
    @Test
    fun canonicalDefaultsUseDistinctValues() {
        assertTrue(DEFAULT_SELECTOR_SEGMENTS.contains("\"value\":\"1\""))
        assertTrue(DEFAULT_SELECTOR_SEGMENTS.contains("\"value\":\"2\""))
        assertTrue(DEFAULT_SELECTOR_SEGMENTS.contains("\"value\":\"3\""))
        assertFalse(DEFAULT_SELECTOR_SEGMENTS.contains("\"val\":"))
    }

    @Test
    fun canonicalValueWinsAndLegacyValRemainsReadable() {
        assertEquals("new", resolveSelectorSegmentValue("new", "old"))
        assertEquals("old", resolveSelectorSegmentValue(null, "old"))
        assertEquals("0", resolveSelectorSegmentValue(null, null))
    }
}
