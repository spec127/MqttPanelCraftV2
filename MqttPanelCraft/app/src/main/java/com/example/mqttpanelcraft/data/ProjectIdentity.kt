package com.example.mqttpanelcraft.data

/** One ID selection rule for both create and edit forms. */
internal fun resolveProjectId(displayedId: String, existingId: String?, generate: () -> String): String =
        displayedId.trim().ifBlank { existingId ?: generate() }
