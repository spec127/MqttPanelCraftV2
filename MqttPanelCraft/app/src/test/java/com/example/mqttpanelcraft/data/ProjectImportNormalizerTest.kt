package com.example.mqttpanelcraft.data

import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectImportNormalizerTest {
    @Test
    fun normalize_assignsUniqueIdsAndRemapsLinkedComponents() {
        val imported = Project(
                id = "imported",
                name = "Imported",
                broker = "broker",
                type = ProjectType.HOME,
                components = mutableListOf(
                        component(-1, "-1,42,999,81"),
                        component(42, "-1"),
                        component(42, "42"),
                        component(81, "")
                )
        )

        val result = ProjectImportNormalizer.normalize(imported)

        assertEquals(listOf(1, 2, 3, 4), result.project.components.map { it.id })
        assertEquals("4", result.project.components[0].props["linked_components"])
        assertEquals("", result.project.components[1].props["linked_components"])
        assertEquals("", result.project.components[2].props["linked_components"])
        assertEquals(5, result.removedLinkedReferences)
        assertEquals(3, result.repairedIds)
        assertEquals("-1,42,999,81", imported.components[0].props["linked_components"])
    }

    @Test
    fun validLinksAndUserDataSurviveImportWithoutMutatingSource() {
        val original = Project("id", "name", "broker", type = ProjectType.HOME,
                components = mutableListOf(component(50, "70"), component(70, "50")))
        original.components[0].props["image_src"] = "user-material"
        val normalized = ProjectImportNormalizer.normalize(original)
        assertEquals("2", normalized.project.components[0].props["linked_components"])
        assertEquals("1", normalized.project.components[1].props["linked_components"])
        assertEquals(0, normalized.repairedIds)
        normalized.project.components[0].props["image_src"] = "changed"
        assertEquals("user-material", original.components[0].props["image_src"])
        assertEquals(50, original.components[0].id)
    }

    @Test
    fun legacyClockBecomesCalendarClockWithoutLosingLinksOrSize() {
        val imported = Project(
            id = "imported",
            name = "Imported",
            broker = "broker",
            type = ProjectType.HOME,
            components = mutableListOf(
                ComponentData(
                    8, "CLOCK", 12f, 24f, 160, 100, "clock1", "",
                    mutableMapOf(
                        "clock_mode" to "COUNTDOWN",
                        "countdown_seconds" to "15",
                        "visual_style" to "ANALOG",
                        "linked_components" to "9",
                        "color" to "#123456"
                    )
                ),
                ComponentData(9, "BUTTON", 0f, 0f, 80, 40, "btn", "room/light", mutableMapOf("payload" to "ON"))
            )
        )

        val result = ProjectImportNormalizer.normalize(imported)
        val clock = result.project.components[0]
        assertEquals("CALENDAR", clock.type)
        assertEquals("CLOCK", clock.props["family_kind"])
        assertEquals("COUNTDOWN", clock.props["clock_mode"])
        assertEquals("15", clock.props["countdown_seconds"])
        assertEquals("ANALOG", clock.props["visual_style"])
        assertEquals("ANALOG", clock.props["clock_style"])
        assertEquals("MONTH", clock.props["calendar_style"])
        assertEquals("#123456", clock.props["color"])
        assertEquals("2", clock.props["linked_components"])
        assertEquals(160, clock.width)
        assertEquals(100, clock.height)
        assertEquals("CLOCK", imported.components[0].type)
    }

    @Test
    fun calendarClockMergeIsIdempotent() {
        val source = ComponentData(
            3, "CALENDAR", 0f, 0f, 180, 180, "calendar1", "",
            mutableMapOf("family_kind" to "CALENDAR", "visual_style" to "MONTH", "color" to "#7B1FA2")
        )
        val first = ProjectImportNormalizer.normalize(
            Project("id", "name", "broker", type = ProjectType.HOME, components = mutableListOf(source.deepCopy()))
        ).project.components[0]
        val second = ProjectImportNormalizer.normalize(
            Project("id", "name", "broker", type = ProjectType.HOME, components = mutableListOf(first.deepCopy()))
        ).project.components[0]
        assertEquals(first, second)
    }

    private fun component(id: Int, links: String) = ComponentData(
            id, "BUTTON", 0f, 0f, 10, 10, "button", props = mutableMapOf("linked_components" to links))
}
