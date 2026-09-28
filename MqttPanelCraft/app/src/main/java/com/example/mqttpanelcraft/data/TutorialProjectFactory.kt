package com.example.mqttpanelcraft.data

import android.content.Context
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.tutorial.*
import com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry
import com.example.mqttpanelcraft.utils.DemoBroker

object TutorialProjectFactory {
    fun ensureTutorialProject(context: Context): Project {
        TutorialSessionStore.currentProjectId(context)?.let { id ->
            ProjectRepository.getProjectById(id)?.takeIf { DemoBroker.isLocal(it.broker) }?.let { return it }
        }
        return newTutorialProject(context)
    }
    fun newTutorialProject(context: Context): Project {
        val project = create(context)
        ProjectRepository.addProject(project)
        return project
    }
    fun create(context: Context): Project {
        val id = ProjectRepository.generateId()
        var name = "Tutorial_Local"
        var suffix = 2
        while (ProjectRepository.isProjectNameTaken(name)) { name = "Tutorial_Local${suffix++}" }
        val project = Project(id = id, name = name, broker = DemoBroker.HOST, port = DemoBroker.PORT,
            type = ProjectType.HOME, components = mutableListOf(), keepMqttInBackground = false)
        val session = TutorialSession(id)
        listOf("text", "graphic", "led", "receiver").forEach { addRole(context, project, session, it) }
        TutorialSessionStore.save(context, session)
        return project
    }
    fun topic(session: TutorialSession, numeric: Boolean) = "tutorial/${session.projectId}/" + if (numeric) "value" else "light"
    fun type(role: String): String = when (role) {
        "receiver" -> "TEXT_DISPLAY"
        "meter" -> "SCALE_METER"
        else -> role.uppercase()
    }
    fun addRole(context: Context, project: Project, session: TutorialSession, role: String): ComponentData {
        project.components.firstOrNull { it.id == session.roles[role] }?.let { return it }
        val def = requireNotNull(ComponentDefinitionRegistry.get(type(role)))
        val density = context.resources.displayMetrics.density
        val availableWidth = (context.resources.displayMetrics.widthPixels / density - 32).coerceAtLeast(200f)
        val row = when (role) {
            "text" -> 12; "graphic" -> 70; "led", "receiver" -> 100
            "button", "switch" -> 205; "slider" -> 310; "meter" -> 405; "chart" -> 500
            else -> 12
        }
        val x = if (role in listOf("receiver", "switch")) availableWidth / 2 + 16 else 16f
        val width = when (role) {
            "led" -> 80f; "receiver", "button", "switch" -> availableWidth / 2 - 8
            else -> minOf(def.defaultSize.width.toFloat(), availableWidth)
        }
        val props = def.getDefaultProps(context).toMutableMap().apply {
            put("showLabel", "true")
            if (role == "graphic") { put("opacity", "35"); put("fill_color", "#A855F7") }
            if (role == "button") { put("payload", "ON"); put("text", "Button") }
            if (role == "switch") { put("payloadLeft", "OFF"); put("payloadRight", "ON") }
        }
        val component = ComponentData((project.components.maxOfOrNull { it.id } ?: 100) + 1, def.type,
            x * density, row * density.toFloat(), (width * density).toInt(),
            ((if (role == "graphic") 20 else def.defaultSize.height) * density).toInt(),
            role, if (role in listOf("led", "receiver")) topic(session, false)
                else if (role in listOf("meter", "chart")) topic(session, true) else "", props)
        project.components.add(component)
        session.roles[role] = component.id
        return component
    }
}
