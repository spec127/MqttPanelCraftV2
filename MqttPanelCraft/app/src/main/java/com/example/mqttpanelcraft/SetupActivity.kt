package com.example.mqttpanelcraft

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.ui.CodeExportDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

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
    private lateinit var cbKeepMqttInBackground: com.google.android.material.checkbox.MaterialCheckBox

    // Data State
    private var originalProject: Project? = null // For Edit Mode
    private var pendingComponents: MutableList<com.example.mqttpanelcraft.model.ComponentData>? =
            null
    private var pendingCustomCode: String? = null // Store imported code
    private var pendingImportedProject: Project? = null
    private var pendingExportJson: String? = null // Temporary hold for export

    // Theme Cards
    private lateinit var cardHome: LinearLayout
    private lateinit var ivHome: ImageView
    private lateinit var tvHome: TextView

    private lateinit var cardWebview: LinearLayout
    private lateinit var ivWebview: ImageView
    private lateinit var tvWebview: TextView
    private lateinit var containerProjectType: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        ProjectRepository.initialize(this)

        // Apply Global Theme
        com.example.mqttpanelcraft.utils.ThemeManager.applyTheme(this)

        setupToolbar()
        setupViews()

        // Initialize Views for ID
        val tilProjectId =
                findViewById<com.google.android.material.textfield.TextInputLayout>(
                        R.id.tilProjectId
                )
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
                            tilProjectId.error = null
                            tilProjectId.isErrorEnabled = false
                        }
                        .setNegativeButton(getString(R.string.common_btn_cancel), null)
                        .show()
            } else {
                // Create Mode: Just generate
                etProjectId.setText(ProjectRepository.generateId())
                tilProjectId.error = null
                tilProjectId.isErrorEnabled = false
            }
        }

        // Copy ID
        etProjectId.setOnClickListener {
            val clipboard =
                    getSystemService(android.content.Context.CLIPBOARD_SERVICE) as
                            android.content.ClipboardManager
            val clip =
                    android.content.ClipData.newPlainText(
                            getString(R.string.project_id),
                            etProjectId.text.toString()
                    )
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(
                            this,
                            getString(R.string.project_msg_id_copied),
                            android.widget.Toast.LENGTH_SHORT
                    )
                    .show()
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
        // vFix: Apply insets to Header Layout ONLY to allow gradient background to flow behind
        // status bar
        val headerLayout = findViewById<LinearLayout>(R.id.headerLayout)

        ViewCompat.setOnApplyWindowInsetsListener(headerLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Only add top padding to header
            view.setPadding(
                    view.paddingLeft,
                    bars.top + 48.dpToPx(), // Original 48dp + Status Bar
                    view.paddingRight,
                    view.paddingBottom
            )
            WindowInsetsCompat.CONSUMED
        }

        // Apply bottom padding to ScrollView or root to avoid Nav Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets
            ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // vFix: Light Status Bar for SetupActivity
        val isDark =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
        // Ensure Transparent Status Bar
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        if (!isDark) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility or
                                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                        0,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                        window.decorView.systemUiVisibility and
                                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }

        com.example.mqttpanelcraft.utils.AdManager.loadRewarded(this)
        com.example.mqttpanelcraft.utils.AdManager.loadInterstitial(this)
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun setupEditMode(id: String) {
        val project = ProjectRepository.getProjectById(id) ?: return
        originalProject = project

        // Show Export & Arduino Code
        btnExport.visibility = android.view.View.VISIBLE
        findViewById<android.view.View>(R.id.btnExportArduino).visibility =
                android.view.View.VISIBLE

        etName.setText(project.name)
        etBroker.setText(project.broker)
        etPort.setText(project.port.toString())
        etUser.setText(project.username)
        etPassword.setText(project.password)
        cbKeepMqttInBackground.isChecked = project.keepMqttInBackground

        // Set ID (Listeners already set in onCreate)
        val etProjectId = findViewById<TextInputEditText>(R.id.etProjectId)
        etProjectId.setText(id)

        // etName is already set above
        // etBroker is already set above
        selectType(project.type)

        // Lock Project Type: Hide UI
        containerProjectType.visibility = View.GONE

        // Load Orientation
        setOrientationUI(project.orientation)

        btnSave.text = getString(R.string.setup_btn_update_start)
        findViewById<MaterialButton>(R.id.btnSaveProject).text =
                getString(R.string.setup_btn_update_only)
        findViewById<TextView>(R.id.tvPageTitle).text = getString(R.string.setup_title_edit)
    }

    private fun setupToolbar() {
        val btnBack = findViewById<android.view.View>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val tvTitle = findViewById<TextView>(R.id.tvPageTitle)
        if (projectId != null) {
            tvTitle.text = getString(R.string.setup_title_edit)
        } else {
            tvTitle.text = getString(R.string.setup_title_new)
        }
    }

    // Removed onSupportNavigateUp as we use direct finish() now
    // override fun onSupportNavigateUp(): Boolean ...

    private val saveJsonLauncher =
            registerForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.CreateDocument(
                            "application/json"
                    )
            ) { uri ->
                if (uri != null && pendingExportJson != null) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(pendingExportJson!!.toByteArray())
                        }
                        android.widget.Toast.makeText(
                                        this,
                                        getString(R.string.project_msg_file_saved),
                                        android.widget.Toast.LENGTH_SHORT
                                )
                                .show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        android.widget.Toast.makeText(
                                        this,
                                        getString(R.string.project_msg_file_save_failed, e.message),
                                        android.widget.Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                }
            }

    private val openJsonLauncher =
            registerForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            val json = inputStream.bufferedReader().use { it.readText() }
                            processImportedJson(json)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        android.widget.Toast.makeText(
                                        this,
                                        getString(R.string.project_msg_file_read_failed, e.message),
                                        android.widget.Toast.LENGTH_SHORT
                                )
                                .show()
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
        cbKeepMqttInBackground = findViewById(R.id.cbKeepMqttInBackground)
        findViewById<View>(R.id.itemKeepMqttInBackground).setOnClickListener {
            cbKeepMqttInBackground.performClick()
        }

        // Orientation Init (Default Sensor)
        setOrientationUI("SENSOR")

        btnImport.setOnClickListener { showImportDialog() }

        btnExport.setOnClickListener {
            if (com.example.mqttpanelcraft.utils.PremiumManager.isPremium(this)) {
                showExportDialog()
            } else {
                com.example.mqttpanelcraft.utils.AdManager.showInterstitial(this) {
                    showExportDialog()
                }
            }
        }

        findViewById<android.view.View>(R.id.btnExportArduino).setOnClickListener {
            val tempProject = buildExportProjectFromForm()
            if (isExportFormUnsaved(tempProject)) {
                android.widget.Toast.makeText(
                                this,
                                R.string.setup_msg_arduino_export_unsaved,
                                android.widget.Toast.LENGTH_LONG
                        )
                        .show()
            }

            if (com.example.mqttpanelcraft.utils.PremiumManager.isPremium(this)) {
                com.example.mqttpanelcraft.ui.ArduinoExportManager.showExportDialog(
                        this,
                        tempProject
                )
            } else {
                // Check if Ad is Ready
                if (com.example.mqttpanelcraft.utils.AdManager.isRewardedReady()) {
                    var isRewardEarned = false
                    com.example.mqttpanelcraft.utils.AdManager.showRewarded(
                            this,
                            onReward = { isRewardEarned = true },
                            onClosed = {
                                if (isRewardEarned) {
                                    com.example.mqttpanelcraft.ui.ArduinoExportManager
                                            .showExportDialog(this, tempProject)
                                } else {
                                    android.widget.Toast.makeText(
                                                    this,
                                                    R.string.export_ad_required,
                                                    android.widget.Toast.LENGTH_LONG
                                            )
                                            .show() // Or custom string
                                }
                            }
                    )
                } else {
                    android.widget.Toast.makeText(
                                    this,
                                    getString(R.string.export_ad_loading),
                                    android.widget.Toast.LENGTH_SHORT
                            )
                            .show()
                    com.example.mqttpanelcraft.utils.AdManager.loadRewarded(this)
                }
            }
        }

        // shadowed var removals

        cardHome = findViewById(R.id.cardHome)
        ivHome = findViewById(R.id.ivHome)
        tvHome = findViewById(R.id.tvHome)
        // ... (lines 242-263 match original) ...
        cardWebview = findViewById(R.id.cardWebview)
        ivWebview = findViewById(R.id.ivWebview)
        tvWebview = findViewById(R.id.tvWebview)
        containerProjectType = findViewById(R.id.containerProjectType)

        // Theme Selection
        cardHome.setOnClickListener { selectType(ProjectType.HOME) }
        cardWebview.setOnClickListener { selectType(ProjectType.WEBVIEW) }

        // Test Connection (Mock)
        btnTest.setOnClickListener { testConnection() }

        // Save
        btnSave.setOnClickListener { saveProject() }

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
     * Need to target saveProject construction of Project object. Since REPLACE tool requires
     * contiguous block, I will replace the Project construction part specifically. Wait, I need a
     * larger chunk or targeted replacement. Let's look at line 529 area.
     */
    private fun showImportDialog() {
        val dialog =
                CodeExportDialogFragment.newInstance(
                        code = "",
                        mode = CodeExportDialogFragment.Mode.IMPORT_JSON,
                        onImport = { result: String ->
                            if (result == "action:OPEN_FILE") {
                                openJsonLauncher.launch(
                                        arrayOf("application/json", "text/plain", "*/*")
                                )
                            } else {
                                processImportedJson(result)
                            }
                        }
                )
        dialog.show(supportFragmentManager, "ImportJson")
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

            // Keep an imported project internally coherent before it ever reaches the canvas.
            val normalized = com.example.mqttpanelcraft.data.ProjectImportNormalizer.normalize(imported)
            pendingComponents = normalized.project.components
            pendingCustomCode = normalized.project.customCode
            pendingImportedProject = normalized.project
            cbKeepMqttInBackground.isChecked = imported.keepMqttInBackground
            setOrientationUI(imported.orientation)

            if (normalized.repairedIds > 0 || normalized.removedLinkedReferences > 0) {
                AlertDialog.Builder(this)
                        .setMessage(getString(R.string.project_msg_import_repaired,
                                normalized.repairedIds, normalized.removedLinkedReferences))
                        .setPositiveButton(R.string.common_btn_ok, null)
                        .show()
            }

            android.widget.Toast.makeText(
                            this,
                            getString(
                                    R.string.project_msg_components_loaded,
                                    normalized.project.components.size
                            ),
                            android.widget.Toast.LENGTH_SHORT
                    )
                    .show()
        } else {
            android.widget.Toast.makeText(
                    this,
                    R.string.error_invalid_json,
                    android.widget.Toast.LENGTH_SHORT
            )
                    .show()
        }
    }

    private fun showExportDialog() {
        if (originalProject == null) return
        val json = ProjectRepository.exportProjectToJson(originalProject!!)

        val dialog =
                CodeExportDialogFragment.newInstance(
                        code = json,
                        mode = CodeExportDialogFragment.Mode.EXPORT_JSON
                )
        dialog.show(supportFragmentManager, "ExportJson")
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
        btnTest.setText(R.string.setup_connecting)

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
                    btnTest.setText(R.string.setup_connected)
                    btnTest.isEnabled = true
                    btnTest.setTextColor(Color.GREEN)
                    btnTest.strokeColor = ColorStateList.valueOf(Color.GREEN)

                    if (client.isConnected) {
                        try {
                            client.disconnect()
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        btnTest.setText(R.string.setup_test_connection)
                        btnTest.isEnabled = true
                        btnTest.setTextColor(Color.RED) // Or default
                        btnTest.strokeColor = ColorStateList.valueOf(Color.RED)

                        AlertDialog.Builder(this@SetupActivity)
                                .setTitle(R.string.setup_connection_failed)
                                .setMessage(e.message ?: getString(R.string.common_unknown_error))
                                .setPositiveButton(R.string.common_btn_ok, null)
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

        // Persist the ID shown by the generator in both create and edit mode.
        val etProjectId = findViewById<TextInputEditText>(R.id.etProjectId)
        val finalId = com.example.mqttpanelcraft.data.resolveProjectId(
                etProjectId.text.toString(), projectId, ProjectRepository::generateId)
        etProjectId.setText(finalId)
        val tilProjectId =
                findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilProjectId)
        if (finalId != projectId && ProjectRepository.getProjectById(finalId) != null) {
            tilProjectId.error = getString(R.string.setup_error_id_exists)
            tilProjectId.isErrorEnabled = true
            return
        }
        tilProjectId.error = null
        tilProjectId.isErrorEnabled = false

        // ...

        // Determine Components & Custom Code
        // Editing or abandoning this form must not mutate the repository's original components.
        val finalComponents = (pendingComponents ?: originalProject?.components)
                ?.map { it.deepCopy() }?.toMutableList() ?: mutableListOf()

        val finalCustomCode = pendingCustomCode ?: originalProject?.customCode ?: ""

        val finalOrientation = getSelectedOrientation()

        // Update Component Topics if ID changed
        // Smart Topic Sync 3.0: Split & Match ID
        val topicSourceProject = originalProject
        if (topicSourceProject != null && pendingImportedProject == null) {
            if (topicSourceProject.id.isNotEmpty()) {
                var updatedCount = 0

                finalComponents.forEach { component ->
                    val rewritten = com.example.mqttpanelcraft.utils.TopicHelper.rewriteGeneratedProjectTopic(
                            component.topicConfig, topicSourceProject, name, finalId)
                    if (rewritten != component.topicConfig) {
                        component.topicConfig = rewritten
                        updatedCount++
                    }
                }
                if (updatedCount > 0) {
                    android.widget.Toast.makeText(
                                    this,
                                    getString(R.string.project_topics_updated, updatedCount),
                                    android.widget.Toast.LENGTH_SHORT
                            )
                            .show()
                }
            }
        }

        val newProject =
                Project(
                        id = finalId,
                        name = name,
                        broker = broker,
                        port = port,
                        username = user,
                        password = pass,
                        clientId = (originalProject ?: pendingImportedProject)?.clientId ?: "",
                        type = selectedType,
                        components = finalComponents,
                        customCode = finalCustomCode,
                        orientation = finalOrientation,
                        createdAt = (originalProject ?: pendingImportedProject)?.createdAt ?: System.currentTimeMillis(),
                        lastOpenedAt = (originalProject ?: pendingImportedProject)?.lastOpenedAt ?: System.currentTimeMillis(),
                        keepMqttInBackground = cbKeepMqttInBackground.isChecked
                )

        // Unified Flow: Always Show Rewarded (unless disabled)
        val targetProjectId = newProject.id
        var isRewardEarned = false

        if (com.example.mqttpanelcraft.utils.PremiumManager.isPremium(this)) {
            // Skip Ads
            saveAndFinish(newProject, targetProjectId)
            return
        }

        // New Feature: First Project is Free (No Ad)
        // If creating new project (projectId == null) AND repository is empty
        if (projectId == null && ProjectRepository.getAllProjects().isEmpty()) {
            // First Project Bonus: Ad Skipped silently
            saveAndFinish(newProject, targetProjectId)
            return
        }

        if (com.example.mqttpanelcraft.utils.AdManager.isRewardedReady()) {
            com.example.mqttpanelcraft.utils.AdManager.showRewarded(
                    this,
                    onReward = { isRewardEarned = true },
                    onClosed = {
                        if (isRewardEarned) {
                            saveAndFinish(newProject, targetProjectId)
                        } else {
                            android.widget.Toast.makeText(
                                            this,
                                            getString(R.string.project_save_ad_required),
                                            android.widget.Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                    }
            )
        } else {
            // Fallback: Show Placeholder UI (Non-Ad) and Proceed
            // User Request: If ad fails, show internal placeholder instead of just waiting
            val dialogView = layoutInflater.inflate(R.layout.layout_ad_placeholder_banner, null)
            val dialogBuilder =
                    androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle(R.string.setup_saving_project)
                            .setView(dialogView)
                            .setCancelable(false)
                            .setNegativeButton(R.string.common_btn_cancel, null)
                            .setPositiveButton(getString(R.string.setup_continue_countdown, 30)) { _, _ ->
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
                                btnContinue.text =
                                        getString(
                                                R.string.setup_continue_countdown,
                                                millisUntilFinished / 1000
                                        )
                            } else {
                                cancel()
                            }
                        }
                        override fun onFinish() {
                            if (dialog.isShowing) {
                                btnContinue.setText(R.string.setup_continue)
                                btnContinue.isEnabled = true
                                btnContinue.setTextColor(
                                        ContextCompat.getColor(this@SetupActivity, R.color.primary)
                                )
                            }
                        }
                    }
                    .start()

            // Background Re-load for next time
            com.example.mqttpanelcraft.utils.AdManager.loadRewarded(this)
        }
    }

    // Helper to get Orientation String
    private fun buildExportProjectFromForm(): Project {
        val etProjectIdField = findViewById<TextInputEditText>(R.id.etProjectId)
        val formId =
                etProjectIdField.text.toString().trim().ifBlank {
                    projectId ?: originalProject?.id ?: "temp_id"
                }
        val name = etName.text.toString().ifBlank { getString(R.string.project_default_untitled) }
        val components =
                (pendingComponents ?: originalProject?.components)
                        ?.map { it.deepCopy() }
                        ?.toMutableList() ?: mutableListOf()
        val source = originalProject
        if (source != null && pendingImportedProject == null) {
            components.forEach { component ->
                component.topicConfig =
                        com.example.mqttpanelcraft.utils.TopicHelper.rewriteGeneratedProjectTopic(
                                component.topicConfig,
                                source,
                                name,
                                formId
                        )
            }
        }
        return Project(
                id = formId,
                name = name,
                broker = etBroker.text.toString().ifBlank { "broker" },
                port = etPort.text.toString().toIntOrNull() ?: 1883,
                username = etUser.text.toString(),
                password = etPassword.text.toString(),
                clientId = (originalProject ?: pendingImportedProject)?.clientId ?: "",
                type = selectedType,
                components = components,
                customCode = pendingCustomCode ?: originalProject?.customCode ?: "",
                orientation = getSelectedOrientation(),
                createdAt =
                        (originalProject ?: pendingImportedProject)?.createdAt
                                ?: System.currentTimeMillis(),
                lastOpenedAt =
                        (originalProject ?: pendingImportedProject)?.lastOpenedAt
                                ?: System.currentTimeMillis(),
                keepMqttInBackground = cbKeepMqttInBackground.isChecked
        )
    }

    private fun isExportFormUnsaved(exportProject: Project): Boolean {
        val saved = originalProject ?: return true
        return saved.id != exportProject.id ||
                saved.name != exportProject.name ||
                saved.broker != exportProject.broker ||
                saved.port != exportProject.port ||
                saved.username != exportProject.username ||
                saved.password != exportProject.password ||
                saved.keepMqttInBackground != exportProject.keepMqttInBackground ||
                saved.type != exportProject.type || saved.orientation != exportProject.orientation ||
                saved.customCode != exportProject.customCode || saved.components != exportProject.components
    }

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
        val returnToHome = intent.getBooleanExtra("RETURN_TO_HOME", false)
        val activeProjectId = com.example.mqttpanelcraft.MqttRepository.activeProjectId
        if (projectId != null) {
            if (newProject.id != projectId) {
                if (activeProjectId == projectId) {
                    com.example.mqttpanelcraft.mqtt.MqttSessionClient.stop(this)
                }
                // ID Changed: Delete old, Add new
                ProjectRepository.deleteProject(projectId!!)
                ProjectRepository.addProject(newProject)

                // Return result to Caller
                val resultIntent = android.content.Intent()
                resultIntent.putExtra("NEW_ID", newProject.id)
                setResult(RESULT_OK, resultIntent)
            } else {
                ProjectRepository.updateProject(newProject)
                if (activeProjectId == newProject.id) {
                    com.example.mqttpanelcraft.mqtt.MqttSessionClient.refresh(this, newProject.id)
                }
                setResult(RESULT_OK)
            }
        } else {
            ProjectRepository.addProject(newProject)
            setResult(RESULT_OK)
        }

        // If we want to open project immediately (optional, but standard flow usually returns to
        // dashboard)
        // XML has "Save and Start" implies opening.
        if (returnToHome) {
            finish()
        } else {
            // If we are editing (projectId != null), just finish and let the caller handle reload.
            // If we are creating (projectId == null), open the new project.
            if (projectId != null) {
                finish()
            } else {
                val targetActivity =
                        if (newProject.type == ProjectType.WEBVIEW) {
                            WebViewActivity::class.java
                        } else {
                            ProjectViewActivity::class.java
                        }
                val intent = android.content.Intent(this, targetActivity)
                intent.putExtra("PROJECT_ID", targetProjectId)

                // Fix: Clear Top to prevent duplicate ProjectViewActivity in stack
                intent.flags =
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP

                startActivity(intent)
                finish()
            }
        }
    }

    private fun selectType(type: ProjectType) {
        selectedType = type
        val primaryColor = ContextCompat.getColor(this, R.color.icy_primary)
        val greyColor = Color.parseColor("#94A3B8") // Slate 400

        if (type == ProjectType.HOME) {
            cardHome.setBackgroundResource(R.drawable.bg_card_selected)
            ivHome.setColorFilter(getColor(R.color.setup_accent_color)) // Purple
            tvHome.setTextColor(getColor(R.color.setup_accent_color)) // Purple

            cardWebview.setBackgroundResource(R.drawable.bg_card_unselected)
            ivWebview.setColorFilter(Color.parseColor("#94A3B8"))
            tvWebview.setTextColor(Color.parseColor("#94A3B8"))
            findViewById<TextView>(R.id.tvThemeDescription).text =
                    getString(R.string.setup_desc_panel)
        } else {
            cardHome.setBackgroundResource(R.drawable.bg_card_unselected)
            ivHome.setColorFilter(Color.parseColor("#94A3B8"))
            tvHome.setTextColor(Color.parseColor("#94A3B8"))

            cardWebview.setBackgroundResource(R.drawable.bg_card_selected)
            ivWebview.setColorFilter(getColor(R.color.setup_accent_color)) // Purple
            tvWebview.setTextColor(getColor(R.color.setup_accent_color)) // Purple
            findViewById<TextView>(R.id.tvThemeDescription).text =
                    getString(R.string.setup_desc_webview)
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
