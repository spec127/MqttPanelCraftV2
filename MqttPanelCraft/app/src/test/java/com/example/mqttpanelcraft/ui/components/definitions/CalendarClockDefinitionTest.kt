package com.example.mqttpanelcraft.ui.components.definitions

import com.example.mqttpanelcraft.model.ComponentData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarClockDefinitionTest {
    @Test
    fun mergeKeepsClockFunctionsOnLegacyType() {
        val merged = CalendarClockDefinition.mergeComponent(
            ComponentData(
                1, "CLOCK", 4f, 8f, 200, 90, "clock1", "",
                mutableMapOf(
                    "clock_mode" to "SCHEDULE",
                    "schedule_time" to "21:15",
                    "trigger_value" to "GO",
                    "linked_components" to "4",
                    "visual_style" to "COMBO"
                )
            )
        )
        assertEquals("CALENDAR", merged.type)
        assertEquals("CLOCK", merged.props["family_kind"])
        assertEquals("SCHEDULE", merged.props["clock_mode"])
        assertEquals("21:15", merged.props["schedule_time"])
        assertEquals("GO", merged.props["trigger_value"])
        assertEquals("4", merged.props["linked_components"])
        assertEquals("COMBO", merged.props["visual_style"])
        assertEquals("COMBO", merged.props["clock_style"])
        assertEquals("MONTH", merged.props["calendar_style"])
        assertEquals("HH:mm", merged.props["time_format"])
        assertEquals(200, merged.width)
        assertEquals(90, merged.height)
    }

    @Test
    fun defaultSizesFollowFamilyAndStyle() {
        assertEquals(180 to 180, CalendarClockDefinition.defaultSizeDp("CALENDAR", "MONTH"))
        assertEquals(140 to 168, CalendarClockDefinition.defaultSizeDp("CALENDAR", "BIG_DATE"))
        assertEquals(180 to 88, CalendarClockDefinition.defaultSizeDp("CALENDAR", "DATE_TIME"))
        assertEquals(160 to 100, CalendarClockDefinition.defaultSizeDp("CLOCK", "DIGITAL"))
        assertEquals(160 to 160, CalendarClockDefinition.defaultSizeDp("CLOCK", "ANALOG"))
        assertEquals(160 to 160, CalendarClockDefinition.defaultSizeDp("CLOCK", "COMBO"))
    }

    @Test
    fun resizeAppliesOnlyWhenCurrentSizeMatchesPreviousDefault() {
        val density = 2f
        val matching = ComponentData(1, "CALENDAR", 0f, 0f, 360, 360, "c", props = mutableMapOf())
        val updates = mutableListOf<Pair<String, String>>()
        CalendarClockDefinition.maybeResizeForStyle(
            matching, density, "CALENDAR", "MONTH", "CLOCK", "DIGITAL"
        ) { key, value -> updates += key to value }
        assertEquals(listOf("w" to "320", "h" to "200"), updates)

        val custom = matching.copy(width = 400, height = 220)
        val skipped = mutableListOf<Pair<String, String>>()
        CalendarClockDefinition.maybeResizeForStyle(
            custom, density, "CALENDAR", "MONTH", "CLOCK", "DIGITAL"
        ) { key, value -> skipped += key to value }
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun familyStylesStayIndependentWhenActiveMirrorChanges() {
        val merged = CalendarClockDefinition.mergeComponent(
            ComponentData(
                1, "CALENDAR", 0f, 0f, 160, 160, "calendar1", "",
                mutableMapOf(
                    "family_kind" to "CLOCK",
                    "visual_style" to "DIGITAL",
                    "calendar_style" to "BIG_DATE",
                    "clock_style" to "ANALOG"
                )
            )
        )
        assertEquals("BIG_DATE", merged.props["calendar_style"])
        assertEquals("ANALOG", merged.props["clock_style"])
        assertEquals("ANALOG", merged.props["visual_style"])
        assertEquals("CLOCK", merged.props["family_kind"])
    }
}
