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
        val container = rootView.findViewById<android.widget.LinearLayout>(R.id.cardsContainer) ?: return
        container.removeAllViews()

        val registry = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry
        val allDefs = registry.getAllTypes().mapNotNull { registry.get(it) }

        // Define Group Order
        val groupOrder = listOf("DISPLAY", "CONTROL", "SENSOR")
        val grouped = allDefs.groupBy { it.group }

        val inflater = android.view.LayoutInflater.from(rootView.context)
        val density = rootView.resources.displayMetrics.density
        fun dpToPx(dp: Int): Int = (dp * density).toInt()

        // Shared Touch Listener
        val touchListener = View.OnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                val tag = view.tag as? String ?: return@OnTouchListener false

                val item = android.content.ClipData.Item(tag)
                val dragData = android.content.ClipData(tag, arrayOf(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN), item)

                // Generate "Real" Preview View for Shadow
                val checkContext = view.context

                // Registry only
                val def = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(tag)
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
                    override fun onProvideShadowMetrics(outShadowSize: android.graphics.Point, outShadowTouchPoint: android.graphics.Point) {
                        outShadowSize.set(previewView.measuredWidth, previewView.measuredHeight)
                        outShadowTouchPoint.set(previewView.measuredWidth / 2, previewView.measuredHeight / 2)
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

            // Add Header
            val header = android.widget.TextView(rootView.context).apply {
                text = groupName.lowercase().replaceFirstChar { it.uppercase() } // "Display"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)

                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                setTextColor(typedValue.data)

                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, dpToPx(8))
                layoutParams = params
            }
            container.addView(header)

            // Chunk by 2
            defsInGroup.chunked(2).forEach { rowDefs ->
                val rowCtx = rootView.context
                val row = android.widget.LinearLayout(rowCtx).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    val params = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(0, 0, 0, dpToPx(16))
                    layoutParams = params
                }

                rowDefs.forEach { def ->
                    val card = inflater.inflate(R.layout.item_sidebar_component, row, false)
                    // Set Icon
                    val img = card.findViewById<android.widget.ImageView>(R.id.imgIcon)
                    img.setImageResource(def.iconResId)

                    // Set Label
                    val tv = card.findViewById<android.widget.TextView>(R.id.tvLabel)

                    // Special Case for Thermometer -> Level Indicator (User preference)
                    tv.text = if (def.type == "THERMOMETER") "Level Indicator" else def.type.lowercase().replaceFirstChar { it.uppercase() }

                    card.tag = def.type
                    card.setOnTouchListener(touchListener)

                    row.addView(card)
                    searchList.add(card to def.type)
                }

                // Filler if only 1 to prevent stretching
                if (rowDefs.size == 1) {
                    val filler = android.view.View(rowCtx)
                    val params = android.widget.LinearLayout.LayoutParams(0, 1)
                    params.weight = 1f
                    params.setMargins(dpToPx(4), 0, dpToPx(4), 0)
                    filler.layoutParams = params
                    row.addView(filler)
                }

                container.addView(row)
            }
        }

        // Search Logic
        val etSearch = rootView.findViewById<android.widget.EditText>(R.id.etSearchComponents)
        etSearch?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
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
                        view.visibility = View.INVISIBLE // Use INVISIBLE to keep layout structure stable
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
}
