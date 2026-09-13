package com.example.mqttpanelcraft.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectIdentityTest {
    @Test fun generatedIdOnScreenIsUsedForBothNewAndExistingProjects() {
        assertEquals("new123", resolveProjectId("new123", null) { error("Must not regenerate") })
        assertEquals("new123", resolveProjectId("new123", "old456") { error("Must not regenerate") })
    }

    @Test fun emptyFieldKeepsExistingIdOrGeneratesOnce() {
        assertEquals("old456", resolveProjectId("  ", "old456") { error("Must keep existing") })
        var calls = 0
        assertEquals("generated", resolveProjectId("", null) { calls++; "generated" })
        assertEquals(1, calls)
    }
}
