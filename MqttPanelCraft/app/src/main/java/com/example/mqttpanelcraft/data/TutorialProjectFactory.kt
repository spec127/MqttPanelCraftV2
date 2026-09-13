package com.example.mqttpanelcraft.data

import android.content.Context
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry
import com.example.mqttpanelcraft.utils.DemoBroker

object TutorialProjectFactory {
    private const val BASE_NAME = "Tutorial_Local"

    fun findTutorialProject(): Project? =
        ProjectRepository.getAllProjects().firstOrNull { DemoBroker.isLocal(it.broker) }

    fun ensureTutorialProject(context: Context): Project {
        findTutorialProject()?.let { existing ->
            resetGuidedLayout(context, existing)
            return ProjectRepository.getProjectById(existing.id) ?: existing
        }
        val project = create(context)
        ProjectRepository.addProject(project)
        return ProjectRepository.getProjectById(project.id) ?: project
    }

    fun create(context: Context): Project {
        val id = ProjectRepository.generateId()
        val name = uniqueName()
        return Project(
            id = id,
            name = name,
            broker = DemoBroker.HOST,
            port = DemoBroker.PORT,
            type = ProjectType.HOME,
            components = guidedComponents(context).toMutableList(),
            keepMqttInBackground = false
        )
    }

    private fun resetGuidedLayout(context: Context, project: Project) {
        project.components.clear()
        project.components.addAll(guidedComponents(context))
        ProjectRepository.updateProject(project)
    }

    private fun guidedComponents(context: Context): List<ComponentData> {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val frameW = dp(300)
        val frameH = dp(250)
        val gap = dp(20)
        val originX = 16f * density
        val originY = 12f * density
        val top = graphicFrame(
            context, 101, "group_light", originX, originY, frameW, frameH, "A"
        )
        val bottom = graphicFrame(
            context, 102, "group_slider", originX, originY + frameH + gap, frameW, frameH, "B"
        )
        return listOf(top, bottom)
    }

    private fun graphicFrame(
        context: Context,
        id: Int,
        label: String,
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        group: String
    ): ComponentData {
        val definition = ComponentDefinitionRegistry.get("GRAPHIC")
        val props = (definition?.getDefaultProps(context) ?: emptyMap()).toMutableMap()
        props["showLabel"] = "false"
        props["opacity"] = "18"
        props["stroke_width"] = "3"
        props["enable_corner"] = "true"
        props["fill_color"] = "#7B1FA2"
        props["stroke_color"] = "#7B1FA2"
        props["tutorial_group"] = group
        return ComponentData(
            id = id,
            type = "GRAPHIC",
            x = x,
            y = y,
            width = width,
            height = height,
            label = label,
            topicConfig = "",
            props = props
        )
    }

    private fun uniqueName(): String {
        if (!ProjectRepository.isProjectNameTaken(BASE_NAME)) return BASE_NAME
        var index = 2
        while (ProjectRepository.isProjectNameTaken("$BASE_NAME$index")) {
            index++
        }
        return "$BASE_NAME$index"
    }
}
