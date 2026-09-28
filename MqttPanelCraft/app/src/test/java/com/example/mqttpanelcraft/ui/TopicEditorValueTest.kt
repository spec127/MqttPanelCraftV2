package com.example.mqttpanelcraft.ui

import org.junit.Assert.*
import org.junit.Test

class TopicEditorValueTest {
    @Test fun fullTopicRoundTripsWithoutTruncationOrNormalization() {
        listOf("Core_Test/long_project_id_123456789/button_1", "tutorial/id/light",
            "outside/感測器/Temperature", "custom/#", "").forEach {
            assertEquals(it, TopicEditorValue.acceptedOrPrevious(it, "old"))
        }
    }
    @Test fun invalidDraftDoesNotReplacePersistedValue() {
        listOf("bad\naddress", "bad\raddress", "bad\u0000address", "a".repeat(65536)).forEach {
            assertEquals("original/id/button", TopicEditorValue.acceptedOrPrevious(it, "original/id/button"))
        }
    }
    @Test fun lengthLimitUsesUtf8Bytes() {
        assertTrue(TopicEditorValue.isValid("a".repeat(65535)))
        assertFalse(TopicEditorValue.isValid("測".repeat(21846)))
    }
}
