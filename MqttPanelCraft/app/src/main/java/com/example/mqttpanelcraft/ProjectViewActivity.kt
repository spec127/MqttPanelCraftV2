package com.example.mqttpanelcraft

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.ui.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AlertDialog
import java.util.Locale

class ProjectViewActivity : AppCompatActivity() {

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

    private var selectedComponentId: Int? = null
    private var isEditMode = false

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

            // --- Initialize Managers ---
            initializeHelpers()
            initializeArchitecture()

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

            setupToolbar()
            updateModeUI()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Init Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeArchitecture() {
        // 1. Renderer (Visuals)
        renderer = ComponentRenderer(editorCanvas, this)

        // 2. Behavior (Logic)
        behaviorManager = ComponentBehaviorManager(this, { viewModel.project.value?.id }) { topic, payload ->
            // Send MQTT
            val intent = Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java).apply {
                action = "PUBLISH"
                putExtra("TOPIC", topic)
                putExtra("PAYLOAD", payload)
            }
            startService(intent)
        }

        // 3. Interaction (Input)
        interactionManager = CanvasInteractionManager(editorCanvas, guideOverlay, dropDeleteZone, object : CanvasInteractionManager.InteractionCallbacks {
            override fun onComponentClicked(id: Int) {
                if (isEditMode) {
                    if (id == -1) {
                        // Background Click -> Deselect
                        selectedComponentId = null
                        propertiesManager.hide()
                    } else {
                        // Component Click -> Select
                        selectedComponentId = id
                        val comp = viewModel.components.value?.find { it.id == id }
                        if (comp != null) {
                            propertiesManager.showProperties(renderer.getView(id) ?: View(this@ProjectViewActivity), comp.label, comp.topicConfig)
                        }
                    }
                    // Trigger Re-render to update Selection Border
                    viewModel.components.value?.let { renderer.render(it, isEditMode, selectedComponentId) }
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

            override fun onComponentDeleted(id: Int) {
                viewModel.saveSnapshot()
                viewModel.removeComponent(id)
                if (selectedComponentId == id) {
                    selectedComponentId = null
                    propertiesManager.hide()
                }
            }

            override fun onNewComponent(type: String, x: Float, y: Float) {
                viewModel.saveSnapshot()
                val newId = com.example.mqttpanelcraft.ProjectViewModel.generateSmartId(viewModel.components.value ?: emptyList(), type)
                val (w, h) = com.example.mqttpanelcraft.ui.ComponentFactory.getDefaultSize(this@ProjectViewActivity, type)
                
                val newData = ComponentData(
                    id = newId,
                    type = type,
                    x = x,
                    y = y,
                    width = w,
                    height = h,
                    label = type,
                    topicConfig = ""
                )
                viewModel.addComponent(newData)
            }
        })
        
        interactionManager.setup { isEditMode }
    }

    private fun subscribeToViewModel() {
        viewModel.components.observe(this) { components ->
            // RENDER: One-way data flow with Selection State
            renderer.render(components, isEditMode, selectedComponentId)
            
            // Re-attach behaviors (Simple approach)
            components.forEach { comp ->
                val view = renderer.getView(comp.id)
                if (view != null) {
                    behaviorManager.attachBehavior(view, comp)
                }
            }
        }
        
        viewModel.project.observe(this) { project ->
            if (project != null) {
                supportActionBar?.title = project.name
                // MQTT Connect...
                val intent = Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java).apply {
                    action = "CONNECT"
                    putExtra("BROKER", project.broker)
                    putExtra("PORT", project.port)
                    putExtra("USER", project.username)
                    putExtra("PASSWORD", project.password)
                    putExtra("CLIENT_ID", project.clientId)
                }
                startService(intent)
            }
        }

        viewModel.isGridVisible.observe(this) { visible ->
            val grid = findViewById<View>(R.id.backgroundGrid)
            val btn = findViewById<View>(R.id.btnGrid)
            grid.visibility = if (visible) View.VISIBLE else View.GONE
            btn.alpha = if (visible) 1.0f else 0.5f
        }
    }

