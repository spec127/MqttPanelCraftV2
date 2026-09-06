package com.example.mqttpanelcraft

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.mqtt.MqttSessionClient
import com.example.mqttpanelcraft.utils.HtmlTemplates
import com.example.mqttpanelcraft.utils.TopicHelper

class WebViewActivity : BaseActivity(), MqttRepository.MessageListener {

    private lateinit var webView: WebView
    private lateinit var codeEditor: EditText
    private var projectId: String? = null
    private var project: Project? = null
    private var mqttListenerRegistered = false

    @SuppressLint("SetJavaScriptEnabled", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure Data Init
        ProjectRepository.initialize(applicationContext)

        setContentView(R.layout.activity_webview)

        // Root Coordinator
        // Note: We do NOT add manual padding here, unlike before.
        // CoordinatorLayout manages insets (or stays behind status bar) naturally.

        val windowInsetsController =
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        val isNightMode =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
        windowInsetsController.isAppearanceLightStatusBars = !isNightMode

        // Toolbar Setup
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val backArrow =
                androidx.core.content.ContextCompat.getDrawable(
                                this,
                                R.drawable.ic_action_back_large
                        )
                        ?.mutate()
        backArrow?.setTint(androidx.core.content.ContextCompat.getColor(this, R.color.toolbar_text))
        supportActionBar?.setHomeAsUpIndicator(backArrow) // Ensure you have this or use default
        toolbar.setNavigationOnClickListener { finish() }

        val tvTitle = findViewById<android.widget.TextView>(R.id.tvToolbarTitle)

        // Views
        webView = findViewById(R.id.webView)
        codeEditor = findViewById(R.id.etCodeEditor)
        val containerCode = findViewById<android.view.View>(R.id.containerCode)
        // Edit Button in Toolbar (Replaces FAB)
        val btnEdit = findViewById<android.widget.ImageView>(R.id.btnEdit)

        projectId = intent.getStringExtra("PROJECT_ID")

        if (projectId != null) {
            // Initial Load
            loadProjectConfig()
        }

        // Status Bar Color and Flags
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        // Transparent Status Bar for Gradient
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(
                window,
                false
        ) // Content behind bars

        // Remove old Coordinator color setting (Let gradient show)
        // findViewById<androidx.coordinatorlayout.widget.CoordinatorLayout>(R.id.rootCoordinator)?.setStatusBarBackgroundColor(bgColor)

        // MQTT Service Integration
        // Ensure Service is Connected using Project Defaults
        project?.let {
            ensureMqttNotificationPermission()
            MqttSessionClient.activate(this, it.id)
        }

        // Subscribe logic moved to connection observer to prevent race conditions
        // if (project != null) { ... }

        // WebView Setup
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(MQTTInterface(), "mqtt")

        // Load Code: Priority: ProjectRepository (JSON) -> Template
        // Fix: Check if customCode accidentally contains Kotlin source (starts with 'package')
        val initialCode =
                if (!project?.customCode.isNullOrEmpty() &&
                                !project!!.customCode.trim().startsWith("package")
                ) {
                    project!!.customCode
                } else {
                    HtmlTemplates.generateDefaultHtml(project)
                }

        // Fix: Editor Style for Dark Mode (Black BG, White Text)
        if (isNightMode) {
            codeEditor.setBackgroundColor(android.graphics.Color.BLACK)
            codeEditor.setTextColor(android.graphics.Color.WHITE)
            containerCode.setBackgroundColor(android.graphics.Color.BLACK)
            window.statusBarColor =
                    android.graphics.Color.BLACK // Optional override for editor focus
        } else {
            // Light Mode defaults
        }

        codeEditor.setText(initialCode)
        codeEditor.hint = getString(R.string.web_editor_hint)

        // Initial Load
        webView.loadDataWithBaseURL(null, initialCode, "text/html", "utf-8", null)

        // Toolbar Edit Action: Toggle Editor & Save/Run
        btnEdit.setOnClickListener {
            if (containerCode.visibility == android.view.View.VISIBLE) {
                // Close Editor -> Run Code
                containerCode.visibility = android.view.View.GONE
                btnEdit.setImageResource(R.drawable.ic_edit_document_custom)

                // Hide Keyboard
                val imm =
                        getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as
                                android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(codeEditor.windowToken, 0)

                val code = codeEditor.text.toString()

                // Save to ProjectRepository (Persistence in JSON)
                if (project != null) {
                    val updatedProject = project!!.copy(customCode = code)
                    ProjectRepository.updateProject(updatedProject)
                    project = updatedProject // Update local Ref
                    Toast.makeText(this, R.string.web_code_saved, Toast.LENGTH_SHORT).show()
                }

                webView.loadDataWithBaseURL(null, code, "text/html", "utf-8", null)
            } else {
                // Open Editor
                containerCode.visibility = android.view.View.VISIBLE
                btnEdit.setImageResource(R.drawable.ic_run_custom)
            }
        }

        // Result Launcher for SetupActivity (to handle ID renaming)
        val setupLauncher =
                registerForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts
                                .StartActivityForResult()
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        // Check if ID was changed
                        val newId = result.data?.getStringExtra("NEW_ID")
                        if (newId != null) {
                            projectId = newId
                            // Reload data immediately
                            loadProjectConfig()
                            Toast.makeText(this, R.string.project_id_updated, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

        findViewById<android.view.View>(R.id.btnSettings).setOnClickListener {
            if (projectId != null) {
                val intent = Intent(this, SetupActivity::class.java)
                intent.putExtra("PROJECT_ID", projectId)
                setupLauncher.launch(intent)
            }
        }

        // Update HTML Import Button (No Premium Lock)
        findViewById<android.view.View>(R.id.btnUpload).setOnClickListener {
            filePickerLauncher.launch("text/html")
        }

        // Info Button: AI Assistance Prompt & Tutorial
        findViewById<android.view.View>(R.id.btnInfo).setOnClickListener {
            val promptText =
                    """
                 **Role**: Expert Web Developer (MQTT Dashboard).
                 
                 **Workflow**:
                 1. Copy this prompt.
                 2. Ask AI: "Create an MQTT dashboard for MqttPanelCraft. Requirements: [Your features here]."
                 3. AI generates a SINGLE HTML file.
                 4. Use the 'Folder' icon in this app to import that .html file.
                 
                 **MQTT API (window.mqtt)**:
                 - `mqtt.publish('topic', 'payload')`: Send message.
                 - `mqtt.subscribe('topic')`: Receive message.
                 - `function mqttOnMessage(topic, payload)`: Handle incoming data.
                 
                 **Topics**: All topics must start with: 
                 `${project?.let { TopicHelper.formatBaseTopic(it.name, it.id) } ?: "project/ID"}/`
                 
                 **Tip**: The default template penguin is hardcoded with pure CSS/JS for reference.
             """.trimIndent()

            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle(R.string.web_ai_tutorial_title)
            builder.setMessage(promptText)
            builder.setPositiveButton(R.string.web_copy_prompt) { _, _ ->
                val clipboard =
                        getSystemService(android.content.Context.CLIPBOARD_SERVICE) as
                                android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("AI Prompt", promptText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.web_prompt_copied, Toast.LENGTH_SHORT).show()
            }
            builder.setNegativeButton(R.string.common_btn_close, null)
            builder.show()
        }

        // Update Initial Status
        updateStatusIndicator(MqttRepository.connectionStatus.value ?: 0)
        MqttRepository.connectionStatus.observe(this) { status -> updateStatusIndicator(status) }

        // Initialize Idle Ad Controller
        com.example.mqttpanelcraft.utils.AdManager.loadInterstitial(this)
        idleAdController =
                com.example.mqttpanelcraft.ui.IdleAdController(this) {
                    // Ad Closed Callback (Resume if needed, but WebView keeps running)
                }
        idleAdController.start()
    }

    // Track subscription state to avoid redundant calls or missing first call
    private var hasSubscribed = false

    private fun updateStatusIndicator(status: Int) {
        val viewStatusDot = findViewById<android.widget.ImageView>(R.id.viewStatusDot)

        val colorGreen = android.graphics.Color.GREEN
        val colorRed = android.graphics.Color.RED
        val colorGray = android.graphics.Color.GRAY

        when (status) {
            1 -> { // Connected
                viewStatusDot.setImageResource(R.drawable.ic_link)
                viewStatusDot.setColorFilter(colorGreen)

                // Fix: Subscribe ONLY when connected to ensure the Service is ready
                if (!hasSubscribed && project != null) {
                        MqttSessionClient.refresh(this, project!!.id)
                    hasSubscribed = true
                }
            }
            2 -> { // Failed
                viewStatusDot.setImageResource(R.drawable.ic_link_off)
                viewStatusDot.setColorFilter(colorRed)
                hasSubscribed = false // Reset so we retry on next connect
            }
            else -> { // Connecting
                viewStatusDot.setImageResource(R.drawable.ic_link)
                viewStatusDot.setColorFilter(colorGray)
                // v44.4: Do NOT reset hasSubscribed here.
                // If we were already subscribed, stay that way during transient reconnects.
            }
        }
    }

    override fun onMessageReceived(topic: String, payload: String) {
        runOnUiThread {
            // Inject into JS
            val safePayload = payload.replace("'", "\\'")
            val safeTopic = topic.replace("'", "\\'")
            webView.evaluateJavascript(
                    "if(window.mqttOnMessage) mqttOnMessage('$safeTopic', '$safePayload')",
                    null
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (!mqttListenerRegistered) {
            MqttRepository.registerListener(this)
            mqttListenerRegistered = true
        }
        project?.let { current ->
            MqttSessionClient.setVisible(this, current.id, true)
            MqttRepository.consumeBackgroundSnapshots(current.id).forEach { snapshot ->
                onMessageReceived(snapshot.topic, snapshot.payload)
            }
        }
    }

    override fun onStop() {
        project?.let { current ->
            MqttRepository.markUiDetached(current.id)
            MqttSessionClient.setVisible(this, current.id, false)
        }
        if (mqttListenerRegistered) {
            MqttRepository.unregisterListener(this)
            mqttListenerRegistered = false
        }
        hasSubscribed = false
        super.onStop()
    }

    // Idle Ads
    private lateinit var idleAdController: com.example.mqttpanelcraft.ui.IdleAdController

    override fun onResume() {
        super.onResume()
        if (::idleAdController.isInitialized) {
            idleAdController.start()
        }
        // Reload Project Config (e.g. Orientation changes from Settings)
        if (projectId != null) {
            loadProjectConfig()
        }
    }

    private fun loadProjectConfig() {
        val currentProject = ProjectRepository.getProjectById(projectId!!)
        if (currentProject != null) {
            project = currentProject
            hasSubscribed = false
            findViewById<android.widget.TextView>(R.id.tvToolbarTitle).text = currentProject.name

            // Apply Orientation
            val targetOrientation =
                    when (currentProject.orientation) {
                        "PORTRAIT" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        "LANDSCAPE" ->
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }

            if (requestedOrientation != targetOrientation) {
                requestedOrientation = targetOrientation
            }

            if (MqttRepository.connectionStatus.value == 1) {
                updateStatusIndicator(1)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::idleAdController.isInitialized) {
            idleAdController.stop()
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (::idleAdController.isInitialized) {
            idleAdController.onUserInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }

    // --- HTML File Import Logic ---
    private val filePickerLauncher =
            registerForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            val htmlContent = inputStream.bufferedReader().use { it.readText() }

                            // Update UI & Data
                            codeEditor.setText(htmlContent)
                            webView.loadDataWithBaseURL(
                                    null,
                                    htmlContent,
                                    "text/html",
                                    "utf-8",
                                    null
                            )

                            // Save immediately
                            if (project != null) {
                                project = project!!.copy(customCode = htmlContent)
                                ProjectRepository.updateProject(project!!)
                            }

                            Toast.makeText(this, R.string.web_import_success, Toast.LENGTH_SHORT)
                                    .show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                                        this,
                                        getString(R.string.web_import_failed, e.message),
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                }
            }

    // Removed onCreateOptionsMenu and onOptionsItemSelected as Menu is depleted.

    // JS Interface
    inner class MQTTInterface {
        @JavascriptInterface
        fun publish(topic: String, message: String) {
            MqttSessionClient.publish(this@WebViewActivity, topic, message)
        }

        @JavascriptInterface
        fun subscribe(topic: String) {
            MqttSessionClient.subscribe(this@WebViewActivity, topic)
        }
    }
}
