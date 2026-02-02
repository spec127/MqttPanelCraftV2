package com.example.mqttpanelcraft

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mqttpanelcraft.adapter.ProjectAdapter
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.databinding.ActivityDashboardBinding
import com.example.mqttpanelcraft.model.Project

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.example.mqttpanelcraft.utils.CrashLogger
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import android.widget.AutoCompleteTextView
// Removed BottomSheet imports
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.util.Locale
import kotlinx.coroutines.*
import android.view.animation.RotateAnimation


class DashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var projectAdapter: ProjectAdapter
    private var isGuest = false

    // v85: Sorting State
    // 0: Custom, 1: Name, 2: Date, 3: Last Opened
    // v85: Sorting State
    // 1: Name Asc, 2: Name Desc, 3: Date New, 4: Date Old, 5: Last Opened
    private var currentSortMode = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Enable Edge-to-Edge
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            
            binding = ActivityDashboardBinding.inflate(layoutInflater)
            setContentView(binding.root)

            isGuest = intent.getBooleanExtra("IS_GUEST", false)

            setupToolbar()
            setupDrawer()
            setupRecyclerView()
            setupFab()

            // Setup Window Insets (Status Bar / Nav Bar) handled by fitsSystemWindows and Themes
            // removed manual setOnApplyWindowInsetsListener logic
            
            // Fix: Explicitly set DrawerLayout status bar color to Transparent to let drawer background show through
            binding.drawerLayout.setStatusBarBackgroundColor(android.graphics.Color.TRANSPARENT)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

            // Adjust Drawer Width to 2/5 of Screen
            val drawerContainer = findViewById<android.view.View>(R.id.drawerContainer)
            drawerContainer?.post {
                val params = drawerContainer.layoutParams
                params.width = (resources.displayMetrics.widthPixels * 0.675).toInt() // Reduced by 10% (0.75 * 0.9)
                drawerContainer.layoutParams = params
            }

            // Initialize Data
            ProjectRepository.initialize(this)
            
            // Apply Theme (Dashboard Respects Setting)
            com.example.mqttpanelcraft.utils.ThemeManager.applyTheme(this)

            // Initialize Ads
            com.example.mqttpanelcraft.utils.AdManager.initialize(this)
            // Deferred load in onResume to speed up start/restart

            // Load Persistent Sort Mode
            val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
            currentSortMode = prefs.getInt("sort_mode", 5) // loadProjects() removed - superseded by LiveData observer in onCreate
            applySortMode(currentSortMode)

            setupSettingsUI() // v96: New Expandable UI
            
            // vFix: Observe LiveData for Async Loading
            ProjectRepository.projectsLiveData.observe(this) { projects ->
                projectAdapter.updateData(projects)
                if (projects.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.rvProjects.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.rvProjects.visibility = View.VISIBLE
                }
            }

            // Restore Drawer State (keeps drawer open across theme recreations)
            if (savedInstanceState?.getBoolean("DRAWER_OPEN") == true) {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }

        } catch (e: Exception) {
            CrashLogger.logError(this, "Dashboard Init Failed", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Re-apply sort mode to ensure "Last Opened" updates or other changes are reflected in order
        applySortMode(currentSortMode)
        
        // Refresh Banner Ad every time we return
        // Defer Ad Load to allow UI to render first (speeds up Theme Switch)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                com.example.mqttpanelcraft.utils.AdManager.loadBannerAd(this, binding.bannerAdContainer, binding.fabAddProject)
            }
        }, 100)
        
        
        startConnectionCheck()
        updateUserBadge()
    }

    private fun updateUserBadge() {
        // vUpdate: Premium Badge Logic
        val headerView = binding.navigationView.getHeaderView(0)
        val tvBadge = headerView.findViewById<android.widget.TextView>(R.id.tvPremiumBadge) ?: return
        
        if (com.example.mqttpanelcraft.utils.PremiumManager.isPremium(this)) {
            tvBadge.text = getString(R.string.dashboard_badge_premium)
            tvBadge.setTextColor(android.graphics.Color.parseColor("#FFD700")) // Gold Text
            tvBadge.setBackgroundResource(R.drawable.bg_premium_badge)
        } else {
            tvBadge.text = getString(R.string.dashboard_badge_free)
            tvBadge.setTextColor(android.graphics.Color.parseColor("#BDBDBD"))
            tvBadge.setBackgroundResource(R.drawable.bg_free_badge)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("DRAWER_OPEN", binding.drawerLayout.isDrawerOpen(GravityCompat.START))
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        // Enable hamburger icon click
        binding.toolbar.setNavigationOnClickListener {
             binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        
        // vFix: Handle Edge-to-Edge Insets
        // Pad the AppBarLayout so the Toolbar content is below the status bar, 
        // but the AppBarLayout background (Gradient) fills the status bar.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    private fun setupDrawer() {
        val menu = binding.navigationView.menu

        // 1. Dark Mode Switch
        val darkModeItem = menu.findItem(R.id.nav_dark_mode)
        val switchDarkMode = darkModeItem.actionView?.findViewById<SwitchMaterial>(R.id.drawer_switch)

        val isDark = com.example.mqttpanelcraft.utils.ThemeManager.isDarkThemeEnabled(this)
        switchDarkMode?.isChecked = isDark

        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            com.example.mqttpanelcraft.utils.ThemeManager.setTheme(this, isChecked)
        }

        // 2. Ads Switch
        val adsItem = menu.findItem(R.id.nav_ads)
        val switchAds = adsItem.actionView?.findViewById<SwitchMaterial>(R.id.drawer_switch)

        switchAds?.isChecked = com.example.mqttpanelcraft.utils.PremiumManager.isPremium(this)
        switchAds?.setOnCheckedChangeListener { _, isChecked ->
            com.example.mqttpanelcraft.utils.PremiumManager.setPremium(this, isChecked)
            // Refresh banner immediately if possible
            com.example.mqttpanelcraft.utils.AdManager.loadBannerAd(this, binding.bannerAdContainer, binding.fabAddProject)
            updateUserBadge()
        }

        updateUserBadge() // Initial state

        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                }
                R.id.nav_language -> showLanguageDialog()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Footer Exit Button Logic
        val btnExit = binding.drawerLayout.findViewById<android.view.View>(R.id.btnExitApp)
        // Note: The ID btnExitApp is inside the drawer layout hierarchy directly now (inside the LinearLayout wrapper)
        // findViewById looks recursively, so it should find it.
        btnExit?.setOnClickListener {
             finishAffinity()
        }
    }

    private fun setupSettingsUI() {
        // 1. Expand/Collapse Logic
        val header = findViewById<View>(R.id.layoutSettingsHeader)
        val content = findViewById<View>(R.id.layoutSettingsContent)
        val arrow = findViewById<android.widget.ImageView>(R.id.ivSettingsArrow)
        val tvTitle = findViewById<android.widget.TextView>(R.id.tvSettingsTitle)

        updateSettingsTitle(tvTitle) // Set initial title

        header.setOnClickListener {
            if (content.visibility == View.VISIBLE) {
                // Collapse
                content.visibility = View.GONE
                arrow.animate().rotation(270f).setDuration(200).start()
            } else {
                // Expand
                content.visibility = View.VISIBLE
                arrow.animate().rotation(90f).setDuration(200).start()
            }
        }

        // 2. Sort Logic
        val radioGroupSort = findViewById<android.widget.RadioGroup>(R.id.radioGroupSort)
        when (currentSortMode) {
            1 -> radioGroupSort?.check(R.id.rbSortNameAsc)
            2 -> radioGroupSort?.check(R.id.rbSortNameDesc)
            3 -> radioGroupSort?.check(R.id.rbSortDateNew)
            4 -> radioGroupSort?.check(R.id.rbSortDateOld)
            5 -> radioGroupSort?.check(R.id.rbSortLastOpened)
        }

        radioGroupSort?.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.rbSortNameAsc -> 1
                R.id.rbSortNameDesc -> 2
                R.id.rbSortDateNew -> 3
                R.id.rbSortDateOld -> 4
                R.id.rbSortLastOpened -> 5
                else -> 5
            }
            applySortMode(newMode)
            updateSettingsTitle(tvTitle) // Update title on change. LiveData will refresh UI.
        }

        // Dark Mode & Ads removed from here (moved to Drawer)
    }

    private fun updateSettingsTitle(tv: android.widget.TextView) {
        val modeText = when(currentSortMode) {
            1 -> getString(R.string.dashboard_sort_name_asc)
            2 -> getString(R.string.dashboard_sort_name_desc)
            3 -> getString(R.string.dashboard_sort_date_new)
            4 -> getString(R.string.dashboard_sort_date_old)
            5 -> getString(R.string.dashboard_sort_last_opened)
            else -> getString(R.string.dashboard_sort_last_opened)
        }
        tv.text = "${getString(R.string.dashboard_sort_label)}: $modeText"
    }

    private fun applySortMode(mode: Int) {
        currentSortMode = mode
        // Save to Prefs
        getSharedPreferences("AppSettings", MODE_PRIVATE).edit().putInt("sort_mode", mode).apply()

        when (mode) {
            1 -> ProjectRepository.sortProjects(compareBy { it.name.lowercase() })
            2 -> ProjectRepository.sortProjects(compareByDescending { it.name.lowercase() })
            3 -> ProjectRepository.sortProjects(compareByDescending { it.createdAt })
            4 -> ProjectRepository.sortProjects(compareBy { it.createdAt })
            5 -> ProjectRepository.sortProjects(compareByDescending { it.lastOpenedAt })
        }
    }

    // --- Language Handling ---
    private fun showLanguageDialog() {
        // Options: System Default, English, Traditional Chinese
        val languages = arrayOf(
            getString(R.string.lang_system_default),
            "English", 
            "繁體中文",
            "简体中文"
        )
        val codes = arrayOf(
            com.example.mqttpanelcraft.utils.LocaleManager.CODE_AUTO,
            com.example.mqttpanelcraft.utils.LocaleManager.CODE_EN,
            com.example.mqttpanelcraft.utils.LocaleManager.CODE_ZH,
            com.example.mqttpanelcraft.utils.LocaleManager.CODE_CN
        )

        val currentCode = com.example.mqttpanelcraft.utils.LocaleManager.getLanguageCode(this)
        var checkedItem = codes.indexOf(currentCode)
        if (checkedItem == -1) checkedItem = 0 // Default to Auto if unknown

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_language_title))
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val selectedCode = codes[which]
                if (selectedCode != currentCode) {
                    com.example.mqttpanelcraft.utils.LocaleManager.setLocale(this, selectedCode)
                    // Full App Restart
                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.common_btn_cancel), null)
            .show()
    }

    private fun setupRecyclerView() {
        projectAdapter = ProjectAdapter(emptyList(), 
            onProjectClick = { project ->
                // Update Last Opened
                project.lastOpenedAt = System.currentTimeMillis()
                ProjectRepository.updateProject(project) // Save timestamp

                if (project.type == com.example.mqttpanelcraft.model.ProjectType.WEBVIEW) {
                     val intent = Intent(this, WebViewActivity::class.java)
                     intent.putExtra("URL", project.broker) // Using Broker field as URL
                     intent.putExtra("PROJECT_ID", project.id) // Pass ID for loading settings
                     startActivity(intent)
                } else {
                     // On Item Click -> Open Project View
                     Toast.makeText(this, getString(R.string.dashboard_msg_opening_project, project.name), Toast.LENGTH_SHORT).show()
                     val intent = Intent(this, ProjectViewActivity::class.java)
                     intent.putExtra("PROJECT_ID", project.id)
                     startActivity(intent)
                }
            },
            onMenuClick = { project, action ->
                if (action == "EDIT") {
                    val intent = Intent(this, SetupActivity::class.java)
                    intent.putExtra("PROJECT_ID", project.id)
                    intent.putExtra("RETURN_TO_HOME", true)
                    startActivity(intent)
                } else if (action == "DELETE") {
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_delete_title))
                        .setMessage(getString(R.string.dialog_delete_message, project.name))
                        .setPositiveButton(getString(R.string.common_btn_delete)) { _, _ ->
                            try {
                                // Delete from repository; UI will refresh via projectsLiveData observer
                                ProjectRepository.deleteProject(project.id)
                                Toast.makeText(this, getString(R.string.dashboard_msg_project_deleted), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                CrashLogger.logError(this, "Delete Failed", e)
                            }
                        }
                        .setNegativeButton(getString(R.string.common_btn_cancel), null)
                        .show()
                }

            }
        )
        binding.rvProjects.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = projectAdapter
        }

        // v85: Drag & Drop (Project Reordering) - Restored
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition

                try {
                     ProjectRepository.swapProjects(fromPos, toPos)
                     projectAdapter.notifyItemMoved(fromPos, toPos)
                     return true
                } catch (e: Exception) { return false }
            }

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(binding.rvProjects)
    }

    private fun setupFab() {
        binding.fabAddProject.setOnClickListener {
            if (!com.example.mqttpanelcraft.utils.PremiumManager.isPremium(this) && 
                ProjectRepository.getAllProjects().size >= 3) {
                
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.title_limit_reached))
                    .setMessage(getString(R.string.msg_limit_reached))
                    .setPositiveButton(getString(R.string.dialog_btn_upgrade)) { _, _ ->
                         com.example.mqttpanelcraft.utils.PremiumManager.showPremiumDialog(this) { success ->
                             if (success) {
                                  // Retry? Or just let them click FAB again.
                                  updateUserBadge()
                             }
                         }
                    }
                    .setNegativeButton(getString(R.string.common_btn_cancel), null)
                    .show()
                return@setOnClickListener
            }
            val intent = Intent(this, SetupActivity::class.java)
            startActivity(intent)
        }
    }

    // v38: Dashboard Connectivity Check (Request 4)
    private var connectionJob: kotlinx.coroutines.Job? = null
    private val dashboardScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    override fun onPause() {
        super.onPause()
        stopConnectionCheck()
    }

    private fun startConnectionCheck() {
        stopConnectionCheck()
        connectionJob = dashboardScope.launch {
            while (isActive) {
                checkAllProjectsConnection()
                kotlinx.coroutines.delay(10000) // Check every 10s
            }
        }
    }

    private fun stopConnectionCheck() {
        connectionJob?.cancel()
        connectionJob = null
    }

    private suspend fun checkAllProjectsConnection() {
        val currentProjects = ProjectRepository.getAllProjects() // Get fresh list
        if (currentProjects.isEmpty()) return

        val updatedList = currentProjects.map { project ->
             val isOnline = checkBrokerConnectivity(project.broker, project.port)
             project.copy(isConnected = isOnline)
        }

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
             // Re-verify existence to prevent ghosting (race condition with delete)
             val validIds = ProjectRepository.getAllProjects().map { it.id }.toSet()
             val validList = updatedList.filter { it.id in validIds }
             projectAdapter.updateData(validList)
        }
    }

    private fun checkBrokerConnectivity(broker: String, port: Int): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(broker, port), 2000) // 2s timeout
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
