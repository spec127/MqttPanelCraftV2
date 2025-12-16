package com.example.mqttpanelcraft.data

import android.content.Context
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ProjectRepository {
    private val projects = mutableListOf<Project>()
    private const val FILE_NAME = "projects.json"

    fun initialize(context: Context) {
        if (projects.isEmpty()) {
            loadFromStorage(context)
            if (projects.isEmpty()) {
                // Add default mock data only if storage is empty
                projects.add(Project("1", "Smart Home", "broker.emqx.io", ProjectType.HOME, true))
                projects.add(Project("2", "Office Env", "test.mosquitto.org", ProjectType.FACTORY, false))
                projects.add(Project("3", "Factory A", "mqtt.eclipseprojects.io", ProjectType.FACTORY, false))
                saveToStorage(context)
            }
        }
    }

    fun getAllProjects(): List<Project> {
        return projects.toList()
    }
    
    fun getProjectById(id: String): Project? {
        return projects.find { it.id == id }
    }

    fun addProject(context: Context, project: Project) {
        projects.add(project)
        saveToStorage(context)
    }
    
    fun updateProject(context: Context, updatedProject: Project) {
        val index = projects.indexOfFirst { it.id == updatedProject.id }
        if (index != -1) {
            projects[index] = updatedProject
            saveToStorage(context)
        }
    }
    
    fun deleteProject(context: Context, id: String) {
        projects.removeIf { it.id == id }
        saveToStorage(context)
    }
    
    fun generateId(): String {
        return System.currentTimeMillis().toString()
    }

    private fun saveToStorage(context: Context) {
        try {
            val jsonArray = JSONArray()
            projects.forEach { p ->
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("broker", p.broker)
                obj.put("type", p.type.name)
                obj.put("isConnected", p.isConnected)
                jsonArray.put(obj)
            }
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(jsonArray.toString().toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFromStorage(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                val content = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(content)
                projects.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    projects.add(Project(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        broker = obj.getString("broker"),
                        type = ProjectType.valueOf(obj.getString("type")),
                        isConnected = obj.getBoolean("isConnected")
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
