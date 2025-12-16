package com.example.mqttpanelcraft.model

data class Project(
    val id: String,
    val name: String,
    val broker: String,
    val type: ProjectType,
    val isConnected: Boolean = false
)

enum class ProjectType {
    HOME, FACTORY, OTHER
}
