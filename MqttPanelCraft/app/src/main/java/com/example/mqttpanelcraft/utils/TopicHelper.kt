package com.example.mqttpanelcraft.utils

import com.example.mqttpanelcraft.model.Project
import java.util.Locale

object TopicHelper {

    private fun normalizeProjectName(projectName: String): String {
        return projectName
                .trim()
                .lowercase(Locale.ROOT)
                .replace("\\s".toRegex(), "_")
                .replace("[^a-z0-9_]".toRegex(), "")
    }

    /**
     * Maps UI tags to standard component types.
     */
    fun getComponentType(tag: String): String {
        return when (tag.uppercase(Locale.ROOT)) {
            "BUTTON" -> "button"
            "SLIDER" -> "slider"
            "LED" -> "led"
            "THERMOMETER" -> "analog"
            "IMAGE" -> "image"
            "TEXT" -> "text"
            else -> "unknown"
        }
    }

    /**
     * Formats the base topic: {projectNameLower}/{projectId}
     */
    fun formatBaseTopic(projectName: String, projectId: String): String {
        return "${normalizeProjectName(projectName)}/${projectId.trim()}"
    }

    fun formatProjectWildcard(project: Project): String {
        return "${formatBaseTopic(project.name, project.id)}/#"
    }

    /** Rewrites only a generated topic under the exact old project base. */
    fun rewriteGeneratedProjectTopic(
        topic: String,
        oldProject: Project,
        newProjectName: String,
        newProjectId: String
    ): String {
        val oldBase = formatBaseTopic(oldProject.name, oldProject.id)
        val trimmedTopic = topic.trim()
        if (!trimmedTopic.startsWith("$oldBase/")) return topic
        val suffix = trimmedTopic.removePrefix(oldBase)
        return "${formatBaseTopic(newProjectName, newProjectId)}$suffix"
    }

    fun collectSubscriptionTopics(project: Project): Set<String> {
        val wildcard = formatProjectWildcard(project)
        val coveredPrefix = wildcard.dropLast(1)
        return linkedSetOf(wildcard).apply {
            project.components
                    .asSequence()
                    .map { it.topicConfig.trim() }
                    .filter { it.isNotEmpty() }
                    .filterNot { it.startsWith(coveredPrefix) }
                    .forEach(::add)
        }
    }

    /**
     * Formats the full topic for a component.
     * {projectNameLower}/{projectId}/{componentType}/{componentIndex}/{direction}
     */
    fun formatTopic(
        projectName: String,
        projectId: String,
        componentType: String,
        componentIndex: String,
        direction: String = "cmd" // "cmd" or "rep"
    ): String {
        val base = formatBaseTopic(projectName, projectId)
        return "$base/$componentType/$componentIndex/$direction"
    }
}
