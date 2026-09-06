package com.example.mqttpanelcraft.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasInteractionGeometryTest {
    @Test
    fun deleteZoneUsesCanvasScreenCoordinatesAndOverlap() {
        assertFalse(isFingerInDeleteZone(799f, 100f, 800, 100))
        assertTrue(isFingerInDeleteZone(801f, 100f, 800, 100))
    }
}
