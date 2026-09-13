package com.example.mqttpanelcraft.utils

import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import org.junit.Assert.assertEquals
import org.junit.Test

class TopicHelperTest {
    @Test
    fun formatBaseTopic_usesRootLowercaseAndSafeName() {
        assertEquals("core_test/id", TopicHelper.formatBaseTopic("Core_Test", "id"))
    }

    @Test
    fun collectSubscriptionTopics_deduplicatesAndKeepsExternalTopics() {
        val project =
                project(
                        "Core_Test",
                        listOf(
                                component("core_test/id/button_1"),
                                component("core_test/id/#"),
                                component(" external/exact "),
                                component("external/#"),
                                component("external/exact"),
                                component("   ")
                        )
                )

        assertEquals(
                linkedSetOf("core_test/id/#", "external/exact", "external/#"),
                TopicHelper.collectSubscriptionTopics(project)
        )
    }

    @Test
    fun collectSubscriptionTopics_afterRenameKeepsLegacyComponentTopic() {
        val project = project("New_Name", listOf(component("old_name/id/sensor/#")))

        assertEquals(
                linkedSetOf("new_name/id/#", "old_name/id/sensor/#"),
                TopicHelper.collectSubscriptionTopics(project)
        )
    }

    @Test
    fun rewriteGeneratedProjectTopic_changesOnlyTheExactOldBase() {
        val oldProject = project("Old Project", emptyList())
        assertEquals(
                "new_project/id2/button/1",
                TopicHelper.rewriteGeneratedProjectTopic(
                        "old_project/id/button/1", oldProject, "New Project", "id2")
        )
        assertEquals(
                "external/id/button/1",
                TopicHelper.rewriteGeneratedProjectTopic(
                        "external/id/button/1", oldProject, "New Project", "id2")
        )
    }

    private fun project(name: String, components: List<ComponentData>) =
            Project(
                    id = "id",
                    name = name,
                    broker = "",
                    type = ProjectType.HOME,
                    components = components.toMutableList()
            )

    private fun component(topic: String) =
            ComponentData(1, "BUTTON", 0f, 0f, 1, 1, "button", topic)
}
