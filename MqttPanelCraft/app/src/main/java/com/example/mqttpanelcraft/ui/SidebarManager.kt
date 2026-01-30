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

        // Shared Touch Listener
        val touchListener = View.OnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val tag = view.tag as? String ?: return@OnTouchListener false

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

                view.startDragAndDrop(dragData, shadow, null, 0)
                closeDrawer()
                return@OnTouchListener true
            }
            false
        }

        // Search List for filtering
        val searchList = mutableListOf<Pair<View, String>>()

        groupOrder.forEach { groupName ->
            val defsInGroup = grouped[groupName] ?: return@forEach

            // 1. Header (Inflate Custom Layout)
            val headerView = inflater.inflate(R.layout.item_sidebar_header, container, false)
            val tvHeader = headerView.findViewById<android.widget.TextView>(R.id.tvHeaderTitle)
            val ivArrow = headerView.findViewById<android.widget.ImageView>(R.id.ivArrow)

            val headerText = when (groupName) {
                "DISPLAY" -> rootView.context.getString(R.string.project_sidebar_category_display)
                "CONTROL" -> rootView.context.getString(R.string.project_cat_control)
                "SENSOR" -> rootView.context.getString(R.string.project_cat_sensor)
                else -> groupName.lowercase().replaceFirstChar { it.uppercase() }
            }
            tvHeader.text = headerText
            container.addView(headerView)

            // 2. Grid Container (2 Columns for Square Grid)
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
                ivArrow.rotation =
                    if (isExpanded) 0f else -90f // 0 is down (expanded), -90 is right (collapsed) if icon is expand_more.
                // formatted icon is expand_less_circle (up).
                // If expanded, we want arrow UP? Or Arrow Down?
                // Typically: Expanded = Arrow Up (Collapse action) or Arrow Down (Content is below).
                // Let's use: Expanded = Arrow Up. Collapsed = Arrow Down.
                // My icon is ic_expand_less_circle (Up).
                // So Expanded (0 rot) = Up. Collapsed (180 rot) = Down.
                ivArrow.animate().rotation(if (isExpanded) 0f else 180f).setDuration(200).start()
            }

            headerView.setOnClickListener {
                isExpanded = !isExpanded
                updateState()
            }

            // Initial State (Default Expanded)
            // ivArrow.rotation = 0f // Already 0 in XML? No, XML has 180 (Down) maybe?
            // Actually let's check XML. XML had rotation="180".
            // If XML is 180 (Down), and we want Expanded (Up), we need to set logic carefully.
            // Let's rely on updateState().

            // 3. Render All Items
            val allItems = grouped[groupName] ?: emptyList()
            allItems.forEach { def ->
                val card = inflater.inflate(R.layout.item_sidebar_component, grid, false)
                val img = card.findViewById<android.widget.ImageView>(R.id.imgIcon)
                img.setImageResource(def.iconResId)
                val tv = card.findViewById<android.widget.TextView>(R.id.tvLabel)

                val labelResName = "component_label_${def.type.lowercase()}"
                val labelId = rootView.resources.getIdentifier(
                    labelResName,
                    "string",
                    rootView.context.packageName
                )
                if (labelId != 0) {
                    tv.text = rootView.context.getString(labelId)
                } else {
                    tv.text = def.type.lowercase().replaceFirstChar { it.uppercase() }
                }

                // Card Layout Params for Grid (Square-ish Box)
                val params = android.widget.GridLayout.LayoutParams()
                params.width = 0
                params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                params.columnSpec =
                    android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                card.layoutParams = params

                card.tag = def.type
                card.setOnTouchListener(touchListener)

                grid.addView(card)
                searchList.add(card to def.type)
            }

            // Set initial state
            isExpanded = true
            grid.visibility = View.VISIBLE
            ivArrow.rotation = 0f // Up


            // Search Logic
            val etSearch = rootView.findViewById<android.widget.EditText>(R.id.etSearchComponents)
            etSearch?.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim().lowercase()
                    searchList.forEach { (view, tag) ->
                        val searchable = when (tag) {
                            "THERMOMETER" -> "level indicator thermometer"
                            else -> tag.lowercase()
                        }

                        // For now, we simply toggle visibility.
                        // Note: Hiding individual cards in a row might break alignment if we don't handle the row.
                        // Simpler approach: If query is not empty, check if match.

                        if (query.isEmpty() || searchable.contains(query)) {
                            view.visibility = View.VISIBLE
                            // We might need to handle the parent row visibility if all children are hidden?
                            // This simplistic search hides the CARD but keeps the empty space in the LinearLayout row.
                            // It's acceptable for now as a "simple search".
                        } else {
                            view.visibility =
                                View.INVISIBLE // Use INVISIBLE to keep layout structure stable
                        }
                    }
                }

                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
    }
}
