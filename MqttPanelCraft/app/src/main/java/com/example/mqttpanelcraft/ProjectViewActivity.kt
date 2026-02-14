package com.example.mqttpanelcraft

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import com.example.mqttpanelcraft.service.MqttService
import com.example.mqttpanelcraft.ui.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Locale

class ProjectViewActivity : BaseActivity() {

    // ViewModel
    private lateinit var viewModel: ProjectViewModel

    // UI
    private lateinit var editorCanvas: FrameLayout
    private lateinit var guideOverlay: AlignmentOverlayView
    private lateinit var dropDeleteZone: View
    private lateinit var fabMode: FloatingActionButton
    private lateinit var drawerLayout: DrawerLayout

    // Managers (The New Trio)
    private lateinit var renderer: ComponentRenderer
    private lateinit var interactionManager: CanvasInteractionManager
    private lateinit var behaviorManager: ComponentBehaviorManager

    // Others
    private lateinit var sidebarManager: SidebarManager
    private lateinit var propertiesManager: PropertiesSheetManager
    private lateinit var logConsoleManager: LogConsoleManager
    private lateinit var idleAdController: IdleAdController

    private var selectedComponentId: Int? = null
    private var isEditMode = false
    private var lastResizeUpdate = 0L

    // Manager
    private lateinit var projectUIManager: ProjectUIManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Globals Init
            com.example.mqttpanelcraft.data.ProjectRepository.initialize(applicationContext)

            setContentView(R.layout.activity_project_view)
            viewModel = ViewModelProvider(this)[ProjectViewModel::class.java]

            // Bind UI
            editorCanvas = findViewById(R.id.editorCanvas)
            guideOverlay = findViewById(R.id.guideOverlay)
            dropDeleteZone = findViewById(R.id.dropDeleteZone)
            fabMode = findViewById(R.id.fabMode)
            drawerLayout = findViewById(R.id.drawerLayout)
            dropDeleteZone.visibility = View.GONE
            guideOverlay.isClickable = false

            // Allow resize handles to bleed out
            editorCanvas.clipChildren = false
            editorCanvas.clipToPadding = false

            // --- Initialize Managers ---
            initializeHelpers()
            initializeArchitecture()

            // Initialize UI Manager
            projectUIManager =
                    ProjectUIManager(
                            this,
                            window.decorView.findViewById(android.R.id.content),
                            viewModel,
                            interactionManager,
                            sidebarManager,
                            propertiesManager,
                            renderer
                    )

            // Wire UI Manager Callbacks
            projectUIManager.onModeToggleCallback = {
                isEditMode = !isEditMode
                idleAdController.onUserInteraction() // Keep Ad Alive

                selectedComponentId = null
                projectUIManager.updateModeUI(isEditMode, selectedComponentId)

                viewModel.components.value?.let {
                    renderer.render(it, isEditMode, selectedComponentId)
                }
                if (!isEditMode) viewModel.saveProject()
            }

            // Subscribers
            subscribeToViewModel()
            subscribeToMqtt()

            // Load
            val projectId = intent.getStringExtra("PROJECT_ID")
            if (projectId != null) {
                viewModel.loadProject(projectId)
            } else {
                finish()
            }

            // UI Setup
            projectUIManager.setupToolbar()
            projectUIManager.updateSystemBars()

            // Check Prefs
            val prefs = getSharedPreferences("ProjectPrefs", MODE_PRIVATE)
            val gridVisible = prefs.getBoolean("GRID_VISIBLE", true)
            val guidesVisible = prefs.getBoolean("GUIDES_VISIBLE", true)
            viewModel.setGridVisibility(gridVisible)
            viewModel.setGuidesVisibility(guidesVisible)

