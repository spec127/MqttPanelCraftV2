package com.example.mqttpanelcraft

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SetupActivity : AppCompatActivity() {

    private var selectedType: ProjectType = ProjectType.HOME
    private var projectId: String? = null
    
    // UI Elements
    private lateinit var cardHome: LinearLayout
    private lateinit var ivHome: ImageView
    private lateinit var tvHome: TextView
    
    private lateinit var cardFactory: LinearLayout
    private lateinit var ivFactory: ImageView
    private lateinit var tvFactory: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        setupToolbar()
        setupViews()
        
        // Check for Edit Mode
        projectId = intent.getStringExtra("PROJECT_ID")
        if (projectId != null) {
            setupEditMode(projectId!!)
        }
    }
    
    private fun setupEditMode(id: String) {
        val project = ProjectRepository.getProjectById(id) ?: return
        
        findViewById<TextInputEditText>(R.id.etProjectName).setText(project.name)
        findViewById<TextInputEditText>(R.id.etBroker).setText(project.broker)
        selectType(project.type)
        findViewById<MaterialButton>(R.id.btnSaveProject).text = "Update Project"
        supportActionBar?.title = "Edit Project"
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back) // Need this icon, or use standard back
        // For now use default if arrow back not custom created, or create one. 
        // Standard Android usually has one if displayHomeAsUp is true.
        // If not creating specific drawable, system default works.
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupViews() {
        val etName = findViewById<TextInputEditText>(R.id.etProjectName)
        val etBroker = findViewById<TextInputEditText>(R.id.etBroker)
        val btnTest = findViewById<MaterialButton>(R.id.btnTestConnection)
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveProject)

        cardHome = findViewById(R.id.cardHome)
        ivHome = findViewById(R.id.ivHome)
        tvHome = findViewById(R.id.tvHome)

        cardFactory = findViewById(R.id.cardFactory)
        ivFactory = findViewById(R.id.ivFactory)
        tvFactory = findViewById(R.id.tvFactory)

        // Theme Selection
        cardHome.setOnClickListener { selectType(ProjectType.HOME) }
        cardFactory.setOnClickListener { selectType(ProjectType.FACTORY) }

        // Test Connection (Mock)
        btnTest.setOnClickListener {
            btnTest.isEnabled = false
            btnTest.text = "Testing..."
            
            Handler(Looper.getMainLooper()).postDelayed({
                btnTest.text = "OK"
                btnTest.isEnabled = true
                btnTest.setTextColor(Color.GREEN)
                btnTest.strokeColor = ColorStateList.valueOf(Color.GREEN)
            }, 1000)
        }

        // Save
        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val broker = etBroker.text.toString()

            if (name.isBlank()) {
                etName.error = getString(R.string.error_name_required)
                return@setOnClickListener
            }
            if (broker.isBlank()) {
                etBroker.error = getString(R.string.error_broker_required)
                return@setOnClickListener
            }

            val newProject = Project(
                id = projectId ?: ProjectRepository.generateId(),
                name = name,
                broker = broker,
                type = selectedType,
                isConnected = false
            )
            
            if (projectId != null) {
                ProjectRepository.updateProject(newProject)
            } else {
                ProjectRepository.addProject(newProject)
            }
            
            // Finish SetupActivity to return to previous screen (Dashboard)
            finish()
        }
    }

    private fun selectType(type: ProjectType) {
        selectedType = type
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val greyColor = Color.parseColor("#757575")

        if (type == ProjectType.HOME) {
            cardHome.setBackgroundResource(R.drawable.bg_card_selected)
            ivHome.setColorFilter(primaryColor)
            tvHome.setTextColor(primaryColor)

            cardFactory.setBackgroundResource(R.drawable.bg_card_unselected)
            ivFactory.setColorFilter(greyColor)
            tvFactory.setTextColor(greyColor)
        } else {
            cardHome.setBackgroundResource(R.drawable.bg_card_unselected)
            ivHome.setColorFilter(greyColor)
            tvHome.setTextColor(greyColor)

            cardFactory.setBackgroundResource(R.drawable.bg_card_selected)
            ivFactory.setColorFilter(primaryColor)
            tvFactory.setTextColor(primaryColor)
        }
    }
}
