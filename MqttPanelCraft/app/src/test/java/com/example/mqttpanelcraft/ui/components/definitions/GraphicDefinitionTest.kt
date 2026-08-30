package com.example.mqttpanelcraft.ui.components.definitions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicDefinitionTest {
    @Test
    fun regularPolygonFallbackCreatesVisibleNormalizedPoints() {
        val points =
            createRegularPolygonPoints(4).split(";").map { point ->
                point.split(",").map(String::toFloat)
            }

        assertEquals(4, points.size)
        assertTrue(points.flatten().all { it in 0f..1f })
    }

    @Test
    fun directionChangeSwapsWidthAndHeight() {
        assertEquals(100 to 200, swapGraphicDimensions(200, 100))
    }
}