            projectUIManager.updateModeUI(isEditMode, selectedComponentId) // Initial State

            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                            this,
                            getString(R.string.project_msg_init_error, e.message),
                            Toast.LENGTH_LONG
                    )
                    .show()
        }
    }

    // Result Launcher for Settings (Handle ID Renaming)
    private val setupLauncher =
            registerForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts
                            .StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val newId = result.data?.getStringExtra("NEW_ID")
                    if (newId != null) {
                        // Update Intent for onResume reload logic
                        intent.putExtra("PROJECT_ID", newId)
                        viewModel.loadProject(newId) // Reload Immediately

                        // Also update local selectedComponentId if strictly needed, but VM reload
                        // handles most.
                        Toast.makeText(this, "Project ID Updated", Toast.LENGTH_SHORT).show()
                    } else {
                        // Just content update, verify if we need to reload?
                        // onResume usually acts, but explicit reload is safer if onResume is
                        // optimized out or verified incorrectly.
                        // But let's rely on onResume for simple updates, or just force reload here.
                        val currentId = intent.getStringExtra("PROJECT_ID")
                        if (currentId != null) viewModel.loadProject(currentId)
                    }
                }
            }

    fun launchSettings() {
        val pid = viewModel.project.value?.id ?: return
        val intent = Intent(this, com.example.mqttpanelcraft.SetupActivity::class.java)
        intent.putExtra("PROJECT_ID", pid)
        setupLauncher.launch(intent)
    }

    // Internal Accessor for Manager
    fun getSelectedComponentId(): Int? = selectedComponentId

    // ... initializeArchitecture ... (Keep as is)
    // ... subscribeToViewModel ... (Keep as is, but remove View updates replaced by Manager if any)

    // Removed: setupToolbar(), updateModeUI(), updateSheetState(), updateCanvasOcclusion(),
    // updateBottomInset()
    // These are now handled by ProjectUIManager. logic.

    // ... (Skipping sections) ...

    private fun initializeArchitecture() {
        // 1. Renderer (Visuals)
        renderer = ComponentRenderer(editorCanvas, this)

        // 2. Behavior (Logic)
        behaviorManager =
                ComponentBehaviorManager(
                        { topic, payload ->
                            // Send MQTT
                            val intent =
                                    Intent(this, MqttService::class.java).apply {
                                        action = "PUBLISH"
                                        putExtra("TOPIC", topic)
                                        putExtra("PAYLOAD", payload)
                                    }
                            startService(intent)
                        },
                        { id, key, value ->
                            // Sync property to ViewModel (and then to disk)
                            viewModel.components.value?.find { it.id == id }?.let { comp ->
                                if (comp.props[key] != value) {
                                    val updated =
                                            comp.copy(
                                                    props =
                                                            comp.props.toMutableMap().apply {
                                                                put(key, value)
                                                            }
                                            )
                                    viewModel.updateComponent(updated)
                                }
                            }
                        }
                )

        // 3. Interaction (Input)
        val peekHeightPx = (100 * resources.displayMetrics.density).toInt()
        interactionManager =
                CanvasInteractionManager(
                        editorCanvas,
                        guideOverlay,
                        peekHeightPx,
                        object : CanvasInteractionManager.InteractionCallbacks {
                            override fun onInteractionStarted() {
                                if (isEditMode) {
                                    val sheet = findViewById<View>(R.id.bottomSheet)
                                    val behavior =
                                            com.google.android.material.bottomsheet
                                                    .BottomSheetBehavior.from(sheet)

                                    // Collapse Sheet on Drag/Resize Start
                                    if (behavior.state !=
                                                    com.google.android.material.bottomsheet
                                                            .BottomSheetBehavior.STATE_COLLAPSED
                                    ) {
                                        behavior.state =
                                                com.google.android.material.bottomsheet
                                                        .BottomSheetBehavior.STATE_COLLAPSED
                                    }
                                }
                            }

                            override fun onComponentSelected(id: Int) {
                                if (isEditMode) {
                                    if (selectedComponentId != id) {
                                        selectedComponentId = id
                                        // Just select, don't force sheet state here (Drag selection
                                        // handles it via onInteractionStarted)

                                        // Update UI Manager
                                        projectUIManager.updateModeUI(
                                                isEditMode,
                                                selectedComponentId
                                        )
                                        // Render Selection Border
                                        viewModel.components.value?.let {
                                            renderer.render(it, isEditMode, selectedComponentId)
                                        }
                                        projectUIManager.updateCanvasOcclusion()
                                    }
                                }
                            }

                            override fun onComponentClicked(id: Int) {
                                if (isEditMode) {
                                    val sheet = findViewById<View>(R.id.bottomSheet)
                                    val behavior =
                                            com.google.android.material.bottomsheet
                                                    .BottomSheetBehavior.from(sheet)

                                    if (id == -1) {
                                        // Background Click -> Deselect & Collapse
                                        selectedComponentId = null
                                        behavior.state =
                                                com.google.android.material.bottomsheet
                                                        .BottomSheetBehavior.STATE_COLLAPSED
                                        propertiesManager.showTitleOnly()
                                    } else {
                                        // Component Click -> ALWAYS Select & Expand (One-Click)

                                        // Ensure selection state
                                        if (selectedComponentId != id) {
                                            onComponentSelected(id)
                                        }

                                        val comp = viewModel.components.value?.find { it.id == id }
                                        val view = renderer.getView(id)
                                        if (comp != null && view != null) {
                                            propertiesManager.showProperties(
                                                    view,
                                                    comp,
                                                    autoExpand = true // Force Expand immediately
                                            )
                                        }
                                    }

                                    // Trigger Re-render
                                    viewModel.components.value?.let {
                                        renderer.render(it, isEditMode, selectedComponentId)
                                    }

                                    projectUIManager.updateCanvasOcclusion()
                                    projectUIManager.updateModeUI(isEditMode, selectedComponentId)
                                }
                            }

                            override fun onComponentMoved(id: Int, newX: Float, newY: Float) {
                                val comp = viewModel.components.value?.find { it.id == id }
                                if (comp != null) {
                                    viewModel.saveSnapshot()
                                    val updated = comp.copy(x = newX, y = newY)
                                    viewModel.updateComponent(updated)
                                }
                            }

                            override fun onComponentResized(id: Int, newW: Int, newH: Int) {
                                val comp = viewModel.components.value?.find { it.id == id }
                                if (comp != null) {
                                    viewModel.saveSnapshot()
                                    val updated = comp.copy(width = newW, height = newH)
                                    viewModel.updateComponent(updated)
                                }
                            }

                            override fun onComponentResizing(id: Int, newW: Int, newH: Int) {
                                propertiesManager.updateDimensions(newW, newH)

                                // Live Visual Update (Throttled ~30ms)
                                val now = System.currentTimeMillis()
                                if (now - lastResizeUpdate > 30) {
                                    lastResizeUpdate = now
                                    val comp = viewModel.components.value?.find { it.id == id }
                                    val view = renderer.getView(id)
                                    if (comp != null && view != null) {
                                        val tempData = comp.copy(width = newW, height = newH)
                                        val def =
                                                com.example.mqttpanelcraft.ui.components
                                                        .ComponentDefinitionRegistry.get(comp.type)
                                        def?.onUpdateView(view, tempData)
                                    }
                                }
                            }

                            override fun onComponentDeleted(id: Int) {
                                if (id != -1) {
                                    viewModel.saveSnapshot()
                                    viewModel.removeComponent(id)
                                    if (selectedComponentId == id) {
                                        selectedComponentId = null
                                        propertiesManager.showTitleOnly()
                                        projectUIManager.updateModeUI(
                                                isEditMode,
                                                selectedComponentId
                                        )
                                    }
                                }
                                Toast.makeText(
                                                this@ProjectViewActivity,
                                                getString(R.string.project_msg_component_deleted),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }

                            override fun onDeleteZoneState(
                                    state: CanvasInteractionManager.DeleteState
                            ) {
                                projectUIManager.setDeleteZoneState(state)
                            }

                            override fun onNewComponent(type: String, x: Float, y: Float) {
                                // Close Drawer on Drop success
                                sidebarManager.closeDrawer()

                                viewModel.saveSnapshot()

                                val tempData = viewModel.createNewComponentData(type, x, y)
                                val w = tempData.width
                                val h = tempData.height

                                val editorW = editorCanvas.width
                                val editorH = editorCanvas.height
                                val maxX = (editorW - w).toFloat().coerceAtLeast(0f)
                                val maxY = (editorH - h).toFloat().coerceAtLeast(0f)
                                var finalX = x.coerceIn(0f, maxX)
                                var finalY = y.coerceIn(0f, maxY)

                                val density = resources.displayMetrics.density
                                val gridPx = 10 * density
                                finalX = (kotlin.math.round(finalX / gridPx) * gridPx)
                                finalY = (kotlin.math.round(finalY / gridPx) * gridPx)
                                finalX = finalX.coerceIn(0f, maxX)
                                finalY = finalY.coerceIn(0f, maxY)

                                val finalData = tempData.copy(x = finalX, y = finalY)
                                val newComp = viewModel.addComponent(finalData)

                                if (newComp != null) {
                                    val newId = newComp.id
                                    viewModel.selectComponent(newId)
                                    selectedComponentId = newId

                                    viewModel.project.value?.components?.let {
                                        renderer.render(it, isEditMode, newId)
                                    }

                                    val view = renderer.getView(newId)
                                    if (view != null) {
                                        propertiesManager.showProperties(
                                                view,
                                                newComp,
                                                autoExpand = true
                                        )
                                    }
                                    projectUIManager.updateModeUI(isEditMode, selectedComponentId)
                                }
                            }
                        }
                )

        drawerLayout.addDrawerListener(
                object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
                    override fun onDrawerOpened(drawerView: View) {
                        val sheet = findViewById<View>(R.id.bottomSheet)
                        val behavior =
                                com.google.android.material.bottomsheet.BottomSheetBehavior.from(
                                        sheet
                                )

                        if (behavior.state ==
                                        com.google.android.material.bottomsheet.BottomSheetBehavior
                                                .STATE_EXPANDED ||
                                        behavior.state ==
                                                com.google.android.material.bottomsheet
                                                        .BottomSheetBehavior.STATE_HIDDEN
                        ) {
                            behavior.state =
                                    com.google.android.material.bottomsheet.BottomSheetBehavior
                                            .STATE_COLLAPSED
                        }
                    }
                }
        )

        // Drag & Drop Listener for New Components
        val dragListener =
                android.view.View.OnDragListener { v, event ->
                    when (event.action) {
                        android.view.DragEvent.ACTION_DRAG_STARTED -> true
                        android.view.DragEvent.ACTION_DROP -> {
                            val clipData = event.clipData
                            if (clipData != null && clipData.itemCount > 0) {
                                val type = clipData.getItemAt(0).text.toString()
                                // Coordinates are local to the view (editorCanvas)
                                interactionManager.callbacks.onNewComponent(type, event.x, event.y)
                                true
                            } else {
                                false
                            }
                        }
                        else -> true
                    }
                }
        editorCanvas.setOnDragListener(dragListener)
        findViewById<View>(R.id.rootCoordinator)?.setOnDragListener(null) // Clean up root listener

        val header = findViewById<View>(R.id.bottomSheetHeader)
        header.setOnClickListener {
            if (isEditMode) {
                if (selectedComponentId == null) {
                    val comps = viewModel.components.value
                    if (comps.isNullOrEmpty()) {
                        Toast.makeText(
                                        this,
                                        getString(R.string.project_msg_add_component),
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    } else {
                        Toast.makeText(
                                        this,
                                        getString(R.string.project_msg_select_component),
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                } else {
                    projectUIManager.toggleBottomSheet()
                }
            } else {
                projectUIManager.toggleBottomSheet()
            }
        }

        interactionManager.setup(
                isEditMode = { isEditMode },
                isGridEnabled = { viewModel.isGridVisible.value ?: true },
                isBottomSheetExpanded = {
                    val sheet = findViewById<View>(R.id.bottomSheet)
                    val behavior =
                            com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                    behavior.state ==
                            com.google.android.material.bottomsheet.BottomSheetBehavior
                                    .STATE_EXPANDED
                }
        )
    }

    private fun subscribeToViewModel() {
        viewModel.logs.observe(this) { logs -> logConsoleManager.updateLogs(logs) }

        viewModel.components.observe(this) { components ->
            logConsoleManager.updateTopics(components, viewModel.project.value)
            renderer.render(components, isEditMode, selectedComponentId)

            components.forEach { comp ->
                val view = renderer.getView(comp.id)
                if (view != null) {
                    behaviorManager.attachBehavior(view, comp)
                }
            }

            // V18.5: Critical Fix for Property Reset Bug
            // Ensure PropertiesSheetManager has the latest geometry data
            if (selectedComponentId != null) {
                val selectedComp = components.find { it.id == selectedComponentId }
                if (selectedComp != null) {
                    // Update the internal data reference without re-binding everything (which would
                    // kill focus)
                    // We need a new method in PropertiesSheetManager for this "silent update"
                    propertiesManager.updateCurrentData(selectedComp)

                    // Also update dimensions UI if it's open, but careful not to interrupt typing
                    // propertiesManager.updateDimensions(selectedComp.width, selectedComp.height)
                    // is safe as it checks isBinding
                    val density = resources.displayMetrics.density
                    propertiesManager.updateDimensions(selectedComp.width, selectedComp.height)
                }
            }

            if (components.isNotEmpty()) {
                val maxY = components.maxOf { it.y + it.height }
                editorCanvas.tag = maxY // Store for occlusion logic
                editorCanvas.post { projectUIManager.updateCanvasOcclusion(maxY) }
            } else {
                editorCanvas.tag = 0f
                editorCanvas.post { projectUIManager.updateCanvasOcclusion(0f) }
            }
        }

        viewModel.project.observe(this) { project ->
            if (project != null) {
                projectUIManager.updateTitle(project.name)
                when (project.orientation) {
                    "PORTRAIT" ->
                            requestedOrientation =
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    "LANDSCAPE" ->
                            requestedOrientation =
                                    android.content.pm.ActivityInfo
                                            .SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else ->
                            requestedOrientation =
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                viewModel.initMqtt()
            }
        }

        viewModel.mqttStatus.observe(this) { status ->
            val iconView =
                    findViewById<android.widget.ImageView>(R.id.indicatorMqttStatus)
                            ?: return@observe

            // Define Colors explicitly
            val colorGray = Color.parseColor("#B0BEC5") // Blue Grey 200 (Neutral)
            val colorGreen = Color.parseColor("#4CAF50") // Green 500
            val colorRed = Color.parseColor("#F44336") // Red 500

            when (status) {
                ProjectViewModel.MqttStatus.CONNECTED -> {
                    iconView.tag = "CONNECTED"
                    iconView.setImageResource(R.drawable.ic_link)
                    iconView.setColorFilter(colorGreen)
                }
                ProjectViewModel.MqttStatus.FAILED -> {
                    if (iconView.tag != "FAILED_SHOWN") {
                        Toast.makeText(
                                        this,
                                        getString(R.string.project_msg_mqtt_failed),
                                        Toast.LENGTH_LONG
                                )
                                .show()
                        iconView.tag = "FAILED_SHOWN"
                    }
                    iconView.setImageResource(R.drawable.ic_link_off)
                    iconView.setColorFilter(colorRed)
                }
                ProjectViewModel.MqttStatus.CONNECTING -> {
                    iconView.tag = null
                    iconView.setImageResource(R.drawable.ic_link)
                    iconView.setColorFilter(colorGray)
                }
                else -> { // IDLE or default
                    iconView.setImageResource(R.drawable.ic_link)
                    iconView.setColorFilter(colorGray)
                }
            }

            viewModel.addLog(getString(R.string.project_log_mqtt_status, status))
            iconView.setOnClickListener {
                if (status == ProjectViewModel.MqttStatus.FAILED) {
                    Toast.makeText(
                                    this,
                                    getString(R.string.project_msg_mqtt_retrying),
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                    viewModel.retryMqtt()
                } else {
                    Toast.makeText(this, "Status: $status", Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.isGridVisible.observe(this) { visible ->
            projectUIManager.updateGridState(visible)
        }

        viewModel.isGuidesVisible.observe(this) { visible ->
            guideOverlay.visibility = if (visible) View.VISIBLE else View.GONE
        }

        viewModel.canUndo.observe(this) { can -> projectUIManager.updateUndoState(can) }
    }

    // --- Helpers (Sidebar, Props, Logs) ---
    private fun initializeHelpers() {
        logConsoleManager = LogConsoleManager(window.decorView)

        propertiesManager =
                PropertiesSheetManager(
                        findViewById(R.id.containerProperties),
                        { // On Expand
                            val sheet = findViewById<View>(R.id.bottomSheet)
                            com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                                    .state =
                                    com.google.android.material.bottomsheet.BottomSheetBehavior
                                            .STATE_EXPANDED
                        },
                        { updatedData -> // On Update
                            viewModel.updateComponent(updatedData)
                        },
                        { id -> // On Clone
                            viewModel.components.value?.find { it.id == id }?.let { source ->
                                // 1. Naming Logic (Label)
                                val name = source.label
                                val regex = "(.*)_copy(\\d*)$".toRegex()
                                val match = regex.find(name)
                                val newLabel =
                                        if (match != null) {
                                            val base = match.groupValues[1]
                                            val numStr = match.groupValues[2]
                                            val num =
                                                    if (numStr.isEmpty()) 2 else numStr.toInt() + 1
                                            "${base}_copy$num"
                                        } else {
                                            "${name}_copy"
                                        }

                                // 2. Naming Logic (Topic) - Same Pattern
                                val topic = source.topicConfig
                                val matchTopic = regex.find(topic)
                                val newTopic =
                                        if (matchTopic != null) {
                                            val base = matchTopic.groupValues[1]
                                            val numStr = matchTopic.groupValues[2]
                                            val num =
                                                    if (numStr.isEmpty()) 2 else numStr.toInt() + 1
                                            "${base}_copy$num"
                                        } else {
                                            "${topic}_copy"
                                        }

                                // 3. Add Component
                                val newComp =
                                        viewModel.addComponent(
                                                source.copy(
                                                        x = source.x + 40f,
                                                        y = source.y + 40f,
                                                        label = newLabel,
                                                        topicConfig = newTopic,
                                                        props = source.props.toMutableMap()
                                                )
                                        )

                                if (newComp != null) {
                                    val newId = newComp.id

                                    // 3. Selection & Props
                                    viewModel.selectComponent(newId)
                                    selectedComponentId = newId

                                    // Force prop update: Use the RETURNED object directly!
                                    val view = renderer.getView(newId)
                                    if (view != null) {
                                        propertiesManager.showProperties(view, newComp)
                                    } else {
                                        // Fallback if renderer hasn't updated view cache yet
                                        // (synchronization)
                                        // Usually renderer updates via Observer.
                                        // But if we are here before observer...
                                        // We can't get the VIEW until renderer creates it.
                                        // So we rely on Observer to eventually call render?
                                        // NO. 'showProperties' needs the VIEW immediately for
                                        // location/highlight.
                                        // If Observer is async, 'getView' returns null.

                                        // Hack: Force Renderer Update with current (mutated) list
                                        // from VM
                                        // VM.components.value might be stale, BUT
                                        // VM.project.value.components IS updated.
                                        viewModel.project.value?.components?.let {
                                            renderer.render(it, isEditMode, newId)
                                            val freshView = renderer.getView(newId)
                                            if (freshView != null)
                                                    propertiesManager.showProperties(
                                                            freshView,
                                                            newComp
                                                    )
                                        }
                                    }
                                }
                            }
                        },
                        { id -> // On Reset Topic
                            viewModel.components.value?.find { it.id == id }?.let { comp ->
                                // Logic: Find first available default topic (e.g. button_1,
                                // button_2)
                                // EXCLUDING the current component (so if it is button_1, it keeps
                                // button_1)

                                val typeLower = comp.type.toLowerCase(Locale.ROOT)
                                val projectPrefix = viewModel.getProjectTopicPrefix()

                                var counter = 1
                                var newTopic = ""
                                val comps = viewModel.components.value ?: emptyList()

                                while (true) {
                                    // Construct candidate suffix
                                    // Example: "button_1"
                                    val candidateSuffix = "${typeLower}_$counter"
                                    val candidateTopic = "${projectPrefix}${candidateSuffix}"

                                    // Check availability (Exclude Self)
                                    val isTaken =
                                            comps.any {
                                                it.id != id && it.topicConfig == candidateTopic
                                            }

                                    if (!isTaken) {
                                        newTopic = candidateTopic
                                        break
                                    }
                                    counter++
                                }

                                val updated = comp.copy(topicConfig = newTopic)
                                viewModel.updateComponent(updated)
                                // Refresh UI
                                val view = renderer.getView(id)
                                if (view != null) {
                                    propertiesManager.showProperties(view, updated)
                                }
                            }
                        }
                )

        logConsoleManager.setClearAction { viewModel.clearLogs() }

        sidebarManager =
                SidebarManager(
                        drawerLayout,
                        null,
                        findViewById(R.id.sidebarEditMode),
                        { type ->
                            // On Click: Add to Center
                            val centerX = editorCanvas.width / 2f
                            val centerY = editorCanvas.height / 2f
                            interactionManager.callbacks.onNewComponent(type, centerX, centerY)
                        }
                )
        sidebarManager.setupComponentPalette(drawerLayout)

        // Exit App Button Logic
        findViewById<android.view.View>(R.id.btnExitApp)?.setOnClickListener {
            finishAffinity() // Close all activities and exit app
        }

        // Exit App Button Logic

        // Ensure AdManager is ready and pre-load Interstitial
        com.example.mqttpanelcraft.utils.AdManager.initialize(this)
        com.example.mqttpanelcraft.utils.AdManager.loadInterstitial(this)

        // Idle Ad Controller
        idleAdController =
                com.example.mqttpanelcraft.ui.IdleAdController(this) {
                    // Ad Closed
                    if (isEditMode) {
                        // Resume behavior if needed
                    }
                }
    }

    private fun subscribeToMqtt() {
        // 1. Message Listener
        com.example.mqttpanelcraft.MqttRepository.registerListener(
                object : com.example.mqttpanelcraft.MqttRepository.MessageListener {
                    override fun onMessageReceived(topic: String, payload: String) {
                        runOnUiThread {
                            // Log Logic: Filter System? (Only clean messages here)
                            // Match Component Label
                            val matched =
                                    viewModel.components.value?.find {
                                        // Exact match or wildcard match
                                        it.topicConfig == topic ||
                                                (it.topicConfig.endsWith("/#") &&
                                                        topic.startsWith(
                                                                it.topicConfig.dropLast(2)
                                                        ))
                                    }

                            val logMsg =
                                    if (matched != null) {
                                        "${matched.label}: $payload"
                                    } else {
                                        "$topic: $payload"
                                    }

                            // Send to VM instead of Manager
                            viewModel.addLog(logMsg)

                            // Update UI for all components that match the topic
                            viewModel.components.value?.forEach { comp ->
                                // Check for exact topic match or wildcard match
                                if (comp.topicConfig == topic ||
                                                (comp.topicConfig.endsWith("/#") &&
                                                        topic.startsWith(
                                                                comp.topicConfig.dropLast(2)
                                                        ))
                                ) {
                                    val view = renderer.getView(comp.id)
                                    if (view != null)
                                            behaviorManager.onMqttMessageReceived(
                                                    view,
                                                    comp,
                                                    payload
                                            )
                                }
                            }
                        }
                    }
                }
        )

        // 2. Default Subscription on Connect
        var hasSubscribed = false
        // Observe Repository Connection Status directly or via VM
        com.example.mqttpanelcraft.MqttRepository.connectionStatus.observe(this) { status ->
            if (status == 1) { // Connected
                if (!hasSubscribed) {
                    val proj = viewModel.project.value
                    if (proj != null) {
                        val defaultTopic = "${proj.name}/${proj.id}/#"
                        val context = applicationContext
                        val intent =
                                android.content.Intent(
                                                context,
                                                com.example.mqttpanelcraft.service
                                                                .MqttService::class
                                                        .java
                                        )
                                        .apply {
                                            action = "SUBSCRIBE"
                                            putExtra("TOPIC", defaultTopic)
                                        }
                        context.startService(intent)
                        // Log Subscription
                        viewModel.addLog("Auto-Subscribing to: $defaultTopic")
                        hasSubscribed = true
                    }
                }
            } else if (status == 2) { // Failed
                hasSubscribed = false // Only reset on explicit failure
            }
            // v44.4: Ignore status 0 (Connecting) to avoid redundant subscription triggers
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        if (::idleAdController.isInitialized) {
            idleAdController.onUserInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        if (::idleAdController.isInitialized) {
            idleAdController.start()
        }

        // Reload Project Data (In case updated via SetupActivity)
        intent.getStringExtra("PROJECT_ID")?.let { id -> viewModel.loadProject(id) }

        projectUIManager.updateModeUI(
                isEditMode,
                selectedComponentId
        ) // Ensure UI state is consistent
    }

    override fun onPause() {
        if (::idleAdController.isInitialized) {
            idleAdController.stop()
        }
        super.onPause()
        val prefs = getSharedPreferences("ProjectPrefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean("GRID_VISIBLE", viewModel.isGridVisible.value ?: true)
            putBoolean("GUIDES_VISIBLE", viewModel.isGuidesVisible.value ?: true)
            apply()
        }
    }
}
