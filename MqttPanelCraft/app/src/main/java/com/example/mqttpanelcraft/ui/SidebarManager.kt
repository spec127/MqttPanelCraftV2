package com.example.mqttpanelcraft.ui

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Canvas
import android.graphics.Point
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.mqttpanelcraft.R

class SidebarManager(
        private val drawerLayout: DrawerLayout?,
        private val propertyContainer: View?,
        private val componentContainer: View?,
        private val onComponentClick: (String) -> Unit
) {
        // Accordion State
        private val categoryHeaders = mutableListOf<View>()
        private val categoryGrids = mutableListOf<View>()
        private val categoryArrowIcons = mutableListOf<ImageView>()

        fun showPropertiesPanel() {
                propertyContainer?.visibility = View.VISIBLE
                componentContainer?.visibility = View.GONE
        }

        fun showComponentsPanel() {
                propertyContainer?.visibility = View.GONE
                componentContainer?.visibility = View.VISIBLE
        }

        fun openDrawer() {
                if (drawerLayout?.isDrawerOpen(GravityCompat.START) == false) {
                        drawerLayout.openDrawer(GravityCompat.START, true)
                }
        }

        fun closeDrawer() {
                if (drawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
                        drawerLayout.closeDrawer(GravityCompat.START, false)
                }
        }

        fun isDrawerOpen(): Boolean {
                return drawerLayout?.isDrawerOpen(GravityCompat.START) == true
        }

        fun setupComponentPalette(rootView: View) {
                val container = rootView.findViewById<LinearLayout>(R.id.cardsContainer) ?: return
                container.removeAllViews()
                categoryHeaders.clear()
                categoryGrids.clear()
                categoryArrowIcons.clear()

                val registry = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry
                val allDefs = registry.getAllTypes().mapNotNull { registry.get(it) }

                // Define Group Order
                val groupOrder = listOf("CONTROL", "SENSOR", "DISPLAY")
                val grouped = allDefs.groupBy { it.group }

                val inflater = LayoutInflater.from(rootView.context)
                val density = rootView.resources.displayMetrics.density
                fun dpToPx(dp: Int): Int = (dp * density).toInt()

                // Shared Touch Listener (For Immediate Drag & Click)
                val touchSlop = ViewConfiguration.get(rootView.context).scaledTouchSlop
                val touchListener =
                        object : View.OnTouchListener {
                                private var startX = 0f
                                private var startY = 0f
                                private var isDragging = false

                                override fun onTouch(view: View, event: MotionEvent): Boolean {
                                        val tag = view.tag as? String ?: return false

                                        when (event.action) {
                                                MotionEvent.ACTION_DOWN -> {
                                                        startX = event.rawX
                                                        startY = event.rawY
                                                        isDragging = false
                                                }
                                                MotionEvent.ACTION_MOVE -> {
                                                        if (!isDragging) {
                                                                val dx =
                                                                        Math.abs(
                                                                                event.rawX - startX
                                                                        )
                                                                val dy =
                                                                        Math.abs(
                                                                                event.rawY - startY
                                                                        )
                                                                if (dx > touchSlop || dy > touchSlop
                                                                ) {
                                                                        isDragging = true
                                                                        startDrag(view, tag)
                                                                }
                                                        }
                                                }
                                                MotionEvent.ACTION_UP -> {
                                                        if (!isDragging) {
                                                                onComponentClick(tag)
                                                                closeDrawer()
                                                        }
                                                }
                                        }
                                        return true
                                }

                                private fun startDrag(view: View, tag: String) {
                                        val item = android.content.ClipData.Item(tag)
                                        val dragData =
                                                android.content.ClipData(
                                                        tag,
                                                        arrayOf(
                                                                android.content.ClipDescription
                                                                        .MIMETYPE_TEXT_PLAIN
                                                        ),
                                                        item
                                                )

                                        val checkContext = view.context
                                        val def =
                                                com.example.mqttpanelcraft.ui.components
                                                        .ComponentDefinitionRegistry.get(tag)
                                        val d = checkContext.resources.displayMetrics.density
                                        val (w, h) =
                                                if (def != null) {
                                                        Pair(
                                                                (def.defaultSize.width * d).toInt(),
                                                                (def.defaultSize.height * d).toInt()
                                                        )
                                                } else {
                                                        Pair(300, 300)
                                                }

                                        val previewView =
                                                def?.createView(checkContext, true)
                                                        ?: View(checkContext)
                                        if (def != null) {
                                                val dummyData =
                                                        com.example.mqttpanelcraft.model
                                                                .ComponentData(
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

                                        val widthSpec =
                                                View.MeasureSpec.makeMeasureSpec(
                                                        w,
                                                        View.MeasureSpec.EXACTLY
                                                )
                                        val heightSpec =
                                                View.MeasureSpec.makeMeasureSpec(
                                                        h,
                                                        View.MeasureSpec.EXACTLY
                                                )
                                        previewView.measure(widthSpec, heightSpec)
                                        previewView.layout(0, 0, w, h)

                                        val shadow =
                                                object : View.DragShadowBuilder(previewView) {
                                                        override fun onProvideShadowMetrics(
                                                                outShadowSize: Point,
                                                                outShadowTouchPoint: Point
                                                        ) {
                                                                outShadowSize.set(
                                                                        previewView.measuredWidth,
                                                                        previewView.measuredHeight
                                                                )
                                                                outShadowTouchPoint.set(
                                                                        previewView.measuredWidth /
                                                                                2,
                                                                        previewView.measuredHeight /
                                                                                2
                                                                )
                                                        }

                                                        override fun onDrawShadow(canvas: Canvas) {
                                                                previewView.draw(canvas)
                                                        }
                                                }

                                        view.performHapticFeedback(
                                                android.view.HapticFeedbackConstants.LONG_PRESS
                                        )
                                        view.startDragAndDrop(dragData, shadow, null, 0)
                                        closeDrawer()
                                }
                        }

                // Search List: View, Type, Label
                val searchList = mutableListOf<Triple<View, String, String>>()

                groupOrder.forEach { groupName ->
                        val defsInGroup = grouped[groupName] ?: return@forEach

                        // Determine Group Style (Color & Icon)
                        val (groupColorRes, headerIconRes) =
                                when (groupName) {
                                        "CONTROL" ->
                                                Pair(
                                                        R.color.vivid_blue,
                                                        android.R.drawable.ic_menu_preferences
                                                ) // Bolt-like
                                        "SENSOR" ->
                                                Pair(
                                                        R.color.warm_amber,
                                                        android.R.drawable.ic_menu_compass
                                                ) // Sensor-like
                                        "DISPLAY" ->
                                                Pair(
                                                        R.color.soft_purple,
                                                        android.R.drawable.ic_menu_gallery
                                                ) // Media-like
                                        else ->
                                                Pair(
                                                        R.color.sidebar_text_primary,
                                                        android.R.drawable.ic_menu_help
                                                )
                                }
                        val groupColor =
                                androidx.core.content.ContextCompat.getColor(
                                        rootView.context,
                                        groupColorRes
                                )

                        // 1. Header (Inflate Custom Layout)
                        val headerView =
                                inflater.inflate(R.layout.item_sidebar_header, container, false)
                        val tvHeader =
                                headerView.findViewById<android.widget.TextView>(R.id.tvHeaderTitle)
                        val ivArrow =
                                headerView.findViewById<android.widget.ImageView>(R.id.ivArrow)
                        val ivCatIcon =
                                headerView.findViewById<android.widget.ImageView>(
                                        R.id.ivCategoryIcon
                                )

                        // Set Header Icon & Color
                        ivCatIcon.setImageResource(headerIconRes)
                        ivCatIcon.setColorFilter(groupColor)
                        ivArrow.setColorFilter(groupColor)

                        // Header Text Mapping
                        val headerText =
                                when (groupName) {
                                        "DISPLAY" ->
                                                rootView.context.getString(
                                                        R.string.project_sidebar_category_display
                                                )
                                        "CONTROL" ->
                                                rootView.context.getString(
                                                        R.string.project_cat_control
                                                )
                                        "SENSOR" ->
                                                rootView.context.getString(
                                                        R.string.project_cat_sensor
                                                )
                                        else ->
                                                groupName.lowercase().replaceFirstChar {
                                                        it.uppercase()
                                                }
                                }
                        tvHeader.text = headerText
                        container.addView(headerView)

                        // 2. Grid Container (2 Columns)
                        val grid =
                                android.widget.GridLayout(rootView.context).apply {
                                        columnCount = 2
                                        alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
                                        layoutParams =
                                                android.widget.LinearLayout.LayoutParams(
                                                        android.widget.LinearLayout.LayoutParams
                                                                .MATCH_PARENT,
                                                        android.widget.LinearLayout.LayoutParams
                                                                .WRAP_CONTENT
                                                )
                                }
                        container.addView(grid)

                        // Accordion Logic
                        fun updateCategoryState(targetIndex: Int) {
                                androidx.transition.TransitionManager.beginDelayedTransition(
                                        container
                                )
                                for (i in categoryGrids.indices) {
                                        val expand = i == targetIndex
                                        categoryGrids[i].visibility =
                                                if (expand) View.VISIBLE else View.GONE
                                        categoryArrowIcons[i]
                                                .animate()
                                                .rotation(if (expand) 0f else 180f)
                                                .setDuration(200)
                                                .start()
                                }
                        }

                        val currentIndex = categoryHeaders.size
                        headerView.setOnClickListener { updateCategoryState(currentIndex) }

                        categoryHeaders.add(headerView)
                        categoryGrids.add(grid)
                        categoryArrowIcons.add(ivArrow)

                        // 3. Render All Items in this Group
                        val allItems = grouped[groupName] ?: emptyList()
                        allItems.forEach { def ->
                                val card =
                                        inflater.inflate(
                                                R.layout.item_sidebar_component,
                                                grid,
                                                false
                                        )
                                val previewContainer =
                                        card.findViewById<android.widget.FrameLayout>(
                                                R.id.previewContainer
                                        )

                                // Color Tint!
                                val groupColorRes =
                                        when (groupName) {
                                                "CONTROL" -> R.color.vivid_blue
                                                "SENSOR" -> R.color.warm_amber
                                                "DISPLAY" -> R.color.soft_purple
                                                else -> R.color.sidebar_text_primary
                                        }
                                val groupColorInt =
                                        androidx.core.content.ContextCompat.getColor(
                                                rootView.context,
                                                groupColorRes
                                        )
                                val groupColorHex =
                                        String.format("#%06X", (0xFFFFFF and groupColorInt))

                                // Create Real Preview View
                                val previewView =
                                        def.createView(
                                                rootView.context,
                                                false
                                        ) // isPreview/EditMode = false for clean look
                                previewView.background = null // Card has border, component does not

                                // Init Dummy Data
                                val dummyProps = mutableMapOf<String, String>()
                                // Default colors (respect component specific overrides if needed)
                                val initialColor = "#2196F3" // Always Blue for preview base
                                dummyProps["color"] = initialColor
                                dummyProps["colorOn"] = initialColor
                                dummyProps["colorOff"] = "#BDBDBD"

                                // Special handling for specific component types
                                when (def.type) {
                                        "BUTTON" -> {
                                                dummyProps["text"] = "" // Remove text, icon only
                                                dummyProps["label"] = ""
                                        }
                                        "SWITCH" -> {
                                                dummyProps["state"] = "2" // ON state (solid track)
                                        }
                                        "SLIDER" -> {
                                                dummyProps["value"] =
                                                        "35" // V18.4: Set to 35% for better
                                                // visibility in sidebar
                                        }
                                        "SELECTOR" -> {
                                                // V21.8: Use 3 segments for sidebar icon to
                                                // increase clarity in small 42dp container
                                                dummyProps["segments"] =
                                                        "[{\"label\":\"S1\",\"val\":\"1\"},{\"label\":\"S2\",\"val\":\"2\"},{\"label\":\"S3\",\"val\":\"3\"}]"
                                                dummyProps["style"] = "rounded"
                                        }
                                }

                                val dummyData =
                                        com.example.mqttpanelcraft.model.ComponentData(
                                                id = -1,
                                                type = def.type,
                                                x = 0f,
                                                y = 0f,
                                                width = 100,
                                                height = 100, // Dummy size
                                                label = "",
                                                topicConfig = "",
                                                props = dummyProps
                                        )

                                // Update View Appearance
                                def.onUpdateView(previewView, dummyData)

                                // Disable interaction on preview recursively
                                fun disableView(v: View) {
                                        v.isClickable = false
                                        v.isFocusable = false
                                        v.isEnabled = false
                                        if (v is android.view.ViewGroup) {
                                                for (i in 0 until v.childCount) {
                                                        disableView(v.getChildAt(i))
                                                }
                                        }
                                }
                                disableView(previewView)
                                if (previewView is android.view.ViewGroup) {
                                        previewView.descendantFocusability =
                                                android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                }

                                // Add to Container
                                // Individualized Dimensions (V21.12: Even Shorter)
                                val (pWidth, pHeight) =
                                        when (def.type) {
                                                "SELECTOR" -> Pair(dpToPx(85), dpToPx(36))
                                                "SLIDER" -> Pair(dpToPx(76), dpToPx(36))
                                                "JOYSTICK" -> Pair(dpToPx(42), dpToPx(42))
                                                "BUTTON" -> Pair(dpToPx(50), dpToPx(42))
                                                "SWITCH" -> Pair(dpToPx(40), dpToPx(42))
                                                "CAMERA" ->
                                                        Pair(
                                                                android.widget.FrameLayout
                                                                        .LayoutParams.MATCH_PARENT,
                                                                dpToPx(36)
                                                        )
                                                "INPUT" ->
                                                        Pair(
                                                                android.widget.FrameLayout
                                                                        .LayoutParams.MATCH_PARENT,
                                                                dpToPx(32)
                                                        )
                                                "LED" -> Pair(dpToPx(32), dpToPx(32))
                                                "THERMOMETER", "LEVEL" ->
                                                        Pair(dpToPx(32), dpToPx(40))
                                                "TEXT", "IMAGE" -> Pair(dpToPx(100), dpToPx(32))
                                                "CHART" ->
                                                        Pair(
                                                                android.widget.FrameLayout
                                                                        .LayoutParams.MATCH_PARENT,
                                                                dpToPx(38)
                                                        )
                                                else ->
                                                        Pair(
                                                                android.widget.FrameLayout
                                                                        .LayoutParams.MATCH_PARENT,
                                                                dpToPx(32)
                                                        )
                                        }

                                val previewParams =
                                        android.widget.FrameLayout.LayoutParams(pWidth, pHeight)
                                                .apply {
                                                        gravity =
                                                                android.view.Gravity
                                                                        .CENTER_HORIZONTAL or
                                                                        android.view.Gravity.BOTTOM
                                                }
                                previewView.layoutParams = previewParams
                                previewContainer.addView(previewView)
                                previewContainer.isClickable = false
                                previewContainer.isFocusable = false
                                previewContainer.isEnabled = false

                                // No more img.setColorFilter needed (handled by onUpdateView with
                                // props)

                                val tv = card.findViewById<android.widget.TextView>(R.id.tvLabel)

                                // Localized Label
                                val labelResName = "component_label_${def.type.lowercase()}"
                                val labelId =
                                        rootView.resources.getIdentifier(
                                                labelResName,
                                                "string",
                                                rootView.context.packageName
                                        )
                                val labelText =
                                        if (labelId != 0) {
                                                rootView.context.getString(labelId)
                                        } else {
                                                def.type.lowercase().replaceFirstChar {
                                                        it.uppercase()
                                                }
                                        }
                                tv.text = labelText

                                // Card Layout Params for Grid (Square-ish Box)
                                val params = android.widget.GridLayout.LayoutParams()
                                // Fixed width to prevent single-item stretching. Sidebar ~280dp.
                                // 280 - 32(padding) - 24(margins) = 224 / 2 = 112. Safe: 108dp.
                                params.width = dpToPx(108)
                                params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                                params.columnSpec =
                                        android.widget.GridLayout.spec(
                                                android.widget.GridLayout.UNDEFINED
                                        ) // Removed Weight 1f
                                params.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                                card.layoutParams = params

                                card.tag = def.type
                                card.setOnTouchListener(touchListener)

                                grid.addView(card)

                                // Dynamic Card Border
                                if (card is com.google.android.material.card.MaterialCardView) {
                                        card.strokeColor = groupColorInt
                                        card.strokeWidth = dpToPx(2) // Make it more visible
                                }

                                // Add to search list
                                searchList.add(Triple(card, def.type, labelText))
                        }

                        // Set initial state (Only first one open)
                        val firstOpen = categoryGrids.size == 1
                        grid.visibility = if (firstOpen) View.VISIBLE else View.GONE
                        ivArrow.rotation = if (firstOpen) 0f else 180f
                } // End Group Loop

                // Search Logic
                val etSearch =
                        rootView.findViewById<android.widget.EditText>(R.id.etSearchComponents)
                                ?: return

                // Clear Button (DrawableEnd) Logic using Touch Listener
                etSearch.setOnTouchListener { v, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                                if (etSearch.compoundDrawables[2] != null) {
                                        // Check if touch is on the drawable (Right side)
                                        // The drawable is on the END (index 2).
                                        // Touch X >= (Width - PaddingRight - DrawableWidth)
                                        val drawableWidth =
                                                etSearch.compoundDrawables[2].bounds.width()
                                        // Add some slop/padding for easier click
                                        val touchAreaStart =
                                                etSearch.width -
                                                        etSearch.paddingEnd -
                                                        drawableWidth -
                                                        dpToPx(20)

                                        if (event.x >= touchAreaStart) {
                                                etSearch.text.clear()
                                                v.performClick()
                                                return@setOnTouchListener true
                                        }
                                }
                        }
                        return@setOnTouchListener false
                }

                etSearch.addTextChangedListener(
                        object : android.text.TextWatcher {
                                override fun beforeTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        count: Int,
                                        after: Int
                                ) {}

                                override fun onTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        before: Int,
                                        count: Int
                                ) {
                                        val query = s.toString().trim().lowercase()

                                        searchList.forEach { (view, tag, label) ->
                                                val match =
                                                        tag.contains(query, ignoreCase = true) ||
                                                                label.contains(
                                                                        query,
                                                                        ignoreCase = true
                                                                )

                                                if (query.isEmpty() || match) {
                                                        view.visibility = View.VISIBLE
                                                } else {
                                                        view.visibility = View.GONE
                                                }
                                        }
                                }

                                override fun afterTextChanged(s: android.text.Editable?) {}
                        }
                )
        }
}
