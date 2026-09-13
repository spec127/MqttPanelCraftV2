package com.example.mqttpanelcraft.data

import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun component(id: Int, links: String) = ComponentData(
            id, "BUTTON", 0f, 0f, 10, 10, "button", props = mutableMapOf("linked_components" to links))
}
