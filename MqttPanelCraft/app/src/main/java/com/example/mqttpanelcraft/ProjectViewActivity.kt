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
import android.graphics.Rect
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
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
    private lateinit var idleAdController: com.example.mqttpanelcraft.ui.IdleAdController
    
    // Others

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

            
            // Restore Preferences
            // Restore Preferences
            val prefs = getSharedPreferences("ProjectPrefs", MODE_PRIVATE)
            val gridVisible = prefs.getBoolean("GRID_VISIBLE", true)
            val guidesVisible = prefs.getBoolean("GUIDES_VISIBLE", true)
            com.example.mqttpanelcraft.utils.DebugLogger.log("ProjectActivity", "Restoring Prefs: Grid=$gridVisible, Guides=$guidesVisible")
            viewModel.setGridVisibility(gridVisible)
            viewModel.setGuidesVisibility(guidesVisible)
            
            // Status Bar Color
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

            val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val wic = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            wic.isAppearanceLightStatusBars = false // Always White Icons for Purple/Dark Toolbar
            
            // Force matches Dashboard Logic (Gray-White or Dark Background)
            val bgColor = androidx.core.content.ContextCompat.getColor(this, R.color.toolbar_bg)
            window.statusBarColor = bgColor
            
            // Fix: CoordinatorLayout & DrawerLayout default scrim color override
            // CoordinatorLayout captures insets first
            findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.rootCoordinator)?.setStatusBarBackgroundColor(bgColor)
            drawerLayout.setStatusBarBackgroundColor(bgColor)
            
            updateModeUI()
            
            // vKeepScreenOn: Prevent auto-lock while in this activity
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.project_msg_init_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeArchitecture() {
        // 1. Renderer (Visuals)
        renderer = ComponentRenderer(editorCanvas, this)

        // 2. Behavior (Logic)
        // 2. Behavior (Logic)
        behaviorManager = ComponentBehaviorManager { topic, payload ->
            // Send MQTT
            val intent = Intent(this, com.example.mqttpanelcraft.service.MqttService::class.java).apply {
                action = "PUBLISH"
                putExtra("TOPIC", topic)
                putExtra("PAYLOAD", payload)
            }
            startService(intent)
        }

        // 3. Interaction (Input)
        val peekHeightPx = (50 * resources.displayMetrics.density).toInt()
        interactionManager = CanvasInteractionManager(editorCanvas, guideOverlay, peekHeightPx, object : CanvasInteractionManager.InteractionCallbacks {
            override fun onComponentSelected(id: Int) {
                if (isEditMode) {
                     if (selectedComponentId != id) {
                         selectedComponentId = id
                         val comp = viewModel.components.value?.find { it.id == id }
                         val view = renderer.getView(id)
                         if (comp != null && view != null) {
                             // Selection Only: Bind Properties but force collapse
                             val sheet = findViewById<View>(R.id.bottomSheet)
                             val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                             behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
                             propertiesManager.showProperties(view, comp, autoExpand = false)
                         }
                         // Render Selection Border
                         viewModel.components.value?.let { renderer.render(it, isEditMode, selectedComponentId) }
                         updateSheetState()
                         
                         // Legacy Occlusion Update (Translation)
                         editorCanvas.post { updateCanvasOcclusion() }
                      }
                }
            }

            override fun onComponentClicked(id: Int) {
                if (isEditMode) {
                    val sheet = findViewById<View>(R.id.bottomSheet)
                    val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                    
                    if (id == -1) {
                        // Background Click -> Deselect & Collapse (Don't Hide)
                        selectedComponentId = null
                        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
                        // Clear visible properties or set "No Selection" state
                        propertiesManager.showTitleOnly() 
                    } else {
                        // Component Logic:
                        // 1. If ALREADY selected -> EXPAND (Second Click)
                        // 2. If NEW selection -> SELECT (First Click)
                        
                        if (selectedComponentId == id) {
                             // Second Click: Expand
                             val comp = viewModel.components.value?.find { it.id == id }
                             val view = renderer.getView(id)
                             if (comp != null && view != null) {
                                 propertiesManager.showProperties(view, comp, autoExpand = true)
                             }
                        } else {
                             // First Click: Select Only 
                             onComponentSelected(id)
                        }
                    }
                    // Trigger Re-render to update Selection Border
                    viewModel.components.value?.let { renderer.render(it, isEditMode, selectedComponentId) }
                    
                    updateCanvasOcclusion()
                    updateSheetState() // Sync Draggability
                }
                // RUN MODE: Background clicks ignored (Prevents auto-collapse)
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
            }

            override fun onComponentDeleted(id: Int) {
                if (id != -1) {
                    viewModel.saveSnapshot()
                    viewModel.removeComponent(id)
                    if (selectedComponentId == id) {
                        selectedComponentId = null
                        propertiesManager.showTitleOnly()
                        updateSheetState() // Sync Draggability
                    }
                }
                Toast.makeText(this@ProjectViewActivity, getString(R.string.project_msg_component_deleted), Toast.LENGTH_SHORT).show()
            }
            
            override fun onDeleteZoneHover(isHovered: Boolean) {
                val header = findViewById<View>(R.id.bottomSheetHeader) ?: return
                val handle = findViewById<View>(R.id.ivHeaderHandle)
                val trash = findViewById<View>(R.id.ivHeaderTrash)
                val text = findViewById<View>(R.id.tvHeaderDelete) // Keep ref but don't show
                
                if (isHovered) {
                     header.setBackgroundResource(R.drawable.bg_delete_gradient)
                     handle?.visibility = View.GONE
                     trash?.visibility = View.VISIBLE
                     text?.visibility = View.GONE
                } else {
                     header.background = null 
                     handle?.visibility = View.VISIBLE
                     trash?.visibility = View.GONE
                     text?.visibility = View.GONE
                }
            }

            override fun onNewComponent(type: String, x: Float, y: Float) {
                viewModel.saveSnapshot()
                
                // 1. Unified Creation from ViewModel
                // This call encapsulates ID, Label, Topic, and Default Size logic.
                val tempData = viewModel.createNewComponentData(type, x, y)
                val w = tempData.width
                val h = tempData.height
                
                // 2. Clamping Logic
                val editorW = editorCanvas.width
                val editorH = editorCanvas.height
                val maxX = (editorW - w).toFloat().coerceAtLeast(0f)
                val maxY = (editorH - h).toFloat().coerceAtLeast(0f)
                var finalX = x.coerceIn(0f, maxX)
                var finalY = y.coerceIn(0f, maxY)
                
                val density = resources.displayMetrics.density
                val gridPx = 10 * density // 10dp Snap
                finalX = (kotlin.math.round(finalX / gridPx) * gridPx)
                finalY = (kotlin.math.round(finalY / gridPx) * gridPx)
                finalX = finalX.coerceIn(0f, maxX)
                finalY = finalY.coerceIn(0f, maxY)

                // 3. Finalize Data & Add
                val finalData = tempData.copy(x = finalX, y = finalY)
                val newComp = viewModel.addComponent(finalData)
                
                if (newComp != null) {
                    val newId = newComp.id
                    viewModel.selectComponent(newId)
                    
                    // Show Properties Immediately (For new components, maybe expand immediately is fine?
                    // User said "first click selects... then drag".
                    // But for A NEW component, usually it's "Edit Immediately". 
                    // Let's keep Auto-Expand for Creation to reduce friction.
                    selectedComponentId = newId
                    
                    // Force Render (Sync)
                    viewModel.project.value?.components?.let { renderer.render(it, isEditMode, newId) }
                    
                    val view = renderer.getView(newId)
                    if (view != null) {
                        propertiesManager.showProperties(view, newComp, autoExpand = true)
                    }
                }
            }
        })
        
        // Auto-Hide Bottom Sheet when Sidebar Opens
        drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                val sheet = findViewById<View>(R.id.bottomSheet)
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                
                // User Request: Keep a bit visible (Peeked) and don't lose selection.
                // So always go to STATE_COLLAPSED, never HIDDEN.
                if (behavior.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED ||
                    behavior.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN) {
                    behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        })
        
        
        // Global Drag Listener to Fix "Fly Back" Bug
        // When dropping outside the canvas (e.g. bottom navbar), consume event as Deletion.
        findViewById<View>(R.id.rootCoordinator)?.setOnDragListener { _, event ->
            if (event.action == android.view.DragEvent.ACTION_DROP) {
                 // Consume drop everywhere outside canvas to prevent "bounce back" animation
                 // This effectively means "Delete" if dropped on UI chrome
                 true 
            } else {
                 true
            }
        }
        
        // Header Interaction Listener (for Empty State Warning)
        val header = findViewById<View>(R.id.bottomSheetHeader)
        header.setOnClickListener {
             if (isEditMode) {
                 if (selectedComponentId == null) {
                     // Check if empty project or just no selection
                     val comps = viewModel.components.value
                     if (comps.isNullOrEmpty()) {
                         Toast.makeText(this, getString(R.string.project_msg_add_component), Toast.LENGTH_SHORT).show()
                     } else {
                         Toast.makeText(this, getString(R.string.project_msg_select_component), Toast.LENGTH_SHORT).show()
                     }
                 } else {
                     // If selected, toggle expand
                     val sheet = findViewById<View>(R.id.bottomSheet)
                     val b = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                     if (b.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED) {
                         b.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                     } else if (b.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
                         b.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
                     }
                 }
             } else {
                 // Run Mode (Logs): Toggle
                 val sheet = findViewById<View>(R.id.bottomSheet)
                 val b = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                 if (b.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED) {
                     b.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                 } else {
                     b.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
                 }
             }
        }

        interactionManager.setup(
            isEditMode = { isEditMode },
            isGridEnabled = { viewModel.isGridVisible.value ?: true },
            isBottomSheetExpanded = {
                val sheet = findViewById<View>(R.id.bottomSheet)
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                behavior.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
        )
    }

    private fun subscribeToViewModel() {
        viewModel.logs.observe(this) { logs ->
             logConsoleManager.updateLogs(logs)
        }

        viewModel.components.observe(this) { components ->
            // Update Console Topics
            logConsoleManager.updateTopics(components, viewModel.project.value)
            
            // RENDER: One-way data flow with Selection State
            renderer.render(components, isEditMode, selectedComponentId)
            
            // Re-attach behaviors (Simple approach)
            components.forEach { comp ->
                val view = renderer.getView(comp.id)
                if (view != null) {
                    behaviorManager.attachBehavior(view, comp)
                }
            }

            // Auto-Resize Canvas for Scrolling
            // FrameLayout with setX/Y children doesn't auto-size. We must force it.
            if (components.isNotEmpty()) {
                val maxY = components.maxOf { it.y + it.height }
                editorCanvas.tag = maxY // Store for occlusion logic
                // Post to ensure BottomSheet layout is complete before calculating occlusion
                editorCanvas.post { updateCanvasOcclusion() }
            } else {
                editorCanvas.tag = 0f
                editorCanvas.post { updateCanvasOcclusion() }
            }
        }
        
        viewModel.project.observe(this) { project ->
            if (project != null) {
                supportActionBar?.title = project.name
                
                // Apply Orientation
                when (project.orientation) {
                    "PORTRAIT" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    "LANDSCAPE" -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED // Sensor
                }
                
                viewModel.initMqtt()
            }
        }
        
        viewModel.mqttStatus.observe(this) { status ->
             val light = findViewById<View>(R.id.indicatorMqttStatus) ?: return@observe
             val bg = light.background as? android.graphics.drawable.GradientDrawable
             bg?.mutate()
             val color = when(status) {
                 ProjectViewModel.MqttStatus.CONNECTED -> android.graphics.Color.GREEN
                 ProjectViewModel.MqttStatus.FAILED -> {
                     if (light.tag != "FAILED_SHOWN") {
                          Toast.makeText(this, getString(R.string.project_msg_mqtt_failed), Toast.LENGTH_LONG).show()
                          light.tag = "FAILED_SHOWN" // Simple debit
                     }
                     android.graphics.Color.RED
                 }
                 ProjectViewModel.MqttStatus.CONNECTING -> {
                     light.tag = null
                     android.graphics.Color.GRAY
                 }
                 else -> android.graphics.Color.GRAY
             }
             bg?.setColor(color)
             
             // Log Status (User Request)
             viewModel.addLog(getString(R.string.project_log_mqtt_status, status))
             
             light.setOnClickListener {
                 if (status == ProjectViewModel.MqttStatus.FAILED) {
                     Toast.makeText(this, getString(R.string.project_msg_mqtt_retrying), Toast.LENGTH_SHORT).show()
                     viewModel.retryMqtt()
                 } else {
                     Toast.makeText(this, "Status: ${status}", Toast.LENGTH_SHORT).show()
                 }
             }
        }

        viewModel.isGridVisible.observe(this) { visible ->
            val grid = findViewById<View>(R.id.backgroundGrid)
            val btn = findViewById<android.widget.ImageView>(R.id.btnGrid)
            grid.visibility = if (visible) View.VISIBLE else View.GONE
            // Visual Consistency: High contrast for toggle state
            btn.alpha = if (visible) 1.0f else 0.3f
            btn.setColorFilter(android.graphics.Color.WHITE)
        }
        
        viewModel.isGuidesVisible.observe(this) { visible ->
             guideOverlay.visibility = if (visible) View.VISIBLE else View.GONE
        }
        
        viewModel.canUndo.observe(this) { can ->
            val btnUndo = findViewById<android.widget.ImageView>(R.id.btnUndo)
            if (btnUndo != null) {
                btnUndo.alpha = if (can) 1.0f else 0.3f
                btnUndo.isEnabled = can
                btnUndo.setColorFilter(android.graphics.Color.WHITE)
            }
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
         
         // Settings Button
         val btnSettings = findViewById<View>(R.id.btnSettings)
         btnSettings.setOnClickListener {
             // Navigate to Setup/Settings
             val intent = Intent(this, com.example.mqttpanelcraft.SetupActivity::class.java)
             intent.putExtra("PROJECT_ID", viewModel.project.value?.id) // Pass Project ID if needed
             startActivity(intent)
         }
         


         // Undo Button
         findViewById<View>(R.id.btnUndo).setOnClickListener {
             viewModel.undo()
         }


         fabMode.setOnClickListener {
             isEditMode = !isEditMode
             
             // Idle Ad: Runs in both modes, do not stop here.
             
             selectedComponentId = null // Clear selection on mode switch
             updateModeUI()
             // Trigger re-render
             viewModel.components.value?.let { renderer.render(it, isEditMode, selectedComponentId) }
             if (!isEditMode) viewModel.saveProject()
         }
         
         // ...
         // Bottom Sheet Callback for Parallax/Push
         // Bottom Sheet Callback for Parallax/Push
         val bottomSheet = findViewById<View>(R.id.bottomSheet)
         val sheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
         
         // Peek Height: 60dp (Show only Header)
         val density = resources.displayMetrics.density
         sheetBehavior.peekHeight = (60 * density).toInt()

          sheetBehavior.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
              override fun onStateChanged(bottomSheet: View, newState: Int) {
                  updateBottomInset(bottomSheet)
              }
              override fun onSlide(bottomSheet: View, slideOffset: Float) {
                  updateCanvasOcclusion(bottomSheet)
                  updateBottomInset(bottomSheet)
              }
          })
          
          // Fix: Initialize Bottom Inset immediately so Drag Resistance/Delete Zone works on start
          bottomSheet.post { 
              updateBottomInset(bottomSheet)
          }
    }

    private fun updateModeUI() {
        // Toggle Logs visibility
        val bottomSheet = findViewById<View>(R.id.bottomSheet)
        val logsContainer = findViewById<View>(R.id.containerLogs) 
        val sheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
        
        // Lock/Unlock Behavior based on Mode
        if (sheetBehavior is LockableBottomSheetBehavior) {
            sheetBehavior.isLocked = isEditMode
        }

        // Toggle Toolbar Navigation Icon (Hamburger)
        if (isEditMode) {
            // Restore Touch Listener for Drag/Drop
            editorCanvas.setOnTouchListener { _, event -> interactionManager.handleTouch(event) }

            fabMode.setImageResource(android.R.drawable.ic_media_play)
            guideOverlay.visibility = View.VISIBLE
            sidebarManager.showComponentsPanel()
            findViewById<View>(R.id.btnUndo).visibility = View.VISIBLE
            
            // Show Sidebar Toggle
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_add_bold) // White bold add icon
            // Fix: Restore Navigation Listener for Drawer (because Run Mode overrides it)
            findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
                sidebarManager.openDrawer()
            }
            
            // Unlock Drawer for Component Palette
            drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
            
            // Edit Mode: Logs OFF, Properties Visible (Collapsed)
            logsContainer.visibility = View.GONE
            findViewById<View>(R.id.containerProperties).visibility = View.VISIBLE
            
            // Allow user to drag it to Close
            sheetBehavior.isHideable = true
            // If nothing selected, maybe HIDDEN? 
            // If we just entered mode, effectively hidden until user clicks.
            // But if we toggle, we might want to keep state.
            // state = STATE_HIDDEN ?? 
            // Let's keep existing logic but allow Hide.
            if (sheetBehavior.state == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN) {
                sheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
            }
            
        } else {
            // Run Mode: Remove Touch Listener to allow NestedScrollView to handle scrolling fully
            editorCanvas.setOnTouchListener(null)

            fabMode.setImageResource(android.R.drawable.ic_menu_edit)
            guideOverlay.visibility = View.GONE // Guides off in run mode
            // Sidebar Disable in Run Mode
            drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            
            // Run Mode: Show Back Button to Exit
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            val backArrow = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_arrow_back_thick)?.mutate()
            backArrow?.setTint(android.graphics.Color.WHITE)
            supportActionBar?.setHomeAsUpIndicator(backArrow)
            
            // Restore functionality: Clicking back exits the activity (or Run Mode?)
            // Usually in Run Mode inside a Project, "Back" would go back to Dashboard.
            findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
                onBackPressed()
            }
            
            findViewById<View>(R.id.btnUndo).visibility = View.GONE
            
            // Run Mode: Logs ON (Peek), Properties OFF
            logsContainer.visibility = View.VISIBLE
            propertiesManager.hide()
            
            // Force Peek to show Logs Header/Initial Rows
            sheetBehavior.isHideable = false // Logs usually stick around? Or allow hide?
            sheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
        }
        updateSheetState()
    }

    private fun updateSheetState() {
        val bottomSheet = findViewById<View>(R.id.bottomSheet) ?: return
        val sheetBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
        
        if (isEditMode) {
             // In Edit Mode: Helper logic
             if (selectedComponentId == null) {
                 sheetBehavior.isDraggable = false
                 if (sheetBehavior.state != com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN) {
                    sheetBehavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED 
                 }
             } else {
                 sheetBehavior.isDraggable = true
             }
        } else {
             // Run Mode: Always draggable (Logs)
             sheetBehavior.isDraggable = true
        }
    }

    // --- Helpers (Sidebar, Props, Logs) ---
    private fun initializeHelpers() {
        logConsoleManager = LogConsoleManager(window.decorView)
        
        propertiesManager = PropertiesSheetManager(
            findViewById(R.id.containerProperties),
            { // On Expand
                val sheet = findViewById<View>(R.id.bottomSheet)
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet).state = 
                    com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            },
{ updatedData -> // On Update
                viewModel.updateComponent(updatedData)
            },
            { id -> // On Clone
                viewModel.components.value?.find { it.id == id }?.let { source ->
                    // 1. Naming Logic
                    val name = source.label
                    val regex = "(.*)_copy(\\d*)$".toRegex()
                    val match = regex.find(name)
                    val newLabel = if (match != null) {
                        val base = match.groupValues[1]
                        val numStr = match.groupValues[2]
                        val num = if (numStr.isEmpty()) 2 else numStr.toInt() + 1
                        "${base}_copy$num"
                    } else {
                        "${name}_copy"
                    }
                
                    // 2. Add Component
                    val newComp = viewModel.addComponent(source.copy(
                        x = source.x + 40f, 
                        y = source.y + 40f,
                        label = newLabel
                    )) 
                    
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
                             // Fallback if renderer hasn't updated view cache yet (synchronization)
                             // Usually renderer updates via Observer.
                             // Trigger manual render pass if needed?
                             // Since we mutated the list in VM, the observer WILL fire.
                             // But if we are here before observer...
                             // We can't get the VIEW until renderer creates it.
                             // So we rely on Observer to eventually call render?
                             // NO. 'showProperties' needs the VIEW immediately for location/highlight.
                             // If Observer is async, 'getView' returns null.
                             
                             // Hack: Force Renderer Update with current (mutated) list from VM
                             // VM.components.value might be stale, BUT VM.project.value.components IS updated.
                             viewModel.project.value?.components?.let { 
                                 renderer.render(it, isEditMode, newId) 
                                 val freshView = renderer.getView(newId)
                                 if (freshView != null) propertiesManager.showProperties(freshView, newComp)
                             }
                        }
                    }
                }
            },
            { id -> // On Reset Topic
                 viewModel.components.value?.find { it.id == id }?.let { comp ->
                    val freshTopic = viewModel.generateSmartTopic(comp.type)
                    val updated = comp.copy(topicConfig = freshTopic)
                    viewModel.updateComponent(updated)
                    // Refresh UI
                    val view = renderer.getView(id)
                    if (view != null) {
                        propertiesManager.showProperties(view, updated)
                    }
                }
            }
        )
        
        logConsoleManager.setClearAction {
            viewModel.clearLogs()
        }

        sidebarManager = SidebarManager(
            drawerLayout,
            null,
            findViewById(R.id.sidebarEditMode)
        ) { view, type ->
            // On Drag Start from Sidebar
            // Create a temporary component to drag
            val def = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(type)
            if (def != null) {
                // ... same logic usually handled inside SidebarManager? 
                // Wait, SidebarManager handles startDragAndDrop internally using the touch listener.
                // The callback is for... checking?
                // Actually SidebarManager's constructor last arg is `onComponentDragStart`.
                // Let's check SidebarManager definition again.
            }
        }
        sidebarManager.setupComponentPalette(drawerLayout)
        
        // Exit App Button Logic
        findViewById<android.view.View>(R.id.btnExitApp)?.setOnClickListener {
             finishAffinity() // Close all activities and exit app
        }
        
        // sidebarRunMode removed
        
        // sidebarRunMode removed
        
        // Ensure AdManager is ready and pre-load Interstitial
        com.example.mqttpanelcraft.utils.AdManager.initialize(this)
        com.example.mqttpanelcraft.utils.AdManager.loadInterstitial(this)
        
        // Idle Ad Controller
        idleAdController = com.example.mqttpanelcraft.ui.IdleAdController(this) {
             // Ad Closed
             if (isEditMode) {
                 // Resume behavior if needed
             }
        }
    }

    private fun subscribeToMqtt() {
        // 1. Message Listener
        com.example.mqttpanelcraft.MqttRepository.registerListener(object : com.example.mqttpanelcraft.MqttRepository.MessageListener {
            override fun onMessageReceived(topic: String, payload: String) {
                 runOnUiThread {
                     // Log Logic: Filter System? (Only clean messages here)
                     // Match Component Label
                     val matched = viewModel.components.value?.find { 
                         // Exact match or wildcard match
                         it.topicConfig == topic || (it.topicConfig.endsWith("/#") && topic.startsWith(it.topicConfig.dropLast(2)))
                     }
                     
                     val logMsg = if (matched != null) {
                         "${matched.label}: $payload"
                     } else {
                         "$topic: $payload"
                     }
                     
                     // Send to VM instead of Manager
                     viewModel.addLog(logMsg)

                     // Update UI for all components that match the topic
                     viewModel.components.value?.forEach { comp ->
                         // Check for exact topic match or wildcard match
                         if (comp.topicConfig == topic || (comp.topicConfig.endsWith("/#") && topic.startsWith(comp.topicConfig.dropLast(2)))) {
                             val view = renderer.getView(comp.id)
                             if (view != null) behaviorManager.onMqttMessageReceived(view, comp, payload)
                         }
                     }
                 }
            }
        })
        
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
                         val intent = android.content.Intent(context, com.example.mqttpanelcraft.service.MqttService::class.java).apply {
                             action = "SUBSCRIBE"
                             putExtra("TOPIC", defaultTopic)
                         }
                         context.startService(intent)
                         // Log Subscription
                         viewModel.addLog("Auto-Subscribing to: $defaultTopic")
                         hasSubscribed = true
                     }
                 }
             } else {
                 hasSubscribed = false
             }
        }
    }


    private fun updateCanvasOcclusion(bottomSheet: View? = null) {
        val sheet = bottomSheet ?: findViewById(R.id.bottomSheet) ?: return
        
        if (isEditMode) {
            // LEGACY LOGIC: Shift Canvas Y to keep selected component visible
            // This logic pushes the entire canvas up using translationY
            val canvas = findViewById<View>(R.id.editorCanvas) ?: return
            val selectedId = selectedComponentId
            
            if (selectedId == null) {
                canvas.translationY = 0f
                return
            }
            
            val compView = renderer.getView(selectedId)
            if (compView == null) {
                 canvas.translationY = 0f
                 return
            }
    
            val compLoc = IntArray(2)
            compView.getLocationOnScreen(compLoc) 
            val currentCompBottom = compLoc[1] + compView.height
            
            val sheetLoc = IntArray(2)
            sheet.getLocationOnScreen(sheetLoc)
            val sheetTop = sheetLoc[1]
            
            val margin = (50 * resources.displayMetrics.density)
            
            val currentTrans = canvas.translationY
            // Calculate where the component WOULD be without translation
            val rawBottom = currentCompBottom - currentTrans
            
            // We want (rawBottom + newTrans) < (sheetTop - margin)
            // newTrans < sheetTop - margin - rawBottom
            var targetTrans = (sheetTop - margin - rawBottom)
            
            // Only translate UP (negative), never down positive (don't detach from top)
            if (targetTrans > 0f) targetTrans = 0f
            
            canvas.translationY = targetTrans
            
        } else {
            // RUN MODE LOGIC: Resize Canvas Height to enable Scrolling
            // Ensure no residual translation
            editorCanvas.translationY = 0f
            
            val density = resources.displayMetrics.density
            val sheetLoc = IntArray(2)
            sheet.getLocationOnScreen(sheetLoc)
            val sheetTop = sheetLoc[1]
            val screenHeight = resources.displayMetrics.heightPixels
            val visibleSheetHeight = (screenHeight - sheetTop).coerceAtLeast(0)
    
            // MaxY stored in Tag from ViewModel observer
            val maxY = (editorCanvas.tag as? Float) ?: 0f
            val requiredHeight = (maxY + visibleSheetHeight + (20 * density)).toInt()
            
            if (editorCanvas.minimumHeight != requiredHeight) {
                editorCanvas.minimumHeight = requiredHeight
                editorCanvas.requestLayout()
            }
        }
    }

    private fun updateBottomInset(bottomSheet: View) {
        // Calculate overlap between BottomSheet and Canvas to adjust Deletion Zone
        if (bottomSheet.visibility != View.VISIBLE) {
            interactionManager.updateBottomInset(0)
            return
        }
        val sheetLoc = IntArray(2)
        bottomSheet.getLocationOnScreen(sheetLoc)
        val sheetTop = sheetLoc[1]
        
        val canvasLoc = IntArray(2)
        editorCanvas.getLocationOnScreen(canvasLoc)
        val canvasBottom = canvasLoc[1] + editorCanvas.height
        
        // Inset is the amount of canvas covered by the sheet
        val overlap = (canvasBottom - sheetTop).coerceAtLeast(0)
        interactionManager.updateBottomInset(overlap)
    }

    private fun ensureComponentVisible(componentId: Int) {
        val view = renderer.getView(componentId) ?: return
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.editorScrollView)
        val sheet = findViewById<View>(R.id.bottomSheet)
        if (scrollView == null || sheet == null) return

        val compLoc = IntArray(2)
        view.getLocationOnScreen(compLoc)
        val compBottomScreen = compLoc[1] + view.height
        
        val sheetLoc = IntArray(2)
        sheet.getLocationOnScreen(sheetLoc)
        val sheetTopScreen = sheetLoc[1]
        
        val density = resources.displayMetrics.density
        // Goal: Component Bottom must be at least 50dp above Sheet Top
        val safetyMargin = (50 * density).toInt()
        val visibleLimit = sheetTopScreen - safetyMargin
        
        if (compBottomScreen > visibleLimit) {
            val diff = compBottomScreen - visibleLimit
            
            // Check if we can scroll
            val currentScrollY = scrollView.scrollY
            // Max scroll is Child Height - Scroll View Height (View port)
            // Note: editorCanvas.height already includes any minimumHeight override
            val maxScrollY = editorCanvas.height - scrollView.height
            val targetScrollY = currentScrollY + diff
            
            // If we need to scroll MORE than currently possible
            if (targetScrollY > maxScrollY) {
                // Extend Canvas to allow scrolling
                // We add the 'diff' to the current height essentially
                // But precisely: We need new maxScrollY >= targetScrollY
                // newHeight - scrollView.height >= targetScrollY
                // newHeight >= targetScrollY + scrollView.height
                
                val requiredHeight = targetScrollY + scrollView.height
                
                if (editorCanvas.minimumHeight < requiredHeight) {
                    editorCanvas.minimumHeight = requiredHeight
                    editorCanvas.requestLayout()
                }
                
                // Allow layout to settle before scrolling
                scrollView.post {
                    scrollView.smoothScrollBy(0, diff)
                }
            } else {
                scrollView.smoothScrollBy(0, diff)
            }
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
        intent.getStringExtra("PROJECT_ID")?.let { id ->
            viewModel.loadProject(id)
        }
        
        updateModeUI() // Ensure UI state is consistent
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
