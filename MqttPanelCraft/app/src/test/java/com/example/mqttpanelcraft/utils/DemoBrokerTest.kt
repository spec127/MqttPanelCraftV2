package com.example.mqttpanelcraft.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBrokerTest {
    @Test
    fun recognizesTutorialHostIgnoringCaseAndSpaces() {
        assertTrue(DemoBroker.isLocal("demo.local"))
        assertTrue(DemoBroker.isLocal("  DEMO.LOCAL  "))
        assertFalse(DemoBroker.isLocal("broker.emqx.io"))
        assertFalse(DemoBroker.isLocal(""))
        assertFalse(DemoBroker.isLocal(null))
    }

    @Test
    fun sharedTutorialTopicMatchesFactorySuffix() {
        assertEquals(
            "tutorial_local/abc123/light/cmd",
            TutorialTopics.sharedCommandTopic("Tutorial_Local", "abc123")
        )
    }
}
