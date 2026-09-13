package com.example.mqttpanelcraft.data

import com.example.mqttpanelcraft.model.Project

/**
 * Repairs component identities at the import boundary.  Imported projects used to give every
 * component View.NO_ID, which made selection and linked-component actions ambiguous.
 */
object ProjectImportNormalizer {
    data class Result(
        val project: Project,
        val repairedIds: Int,
        val removedLinkedReferences: Int
    )

    fun normalize(project: Project): Result {
        val idMap = linkedMapOf<Int, Int>()
        val idCounts = project.components.groupingBy { it.id }.eachCount()
        var nextId = 1
        var repaired = 0
        val normalizedComponents = project.components.map { source ->
            val normalizedId = nextId++
            if (source.id <= 0 || idCounts[source.id] != 1) repaired++
            // Ambiguous links must not silently trigger an arbitrary actuator.
            if (source.id > 0 && idCounts[source.id] == 1) idMap[source.id] = normalizedId
            source.deepCopy().apply { id = normalizedId }
        }.toMutableList()

        var removedLinks = 0
        normalizedComponents.forEach { component ->
            val linked = component.props["linked_components"] ?: return@forEach
            val remapped = linked.split(',')
                .mapNotNull { token ->
                    val oldId = token.trim().toIntOrNull()
                    val newId = oldId?.let(idMap::get)
                    if (newId == null && token.isNotBlank()) removedLinks++
                    newId
                }
                .distinct()
                .joinToString(",")
            component.props["linked_components"] = remapped
        }

        return Result(project.copy(components = normalizedComponents), repaired, removedLinks)
    }
}
