package com.example.mqttpanelcraft

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import java.util.UUID

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    // Repository is a Singleton Object, no instantiation needed
    // private val repository = ProjectRepository(application) 

    private val _currentProjectId = MutableLiveData<String>()
    
    // Reactive Project Data using MediatorLiveData to avoid Transformations dependency issues
    val project = androidx.lifecycle.MediatorLiveData<Project?>()
    
    // Components derived from Project
    val components = androidx.lifecycle.MediatorLiveData<List<ComponentData>>()

    init {
        // Update project whenever ID changes or Repository list changes
        val updateProjectFunc = {
            val id = _currentProjectId.value
            val list = ProjectRepository.projectsLiveData.value
            if (id != null && list != null) {
                project.value = list.find { it.id == id }
            } else {
                 // Optionally set null if not ready
            }
        }

        project.addSource(_currentProjectId) { updateProjectFunc() }
        project.addSource(ProjectRepository.projectsLiveData) { updateProjectFunc() }
        
        // Update components whenever project changes
        components.addSource(project) { proj ->
            components.value = proj?.components ?: emptyList() 
        }
    }

    private val _selectedComponentId = MutableLiveData<Int?>(null)
    val selectedComponentId: LiveData<Int?> = _selectedComponentId

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isGridVisible = MutableLiveData<Boolean>(true)
    val isGridVisible: LiveData<Boolean> = _isGridVisible

    fun toggleGrid() {
        _isGridVisible.value = !(_isGridVisible.value ?: true)
    }

    fun setGridVisibility(visible: Boolean) {
        _isGridVisible.value = visible
    }

    fun loadProject(projectId: String) {
        _currentProjectId.value = projectId
    }

    fun saveProject() {
        val currentProj = project.value ?: return
        // Note: ProjectRepository.updateProject needs the EXACT object or ID matching.
        // Since we are observing the live list, 'currentProj' is a reference to the object in the list 
        // (or a copy depending on Repository impl). 
        // Repository uses CopyOnWriteList, so we should be careful.
        // Actually Repository.updateProject replaces by ID.
        // The 'components' LiveData is derived. If we modify 'currentProj.components' directly,
        // we should call updateProject to notify others and save to disk.
        
        ProjectRepository.updateProject(currentProj)
    }

    // Undo Stack
    private val undoStack = java.util.Stack<List<ComponentData>>()

    val undoEvent = MutableLiveData<Long>()

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previousState = undoStack.pop()
            val proj = project.value ?: return
            
            proj.components.clear()
            proj.components.addAll(previousState)
            
            // Force Observer Notification and Save
            project.value = proj
            saveProject()
            
            // Trigger UI Refresh
            undoEvent.value = System.currentTimeMillis()
            
            com.example.mqttpanelcraft.utils.DebugLogger.log("ProjectVM", "Undo performed. Stack size: ${undoStack.size}")
        } else {
             com.example.mqttpanelcraft.utils.DebugLogger.log("ProjectVM", "Undo failed: Stack empty")
        }
    }
    
    fun saveSnapshot() {
        val proj = project.value ?: return
        // Deep copy of the list items to avoid reference issues
        val snapshot = proj.components.map { it.copy() }
        undoStack.push(snapshot)
        if (undoStack.size > 20) undoStack.removeAt(0) // Limit stack
        com.example.mqttpanelcraft.utils.DebugLogger.log("ProjectVM", "Snapshot saved. Stack size: ${undoStack.size}")
    }

    // Helper to get Density
    private val density: Float
        get() = getApplication<Application>().resources.displayMetrics.density

    // Grid Unit
    private val GRID_UNIT_DP = 20

    private fun getNextSmartLabel(type: String): String {
        val proj = project.value ?: return "$type 1"
        
        // Find all used IDs for this Type (Labels starting with "Type ")
        val usedIds = proj.components
            .map { it.label }
            .filter { it.startsWith("$type ") }
            .mapNotNull { it.substringAfter("$type ").toIntOrNull() }
            .sorted()
            
        // Find gap
        var nextId = 1
        for (id in usedIds) {
            if (id == nextId) nextId++
            else if (id > nextId) return "$type $nextId"
        }
        return "$type $nextId"
    }

    fun addComponent(type: String, defaultTopic: String) {
        saveSnapshot()
        val proj = project.value ?: return
        
        // Fix ID Collision: Use Max ID + 1 (System ID)
        val maxId = proj.components.maxOfOrNull { it.id } ?: 100
        val newSystemId = maxId + 1
        
        // Smart Label (User Facing ID)
        val newLabel = getNextSmartLabel(type)
        
        // Default Size (Grid Aligned)
        // Match logic in ComponentFactory
        // Unit = 20dp
        val (wDp, hDp) = when(type) {
            "SLIDER", "THERMOMETER", "TEXT" -> 160 to 100
            "BUTTON", "CAMERA" -> 120 to 60
            "LED" -> 80 to 80
            else -> 100 to 100
        }

        val newComp = ComponentData(
            id = newSystemId, 
            type = type,
            topicConfig = defaultTopic,
            x = 100f, // TODO: Smart placement? For now 100f is fine, ideally snap this too
            y = 100f, 
            width = (wDp * density).toInt(),
            height = (hDp * density).toInt(),
            label = newLabel,
            props = mutableMapOf()
        )
        // Ensure x,y are also snapped?
        // 100f is 5 * 20 (if density=1). 
        // Better to just let drag handle it, or pre-snap.
        
        proj.components.add(newComp)
        saveProject() 
    }

    fun addComponent(component: ComponentData) {
        saveSnapshot()
        val proj = project.value ?: return
        
        var finalComp = component
        // Regen ID if exists
        if (proj.components.any { it.id == component.id }) {
             val maxId = proj.components.maxOfOrNull { it.id } ?: 100
             finalComp = component.copy(id = maxId + 1)
        }
        
        proj.components.add(finalComp)
        saveProject()
    }

    fun removeComponent(componentId: Int) {
        saveSnapshot()
        val proj = project.value ?: return
        val removed = proj.components.removeIf { it.id == componentId }
        if (removed) {
            com.example.mqttpanelcraft.utils.DebugLogger.log("ProjectVM", "Removed component ID: $componentId")
            if (_selectedComponentId.value == componentId) {
                _selectedComponentId.value = null
            }
            saveProject()
        }
    }

    fun updateComponent(updatedComponent: ComponentData) {
        val proj = project.value ?: return
        val index = proj.components.indexOfFirst { it.id == updatedComponent.id }
        if (index != -1) {
            proj.components[index] = updatedComponent
            saveProject()
        }
    }
    
    fun selectComponent(id: Int?) {
        _selectedComponentId.value = id
    }

    fun getSelectedComponent(): ComponentData? {
        val id = _selectedComponentId.value ?: return null
        return components.value?.find { it.id == id }
    }
    fun updateComponentsBatch(updatedComponents: List<ComponentData>) {
        val proj = project.value ?: return
        var changed = false
        updatedComponents.forEach { updated ->
            val index = proj.components.indexOfFirst { it.id == updated.id }
            if (index != -1) {
                proj.components[index] = updated
                changed = true
            }
        }
        if (changed) saveProject()
    }
    companion object {
        fun generateSmartId(components: List<ComponentData>, type: String): Int {
            // Logic: Find max ID + 1 to ensure uniqueness based on integer IDs
            val maxId = components.maxOfOrNull { it.id } ?: 100
            return maxId + 1
        }
    }
}
