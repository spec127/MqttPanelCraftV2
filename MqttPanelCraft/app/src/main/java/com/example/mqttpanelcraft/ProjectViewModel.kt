package com.example.mqttpanelcraft

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.Project
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    // Repository is a Singleton Object, no instantiation needed
    // private val repository = ProjectRepository(application)

    private val _currentProjectId = MutableLiveData<String>()

    // Reactive Project Data using MediatorLiveData to avoid Transformations dependency issues
    val project = androidx.lifecycle.MediatorLiveData<Project?>()

    // Manual Coroutine Scope as fallback
    private val viewModelJob = kotlinx.coroutines.SupervisorJob()
    private val uiScope =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + viewModelJob)

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

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
        components.addSource(project) { proj -> components.value = proj?.components ?: emptyList() }
    }

    private val _selectedComponentId = MutableLiveData<Int?>(null)
    val selectedComponentId: LiveData<Int?> = _selectedComponentId

    val canUndo = MutableLiveData<Boolean>(false)

    private val _isGridVisible = MutableLiveData<Boolean>(true)
    val isGridVisible: LiveData<Boolean> = _isGridVisible

    private val _isGuidesVisible = MutableLiveData<Boolean>(true) // Guides (Alignment Lines)
    val isGuidesVisible: LiveData<Boolean> = _isGuidesVisible

    fun toggleGrid() {
        _isGridVisible.value = !(_isGridVisible.value ?: true)
    }

    fun toggleGuides() {
        _isGuidesVisible.value = !(_isGuidesVisible.value ?: true)
    }

    fun setGridVisibility(visible: Boolean) {
        _isGridVisible.value = visible
    }

    fun setGuidesVisibility(visible: Boolean) {
        _isGuidesVisible.value = visible
    }

    fun loadProject(projectId: String) {
        mqttStatus.postValue(MqttStatus.IDLE)
        _currentProjectId.value = projectId
    }

    fun saveProject() {
        val currentProj = project.value ?: return
        // Note: ProjectRepository.updateProject needs the EXACT object or ID matching.
        // Since we are observing the live list, 'currentProj' is a reference to the object in the
        // list
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
            proj.components.addAll(previousState.map { it.deepCopy() })

            // Force Observer Notification and Save
            project.value = proj
            saveProject()

            // Trigger UI Refresh
            undoEvent.value = System.currentTimeMillis()

            com.example.mqttpanelcraft.utils.DebugLogger.log(
                    "ProjectVM",
                    "Undo performed. Stack size: ${undoStack.size}"
            )
            canUndo.value = undoStack.isNotEmpty()
        } else {
            com.example.mqttpanelcraft.utils.DebugLogger.log(
                    "ProjectVM",
                    "Undo failed: Stack empty"
            )
        }
    }

    fun saveSnapshot() {
        val proj = project.value ?: return
        // Deep copy of the list items to avoid reference issues
        val snapshot = proj.components.map { it.deepCopy() }
        undoStack.push(snapshot)
        if (undoStack.size > 20) undoStack.removeAt(0) // Limit stack
        canUndo.value = undoStack.isNotEmpty()
        com.example.mqttpanelcraft.utils.DebugLogger.log(
                "ProjectVM",
                "Snapshot saved. Stack size: ${undoStack.size}"
        )
    }

    // Helper to get Density
    private val density: Float
        get() = getApplication<Application>().resources.displayMetrics.density

    private fun getNextSmartLabel(type: String): String {
        val proj = project.value ?: return "${type.lowercase()}1"
        // 優先從元件註冊表獲取定義好的 labelPrefix，使預設名稱更簡潔且無底線（Design Intent：防呆命名）
        val definition = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(type)
        val prefix = definition?.labelPrefix ?: type.lowercase()

        // Find all used IDs for this Type (Labels starting with "type")
        val usedIds =
                proj.components
                        .map { it.label }
                        .filter { it.startsWith(prefix, ignoreCase = true) }
                        .mapNotNull {
                            // Remove prefix, try to parse integer
                            it.substring(prefix.length).toIntOrNull()
                        }
                        .sorted()

        // Find gap
        var nextId = 1
        for (id in usedIds) {
            if (id == nextId) nextId++ else if (id > nextId) return "$prefix$nextId"
        }
        return "$prefix$nextId"
    }

    fun generateSmartTopic(type: String): String {
        val proj = project.value ?: return "topic"
        val newLabel = getNextSmartLabel(type)

        // Topic Generation: All Lowercase
        // Topic Generation: All Lowercase, insert underscore between text and number
        // safeItemName: "button1" -> "button_1"
        val safeItemName =
                newLabel
                        .lowercase(java.util.Locale.ROOT)
                        .replace(Regex("(?<=[a-z])(?=\\d)"), "_")

        // Topic Config: ProjectName/ProjectID/ItemName
        return "${com.example.mqttpanelcraft.utils.TopicHelper.formatBaseTopic(proj.name, proj.id)}/$safeItemName"
    }

    fun getProjectTopicPrefix(): String {
        val proj = project.value ?: return ""
        return "${com.example.mqttpanelcraft.utils.TopicHelper.formatBaseTopic(proj.name, proj.id)}/"
    }

    // === Log Persistence ===
    private val _logs = MutableLiveData<List<String>>(emptyList())
    val logs: LiveData<List<String>> = _logs
    private val logBuffer = mutableListOf<String>()

    fun addLog(msg: String) {
        logBuffer.add(msg)
        // Optimization: limit size if needed, e.g. 1000 logs
        if (logBuffer.size > 2000) logBuffer.removeAt(0)
        _logs.value = logBuffer.toList() // Trigger update
    }

    fun clearLogs() {
        logBuffer.clear()
        _logs.value = emptyList()
    }

    // Unified Factory Method for Creating ComponentData
    fun createNewComponentData(type: String, x: Float, y: Float): ComponentData {
        val proj = project.value ?: throw IllegalStateException("Project not loaded")

        // Hybrid Strategy: Definition Architecture > Legacy Factory
        val definition =
                com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(type)

        // 1. Smart Label (prefix from Definition or Legacy helper)
        val prefix = definition?.labelPrefix ?: type.lowercase()
        // We reuse the existing helper but logic might need prefix override if helper doesn't
        // support generic prefix
        // Actually, getNextSmartLabel(type) is currently private and uses 'when'.
        // We will stick to getNextSmartLabel(type) for legacy, but for Definition we might want a
        // new helper?
        // Let's rely on getNextSmartLabel(type) for now -> It needs to be updated or we assume
        // prefix match.
        val newLabel = getNextSmartLabel(type)

        // 2. Smart Topic
        val smartTopic = generateSmartTopic(type)

        // 3. Default Size
        val (wPx, hPx) =
                if (definition != null) {
                    val density =
                            getApplication<android.app.Application>()
                                    .resources
                                    .displayMetrics
                                    .density
                    val w = (definition.defaultSize.width * density).toInt()
                    val h = (definition.defaultSize.height * density).toInt()
                    Pair(w, h)
                } else {
                    // Fallback default
                    Pair(300, 300)
                }

        // 4. System ID
        val maxId = proj.components.maxOfOrNull { it.id } ?: 100
        val newSystemId = maxId + 1

        // 5. Default Props (From Component Definition single source of truth)
        val initialProps = definition?.getDefaultProps()?.toMutableMap() ?: mutableMapOf()

        if (type == "BUTTON") {
            initialProps["text"] = newLabel // Default text is the label name
        } else if (type == "SWITCH") {
            initialProps["payloadLeft"] = "OFF"
            initialProps["payloadRight"] = "ON"
        }

        return ComponentData(
                id = newSystemId,
                type = type,
                topicConfig = smartTopic,
                x = x,
                y = y,
                width = wPx,
                height = hPx,
                label = newLabel,
                props = initialProps
        )
    }

    fun addComponent(type: String, defaultTopic: String): ComponentData? {
        // Legacy support or "Add Button" support - defaulting to 100,100
        saveSnapshot()
        val proj = project.value ?: return null

        val newData = createNewComponentData(type, 100f, 100f)

        proj.components.add(newData)
        saveProject()
        return newData
    }

    fun addComponent(component: ComponentData): ComponentData? {
        saveSnapshot()
        val proj = project.value ?: return null

        var finalComp = component
        // Regen ID if exists
        if (proj.components.any { it.id == component.id }) {
            val maxId = proj.components.maxOfOrNull { it.id } ?: 100
            finalComp = component.copy(id = maxId + 1)
        }

        proj.components.add(finalComp)
        saveProject()
        return finalComp
    }

    fun removeComponent(componentId: Int) {
        saveSnapshot()
        val proj = project.value ?: return
        val removed = proj.components.removeIf { it.id == componentId }
        if (removed) {
            com.example.mqttpanelcraft.utils.DebugLogger.log(
                    "ProjectVM",
                    "Removed component ID: $componentId"
            )
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
    // === MQTT Logic ===
    enum class MqttStatus {
        IDLE,
        CONNECTING,
        CONNECTED,
        FAILED
    }
    val mqttStatus = androidx.lifecycle.MediatorLiveData<MqttStatus>().apply {
        value = MqttStatus.IDLE
        addSource(com.example.mqttpanelcraft.MqttRepository.connectionState) { state ->
            value = when (state) {
                com.example.mqttpanelcraft.mqtt.MqttConnectionState.CONNECTED -> MqttStatus.CONNECTED
                com.example.mqttpanelcraft.mqtt.MqttConnectionState.FAILED -> MqttStatus.FAILED
                com.example.mqttpanelcraft.mqtt.MqttConnectionState.CONNECTING,
                com.example.mqttpanelcraft.mqtt.MqttConnectionState.RECONNECTING -> MqttStatus.CONNECTING
                else -> MqttStatus.IDLE
            }
        }
    }

    fun initMqtt() {
        project.value?.let { com.example.mqttpanelcraft.mqtt.MqttSessionClient.activate(getApplication(), it.id) }
    }

    fun retryMqtt() {
        initMqtt()
    }

    companion object {
        fun generateSmartId(components: List<ComponentData>, type: String): Int {
            // Logic: Find max ID + 1 to ensure uniqueness based on integer IDs
            val maxId = components.maxOfOrNull { it.id } ?: 100
            return maxId + 1
        }
    }
}
