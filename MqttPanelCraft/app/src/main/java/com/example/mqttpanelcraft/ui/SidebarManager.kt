package com.example.mqttpanelcraft.ui

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.mqttpanelcraft.R
import com.google.android.material.navigation.NavigationView

class SidebarManager(
    private val drawerLayout: DrawerLayout?,
    private val propertyContainer: View?,
    private val componentContainer: View?,
    private val onComponentDragStart: (View, String) -> Unit // Callback when dragging from sidebar
) {

    fun showPropertiesPanel() {
        propertyContainer?.visibility = View.VISIBLE
        componentContainer?.visibility = View.GONE
        // openDrawer() // User requested NO auto-open
    }

    fun showComponentsPanel() {
        propertyContainer?.visibility = View.GONE
        componentContainer?.visibility = View.VISIBLE
        // openDrawer() // User requested NO auto-open
    }

    fun openDrawer() {
        if (drawerLayout?.isDrawerOpen(GravityCompat.START) == false) {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    fun closeDrawer() {
        if (drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    fun isDrawerOpen(): Boolean {
        return drawerLayout?.isDrawerOpen(GravityCompat.START) == true
    }

    fun setupComponentPalette(rootView: View) {
        val container =
            rootView.findViewById<android.widget.LinearLayout>(R.id.cardsContainer) ?: return
        container.removeAllViews()

        val registry = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry
        val allDefs = registry.getAllTypes().mapNotNull { registry.get(it) }

        // Define Group Order
        val groupOrder = listOf("CONTROL", "SENSOR", "DISPLAY")
        val grouped = allDefs.groupBy { it.group }

        val inflater = android.view.LayoutInflater.from(rootView.context)
        val density = rootView.resources.displayMetrics.density
        fun dpToPx(dp: Int): Int = (dp * density).toInt()

        // Shared Long Click Listener (For Dragging)
        val longClickListener = View.OnLongClickListener { view ->
            val tag = view.tag as? String ?: return@OnLongClickListener false

            val item = android.content.ClipData.Item(tag)
            val dragData = android.content.ClipData(
                tag,
                arrayOf(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN),
                item
            )

            // Generate "Real" Preview View for Shadow
            val checkContext = view.context

            // Registry only
            val def =
                com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(tag)
            val (w, h) = if (def != null) {
                val d = checkContext.resources.displayMetrics.density
                Pair((def.defaultSize.width * d).toInt(), (def.defaultSize.height * d).toInt())
            } else {
                Pair(300, 300)
            }

            // Create temp preview for shadow
            val previewView = def?.createView(checkContext, true) ?: View(checkContext)

            // FIX: Apply default styles (Shape, Color, etc.) via onUpdateView
            if (def != null) {
                val dummyData = com.example.mqttpanelcraft.model.ComponentData(
                    id = -1,
                    type = tag,
                    x = 0f,
                    y = 0f,
                    width = w,
                    height = h,
                    label = def.labelPrefix,
                    topicConfig = "",
                    props = mutableMapOf()
                )
                def.onUpdateView(previewView, dummyData)
            }

            val widthSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            previewView.measure(widthSpec, heightSpec)
            previewView.layout(0, 0, w, h)

            val shadow = object : View.DragShadowBuilder(previewView) {
                override fun onProvideShadowMetrics(
                    outShadowSize: android.graphics.Point,
                    outShadowTouchPoint: android.graphics.Point
                ) {
                    outShadowSize.set(previewView.measuredWidth, previewView.measuredHeight)
                    outShadowTouchPoint.set(
                        previewView.measuredWidth / 2,
                        previewView.measuredHeight / 2
                    )
                }

                override fun onDrawShadow(canvas: android.graphics.Canvas) {
                    previewView.draw(canvas)
                }
            }
            
            // Haptic Feedback for "Materialize" feel
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

            view.startDragAndDrop(dragData, shadow, null, 0)
            closeDrawer() // User requested auto-close on drag start
            return@OnLongClickListener true
        }

        // Search List: View, Type, Label
        val searchList = mutableListOf<Triple<View, String, String>>()

        groupOrder.forEach { groupName ->
            val defsInGroup = grouped[groupName] ?: return@forEach

            // Determine Group Style (Color & Icon)
            val (groupColorRes, headerIconRes) = when(groupName) {
                "CONTROL" -> Pair(R.color.vivid_blue, android.R.drawable.ic_menu_preferences) // Bolt-like
                "SENSOR" -> Pair(R.color.warm_amber, android.R.drawable.ic_menu_compass) // Sensor-like
                "DISPLAY" -> Pair(R.color.soft_purple, android.R.drawable.ic_menu_gallery) // Media-like
                else -> Pair(R.color.sidebar_text_primary, android.R.drawable.ic_menu_help)
            }
            val groupColor = androidx.core.content.ContextCompat.getColor(rootView.context, groupColorRes)

            // 1. Header (Inflate Custom Layout)
            val headerView = inflater.inflate(R.layout.item_sidebar_header, container, false)
            val tvHeader = headerView.findViewById<android.widget.TextView>(R.id.tvHeaderTitle)
            val ivArrow = headerView.findViewById<android.widget.ImageView>(R.id.ivArrow)
            val ivCatIcon = headerView.findViewById<android.widget.ImageView>(R.id.ivCategoryIcon)
            
            // Set Header Icon & Color
            ivCatIcon.setImageResource(headerIconRes)
            ivCatIcon.setColorFilter(groupColor)
            ivArrow.setColorFilter(groupColor)
            
            // Header Text Mapping
            val headerText = when(groupName) {
                "DISPLAY" -> rootView.context.getString(R.string.project_sidebar_category_display)
                "CONTROL" -> rootView.context.getString(R.string.project_cat_control)
                "SENSOR" -> rootView.context.getString(R.string.project_cat_sensor)
                else -> groupName.lowercase().replaceFirstChar { it.uppercase() }
            }
            tvHeader.text = headerText
            container.addView(headerView)

            // 2. Grid Container (2 Columns)
            val grid = android.widget.GridLayout(rootView.context).apply {
                columnCount = 2
                alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(grid)

            // Toggle Logic
            var isExpanded = true // Default Expanded

            fun updateState() {
                grid.visibility = if (isExpanded) View.VISIBLE else View.GONE
                ivArrow.animate().rotation(if (isExpanded) 0f else 180f).setDuration(200).start()
            }

            headerView.setOnClickListener {
                isExpanded = !isExpanded
                updateState()
            }

            // 3. Render All Items in this Group
            val allItems = grouped[groupName] ?: emptyList()
            allItems.forEach { def ->
                val card = inflater.inflate(R.layout.item_sidebar_component, grid, false)
                // val img = card.findViewById<android.widget.ImageView>(R.id.imgIcon) // REMOVED
                val previewContainer = card.findViewById<android.widget.FrameLayout>(R.id.previewContainer)
                
                // Color Tint!
                 val groupColorRes = when(groupName) {
                    "CONTROL" -> R.color.vivid_blue
                    "SENSOR" -> R.color.warm_amber
                    "DISPLAY" -> R.color.soft_purple
                    else -> R.color.sidebar_text_primary
                 }
                 val groupColorInt = androidx.core.content.ContextCompat.getColor(rootView.context, groupColorRes)
                 val groupColorHex = String.format("#%06X", (0xFFFFFF and groupColorInt))

                 // Create Real Preview View
                 val previewView = def.createView(rootView.context, true) // isPreview = true
                 
                 // Init Dummy Data with Group Color
                 val dummyProps = mutableMapOf<String, String>()
                 dummyProps["color"] = groupColorHex
                 dummyProps["colorOn"] = groupColorHex // For switches
                 dummyProps["colorOff"] = "#BDBDBD" // Grey for off state
                 // Special handling for some types if needed?
                 
                 val dummyData = com.example.mqttpanelcraft.model.ComponentData(
                    id = -1, type = def.type, x = 0f, y = 0f, 
                    width = 100, height = 100, // Dummy size
                    label = "", topicConfig = "", props = dummyProps
                 )
                 
                 // Update View Appearance
                 def.onUpdateView(previewView, dummyData)
                 
                 // Disable interaction on preview
                 previewView.isClickable = false
                 previewView.isFocusable = false
                 if (previewView is android.view.ViewGroup) {
                     previewView.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                 }
                 
                 // Add to Container
                 // We want to FIT it into 60dp.
                 // Most components are 1:1 or responsive.
                 // We let them match parent of container (60dp).
                 val previewParams = android.widget.FrameLayout.LayoutParams(
                     android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                     android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                 )
                 // Or wrap content and center? 
                 // MATCH_PARENT suggests component fills the box. 
                 // If component is huge (text area), it might clip.
                 // Let's rely on standard views being responsive.
                 previewView.layoutParams = previewParams
                 previewContainer.addView(previewView)
                 
                 // No more img.setColorFilter needed (handled by onUpdateView with props)
                
                val tv = card.findViewById<android.widget.TextView>(R.id.tvLabel)

                // Localized Label
                val labelResName = "component_label_${def.type.lowercase()}"
                val labelId = rootView.resources.getIdentifier(
                    labelResName,
                    "string",
                    rootView.context.packageName
                )
                val labelText = if (labelId != 0) {
                    rootView.context.getString(labelId)
                } else {
                    def.type.lowercase().replaceFirstChar { it.uppercase() }
                }
                tv.text = labelText

                // Card Layout Params for Grid (Square-ish Box)
                val params = android.widget.GridLayout.LayoutParams()
                // Fixed width to prevent single-item stretching. Sidebar ~280dp.
                // 280 - 32(padding) - 24(margins) = 224 / 2 = 112. Safe: 108dp.
                params.width = dpToPx(108) 
                params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                params.columnSpec =
                    android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED) // Removed Weight 1f
                params.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                card.layoutParams = params

                card.tag = def.type
                card.setOnLongClickListener(longClickListener)

                grid.addView(card)
                
                // Add to search list
                searchList.add(Triple(card, def.type, labelText))
            }

            // Set initial state
            isExpanded = true
            grid.visibility = View.VISIBLE
            ivArrow.rotation = 0f 
        } // End Group Loop

        // Search Logic
        val etSearch = rootView.findViewById<android.widget.EditText>(R.id.etSearchComponents) ?: return
        
        // Clear Button (DrawableEnd) Logic using Touch Listener
        etSearch.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                if (etSearch.compoundDrawables[2] != null) {
                    // Check if touch is on the drawable (Right side)
                    // The drawable is on the END (index 2).
                    // Touch X >= (Width - PaddingRight - DrawableWidth)
                    val drawableWidth = etSearch.compoundDrawables[2].bounds.width()
                    // Add some slop/padding for easier click
                    val touchAreaStart = etSearch.width - etSearch.paddingEnd - drawableWidth - dpToPx(20) 
                    
                    if (event.x >= touchAreaStart) {
                        etSearch.text.clear()
                        v.performClick()
                        return@setOnTouchListener true
                    }
                }
            }
            return@setOnTouchListener false
        }
        
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase()
                
                searchList.forEach { (view, tag, label) ->
                    val match = tag.contains(query, ignoreCase = true) || 
                                label.contains(query, ignoreCase = true)

                    if (query.isEmpty() || match) {
                        view.visibility = View.VISIBLE
                    } else {
                        view.visibility = View.GONE
                    }
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
}
