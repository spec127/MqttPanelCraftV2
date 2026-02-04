package com.example.mqttpanelcraft.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import com.example.mqttpanelcraft.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Manages the UI elements of ProjectViewActivity to reduce bloat.
 * Handles Toolbar, BottomSheet, FAB, System Bars, and Layout adjustments.
 */
class ProjectUIManager(
    private val activity: AppCompatActivity,
    private val root: View,
    private val viewModel: com.example.mqttpanelcraft.ProjectViewModel,
    private val interactionManager: CanvasInteractionManager,
    private val sidebarManager: SidebarManager,
    private val propertiesManager: PropertiesSheetManager,
    private val renderer: ComponentRenderer
) {

    // UI References
    private val toolbar: Toolbar = root.findViewById(R.id.toolbar)
    private val drawerLayout: DrawerLayout = root.findViewById(R.id.drawerLayout)
    private val bottomSheet: View = root.findViewById(R.id.bottomSheet)
    private val fabMode: FloatingActionButton = root.findViewById(R.id.fabMode)
    private val editorCanvas: FrameLayout = root.findViewById(R.id.editorCanvas)
    private val guideOverlay: View = root.findViewById(R.id.guideOverlay)
    private val containerLogs: View = root.findViewById(R.id.containerLogs)
    private val containerProperties: View = root.findViewById(R.id.containerProperties)
    private val btnUndo: ImageView = root.findViewById(R.id.btnUndo)
    private val tvToolbarTitle: android.widget.TextView = root.findViewById(R.id.tvToolbarTitle)


    
    private val sheetBehavior = BottomSheetBehavior.from(bottomSheet)
    private var currentSlideOffset = 0f
    private var lastSelectedId: Int? = null

    init {
        setupStateMachine()
    }

    private fun setupStateMachine() {
        // Bottom Sheet Callback
        val density = activity.resources.displayMetrics.density
        sheetBehavior.peekHeight = (100 * density).toInt()

        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // Sync Offset for discrete states
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    currentSlideOffset = 1f
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    currentSlideOffset = 0f
                }
                updateCanvasOcclusion()
                updateBottomInset()
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                currentSlideOffset = slideOffset
                updateCanvasOcclusion()
                updateBottomInset()
            }
        })
        
        // Initial Layout
        bottomSheet.post { updateBottomInset() }
        
        // Mode FAB
        fabMode.setOnClickListener {
             onModeToggleCallback?.invoke()
        }
        
        // Undo
        btnUndo.setOnClickListener {
            viewModel.undo()
        }
    }
    
    var onModeToggleCallback: (() -> Unit)? = null

    fun toggleBottomSheet() {
        if (sheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    fun setDeleteZoneHover(isHovered: Boolean) {
        val header = root.findViewById<View>(R.id.bottomSheetHeader) ?: return
        val handle = root.findViewById<View>(R.id.ivHeaderHandle)
        val trash = root.findViewById<View>(R.id.ivHeaderTrash)
        val text = root.findViewById<View>(R.id.tvHeaderDelete) 
        
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


    fun setupToolbar() {
         activity.setSupportActionBar(toolbar)
         activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
         activity.supportActionBar?.setDisplayShowTitleEnabled(false)
         
         val toolbarColor = ContextCompat.getColor(activity, R.color.toolbar_text)
         toolbar.setTitleTextColor(toolbarColor)
         toolbar.setSubtitleTextColor(toolbarColor)
         
         // Menu/Hamburger Icon
         val menuIcon = ContextCompat.getDrawable(activity, android.R.drawable.ic_menu_sort_by_size)?.mutate()
         menuIcon?.setTint(toolbarColor)
         activity.supportActionBar?.setHomeAsUpIndicator(menuIcon)
         
         toolbar.setNavigationOnClickListener { sidebarManager.openDrawer() }
         
         // Grid Button
         root.findViewById<View>(R.id.btnGrid).setOnClickListener {
             viewModel.toggleGrid()
         }
         
         // Settings Button
         val btnSettings = root.findViewById<ImageView>(R.id.btnSettings)
         btnSettings.setColorFilter(toolbarColor)
         btnSettings.setOnClickListener {
             val intent = Intent(activity, com.example.mqttpanelcraft.SetupActivity::class.java)
             intent.putExtra("PROJECT_ID", viewModel.project.value?.id)
             activity.startActivity(intent)
         }
    }

    fun updateSystemBars() {
        val window = activity.window
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        val wic = WindowInsetsControllerCompat(window, window.decorView)
        val isNightMode = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        wic.isAppearanceLightStatusBars = !isNightMode // True (Dark Icons) only if NOT Night Mode

        val bgColor = ContextCompat.getColor(activity, R.color.toolbar_bg)
        window.statusBarColor = bgColor
        
        root.findViewById<CoordinatorLayout>(R.id.rootCoordinator)?.setStatusBarBackgroundColor(bgColor)
        drawerLayout.setStatusBarBackgroundColor(bgColor)
    }

    fun updateModeUI(isEditMode: Boolean, selectedId: Int?) {
        val toolbarColor = ContextCompat.getColor(activity, R.color.toolbar_text)
        
        // Lock Sheet?
        if (sheetBehavior is LockableBottomSheetBehavior) {
            sheetBehavior.isLocked = isEditMode
        }

        if (isEditMode) {
            // EDIT MODE
            // Fix: Return TRUE to consume event and block NestedScrollView from scrolling on empty space
            editorCanvas.setOnTouchListener { _, event -> 
                interactionManager.handleTouch(event)
                true 
            }
            fabMode.setImageResource(android.R.drawable.ic_media_play)
            guideOverlay.visibility = View.VISIBLE
            sidebarManager.showComponentsPanel()
            btnUndo.visibility = View.VISIBLE
            
            // Toolbar Left Icon: Add
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            val addIcon = ContextCompat.getDrawable(activity, R.drawable.ic_action_add_large)?.mutate()
            addIcon?.setTint(toolbarColor)
            activity.supportActionBar?.setHomeAsUpIndicator(addIcon)
            toolbar.setNavigationOnClickListener { sidebarManager.openDrawer() }
            
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            
            containerLogs.visibility = View.GONE
            containerProperties.visibility = View.VISIBLE
            
            sheetBehavior.isHideable = false
            if (sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            
            // Scroll to Top if Selection Changed
            if (selectedId != lastSelectedId && selectedId != null) {
                root.findViewById<NestedScrollView>(R.id.svPropertiesContent)?.scrollTo(0, 0)
            }
            lastSelectedId = selectedId
        } else {
            // RUN MODE
            editorCanvas.setOnTouchListener(null)
            fabMode.setImageResource(android.R.drawable.ic_menu_edit)
            guideOverlay.visibility = View.GONE
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            val backArrow = ContextCompat.getDrawable(activity, R.drawable.ic_action_back_large)?.mutate()
            backArrow?.setTint(toolbarColor)
            activity.supportActionBar?.setHomeAsUpIndicator(backArrow)
            toolbar.setNavigationOnClickListener { activity.onBackPressed() }
            
            btnUndo.visibility = View.GONE
            containerLogs.visibility = View.VISIBLE
            propertiesManager.hide()
            
            sheetBehavior.isHideable = false
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            
            // Clear Selection
            renderer.render(viewModel.components.value ?: emptyList(), false, null)
        }
        
        updateSheetDraggability(isEditMode, selectedId)
    }

    private fun updateSheetDraggability(isEditMode: Boolean, selectedId: Int?) {
        if (isEditMode) {
             if (selectedId == null) {
                 sheetBehavior.isDraggable = false
                 if (sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED 
                 }
             } else {
                 sheetBehavior.isDraggable = true
             }
        } else {
             sheetBehavior.isDraggable = true
        }
    }

    fun updateTitle(title: String) {
        tvToolbarTitle.text = title
    }

    fun updateGridState(visible: Boolean) {
        val grid = root.findViewById<View>(R.id.backgroundGrid)
        val btn = root.findViewById<ImageView>(R.id.btnGrid)
        val toolbarColor = ContextCompat.getColor(activity, R.color.toolbar_text)
        
        grid.visibility = if (visible) View.VISIBLE else View.GONE
        btn.alpha = if (visible) 1.0f else 0.3f
        btn.setColorFilter(toolbarColor)
    }

    fun updateUndoState(canUndo: Boolean) {
        val toolbarColor = ContextCompat.getColor(activity, R.color.toolbar_text)
        btnUndo.alpha = if (canUndo) 1.0f else 0.3f
        btnUndo.isEnabled = canUndo
        btnUndo.setColorFilter(toolbarColor)
    }

    fun updateCanvasOcclusion(compMaxY: Float = 0f) {
        val actuallyEditMode = (containerProperties.visibility == View.VISIBLE)

        if (actuallyEditMode) {
             // EDIT MODE
             
             // 1. Reset Natural Height (Fix "Too Long")
             if (editorCanvas.minimumHeight != 0) {
                 editorCanvas.minimumHeight = 0
                 editorCanvas.requestLayout()
             }
             
             // 2. Auto-Shift Logic (Continuous "Push Up")
             // Calculates translation relative to current Sheet Top (smooths out during onSlide)
             val selectedId = (activity as? com.example.mqttpanelcraft.ProjectViewActivity)?.getSelectedComponentId()

                 
             if (selectedId != null) {
                 // Optimization: If Sheet is fully collapsed, force Y=0 (Strict "Return to Zero")
                 if (currentSlideOffset == 0f) {
                     editorCanvas.translationY = 0f
                 } else {
                     val compView = renderer.getView(selectedId)
                     if (compView != null) {
                        val compLoc = IntArray(2)
                        compView.getLocationOnScreen(compLoc) 
                        val currentCompBottom = compLoc[1] + compView.height
                        
                        val sheetLoc = IntArray(2)
                        bottomSheet.getLocationOnScreen(sheetLoc)
                        val sheetTop = sheetLoc[1]
                        
                        // Margin: Interpolate 0dp (Collapsed) -> 20dp (Expanded) (User Request)
                        val safeOffset = currentSlideOffset.coerceIn(0f, 1f)
                        val margin = (20 * activity.resources.displayMetrics.density) * safeOffset
                        
                        val currentTrans = editorCanvas.translationY
                        val rawBottom = currentCompBottom - currentTrans
                        
                        var targetTrans = (sheetTop - margin - rawBottom)
                        if (targetTrans > 0f) targetTrans = 0f // Only shift UP
                        
                        editorCanvas.translationY = targetTrans
                     } else {
                        editorCanvas.translationY = 0f
                     }
                 }
             } else {
                 editorCanvas.translationY = 0f
             }
             
        } else {
             // RUN MODE Logic
             editorCanvas.translationY = 0f
             
             val sheetLoc = IntArray(2)
             bottomSheet.getLocationOnScreen(sheetLoc)
             val sheetTop = sheetLoc[1]
             val screenHeight = activity.resources.displayMetrics.heightPixels
             val visibleSheetHeight = (screenHeight - sheetTop).coerceAtLeast(0)
             
             // Fix: If called from onSlide/StateChanged with default 0f, fallback to stored Tag
             // This matches legacy behavior where Tag held the truth.
             val effectiveMaxY = if (compMaxY > 0f) compMaxY else (editorCanvas.tag as? Float) ?: 0f

             val density = activity.resources.displayMetrics.density
             val requiredHeight = (effectiveMaxY + visibleSheetHeight + (20 * density)).toInt()
             
             if (editorCanvas.minimumHeight != requiredHeight) {
                 editorCanvas.minimumHeight = requiredHeight
                 editorCanvas.requestLayout()
             }
        }
    }
    
    private fun updateBottomInset() {
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
        
        val overlap = (canvasBottom - sheetTop).coerceAtLeast(0)
        interactionManager.updateBottomInset(overlap)
    }
}
