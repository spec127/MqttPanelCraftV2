package com.example.mqttpanelcraft

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import android.graphics.Rect
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

class SetupActivity : BaseActivity() {

    private var selectedType: ProjectType = ProjectType.HOME
    private var projectId: String? = null
    
    // UI Elements
    private lateinit var tilName: com.google.android.material.textfield.TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var tilBroker: com.google.android.material.textfield.TextInputLayout
    private lateinit var etBroker: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var etUser: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnTest: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnImport: MaterialButton
    private lateinit var btnExport: MaterialButton

    // Data State
    private var originalProject: Project? = null // For Edit Mode
    private var pendingComponents: MutableList<com.example.mqttpanelcraft.model.ComponentData>? = null
    private var pendingCustomCode: String? = null // Store imported code
    private var pendingExportJson: String? = null // Temporary hold for export


    // Theme Cards
    private lateinit var cardHome: LinearLayout
    private lateinit var ivHome: ImageView
    private lateinit var tvHome: TextView

    private lateinit var cardWebview: LinearLayout
    private lateinit var ivWebview: ImageView
    private lateinit var tvWebview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        ProjectRepository.initialize(this)
        
        // Apply Global Theme
        com.example.mqttpanelcraft.utils.ThemeManager.applyTheme(this)
        
        setupToolbar()
        setupViews()
        
