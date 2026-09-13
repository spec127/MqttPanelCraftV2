package com.example.mqttpanelcraft.data

import android.content.Context
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry
import com.example.mqttpanelcraft.utils.DemoBroker
import com.example.mqttpanelcraft.utils.TutorialTopics

object TutorialProjectFactory {
    private const val BASE_NAME = "Tutorial_Local"

    fun findTutorialProject(): Project? =
        ProjectRepository.getAllProjects().firstOrNull { DemoBroker.isLocal(it.broker) }

    fun ensureTutorialProject(context: Context): Project {
        findTutorialProject()?.let { return it }
        val project = create(context)
        ProjectRepository.addProject(project)
        return ProjectRepository.getProjectById(project.id) ?: project
    }

    fun create(context: Context): Project {
        val id = ProjectRepository.generateId()
        val name = uniqueName()
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val topic = TutorialTopics.sharedCommandTopic(name, id)
        val buttonOn = component(context, 101, "BUTTON", "light_on", 24f * density, 72f * density, topic) {
            it["text"] = "ON"
            it["payload"] = "ON"
            it["trigger_mode"] = "tap"
        }
        val buttonOff = component(context, 102, "BUTTON", "light_off", 160f * density, 72f * density, topic) {
            it["text"] = "OFF"
            it["payload"] = "OFF"
            it["trigger_mode"] = "tap"
        }
        val led = component(context, 103, "LED", "led", 88f * density, 176f * density, topic)
        val display = component(
            context,
            104,
            "TEXT_DISPLAY",
            "receivebox",
            24f * density,
            280f * density,
            topic
        ) {
            it["default_text"] = "loading"
        }
        display.width = dp(220)
        display.height = dp(52)
        return Project(
            id = id,
            name = name,
            broker = DemoBroker.HOST,
            port = DemoBroker.PORT,
            type = ProjectType.HOME,
            components = mutableListOf(buttonOn, buttonOff, led, display),
            keepMqttInBackground = false
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

    private fun component(
        context: Context,
        id: Int,
        type: String,
        label: String,
        x: Float,
        y: Float,
        topic: String,
        extras: (MutableMap<String, String>) -> Unit = {}
    ): ComponentData {
        val definition = ComponentDefinitionRegistry.get(type)
        val density = context.resources.displayMetrics.density
        val width = ((definition?.defaultSize?.width ?: 120) * density).toInt()
        val height = ((definition?.defaultSize?.height ?: 70) * density).toInt()
        val props = (definition?.getDefaultProps(context) ?: emptyMap()).toMutableMap()
        extras(props)
        return ComponentData(
            id = id,
            type = type,
            x = x,
            y = y,
            width = width,
            height = height,
            label = label,
            topicConfig = topic,
            props = props
        )
    }
}
