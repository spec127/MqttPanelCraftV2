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
import com.example.mqttpanelcraft.ui.views.LedView
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

                // 強制 ScrollView 以 GPU Outline 嚴格裁切，解決上滑溢出格線的問題
                val scrollView = rootView.findViewById<android.widget.ScrollView>(R.id.sidebarScrollView)
                scrollView?.apply {
                        clipToOutline = true
                        outlineProvider = object : android.view.ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: android.graphics.Outline) {
                                        outline.setRect(0, 0, view.width, view.height)
                                }
                        }
                        clipChildren = true
                        clipToPadding = true
                }

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
                                                                        props = def.getDefaultProps().toMutableMap()
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

                        // 2. Grid Container (2 Columns) — 啟用裁切避免溢出
                        val grid =
                                android.widget.GridLayout(rootView.context).apply {
                                        columnCount = 2
                                        alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
                                        clipChildren = true
                                        clipToPadding = true
                                        layoutParams =
                                                android.widget.LinearLayout.LayoutParams(
                                                        android.widget.LinearLayout.LayoutParams
                                                                .MATCH_PARENT,
                                                        android.widget.LinearLayout.LayoutParams
                                                                .WRAP_CONTENT
                                                )
                                }
                        container.addView(grid)

                        // Accordion Logic: 使用高度動畫 + 箭頭旋轉實現流暢展開/合起效果
                        var isAccordionAnimating = false
                        fun updateCategoryState(targetIndex: Int) {
                                if (isAccordionAnimating) return
                                val wasOpen = categoryGrids[targetIndex].visibility == View.VISIBLE

                                isAccordionAnimating = true
                                for (i in categoryGrids.indices) {
                                        val expand = (i == targetIndex) && !wasOpen
                                        val gridView = categoryGrids[i]
                                        val arrowView = categoryArrowIcons[i]

                                        // 箭頭旋轉動畫
                                        arrowView.animate()
                                                .rotation(if (expand) 0f else 180f)
                                                .setDuration(250)
                                                .start()

                                        if (expand) {
                                                // 展開：先設 VISIBLE，量測高度後從 0 動畫到完整高度
                                                gridView.visibility = View.VISIBLE
                                                gridView.alpha = 1f
                                                gridView.measure(
                                                        View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.EXACTLY),
                                                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                                                )
                                                val targetHeight = gridView.measuredHeight
                                                gridView.layoutParams.height = 0
                                                gridView.requestLayout()

                                                val anim = android.animation.ValueAnimator.ofInt(0, targetHeight)
                                                anim.addUpdateListener { animator ->
                                                        gridView.layoutParams.height = animator.animatedValue as Int
                                                        gridView.requestLayout()
                                                }
                                                anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                                                        override fun onAnimationEnd(animation: android.animation.Animator) {
                                                                gridView.layoutParams.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                                                gridView.requestLayout()
                                                        }
                                                })
                                                anim.duration = 250
                                                anim.interpolator = android.view.animation.DecelerateInterpolator()
                                                anim.start()
                                        } else if (gridView.visibility == View.VISIBLE) {
                                                // 合起：從當前高度動畫到 0 後設 GONE
                                                val startHeight = gridView.height
                                                val anim = android.animation.ValueAnimator.ofInt(startHeight, 0)
                                                anim.addUpdateListener { animator ->
                                                        gridView.layoutParams.height = animator.animatedValue as Int
                                                        gridView.requestLayout()
                                                }
                                                anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                                                        override fun onAnimationEnd(animation: android.animation.Animator) {
                                                                gridView.visibility = View.GONE
                                                                gridView.layoutParams.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                                                gridView.alpha = 1f
                                                        }
                                                })
                                                anim.duration = 200
                                                anim.interpolator = android.view.animation.AccelerateInterpolator()
                                                anim.start()
                                        }
                                }
                                container.postDelayed({ isAccordionAnimating = false }, 280)
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

                                val usesPaletteIcon = def.type == "WEB_BOX"
                                val previewView =
                                        if (usesPaletteIcon) {
                                                ImageView(rootView.context).apply {
                                                        setImageResource(def.iconResId)
                                                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                                                        setColorFilter(groupColorInt)
                                                        setPadding(
                                                                dpToPx(1),
                                                                dpToPx(1),
                                                                dpToPx(1),
                                                                dpToPx(1)
                                                        )
                                                }
                                        } else {
                                                def.createView(rootView.context, false)
                                        }
                                previewView.background = null // Card has border, component does not

                                // Init Dummy Data from Definition defaults (Single Source of Truth matching dragged instances)
                                val dummyProps = def.getDefaultProps().toMutableMap()
                                if (!dummyProps.containsKey("color")) dummyProps["color"] = "#2196F3"
                                if (!dummyProps.containsKey("colorOn")) dummyProps["colorOn"] = dummyProps["color"] ?: "#2196F3"
                                if (!dummyProps.containsKey("colorOff")) dummyProps["colorOff"] = "#BDBDBD"

                                // Special handling for preview look only
                                if (def.type == "BUTTON") {
                                        dummyProps["text"] = "" // Remove text in small thumbnail
                                        dummyProps["label"] = ""
                                }
                                if (def.type == "CHART") {
                                        dummyProps["is_preview"] = "true"
                                        dummyProps["series_color_1"] = "#FF9800"
                                } else if (def.type == "SWITCH") {
                                        dummyProps["state"] = "2" // Show solid ON color in thumbnail
                                } else if (def.type == "CALENDAR") {
                                        dummyProps["visual_style"] = "BIG_DATE"
                                } else if (def.type == "CLOCK") {
                                        dummyProps["clock_mode"] = "TIME"
                                        dummyProps["visual_style"] = "DIGITAL"
                                        dummyProps["time_format"] = "HH:mm"
                                }

                                val dummyData =
                                        com.example.mqttpanelcraft.model.ComponentData(
                                                id = -1,
                                                type = def.type,
                                                x = 0f,
                                                y = 0f,
                                                width = 100,
                                                height = 100,
                                                label = "",
                                                topicConfig = "",
                                                props = dummyProps
                                        )

                                // Update View Appearance
                                if (!usesPaletteIcon) def.onUpdateView(previewView, dummyData)

                                // V21.12: Force LED to be ON in sidebar preview
                                if (def.type == "LED" && previewView is android.view.ViewGroup) {
                                        val ledView = previewView.getChildAt(0) as? com.example.mqttpanelcraft.ui.views.LedView
                                        ledView?.isActive = true
                                        ledView?.effect = com.example.mqttpanelcraft.ui.views.LedView.Effect.NONE
                                }

                                // Disable interaction on preview recursively
                                fun disableView(v: View) {
                                        v.isClickable = false
                                        v.isFocusable = false
                                        v.isFocusableInTouchMode = false
                                        v.isEnabled = false
                                        v.setOnTouchListener { _, _ -> false }
                                        if (v is android.widget.TextView) {
                                                v.movementMethod = null
                                        }
                                        if (v is android.widget.EditText) {
                                                v.isSingleLine = true
                                                v.maxLines = 1
                                                v.isVerticalScrollBarEnabled = false
                                                v.isHorizontalScrollBarEnabled = false
                                        }
                                        if (v is com.example.mqttpanelcraft.ui.views.TextDisplayView) {
                                                v.isScrollable = false
                                                v.displayLines = 1
                                        }
                                        if (v is android.widget.ScrollView || v is androidx.core.widget.NestedScrollView) {
                                                v.requestDisallowInterceptTouchEvent(false)
                                                v.setOnTouchListener { _, _ -> false }
                                        }
                                        if (v is android.view.ViewGroup) {
                                                v.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
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

                                // Add to Container (slightly scaled down so bottom is never clipped)
                                val (pWidth, pHeight) =
                                        when (def.type) {
                                                "SELECTOR" -> Pair(dpToPx(85), dpToPx(34))
                                                "SLIDER" -> Pair(dpToPx(76), dpToPx(34))
                                                "DPAD", "JOYSTICK" -> Pair(dpToPx(52), dpToPx(52))
                                                "PALETTE" -> Pair(dpToPx(46), dpToPx(46))
                                                "BUTTON" -> Pair(dpToPx(50), dpToPx(38))
                                                "BROADCAST" -> Pair(dpToPx(88), dpToPx(40))
                                                "SWITCH" -> Pair(dpToPx(40), dpToPx(38))
                                                "CAMERA" ->
                                                        Pair(
                                                                android.widget.FrameLayout
                                                                        .LayoutParams.MATCH_PARENT,
                                                                dpToPx(34)
                                                        )
                                                "INPUT", "INPUTBOX" -> Pair(dpToPx(96), dpToPx(30))
                                                "LED" -> Pair(dpToPx(52), dpToPx(52))
                                                "SCALE_METER" -> Pair(dpToPx(96), dpToPx(48))
                                                "GAUGE_METER" -> Pair(dpToPx(56), dpToPx(56))
                                                "THERMOMETER", "LEVEL" ->
                                                        Pair(dpToPx(32), dpToPx(40))
                                                "TEXT" -> Pair(dpToPx(96), dpToPx(30))
                                                "IMAGE", "IMAGE_SENSOR" -> Pair(dpToPx(56), dpToPx(42))
                                                "GRAPHIC" -> Pair(dpToPx(56), dpToPx(56))
                                                "TEXT_DISPLAY" -> Pair(dpToPx(96), dpToPx(34))
                                                "STEPPER" -> Pair(dpToPx(60), dpToPx(26))
                                                "CHART" ->
                                                        Pair(
                                                                dpToPx(72),
                                                                dpToPx(36)
                                                        )
                                                "WEB_BOX" -> Pair(dpToPx(56), dpToPx(56))
                                                "CALENDAR" -> Pair(dpToPx(44), dpToPx(44))
                                                "CLOCK" -> Pair(dpToPx(68), dpToPx(38))
                                                else ->
                                                        Pair(
                                                                android.widget.FrameLayout
                                                                        .LayoutParams.MATCH_PARENT,
                                                                dpToPx(30)
                                                        )
                                        }

                                val previewParams =
                                        android.widget.FrameLayout.LayoutParams(pWidth, pHeight)
                                previewParams.gravity = android.view.Gravity.CENTER

                                if (def.type == "PALETTE") {
                                        previewParams.setMargins(0, 0, 0, dpToPx(2))
                                } else if (def.type == "LED" || def.type == "GAUGE_METER" || def.type == "SCALE_METER") {
                                        previewParams.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                                }
                                previewView.layoutParams = previewParams
                                previewContainer.addView(previewView)
                                previewContainer.isClickable = false
                                previewContainer.isFocusable = false
                                previewContainer.isEnabled = false

                                // Transparent shield on top of previewView to block ALL touch interactions and pass to card drag
                                val touchShield = View(rootView.context)
                                touchShield.layoutParams = android.widget.FrameLayout.LayoutParams(
                                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                touchShield.setOnTouchListener(touchListener)
                                previewContainer.addView(touchShield)

                                // No more img.setColorFilter needed (handled by onUpdateView with
                                // props)

                                val tv = card.findViewById<android.widget.TextView>(R.id.tvLabel)
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
                                val params = card.layoutParams as android.widget.GridLayout.LayoutParams
                                params.width = 0
                                params.height = dpToPx(100) // EXPLICIT HEIGHT 100dp
                                params.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
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