        // Initialize Views for ID
        val tilProjectId = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilProjectId)
        val etProjectId = findViewById<TextInputEditText>(R.id.etProjectId)

        // Global ID Generation Logic (Refresh Button)
        tilProjectId.setEndIconOnClickListener {
            // Confirmation only needed if editing existing project to prevent breaking links
            if (projectId != null) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_change_id_title))
                    .setMessage(getString(R.string.dialog_change_id_msg))
                    .setPositiveButton(getString(R.string.common_btn_gen_id)) { _, _ ->
                         etProjectId.setText(ProjectRepository.generateId())
                    }
                    .setNegativeButton(getString(R.string.common_btn_cancel), null)
                    .show()
            } else {
                // Create Mode: Just generate
                etProjectId.setText(ProjectRepository.generateId())
            }
        }
        
        // Copy ID
        etProjectId.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(getString(R.string.project_id), etProjectId.text.toString())
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, getString(R.string.project_msg_id_copied), android.widget.Toast.LENGTH_SHORT).show()
        }

        // Check for Edit Mode
        projectId = intent.getStringExtra("PROJECT_ID")
        if (projectId != null) {
            setupEditMode(projectId!!)
        } else {
             // Create Mode: Generate initial random ID
             etProjectId.setText(ProjectRepository.generateId())
        }

        setupWindowInsets()
    }



    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)

            // vFix: Light Status Bar for SetupActivity
            val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            if (!isDark) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                     window.insetsController?.setSystemBarsAppearance(
                         android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                         android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                     )
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                     @Suppress("DEPRECATION")
                     window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            } else {
                 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                     window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                     @Suppress("DEPRECATION")
                     window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }

            WindowInsetsCompat.CONSUMED
        }
        // com.example.mqttpanelcraft.utils.AdManager.loadBannerAd(this, findViewById(R.id.bannerAdContainer))
        com.example.mqttpanelcraft.utils.AdManager.loadRewarded(this)
    }

    private fun setupEditMode(id: String) {
        val project = ProjectRepository.getProjectById(id) ?: return
        originalProject = project

        // Show Export & Arduino Code
        btnExport.visibility = android.view.View.VISIBLE
        findViewById<android.view.View>(R.id.btnExportArduino).visibility = android.view.View.VISIBLE

        etName.setText(project.name)
        etBroker.setText(project.broker)
        etPort.setText(project.port.toString())
        etUser.setText(project.username)
        etPassword.setText(project.password)

        // Set ID (Listeners already set in onCreate)
        val etProjectId = findViewById<TextInputEditText>(R.id.etProjectId)
        etProjectId.setText(id)

        // etName is already set above
        // etBroker is already set above
        selectType(project.type)
        
        // Load Orientation
        setOrientationUI(project.orientation)
        
        btnSave.text = getString(R.string.setup_btn_update_start)
        findViewById<MaterialButton>(R.id.btnSaveProject).text = getString(R.string.setup_btn_update_only)
        supportActionBar?.title = getString(R.string.setup_title_edit)
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Fix: Tint Back Arrow for Dark Mode
        val backArrow = ContextCompat.getDrawable(this, R.drawable.ic_arrow_back)?.mutate()
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.BLACK
        backArrow?.setTint(color)
        
        supportActionBar?.setHomeAsUpIndicator(backArrow) 
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private val saveJsonLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && pendingExportJson != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingExportJson!!.toByteArray())
                }
                android.widget.Toast.makeText(this, getString(R.string.project_msg_file_saved), android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(this, getString(R.string.project_msg_file_save_failed, e.message), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val openJsonLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val json = inputStream.bufferedReader().use { it.readText() }
                    processImportedJson(json)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(this, getString(R.string.project_msg_file_read_failed, e.message), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupViews() {
        tilName = findViewById(R.id.tilProjectName)
        etName = findViewById(R.id.etProjectName)
        
        tilBroker = findViewById(R.id.tilBroker)
        etBroker = findViewById(R.id.etBroker)
        
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPassword = findViewById(R.id.etPassword)

        btnTest = findViewById(R.id.btnTestConnection)
        btnSave = findViewById(R.id.btnSaveProject)
        btnImport = findViewById(R.id.btnImportJson)
        btnExport = findViewById(R.id.btnExportJson)

        // Orientation Init (Default Sensor)
        setOrientationUI("SENSOR")

        btnImport.setOnClickListener { showImportDialog() }
        btnExport.setOnClickListener { showExportDialog() }
        
        findViewById<android.view.View>(R.id.btnExportArduino).setOnClickListener {
            // Generate temporary project object for valid state
            val tempProject = originalProject ?: run {
                 // Warn if new project not saved? Or try to construct on fly?
                 // Constructing on fly is better UX.
                 val name = etName.text.toString().ifBlank { "Untitled" }
                 val broker = etBroker.text.toString().ifBlank { "broker" }
                 
                 Project(
                    id = projectId ?: "temp_id",
                    name = name,
                    broker = broker,
                    port = etPort.text.toString().toIntOrNull() ?: 1883,
                    username = etUser.text.toString(),
                    password = etPassword.text.toString(),
                    type = selectedType,
                    components = pendingComponents ?: mutableListOf(),
                    customCode = pendingCustomCode ?: ""
                 )
            }
            com.example.mqttpanelcraft.ui.ArduinoExportManager.showExportDialog(this, tempProject)
        }
        
        // shadowed var removals
        
        cardHome = findViewById(R.id.cardHome)
        ivHome = findViewById(R.id.ivHome)
        tvHome = findViewById(R.id.tvHome)
        // ... (lines 242-263 match original) ...
        cardWebview = findViewById(R.id.cardWebview)
        ivWebview = findViewById(R.id.ivWebview)
        tvWebview = findViewById(R.id.tvWebview)

        // Theme Selection
        cardHome.setOnClickListener { selectType(ProjectType.HOME) }
        cardWebview.setOnClickListener { selectType(ProjectType.WEBVIEW) }

        // Test Connection (Mock)
        btnTest.setOnClickListener {
           testConnection()
        }

        // Save
        btnSave.setOnClickListener {
            saveProject()
        }

        // Real-time Validation on Focus Loss
        etName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val name = etName.text.toString()
                if (name.isBlank()) {
                    tilName.error = getString(R.string.setup_error_name_required)
                    tilName.isErrorEnabled = true
                } else if (!name.matches(Regex("^[A-Za-z0-9_]+$"))) {
                    tilName.error = getString(R.string.setup_error_only_letters)
                    tilName.isErrorEnabled = true
                } else if (ProjectRepository.isProjectNameTaken(name, projectId)) {
                    tilName.error = getString(R.string.setup_error_name_exists)
                    tilName.isErrorEnabled = true
                } else {
                    tilName.isErrorEnabled = false
                    tilName.error = null
                }
            }
        }
        
        etBroker.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                 if (etBroker.text.toString().isBlank()) {
                    tilBroker.error = getString(R.string.setup_error_broker_required)
                    tilBroker.isErrorEnabled = true
                } else {
                    tilBroker.isErrorEnabled = false
                    tilBroker.error = null
                }
            }
        }
    }
    
    // ... (Lines 296-528 Omitted for brevity, assume unchanged logic between) ...

    /** 
     * Need to target saveProject construction of Project object. 
     * Since REPLACE tool requires contiguous block, I will replace the Project construction part specifically.
     * Wait, I need a larger chunk or targeted replacement. 
     * Let's look at line 529 area.
     */
     

    
    private fun showImportDialog() {
        val context = this
        val builder = AlertDialog.Builder(context)
        builder.setTitle(getString(R.string.dialog_import_title))

        val input = android.widget.EditText(context)
        input.hint = getString(R.string.hint_paste_json)
        input.isSingleLine = false
        input.minLines = 10
        input.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        input.setPadding(40, 40, 40, 40)
        input.textSize = 12f

        builder.setView(input)

        builder.setPositiveButton(getString(R.string.common_btn_load_text)) { _, _ ->
            val json = input.text.toString()
            if (json.isNotBlank()) {
                processImportedJson(json)
            }
        }

        builder.setNeutralButton(getString(R.string.common_btn_load_file)) { _, _ ->
             // MIME types: json, text, or any
             openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        builder.setNegativeButton(getString(R.string.common_btn_cancel), null)

        val dialog = builder.create()
        // Fix: Set input mode on the dialog's window, not via view
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    private fun processImportedJson(json: String) {
        val imported = ProjectRepository.parseProjectJson(json)
        if (imported != null) {
            // Populate Fields with "Copy" suffix
            var newName = imported.name + "_copy"
            // Simple check to avoid loop, though repository check later handles strict uniqueness
            if (ProjectRepository.isProjectNameTaken(newName)) {
                 newName += "_" + System.currentTimeMillis() % 1000
            }
            etName.setText(newName)

            etBroker.setText(imported.broker)
            etPort.setText(imported.port.toString())
            etUser.setText(imported.username)
            // Password usually ignored
            selectType(imported.type)

            // Store Structure
            pendingComponents = imported.components

            // vFix: Sanitize Imported IDs to prevent collisions and crashes
            // Resetting to NO_ID causes restoreProjectState to generate fresh unique IDs.
            pendingComponents?.forEach {
                it.id = android.view.View.NO_ID
            }

            pendingCustomCode = imported.customCode

            android.widget.Toast.makeText(this, getString(R.string.project_msg_components_loaded, imported.components.size), android.widget.Toast.LENGTH_SHORT).show()
        } else {
             android.widget.Toast.makeText(this, "Invalid JSON", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExportDialog() {
        if (originalProject == null) return

        val json = ProjectRepository.exportProjectToJson(originalProject!!)
        val context = this
        val builder = AlertDialog.Builder(context)
        builder.setTitle(getString(R.string.dialog_export_title))

        val input = android.widget.EditText(context)
        input.setText(json)
        input.isSingleLine = false
        input.minLines = 10
        input.maxLines = 15
        input.isFocusable = false // Read only-ish
        input.setPadding(40, 40, 40, 40)
        input.textSize = 10f
        input.typeface = android.graphics.Typeface.MONOSPACE

        builder.setView(input)

        builder.setPositiveButton(getString(R.string.common_btn_copy)) { _, _ ->
             val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
             val clip = android.content.ClipData.newPlainText("Config JSON", json)
             clipboard.setPrimaryClip(clip)
              android.widget.Toast.makeText(context, getString(R.string.common_msg_copied_clipboard), android.widget.Toast.LENGTH_SHORT).show()
        }

        builder.setNeutralButton(getString(R.string.common_btn_save)) { _, _ ->
             pendingExportJson = json
             saveJsonLauncher.launch("${originalProject?.name ?: "config"}.json")
        }

        builder.setNegativeButton(getString(R.string.common_btn_close), null)
        builder.show()
    }

    private fun testConnection() {
        val broker = etBroker.text.toString()
        val portStr = etPort.text.toString()
        val user = etUser.text.toString()
        val pass = etPassword.text.toString()

        if (broker.isBlank()) {
            tilBroker.error = getString(R.string.setup_error_broker_required)
            tilBroker.isErrorEnabled = true
            return
        } else {
            tilBroker.isErrorEnabled = false
        }

        val port = portStr.toIntOrNull() ?: 1883
        val uri = "tcp://$broker:$port"

        btnTest.isEnabled = false
        btnTest.text = "Connecting..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val clientId = "TestClient_" + System.currentTimeMillis()
                val client = MqttClient(uri, clientId, MemoryPersistence())
                val options = MqttConnectOptions()
                options.isCleanSession = true
                options.connectionTimeout = 30
                options.keepAliveInterval = 60

                if (user.isNotEmpty()) {
                    options.userName = user
                    options.password = pass.toCharArray()
                }

                client.connect(options)

                withContext(Dispatchers.Main) {
                    btnTest.text = "Connected!"
                    btnTest.isEnabled = true
                    btnTest.setTextColor(Color.GREEN)
                    btnTest.strokeColor = ColorStateList.valueOf(Color.GREEN)

                    if (client.isConnected) {
                        try { client.disconnect() } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        btnTest.text = "Test Connection"
                        btnTest.isEnabled = true
                        btnTest.setTextColor(Color.RED) // Or default
                        btnTest.strokeColor = ColorStateList.valueOf(Color.RED)

                        AlertDialog.Builder(this@SetupActivity)
                            .setTitle("Connection Failed")
                            .setMessage(e.message ?: "Unknown Error")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }


    private fun saveProject() {
        val name = etName.text.toString()
        val broker = etBroker.text.toString()
        val portStr = etPort.text.toString()
        val user = etUser.text.toString()
        val pass = etPassword.text.toString()

        if (name.isBlank()) {
            tilName.error = getString(R.string.setup_error_name_required)
            tilName.isErrorEnabled = true
            return
        }

        // Regex Validation
        if (!name.matches(Regex("^[A-Za-z0-9_]+$"))) {
            tilName.error = getString(R.string.setup_error_only_letters)
            tilName.isErrorEnabled = true
            return
        }

        // Duplicate Name Check
        if (ProjectRepository.isProjectNameTaken(name, projectId)) {
            tilName.error = getString(R.string.setup_error_name_exists)
            tilName.isErrorEnabled = true
            return
        }
        
        tilName.isErrorEnabled = false

        if (broker.isBlank()) {
            tilBroker.error = getString(R.string.setup_error_broker_required)
            tilBroker.isErrorEnabled = true
            return
        }
        
        tilBroker.isErrorEnabled = false

        val port = portStr.toIntOrNull() ?: 1883

        // Determine ID
        var finalId = projectId ?: ProjectRepository.generateId()

        // Check if ID was changed in UI (Only in Edit Mode)
        if (projectId != null) {
             val etProjectId = findViewById<TextInputEditText>(R.id.etProjectId)
             val currentUiId = etProjectId.text.toString()
             if (currentUiId.isNotEmpty() && currentUiId != projectId) {
                 finalId = currentUiId
             }
        }
        
        // ...

        // Determine Components & Custom Code
        val finalComponents = pendingComponents
            ?: originalProject?.components?.toMutableList() // Copy to avoid mutation issues
            ?: mutableListOf()

        val finalCustomCode = pendingCustomCode
            ?: originalProject?.customCode
            ?: ""
            
        val finalOrientation = getSelectedOrientation()

        val newProject = Project(
            id = finalId,
            name = name,
            broker = broker,
            port = port,
            username = user,
            password = pass,
            type = selectedType,
            isConnected = false,
            components = finalComponents,
            customCode = finalCustomCode,
            orientation = finalOrientation
        )

        // Unified Flow: Always Show Rewarded (unless disabled)
        val targetProjectId = newProject.id
        var isRewardEarned = false

        if (com.example.mqttpanelcraft.utils.PremiumManager.isPremium(this)) {
             // Skip Ads
             saveAndFinish(newProject, targetProjectId)
             return
        }

        if (com.example.mqttpanelcraft.utils.AdManager.isRewardedReady()) {
            com.example.mqttpanelcraft.utils.AdManager.showRewarded(this,
                onReward = {
                    isRewardEarned = true
                },
                onClosed = {
                    if (isRewardEarned) {
                        saveAndFinish(newProject, targetProjectId)
                    } else {
                        android.widget.Toast.makeText(this, "You must watch the full ad to save/update!", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            )
        } else {
             // Fallback: Show Placeholder UI (Non-Ad) and Proceed
             // User Request: If ad fails, show internal placeholder instead of just waiting
             val dialogView = layoutInflater.inflate(R.layout.layout_ad_placeholder_banner, null)
             val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
                 .setTitle("Saving Project")
                 .setView(dialogView)
                 .setCancelable(false)
                 .setNegativeButton("Cancel", null) // Just dismiss -> No Save
                 .setPositiveButton("Continue (30)") { _, _ ->
                      saveAndFinish(newProject, targetProjectId)
                 }

             val dialog = dialogBuilder.create()
             dialog.show()
             
             // Setup Countdown
             val btnContinue = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
             btnContinue.isEnabled = false
             btnContinue.setTextColor(Color.GRAY)
             
             object : android.os.CountDownTimer(30000, 1000) {
                 override fun onTick(millisUntilFinished: Long) {
                     if (dialog.isShowing) {
                        btnContinue.text = "Continue (${millisUntilFinished / 1000})"
                     } else {
                        cancel()
                     }
                 }
                 override fun onFinish() {
                     if (dialog.isShowing) {
                        btnContinue.text = "Continue"
                        btnContinue.isEnabled = true
                        btnContinue.setTextColor(ContextCompat.getColor(this@SetupActivity, R.color.primary))
                     }
                 }
             }.start()
             
             // Background Re-load for next time
             com.example.mqttpanelcraft.utils.AdManager.loadRewarded(this)
        }
    }
    
    // Helper to get Orientation String
    private fun getSelectedOrientation(): String {
        val rg = findViewById<android.widget.RadioGroup>(R.id.rgOrientation)
        return when (rg.checkedRadioButtonId) {
            R.id.rbPortrait -> "PORTRAIT"
            R.id.rbLandscape -> "LANDSCAPE"
            else -> "SENSOR"
        }
    }
    
    // Helper to set UI
    private fun setOrientationUI(value: String) {
        val rg = findViewById<android.widget.RadioGroup>(R.id.rgOrientation)
        when (value) {
            "PORTRAIT" -> rg.check(R.id.rbPortrait)
            "LANDSCAPE" -> rg.check(R.id.rbLandscape)
            else -> rg.check(R.id.rbSensor)
        }
    }

    private fun saveAndFinish(newProject: Project, targetProjectId: String) {
        if (projectId != null) {
            if (newProject.id != projectId) {
                // ID Changed: Delete old, Add new
                ProjectRepository.deleteProject(projectId!!)
                ProjectRepository.addProject(newProject)

                 // Return result to Caller
                val resultIntent = android.content.Intent()
                resultIntent.putExtra("NEW_ID", newProject.id)
                setResult(RESULT_OK, resultIntent)
            } else {
                ProjectRepository.updateProject(newProject)
                setResult(RESULT_OK)
            }
        } else {
            ProjectRepository.addProject(newProject)
            setResult(RESULT_OK)
        }

        // If we want to open project immediately (optional, but standard flow usually returns to dashboard)
        // XML has "Save and Start" implies opening.
        val returnToHome = intent.getBooleanExtra("RETURN_TO_HOME", false)

        if (returnToHome) {
             finish()
        } else {
             val targetActivity = if (newProject.type == ProjectType.WEBVIEW) {
                 WebViewActivity::class.java
             } else {
                 ProjectViewActivity::class.java
             }
             val intent = android.content.Intent(this, targetActivity)
             intent.putExtra("PROJECT_ID", targetProjectId)
             
             // Fix: Clear Top to prevent duplicate ProjectViewActivity in stack
             intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
             
             startActivity(intent)
             finish()
        }
    }

    private fun selectType(type: ProjectType) {
        selectedType = type
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val greyColor = Color.parseColor("#757575")

        // 1. Reset all to unselected state
        cardHome.setBackgroundResource(R.drawable.bg_card_unselected)
        ivHome.setColorFilter(greyColor)
        tvHome.setTextColor(greyColor)

        cardWebview.setBackgroundResource(R.drawable.bg_card_unselected)
        ivWebview.setColorFilter(greyColor)
        tvWebview.setTextColor(greyColor)

        // 2. Highlight selected
        val tvDesc = findViewById<TextView>(R.id.tvThemeDescription)
        
        when (type) {
            ProjectType.HOME -> {
                cardHome.setBackgroundResource(R.drawable.bg_card_selected)
                ivHome.setColorFilter(primaryColor)
                tvHome.setTextColor(primaryColor)
                tvDesc.text = getString(R.string.setup_desc_panel)
            }
            ProjectType.WEBVIEW -> {
                cardWebview.setBackgroundResource(R.drawable.bg_card_selected)
                ivWebview.setColorFilter(primaryColor)
                tvWebview.setTextColor(primaryColor)
                tvDesc.text = getString(R.string.setup_desc_webview)
            }
            else -> {} // Handle OTHER or Legacy FACTORY (No UI)
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
        return super.dispatchTouchEvent(ev)
    }
}

