package com.example.mqttpanelcraft_beta.data

import com.example.mqttpanelcraft_beta.model.Project
import com.example.mqttpanelcraft_beta.model.ProjectType

object ProjectRepository {
    private val projects = mutableListOf<Project>()

    init {
        // Add some mock data for v1 visualization
        projects.add(Project("1", "Smart Home", "broker.emqx.io", ProjectType.HOME, true))
        projects.add(Project("2", "Office Env", "test.mosquitto.org", ProjectType.FACTORY, false))
        projects.add(Project("3", "Factory A", "mqtt.eclipseprojects.io", ProjectType.FACTORY, false))
    }

    fun getAllProjects(): List<Project> {
        return projects.toList()
    }
    
    fun getProjectById(id: String): Project? {
        return projects.find { it.id == id }
    }

    fun addProject(project: Project) {
        projects.add(project)
    }
    
    fun updateProject(updatedProject: Project) {
        val index = projects.indexOfFirst { it.id == updatedProject.id }
        if (index != -1) {
            projects[index] = updatedProject
        }
    }
    
    fun deleteProject(id: String) {
        projects.removeIf { it.id == id }
    }
    
    fun generateId(): String {
        return (projects.size + 1).toString()
    }
}
