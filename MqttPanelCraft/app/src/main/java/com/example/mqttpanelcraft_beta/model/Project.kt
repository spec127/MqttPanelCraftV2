package com.example.mqttpanelcraft_beta.model

data class Project(
    val id: String,
    val name: String,
    val broker: String,
    val port: Int = 1883,
    val username: String = "",
    val password: String = "",
    val clientId: String = "",
    val type: ProjectType,
    val isConnected: Boolean = false
)

enum class ProjectType {
    HOME, FACTORY, OTHER
}
