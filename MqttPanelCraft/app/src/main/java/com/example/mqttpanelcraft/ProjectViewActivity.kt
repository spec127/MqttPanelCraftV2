package com.example.mqttpanelcraft

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Point
import android.graphics.Shader
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.ui.AlignmentOverlayView
import com.example.mqttpanelcraft.utils.CrashLogger
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class ProjectViewActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var editorCanvas: ConstraintLayout
    private lateinit var guideOverlay: AlignmentOverlayView
    private lateinit var fabMode: FloatingActionButton
    private lateinit var logAdapter: LogAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    
    // Bottom Sheet Containers
    private lateinit var containerLogs: LinearLayout
    private lateinit var containerProperties: ScrollView

    // Property Inputs
    private lateinit var etPropName: TextInputEditText
    private lateinit var etPropWidth: TextInputEditText
    private lateinit var etPropHeight: TextInputEditText
    private lateinit var etPropColor: TextInputEditText
    private lateinit var btnSaveProps: Button
    
    // Console Inputs
    private lateinit var etTopic: EditText
    private lateinit var etPayload: EditText
    private lateinit var btnSend: Button

    private var isEditMode = false 
    private var projectId: String? = null
    private var project: com.example.mqttpanelcraft.model.Project? = null
    
    private var selectedView: View? = null
    private val snapThreshold = 16f // dp


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_project_view)

            // Restore State
            if (savedInstanceState != null) {
                isEditMode = savedInstanceState.getBoolean("IS_EDIT_MODE", false)
            }

            setupUI()
            setupConsole()
            setupSidebarInteraction()
            setupPropertiesPanel()
            
            // Load Project Data
            projectId = intent.getStringExtra("PROJECT_ID")
            if (projectId != null) {
                loadProjectDetails(projectId!!)
            } else {
                finish()
            }

            checkMqttConnection()
            
            // Restore Drawer State (Must be after UI setup)
            if (savedInstanceState != null) {
                val wasDrawerOpen = savedInstanceState.getBoolean("IS_DRAWER_OPEN", false)
                if (wasDrawerOpen) {
                    drawerLayout.openDrawer(GravityCompat.START)
                }
            }
            
            // Ensure UI reflects restored mode
            updateModeUI()
            
        } catch (e: Exception) {
            CrashLogger.logError(this, "Project View Init Failed", e)
            finish()
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("IS_EDIT_MODE", isEditMode)
        if (::drawerLayout.isInitialized) {
            outState.putBoolean("IS_DRAWER_OPEN", drawerLayout.isDrawerOpen(GravityCompat.START))
        }
    }
    
    override fun onStart() {
        super.onStart()
        setupWindowInsets()

        setupDrawerListener()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView.findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
    
    private fun setupDrawerListener() {
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // STRICT LOCKING: Disable bottom sheet dragging if drawer is moved
                if (slideOffset > 0.05f) {
                    bottomSheetBehavior.isDraggable = false
                    
                    // Auto-collapse bottom sheet if it's open
                    if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                 // STRICT LOCKING: Ensure bottom sheet cannot be dragged
                 bottomSheetBehavior.isDraggable = false
                 
                 // Ensure collapsed
                 if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                     bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                 }
            }
            
            override fun onDrawerClosed(drawerView: View) {
                 // Unlock bottom sheet when drawer is closed
                 if (isEditMode) {
                     bottomSheetBehavior.isDraggable = true
                 }
            }
            
            override fun onDrawerStateChanged(newState: Int) {
                 if (newState == DrawerLayout.STATE_DRAGGING) {
                     bottomSheetBehavior.isDraggable = false
                 }
            }
        })
    }
    

    
    private fun loadProjectDetails(id: String) {
        project = ProjectRepository.getProjectById(id)
        if (project != null) {
            supportActionBar?.title = project!!.name
        }
    }

    private fun setupUI() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    
    // Add Hamburger Menu Icon
    toolbar.setNavigationIcon(R.drawable.ic_menu)
    toolbar.setNavigationOnClickListener { 
        // Allow opening drawer even if bottom sheet is expanded
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
             bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }
    }
        
        // Settings Button (Custom View)
        val btnSettings = findViewById<ImageView>(R.id.btnSettings)
        btnSettings.setOnClickListener {
             if (projectId != null) {
                // val intent = android.content.Intent(this, SetupActivity::class.java) 
                // ERROR: SetupActivity import might be missing, using fully qualified or resolving
                try {
                     val intent = android.content.Intent(this, Class.forName("com.example.mqttpanelcraft_beta.SetupActivity"))
                     intent.putExtra("PROJECT_ID", projectId)
                     startActivity(intent)
                     finish()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Setup Activity not found", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Run Mode Sidebar Actions
        try {
            val switchDarkMode = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchDarkMode)
            
            // Set initial state
            val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            switchDarkMode?.isChecked = (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES)

            switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
                }
                // Do NOT close drawer; let onSaveInstanceState handle state persistence
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Trash Bin Drag Listener
        val binTrash = findViewById<ImageView>(R.id.binTrash)
        binTrash.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    (v as? ImageView)?.setColorFilter(Color.RED) // Highlight on hover
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    (v as? ImageView)?.clearColorFilter()
                    (v as? ImageView)?.setColorFilter(Color.WHITE) // Restore
                    true
                }
                DragEvent.ACTION_DROP -> {
                     (v as? ImageView)?.clearColorFilter()
                     (v as? ImageView)?.setColorFilter(Color.WHITE)
                     
                     val clipData = event.clipData
                     if (clipData != null && clipData.itemCount > 0) {
                         val idStr = clipData.getItemAt(0).text.toString()
                         // Check if it's an ID (View ID usually integer, encoded as string)
                         try {
                             val viewId = idStr.toInt()
                             val component = editorCanvas.findViewById<View>(viewId)
                             if (component != null) {
                                  AlertDialog.Builder(this)
                                      .setTitle("Delete Component")
                                      .setMessage("Are you sure you want to delete this component?")
                                      .setPositiveButton("Delete") { _, _ ->
                                          editorCanvas.removeView(component)
                                          guideOverlay.clear() // Clear lines
                                      }
                                      .setNegativeButton("Cancel", null)
                                      .show()
                             }
                         } catch (e: NumberFormatException) {
                             // Not a valid ID (maybe palette drag)
                         }
                     }
                     true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    (v as? ImageView)?.clearColorFilter()
                    (v as? ImageView)?.setColorFilter(Color.WHITE)
                    true
                }
                else -> false
            }
        }

        drawerLayout = findViewById(R.id.drawerLayout)
        editorCanvas = findViewById(R.id.editorCanvas)
        guideOverlay = findViewById(R.id.guideOverlay)
        fabMode = findViewById(R.id.fabMode)
        
        containerLogs = findViewById(R.id.containerLogs)
        containerProperties = findViewById(R.id.containerProperties)
        
        
        val bottomSheet = findViewById<FrameLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // Sidebar Actions
        try { // Wrap in try-catch in case views are missing
            val backgroundGrid = findViewById<View>(R.id.backgroundGrid)
            val switchGridToggle = findViewById<SwitchMaterial>(R.id.switchGridToggle)
            
            switchGridToggle?.setOnCheckedChangeListener { _, isChecked ->
                backgroundGrid?.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        
        val bottomSheetScrim = findViewById<View>(R.id.bottomSheetScrim)
        bottomSheetScrim?.setOnClickListener {
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        
        // Update: Strict Mutual Exclusivity - Lock drawer if bottom sheet is expanded
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING || newState == BottomSheetBehavior.STATE_EXPANDED) {
                     // Lock drawer closed -> REMOVED per user request
                     // drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                     bottomSheetScrim?.visibility = View.VISIBLE
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                     // Unlock drawer (only if in Edit Mode)
                     if (isEditMode) {
                         drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
                     }
                     bottomSheetScrim?.visibility = View.GONE
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // If sliding up, ensure drawer is locked -> REMOVED per user request
                if (slideOffset > 0.05f) {
                    // drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    bottomSheetScrim?.visibility = View.VISIBLE
                    bottomSheetScrim?.alpha = slideOffset.coerceIn(0f, 1f)
                } else {
                     bottomSheetScrim?.visibility = View.GONE
                }
            }
        })

        val bottomSheetHeader = findViewById<LinearLayout>(R.id.bottomSheetHeader)
        bottomSheetHeader.setOnClickListener {
            // Check if Drawer is open before allowing toggle
            if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                return@setOnClickListener
            }
            
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        fabMode.setOnClickListener { toggleMode() }
        updateModeUI()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_project_view, menu)
        return true
    }


    private fun setupPropertiesPanel() {
        etPropName = findViewById(R.id.etPropName)
        etPropWidth = findViewById(R.id.etPropWidth)
        etPropHeight = findViewById(R.id.etPropHeight)
        etPropColor = findViewById(R.id.etPropColor)
        btnSaveProps = findViewById(R.id.btnSaveProps)
        
        etPropColor.setOnClickListener { showGradientColorPicker() } 
        etPropColor.focusable = View.FOCUSABLE_AUTO
        etPropColor.isFocusableInTouchMode = false
        
        btnSaveProps.setOnClickListener {
            selectedView?.let { view ->
                try {
                    val w = etPropWidth.text.toString().toIntOrNull()
                    val h = etPropHeight.text.toString().toIntOrNull()
                    if (w != null && h != null) {
                        val params = view.layoutParams as ConstraintLayout.LayoutParams
                        val density = resources.displayMetrics.density
                        params.width = (w * density).toInt()
                        params.height = (h * density).toInt()
                        view.layoutParams = params
                    }
                    
                    val colorStr = etPropColor.text.toString()
                    if (colorStr.isNotEmpty()) {
                        try {
                             view.setBackgroundColor(Color.parseColor(colorStr))
                        } catch (e: Exception) {}
                    }
                    
                    if (view is TextView) view.text = etPropName.text.toString()
                    else if (view is Button) view.text = etPropName.text.toString()
                    else if (view is SwitchMaterial) view.text = etPropName.text.toString()
                    
                    Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showGradientColorPicker() {
        val width = 600
        val height = 400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val gradient = LinearGradient(0f, 0f, width.toFloat(), 0f,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
            null, Shader.TileMode.CLAMP)
        val paint = android.graphics.Paint()
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        val darkGradient = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            null, Shader.TileMode.CLAMP)
        val darkPaint = android.graphics.Paint()
        darkPaint.shader = darkGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), darkPaint)

        val imageView = ImageView(this)
        imageView.setImageBitmap(bitmap)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Color")
            .setView(imageView)
            .setPositiveButton("Close", null)
            .create()
            
        imageView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val x = event.x.toInt().coerceIn(0, width - 1)
                val y = event.y.toInt().coerceIn(0, height - 1)
                val pixel = bitmap.getPixel(x, y)
                val hexBox = String.format("#%06X", (0xFFFFFF and pixel))
                etPropColor.setText(hexBox)
                dialog.setTitle("Color: $hexBox")
            }
            true
        }
        
        dialog.show()
    }
    
    private fun setupConsole() {
        val rvLogs = findViewById<RecyclerView>(R.id.rvConsoleLogs)
        etTopic = findViewById(R.id.etConsoleTopic)
        etPayload = findViewById(R.id.etConsolePayload)
        btnSend = findViewById(R.id.btnConsoleSend)
        logAdapter = LogAdapter()
        rvLogs.layoutManager = LinearLayoutManager(this)
        rvLogs.adapter = logAdapter
         MqttRepository.logs.observe(this) { logs ->
            logAdapter.submitList(logs)
            if (logs.isNotEmpty()) rvLogs.smoothScrollToPosition(logs.size - 1)
        }
        btnSend.setOnClickListener {
             val topic = etTopic.text.toString()
            val payload = etPayload.text.toString()
            if (topic.isNotEmpty() && payload.isNotEmpty()) {
                val client = MqttRepository.mqttClient
                if (client != null && client.isConnected) {
                    try {
                        val message = org.eclipse.paho.client.mqttv3.MqttMessage(payload.toByteArray())
                        client.publish(topic, message)
                        MqttRepository.addLog("TX [$topic]: $payload", java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date()))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Publish Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Not Connected.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun checkMqttConnection() {
        val client = MqttRepository.mqttClient
        if (client == null || !client.isConnected) {
            MqttRepository.addLog("System: Editor opened without active MQTT connection.", "")
        } else {
            MqttRepository.addLog("System: Connected to broker.", "")
        }
    }

    private fun toggleMode() {
        isEditMode = !isEditMode
        updateModeUI()
    }

    private fun updateModeUI() {
        val sidebarEditMode = findViewById<View>(R.id.sidebarEditMode)
        val sidebarRunMode = findViewById<View>(R.id.sidebarRunMode)

        if (isEditMode) {
             fabMode.setImageResource(android.R.drawable.ic_media_play) 
             editorCanvas.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_grid_pattern)
             // Edit Mode: Drawer unlocked intentionally? Or locked closed?
             // User wants to open sidebar in Edit mode (Components). 
             drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
             
             sidebarEditMode?.visibility = View.VISIBLE
             sidebarRunMode?.visibility = View.GONE
             
             containerLogs.visibility = View.GONE
             containerProperties.visibility = View.VISIBLE 
             guideOverlay.visibility = View.VISIBLE 
             guideOverlay.bringToFront()
             Toast.makeText(this, "Edit Mode", Toast.LENGTH_SHORT).show()
        } else {
             fabMode.setImageResource(android.R.drawable.ic_menu_edit)
             editorCanvas.background = null
             guideOverlay.clear()
             guideOverlay.visibility = View.GONE
             
             // Run Mode: User now wants to open sidebar for Export/Import buttons
             // So we UNLOCK it here too.
             drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
             
             sidebarEditMode?.visibility = View.GONE
             sidebarRunMode?.visibility = View.VISIBLE
             
             containerLogs.visibility = View.VISIBLE
             containerProperties.visibility = View.GONE
             Toast.makeText(this, "Run Mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSidebarInteraction() {
        val touchListener = View.OnTouchListener { view, event ->
             if (event.action == MotionEvent.ACTION_DOWN) {
                 val item = ClipData.Item(view.tag as? CharSequence)
                 val dragData = ClipData(view.tag as? CharSequence, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
                 val shadow = View.DragShadowBuilder(view)
                 view.startDragAndDrop(dragData, shadow, view, 0)
                 
                 drawerLayout.closeDrawer(GravityCompat.START)
                 return@OnTouchListener true
             }
             false
        }

        findViewById<View>(R.id.cardText).apply { tag="TEXT"; setOnTouchListener(touchListener) }
        findViewById<View>(R.id.cardImage).apply { tag="IMAGE"; setOnTouchListener(touchListener) }
        
        findViewById<View>(R.id.cardButton).apply { tag="BUTTON"; setOnTouchListener(touchListener) }
        findViewById<View>(R.id.cardSlider).apply { tag="SLIDER"; setOnTouchListener(touchListener) }
        
        findViewById<View>(R.id.cardLed).apply { tag="LED"; setOnTouchListener(touchListener) }
        findViewById<View>(R.id.cardThermometer).apply { tag="THERMOMETER"; setOnTouchListener(touchListener) }

        editorCanvas.setOnDragListener { v, event ->
            if (!isEditMode) return@setOnDragListener false
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                     handleDrop(event)
                }
                DragEvent.ACTION_DRAG_LOCATION -> {
                     val localState = event.localState as? View
                     if (localState != null && localState.parent == editorCanvas) {
                         checkAlignment(event.x, event.y, localState)
                     }
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    guideOverlay.clear()
                    val localState = event.localState as? View
                    localState?.visibility = View.VISIBLE
                }
            }
            true
        }
    }
    
    private fun handleDrop(event: DragEvent) {
        val x = event.x
        val y = event.y
        val localView = event.localState as? View
        
        if (localView != null && localView.parent == editorCanvas) {
            guideOverlay.clear()
             var finalX = x - (localView.width/2)
             var finalY = y - (localView.height/2)
             
             val snapPos = calculateSnap(x, y, localView.width, localView.height, localView)
             if (snapPos != null) {
                 finalX = snapPos.x.toFloat()
                 finalY = snapPos.y.toFloat()
             }
             
             localView.x = finalX
             localView.y = finalY
             localView.visibility = View.VISIBLE
        } else {
            val tag = event.clipData?.getItemAt(0)?.text?.toString() ?: "TEXT"
            val newView = createComponentView(tag)
            
            // v16: Default Size 50x50 dp
            val density = resources.displayMetrics.density
            val sizePx = (50 * density).toInt()
            
            val params = ConstraintLayout.LayoutParams(sizePx, sizePx)
            newView.layoutParams = params
            newView.x = x - (sizePx / 2)
            newView.y = y - (sizePx / 2)
            
            // v16: Unique ID Fix
            newView.id = View.generateViewId()
            
            editorCanvas.addView(newView)
            makeDraggable(newView) 
        }
    }
    
    private fun createComponentView(tag: String): View {
        return when (tag) {
            "TEXT" -> TextView(this).apply {
                text = "Txt"
                setBackgroundColor(0xFFE1F5FE.toInt())
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
            }
            "IMAGE" -> ImageView(this).apply {
                setImageResource(android.R.drawable.ic_menu_gallery)
                setBackgroundColor(Color.LTGRAY)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            "BUTTON" -> Button(this).apply {
                text = "Btn"
                setPadding(0,0,0,0) 
            }
            "SLIDER" -> com.google.android.material.slider.Slider(this).apply {
                valueFrom = 0f
                valueTo = 100f
                value = 50f
            }
            "LED" -> View(this).apply {
                setBackgroundResource(R.drawable.shape_circle_green) // Using existing circle shape
            }
            "THERMOMETER" -> android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                progress = 50
                max = 100
                // Hack to make it look vertical? Or just horizontal for now.
                // Or use a simple View that looks like a bar.
                // For simplicity, let's use a narrow view with a background.
            }
            else -> TextView(this).apply { text = "?" }
        }
    }
    
    private fun makeDraggable(view: View) {
        view.setOnClickListener {
            if (isEditMode) {
                selectedView = view
                populateProperties(view)
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                Toast.makeText(this, "Clicked!", Toast.LENGTH_SHORT).show()
            }
        }
        
        view.setOnLongClickListener {
            if (!isEditMode) return@setOnLongClickListener false
             val data = ClipData.newPlainText("id", view.id.toString()) 
             val shadow = View.DragShadowBuilder(view)
             view.startDragAndDrop(data, shadow, view, 0)
             view.visibility = View.INVISIBLE 
             true
        }
    }

    private fun checkAlignment(x: Float, y: Float, currentView: View) {
        guideOverlay.clear()
        val threshold = snapThreshold * resources.displayMetrics.density
        
        for (i in 0 until editorCanvas.childCount) {
            val other = editorCanvas.getChildAt(i)
            if (other == currentView || other == guideOverlay) continue
            
            val otherCx = other.x + other.width/2
            val otherCy = other.y + other.height/2
            
            if (kotlin.math.abs(x - otherCx) < threshold) {
                guideOverlay.addLine(otherCx, 0f, otherCx, editorCanvas.height.toFloat())
            }
            if (kotlin.math.abs(y - otherCy) < threshold) {
                guideOverlay.addLine(0f, otherCy, editorCanvas.width.toFloat(), otherCy)
            }
        }
    }

    private fun calculateSnap(rawX: Float, rawY: Float, w: Int, h: Int, currentView: View): Point? {
        var bestX = rawX - w/2
        var bestY = rawY - h/2
        var snapped = false
        val threshold = snapThreshold * resources.displayMetrics.density
        
        for (i in 0 until editorCanvas.childCount) {
            val other = editorCanvas.getChildAt(i)
            if (other == currentView || other == guideOverlay) continue
            
            val otherCx = other.x + other.width/2
            val otherCy = other.y + other.height/2
            
            if (kotlin.math.abs(rawX - otherCx) < threshold) {
                bestX = otherCx - w/2
                snapped = true
            }
            if (kotlin.math.abs(rawY - otherCy) < threshold) {
                bestY = otherCy - h/2
                snapped = true
            }
        }
        return if (snapped) Point(bestX.toInt(), bestY.toInt()) else null
    }
    
     private fun populateProperties(view: View) {
         val params = view.layoutParams
        val density = resources.displayMetrics.density
        etPropWidth.setText((params.width / density).toInt().toString())
        etPropHeight.setText((params.height / density).toInt().toString())
        
        if (view is TextView) etPropName.setText(view.text)
        else if (view is Button) etPropName.setText(view.text)
        else if (view is SwitchMaterial) etPropName.setText(view.text)
     }
}