    private fun setupToolbar() {
         val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
         setSupportActionBar(toolbar)
         supportActionBar?.setDisplayHomeAsUpEnabled(true)
         supportActionBar?.setHomeAsUpIndicator(android.R.drawable.ic_menu_sort_by_size) // Hamburger Icon
         toolbar.setNavigationOnClickListener { sidebarManager.openDrawer() }
         
         // Fix Grid Button Logic and Logs
         val btnGrid = findViewById<View>(R.id.btnGrid)
         btnGrid.setOnClickListener {
             viewModel.toggleGrid()
         }
         
         // Long Press for Logs
         btnGrid.setOnLongClickListener {
             AlertDialog.Builder(this)
                 .setTitle("Debug Logs")
                 .setMessage(logConsoleManager.getLogs()) // Assuming getLogs exists or just show console
                 .setPositiveButton("Copy") { _, _ ->
                     val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                     val clip = android.content.ClipData.newPlainText("MqttLogs", logConsoleManager.getLogs())
                     clipboard.setPrimaryClip(clip)
                     Toast.makeText(this, "Logs Copied", Toast.LENGTH_SHORT).show()
                 }
                 .setNegativeButton("Close", null)
                 .show()
             true
         }


         fabMode.setOnClickListener {
             isEditMode = !isEditMode
             selectedComponentId = null // Clear selection on mode switch
             updateModeUI()
             // Trigger re-render
             viewModel.components.value?.let { renderer.render(it, isEditMode, selectedComponentId) }
             if (!isEditMode) viewModel.saveProject()
         }
         
         // ...
    }

    private fun updateModeUI() {
        // Toggle Logs visibility
        val bottomSheet = findViewById<View>(R.id.bottomSheet)
        val logsContainer = findViewById<View>(R.id.containerLogs) // Assuming this is the logs container
        
        if (isEditMode) {
            fabMode.setImageResource(android.R.drawable.ic_media_play)
            guideOverlay.visibility = View.VISIBLE
            sidebarManager.showComponentsPanel()
            findViewById<View>(R.id.btnUndo).visibility = View.VISIBLE
            
            // Edit Mode: Logs OFF, Properties Hidden initially
            logsContainer.visibility = View.GONE
            propertiesManager.hide()
            
        } else {
            fabMode.setImageResource(android.R.drawable.ic_menu_edit)
            guideOverlay.visibility = View.GONE // Guides off in run mode
            sidebarManager.showRunModePanel()
            findViewById<View>(R.id.btnUndo).visibility = View.GONE
            
            // Run Mode: Logs ON (Peek), Properties OFF
            logsContainer.visibility = View.VISIBLE
            propertiesManager.hide()
        }
    }

    // --- Helpers (Sidebar, Props, Logs) ---
    private fun initializeHelpers() {
        logConsoleManager = LogConsoleManager(window.decorView)
        
        propertiesManager = PropertiesSheetManager(
             findViewById(R.id.containerProperties),
             onExpandRequest = { /* Handle Sheet Expanded */ },
             onPropertyUpdated = { id, name, w, h, _, topic ->
                 val comp = viewModel.components.value?.find { it.id == id }
                 if (comp != null) {
                     val updated = comp.copy(label = name, width = (w * resources.displayMetrics.density).toInt(), height = (h * resources.displayMetrics.density).toInt(), topicConfig = topic)
                     viewModel.updateComponent(updated)
                 }
             }
        )

        sidebarManager = SidebarManager(
            drawerLayout, 
            null, 
            findViewById(R.id.sidebarEditMode),
            findViewById(R.id.sidebarRunMode),
            { _,_ -> }
        )
        sidebarManager.setupComponentPalette(drawerLayout)
    }

    private fun subscribeToMqtt() {
         com.example.mqttpanelcraft.MqttRepository.registerListener(object : com.example.mqttpanelcraft.MqttRepository.MessageListener {
            override fun onMessageReceived(topic: String, payload: String) {
                 runOnUiThread {
                     logConsoleManager.addLog("$topic: $payload")
                     viewModel.components.value?.forEach { comp ->
                         // Simple topic match
                         if (comp.topicConfig == topic || topic.endsWith("/#")) {
                             val view = renderer.getView(comp.id)
                             if (view != null) behaviorManager.onMqttMessageReceived(view, comp, payload)
                         }
                     }
                 }
            }
        })
    }


}
