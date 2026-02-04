package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.content.res.ColorStateList
import android.graphics.Color
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object ButtonDefinition : IComponentDefinition {
    
    // 1. Identity
    override val type = "BUTTON"
    override val defaultSize = Size(120, 60)
    override val labelPrefix = "btn"
    override val iconResId = R.drawable.ic_btn_power
    override val group = "CONTROL"

    // 2. Factory (Appearance)
    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        
        val button = androidx.appcompat.widget.AppCompatButton(context).apply { 
            text = "BTN"
            stateListAnimator = null
            elevation = 0f
            textSize = 14f
            isAllCaps = false
            tag = "target" // Mark as target for behavior
            
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
                setMargins(8, 8, 8, 8)
            }
        }
        container.addView(button, 0)
        
        // Overlay ImageView for large centered icon
        val iconView = ImageView(context).apply {
            tag = "ICON_OVERLAY"
            visibility = View.GONE
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = false
            isFocusable = false
            elevation = 50f // Very high to ensure on top of button
            translationZ = 50f
        }
        container.addView(iconView)
        
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val button = findButtonIn(container) ?: return
        
        // 1. Color
        val colorHex = data.props["color"] ?: "#7C3AED" // Default Vivid Purple
        val color = try { android.graphics.Color.parseColor(colorHex) } catch(e: Exception) { android.graphics.Color.LTGRAY }
        
        // 2. Shape
        // 2. Shape & Tactile Background
        val shapeMode = data.props["shape"] ?: "pill" // circle, rect, pill
        val cornerRadius = when(shapeMode) {
             "rect" -> 16f
             "pill" -> 100f
             else -> 100f // circle handled via OVAL shape
        }
        val isOval = (shapeMode == "circle")
        
        button.background = createTactileDrawable(color, isOval, cornerRadius)
        
        // Elevation (Physical feel)
        button.elevation = 6f
        button.stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(button.context, R.animator.anim_btn_tactile_elevation)
        // If animator resource missing, fall back to manual elevation or null
        // Since I haven't created the XML animator yet, I'll use simple ViewPropertyAnimator in code or just rely on elevation.
        // Actually, let's create the animator XML next. For now, set elevation.
        
        // 3. Mode (Text vs Icon)
        val mode = data.props["mode"] ?: "text" // text, icon
        
        if (mode == "icon") {
            button.text = ""
        } else {
            // Text or Text+Icon
            button.text = data.props["text"] ?: data.label
            // Color set below for unified logic
        }


        
        // Set Colors (Text & Icon)
        val contentColor = if (isLightColor(color)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        button.setTextColor(contentColor)
        
        // Find Overlay Icon
        val overlayIcon = container.findViewWithTag<ImageView>("ICON_OVERLAY")
        
        if (mode == "icon") {
            // ICON ONLY MODE: Use Overlay for perfect centering and resizing
            button.text = ""
            button.setCompoundDrawables(null, null, null, null)
            
            if (overlayIcon != null) {
                overlayIcon.visibility = View.VISIBLE
                
                // key mapping
                val iconKey = data.props["icon"]
                
                if (iconKey == "none") {
                    overlayIcon.visibility = View.GONE
                } else {
                    val iconRes = when(iconKey) {
                        "power" -> R.drawable.ic_btn_power
                        "lighting" -> R.drawable.ic_btn_lighting
                        "fan" -> R.drawable.ic_btn_fan
                        "play" -> R.drawable.ic_btn_play
                        "tune" -> R.drawable.ic_btn_tune
                        "energy" -> R.drawable.ic_btn_energy
                        else -> R.drawable.ic_btn_power // Default
                    }
                    
                    overlayIcon.setImageResource(iconRes)
                    overlayIcon.setColorFilter(contentColor)
                    
                    // Size Calculation: 75% of button
                    val w = data.width
                    val h = data.height
                    
                    // Safety check for 0 width/height during init
                    if (w > 0 && h > 0) {
                        val minDim = kotlin.math.min(w, h)
                        val targetSize = (minDim * 0.75f).toInt()
                        
                        val params = overlayIcon.layoutParams as? FrameLayout.LayoutParams 
                            ?: FrameLayout.LayoutParams(targetSize, targetSize)
                        
                        params.width = targetSize
                        params.height = targetSize
                        params.gravity = Gravity.CENTER
                        overlayIcon.layoutParams = params
                    }
                    overlayIcon.bringToFront()
                    overlayIcon.requestLayout()
                }
            }
            
        } else {
            // TEXT or TEXT+ICON Mode: Use standard button features
            overlayIcon?.visibility = View.GONE
            
            if (mode == "text") {
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            } else {
                // Text+Icon
                val iconKey = data.props["icon"]
                 val iconRes = when(iconKey) {
                    "power" -> R.drawable.ic_btn_power
                    "lighting" -> R.drawable.ic_btn_lighting
                    "fan" -> R.drawable.ic_btn_fan
                    "play" -> R.drawable.ic_btn_play
                    "tune" -> R.drawable.ic_btn_tune
                    "energy" -> R.drawable.ic_btn_energy
                    else -> R.drawable.ic_btn_power
                }
                val d = androidx.core.content.ContextCompat.getDrawable(button.context, iconRes)?.mutate()
                d?.setTint(contentColor) 
                
                // Standard Size (24dp)
                val density = button.resources.displayMetrics.density
                val size = (24 * density).toInt()
                d?.setBounds(0, 0, size, size)
                
                button.setCompoundDrawables(d, null, null, null)
                button.gravity = android.view.Gravity.CENTER
                button.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                button.compoundDrawablePadding = 16 
            }
        }
        
        button.isClickable = true
        button.isFocusable = true
    }
    
    private fun isLightColor(color: Int): Boolean {
        return androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5
    }

    private fun createTactileDrawable(baseColor: Int, isOval: Boolean, radius: Float): android.graphics.drawable.Drawable {
        // Normal State Drawable
        val normalDrawable = createTactileLayer(baseColor, isOval, radius, false)
        
        // Pressed State Drawable
        val pressedDrawable = createTactileLayer(baseColor, isOval, radius, true)
        
        val sld = android.graphics.drawable.StateListDrawable()
        sld.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
        sld.addState(intArrayOf(), normalDrawable)
        
        // Ensure changes in state trigger redraw
        // In some API levels, resizing layers inside StateListDrawable needs explicit bounds handling, 
        // but typically standard Views handle it fine.
        return sld
    }
    
    private fun createTactileLayer(color: Int, isOval: Boolean, radius: Float, isPressed: Boolean): android.graphics.drawable.LayerDrawable {
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        // 3D Height Configuration (Increased for visibility)
        val heightNormal = (8 * density).toInt()
        
        // When pressed, top inset increases (shift down), bottom inset becomes 0 (shadow hidden)
        val topInset = if (isPressed) heightNormal else 0
        val bottomInset = if (isPressed) 0 else heightNormal
        
        // Layer 0: Side/Bottom (Darker Shadow)
        // This is the full background, but effectively visible only at the bottom
        val sideColor = darkenColor(color, 0.6f) // Darker for the side
        val sideLayer = android.graphics.drawable.GradientDrawable().apply {
            setColor(sideColor)
            if (isOval) shape = android.graphics.drawable.GradientDrawable.OVAL
            else {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = radius
            }
        }
        
        // Layer 1: Face (Main Color)
        val faceLayer = android.graphics.drawable.GradientDrawable().apply {
            setColor(color) // Keep original color even when pressed
            if (isOval) shape = android.graphics.drawable.GradientDrawable.OVAL
            else {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = radius
            }
        }
        
        // Layer 2: Gloss/Gradient (On Top of Face)
        // ADJUSTED: Removed heavy black overlay for pressed state to avoid "grey" look.
        val gradientColors = if (isPressed) {
            intArrayOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(android.graphics.Color.WHITE, 20), // Subtle shine even when pressed
                androidx.core.graphics.ColorUtils.setAlphaComponent(android.graphics.Color.WHITE, 0)
            )
        } else {
             intArrayOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(android.graphics.Color.WHITE, 100), // High Gloss Top
                androidx.core.graphics.ColorUtils.setAlphaComponent(android.graphics.Color.WHITE, 0)
            )
        }
         val glossLayer = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            gradientColors
        ).apply {
             if (isOval) shape = android.graphics.drawable.GradientDrawable.OVAL
             else {
                 shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                 cornerRadius = radius
             }
             gradientType = android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT
        }
        
        // Combine Face + Gloss
        val faceWithGloss = android.graphics.drawable.LayerDrawable(arrayOf(faceLayer, glossLayer))
        
        // Final Composite
        // We wrap faceWithGloss to apply insets
        val layers = arrayOf(sideLayer, faceWithGloss)
        val layerDrawable = android.graphics.drawable.LayerDrawable(layers)
        
        // Inset Side Layer (Index 0) - NEW: Shift down when pressed to avoid dark top artifact
        val sideTopInset = if (isPressed) heightNormal else 0
        layerDrawable.setLayerInset(0, 0, sideTopInset, 0, 0)
        
        // Inset Face Layer (Index 1)
        // left, top, right, bottom
        layerDrawable.setLayerInset(1, 0, topInset, 0, bottomInset)
        
        return layerDrawable
    }
    
    private fun darkenColor(color: Int, factor: Float): Int {
        val a = android.graphics.Color.alpha(color)
        val r = (android.graphics.Color.red(color) * factor).toInt()
        val g = (android.graphics.Color.green(color) * factor).toInt()
        val b = (android.graphics.Color.blue(color) * factor).toInt()
        return android.graphics.Color.argb(a, r, g, b)
    }

    // 3. Properties (Binder)
    override val propertiesLayoutId = R.layout.layout_prop_button
    
    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
        val context = panelView.context
        
        // --- 0. Appearance Mode (Dropdown) & Content ---
        val spApprMode = panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.spPropApprMode)
        val containerText = panelView.findViewById<View>(R.id.containerPropText)
        val containerIcon = panelView.findViewById<View>(R.id.containerPropIcon)
        val etText = panelView.findViewById<EditText>(R.id.etPropText)

        // Init Text Value
        etText?.setText(data.props["text"] ?: "")
        etText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { onUpdate("text", s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // Icon Binding
        val iconMap = mapOf(
            R.id.iconPreview0 to "none",
            R.id.iconPreview1 to "power",
            R.id.iconPreview2 to "lighting",
            R.id.iconPreview3 to "fan",
            R.id.iconPreview4 to "play",
            R.id.iconPreview5 to "tune",
            R.id.iconPreview6 to "energy"
        )
        iconMap.forEach { (id, key) ->
             panelView.findViewById<View>(id)?.setOnClickListener { onUpdate("icon", key) }
        }
        
        // Helper to update visibility
        fun updateApprMode(mode: String) {
            when(mode) {
                "text" -> {
                    containerText?.visibility = View.VISIBLE
                    containerIcon?.visibility = View.GONE
                }
                "icon" -> {
                    containerText?.visibility = View.GONE
                    containerIcon?.visibility = View.VISIBLE
                }
                "text_icon" -> {
                    containerText?.visibility = View.VISIBLE
                    containerIcon?.visibility = View.VISIBLE
                }
            }
        }

        // Init Mode Dropdown
        val appModes = listOf(
            context.getString(R.string.properties_mode_text),
            context.getString(R.string.properties_mode_icon),
            context.getString(R.string.properties_mode_text_icon)
        )
        val appModeAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, appModes)
        spApprMode?.setAdapter(appModeAdapter)
        
        val currentMode = data.props["mode"] ?: "text"
        val displayAppMode = when(currentMode) {
             "icon" -> context.getString(R.string.properties_mode_icon)
             "text_icon" -> context.getString(R.string.properties_mode_text_icon)
             else -> context.getString(R.string.properties_mode_text)
        }
        spApprMode?.setText(displayAppMode, false)
        updateApprMode(currentMode)
        
        spApprMode?.setOnItemClickListener { _, _, position, _ ->
             val newMode = when(position) {
                 1 -> "icon"
                 2 -> "text_icon"
                 else -> "text"
             }
             onUpdate("mode", newMode)
             updateApprMode(newMode)
        }


        // --- 1. Shape Spinner --- (unchanged, skipping lines 167-194)

        // ... [Skipping Shape Spinner Block for simplicity in replacement if possible, or I include it] ...
        // Since I'm using replace_file_content, I must include or carefully target.
        // I will target the RANGE from Line 130 to 269? That covers Shape too.
        // No, I can do MultiReplace.
        // Wait, the user tool call had StartLine 130 EndLine 269.
        // I should just replace the Whole Block if it's easier.
        // But Shape Spinner is in the middle (Lines 167-194).
        // I will use MultiChunk replacement.
        
        // Actually, looking at the range: 130 (Top Appr) -> ... -> 167 (Shape) -> ... -> 197 (Payload) -> 220 (Trigger)
        // I need to replace 130-165 (Appr) AND 221-269 (Trigger).
        // I will use `multi_replace_file_content` ? No, `replace_file_content` is requested?
        // Ah, I am the model.
        // I will use `multi_replace_file_content`.
        
        // Wait, the previous tool call instructions for ME? No.
        // I will generate a `multi_replace_file_content` call.


        // --- 1. Shape Spinner ---
        val spShape = panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.spPropShape)
        // Values: "Rounded", "Circle", "Rect" -> Translated
        val shapeLabels = listOf(
            context.getString(R.string.val_shape_rounded),
            context.getString(R.string.val_shape_circle),
            context.getString(R.string.val_shape_rect)
        )
        val shapeAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, shapeLabels)
        spShape.setAdapter(shapeAdapter)
        
        // Init Value
        val currentShape = data.props["shape"] ?: "pill"
        spShape.setText(when(currentShape) {
            "circle" -> context.getString(R.string.val_shape_circle)
            "rect" -> context.getString(R.string.val_shape_rect)
            else -> context.getString(R.string.val_shape_rounded)
        }, false)
        
        spShape.setOnItemClickListener { _, _, position, _ ->
            // Map Back (0->pill, 1->circle, 2->rect)
            val code = when(position) {
                1 -> "circle"
                2 -> "rect"
                else -> "pill"
            }
            onUpdate("shape", code)
        }


        // --- 4. Press Payload (Dropdown) ---
        val spPress = panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.etPropPayloadPress)
        if (spPress != null) {
            val pressAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, listOf("ON", "OFF", "TRUE", "FALSE", "1", "0"))
            spPress.setAdapter(pressAdapter)
            spPress.threshold = 0
            
            val currPress = data.props["payloadPressed"] ?: data.props["payload"]
            val displayText = if (currPress.isNullOrEmpty()) "ON" else currPress
            spPress.setText(displayText, false)
            
            spPress.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { onUpdate("payloadPressed", s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            
            spPress.setOnItemClickListener { _, _, position, _ ->
                 val sel = pressAdapter.getItem(position) ?: "ON"
                 onUpdate("payloadPressed", sel)
            }
        }

        // --- 5. Button Mode & Dynamic Inputs ---
        val toggleTrigger = panelView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.togglePropTrigger)
        val tvTriggerDesc = panelView.findViewById<TextView>(R.id.tvPropTriggerDesc) // New Description
        val containerReleaseOnly = panelView.findViewById<View>(R.id.containerReleaseOnly)
        val containerTimerMode = panelView.findViewById<View>(R.id.containerTimerMode)
        val etTimer = panelView.findViewById<EditText>(R.id.etPropTimer)
        
        // Helper to update visibility & Description
        fun updateVisibility(mode: String) {
            when (mode.lowercase()) {
                "timer" -> {
                    containerReleaseOnly?.visibility = View.GONE
                    containerTimerMode?.visibility = View.VISIBLE
                    tvTriggerDesc?.text = context.getString(R.string.desc_mode_timer)
                }
                "momentary" -> {
                    containerReleaseOnly?.visibility = View.VISIBLE
                    containerTimerMode?.visibility = View.GONE
                    tvTriggerDesc?.text = context.getString(R.string.desc_mode_hold)
                }
                else -> { // Standard
                    containerReleaseOnly?.visibility = View.GONE 
                    containerTimerMode?.visibility = View.GONE
                    tvTriggerDesc?.text = context.getString(R.string.desc_mode_tap)
                }
            }
        }

        // Init Mode
        val currMode = data.props["triggerMode"] ?: "Standard"
        if (toggleTrigger != null) {
            when(currMode.lowercase()) {
                "momentary" -> toggleTrigger.check(R.id.btnTriggerHold)
                "timer" -> toggleTrigger.check(R.id.btnTriggerTimer)
                else -> toggleTrigger.check(R.id.btnTriggerTap)
            }
            
            toggleTrigger.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                     val code = when(checkedId) {
                        R.id.btnTriggerHold -> "Momentary"
                        R.id.btnTriggerTimer -> "Timer"
                        else -> "Standard"
                    }
                    onUpdate("triggerMode", code)
                    updateVisibility(code)
                }
            }
        }
        updateVisibility(currMode)
        
        // Release Payload
        val etRelease1 = panelView.findViewById<EditText>(R.id.etPropPayloadRelease)
        val etRelease2 = panelView.findViewById<EditText>(R.id.etPropPayloadReleaseRef)
        
        val releaseVal = data.props["payloadReleased"] ?: "0"
        etRelease1?.setText(releaseVal)
        etRelease2?.setText(releaseVal)
        
        val releaseWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { onUpdate("payloadReleased", s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        etRelease1?.addTextChangedListener(releaseWatcher)
        etRelease2?.addTextChangedListener(releaseWatcher) 
        
        // Timer Input
        etTimer?.setText(data.props["timerDuration"] ?: "1000")
        etTimer?.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) { onUpdate("timerDuration", s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // --- 6. Color Palette (Memory) ---
        // Load recent colors from SP using ProjectId
        val projectId = (context as? com.example.mqttpanelcraft.ProjectViewActivity)?.getViewModelAccess()?.project?.value?.id ?: "global"
        val prefs = context.getSharedPreferences("colors_$projectId", Context.MODE_PRIVATE)
        
        fun loadRecentColors(): List<String> {
            val saved = prefs.getString("recent_colors", null)
            return saved?.split(",")?.filter { it.isNotEmpty() } ?: listOf("#a573bc", "#2196F3", "#4CAF50", "#FFC107", "#FF5722") // Default
        }
        
        fun saveRecentColor(newColor: String) {
            val current = loadRecentColors().toMutableList()
            current.remove(newColor) // Move to top
            current.add(0, newColor)
            if (current.size > 5) current.removeAt(current.size - 1)
            prefs.edit().putString("recent_colors", current.joinToString(",")).apply()
        }
        
        val recentColors = loadRecentColors()
        
        // Bind to Views (vColor1..5)
        val colorViews = listOf(
            panelView.findViewById<View>(R.id.vColor1),
            panelView.findViewById<View>(R.id.vColor2),
            panelView.findViewById<View>(R.id.vColor3),
            panelView.findViewById<View>(R.id.vColor4),
            panelView.findViewById<View>(R.id.vColor5)
        )
        
        colorViews.forEachIndexed { index, view ->
            if (index < recentColors.size) {
                val cHex = recentColors[index]
                try {
                    val colorInt = android.graphics.Color.parseColor(cHex)
                    view?.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
                    view?.setOnClickListener { onUpdate("color", cHex) }
                } catch(e: Exception) {}
            }
        }
        
        // Custom Color
        panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener {
             val cur = data.props["color"] ?: "#a573bc"
             var tempColor = cur 
             
             com.example.mqttpanelcraft.ui.ColorPickerDialog(
                 context, 
                 cur, 
                 true,
                 onColorSelected = { newColor ->
                     tempColor = newColor
                     onUpdate("color", newColor)
                 },
                 onDismiss = {
                     // Save ONLY when closed
                     if (tempColor != cur) { // Save only if changed? Or always? User said "save only when closed" to avoid flood.
                        saveRecentColor(tempColor)
                        // Refresh Palette logic (Visually update views with NEW LIST including the new saved color)
                         val updated = loadRecentColors()
                         colorViews.forEachIndexed { i, v ->
                             if (i < updated.size) {
                                 v?.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(updated[i]))
                                 v?.setOnClickListener { onUpdate("color", updated[i]) }
                             }
                         }
                     }
                 }
             ).show(it)
        }
        

    }
    
    // Removed helpers (updateModeVisibility, updateIconPreview, showIconSelector) as they are unused now.

    // 4. Runtime Behavior
    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        val container = view as? FrameLayout ?: return
        val button = findButtonIn(container) ?: return
        
        button.setOnTouchListener { v, event ->
            val topic = data.topicConfig
            if (topic.isNotEmpty()) {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        // Press
                        if (data.props["haptic"] == "true") {
                             v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        // Visual
                        v.alpha = 0.7f
                        
                        // Payload
                        val pPress = data.props["payloadPressed"] ?: data.props["payload"] ?: "1"
                        if (pPress.isNotEmpty()) sendMqtt(topic, pPress)
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        // Release
                        v.alpha = 1.0f
                        
                        // Payload
                        val pRelease = data.props["payloadReleased"]
                        // Only send release payload if explicitly set? Or default 0?
                        // User requirement said "Separate payloads". 
                        // If empty, do nothing?
                        if (!pRelease.isNullOrEmpty()) sendMqtt(topic, pRelease)
                    }
                }
            }
            true // Consume event
        }
    }

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {}

    private fun findButtonIn(container: FrameLayout): androidx.appcompat.widget.AppCompatButton? {
        for (i in 0 until container.childCount) {
             val child = container.getChildAt(i)
             if (child is androidx.appcompat.widget.AppCompatButton) return child
        }
        return null
    }
}
