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
    // Larger default size as requested
    override val defaultSize = Size(120, 60)
    override val labelPrefix = "button"
    override val iconResId = R.drawable.ic_btn_power
    override val group = "CONTROL"

    // 2. Factory (Appearance)
    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        
        val button = androidx.appcompat.widget.AppCompatButton(context).apply { 
             text = "Button" // Default Text
             stateListAnimator = null
             elevation = 0f
             textSize = 14f
             isAllCaps = false
             tag = "target"
             
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
            elevation = 50f 
            translationZ = 50f
        }
        container.addView(iconView)
        
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val button = findButtonIn(container) ?: return
        
        // 1. Color
        val colorHex = data.props["color"] ?: "#00B0FF" // Default Vivid Blue
        val color = try { android.graphics.Color.parseColor(colorHex) } catch(e: Exception) { android.graphics.Color.LTGRAY }
        
        // 2. Shape
        val shapeMode = data.props["shape"] ?: "pill" // circle, rect, pill
        val cornerRadius = when(shapeMode) {
             "rect" -> 16f
             "pill" -> 100f
             else -> 100f // circle handled via OVAL shape
        }
        val isOval = (shapeMode == "circle")
        
        // Create Visuals (Updated)
        button.background = createTactileDrawable(color, isOval, cornerRadius)
        
        // Elevation (Physical feel)
        button.elevation = 6f
        button.stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(button.context, R.animator.anim_btn_tactile_elevation)
        
        // 3. Mode & Content Color
        /*
          Text Color Logic Request: 
           - When (White Level > 50%) OR (Transparency > 50%) OR (Sum > 50%) -> Turn text BLACK.
           - Otherwise WHITE.
        */
        val isLight = isLightColorNewLogic(color)
        val contentColor = if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        button.setTextColor(contentColor)
        
        // Mode Handling
        val mode = data.props["mode"] ?: "text"
        val overlayIcon = container.findViewWithTag<ImageView>("ICON_OVERLAY")
        val density = button.resources.displayMetrics.density
        val minDimDp = kotlin.math.min(data.width, data.height) / density
        val isSmall = minDimDp < 140
        
        if (mode == "icon") {
            button.text = ""
            button.setCompoundDrawables(null, null, null, null)
             if (overlayIcon != null) {
                overlayIcon.visibility = View.VISIBLE
                val iconKey = data.props["icon"]
                
                if (iconKey == "none") {
                    overlayIcon.visibility = View.GONE
                } else {
                    val iconRes = getIconRes(iconKey)
                    overlayIcon.setImageResource(iconRes)
                    overlayIcon.setColorFilter(contentColor)
                    
                    val w = data.width
                    val h = data.height
                     if (w > 0 && h > 0) {
                        val minDim = kotlin.math.min(w, h)
                        val targetSize = (minDim * 0.60f).toInt() // 60% Scale
                        
                        val params = overlayIcon.layoutParams as? FrameLayout.LayoutParams 
                            ?: FrameLayout.LayoutParams(targetSize, targetSize)
                        params.width = targetSize
                        params.height = targetSize
                        params.gravity = Gravity.CENTER
                        overlayIcon.layoutParams = params
                    }
                    overlayIcon.bringToFront()
                }
             }
        } else {
            overlayIcon?.visibility = View.GONE
             if (mode == "text") {
                button.text = data.props["text"] ?: data.label
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                val bottomOffset = (2 * density).toInt()
                button.setPadding(0, 0, 0, bottomOffset)

                // Text AutoSize
                val maxSp = if (isSmall) 18 else 30
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeWithDefaults(button, androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM)
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(button, 5, maxSp, 1, android.util.TypedValue.COMPLEX_UNIT_SP)

            } else {
                // Text+Icon
                button.text = data.props["text"] ?: data.label
                val iconKey = data.props["icon"]
                val iconRes = getIconRes(iconKey)
                val d = androidx.core.content.ContextCompat.getDrawable(button.context, iconRes)?.mutate()
                d?.setTint(contentColor)
                
                val w = data.width
                val h = data.height
                val size = if (w > 0 && h > 0) (kotlin.math.min(w, h) * 0.40f).toInt() else (32 * density).toInt()
                d?.setBounds(0, 0, size, size)
                button.setCompoundDrawables(null, d, null, null)
                
                // Gap between icon and text
                val gap = if (h > 0) (h * 0.01f).toInt() else (2 * density).toInt() 
                button.compoundDrawablePadding = gap
                
                // Safe Zone & Centering (Sync with attachBehavior)
                val shadowHeight = (8 * density).toInt()
                val faceHeight = if (h > 0) h - shadowHeight else (40 * density).toInt()
                val topNudge = (faceHeight * 0.10f).toInt()
                
                // Normal State: Push down by Nudge; Protect Bottom Shadow
                button.setPadding(0, topNudge, 0, shadowHeight)
                
                // Fix "Gap not improved": Disable font padding to remove invisible text whitespace
                button.includeFontPadding = false
                button.gravity = Gravity.CENTER
                
                val maxSp = if (isSmall) 16 else 24
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeWithDefaults(button, androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM)
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(button, 8, maxSp, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }
        }
        
        button.maxLines = 1
        button.isClickable = true
        button.isFocusable = true
    }
    
    private fun getIconRes(key: String?): Int {
         return when(key) {
             "power" -> R.drawable.ic_btn_power
             "lighting" -> R.drawable.ic_btn_lighting
             "fan" -> R.drawable.ic_btn_fan
             "play" -> R.drawable.ic_btn_play
             "tune" -> R.drawable.ic_btn_tune
             "energy" -> R.drawable.ic_btn_energy
             else -> R.drawable.ic_btn_power
         }
    }

    private fun isLightColorNewLogic(color: Int): Boolean {
         val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(color) // 0.0 (Black) to 1.0 (White)
         val alpha = android.graphics.Color.alpha(color) / 255.0 // 0.0 to 1.0
         val transparency = 1.0 - alpha // 0.0 (Opaque) to 1.0 (Transparent)
         
         /*
          User Rules: 
           1. Luminance > 0.5 (White degree 50%)
           2. Transparency > 0.5 (Transparent degree 50%)
           3. (Luminance + Transparency) > 0.5 (Sum 50%)
           If ANY is true -> return TRUE (Light/Bright context -> Black Text)
         */
         
         if (luminance > 0.5) return true
         if (transparency > 0.5) return true
         if ((luminance + transparency) > 0.5) return true
         
         return false
    }

    private fun createTactileDrawable(baseColor: Int, isOval: Boolean, radius: Float): android.graphics.drawable.Drawable {
        // Normal State
        val normalDrawable = createTactileLayer(baseColor, isOval, radius, false)
        // Pressed State
        val pressedDrawable = createTactileLayer(baseColor, isOval, radius, true)
        
        val sld = android.graphics.drawable.StateListDrawable()
        sld.addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
        sld.addState(intArrayOf(), normalDrawable)
        return sld
    }
    
    private fun createTactileLayer(color: Int, isOval: Boolean, radius: Float, isPressed: Boolean): android.graphics.drawable.LayerDrawable {
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val heightNormal = (8 * density).toInt()

        val topInset = if (isPressed) heightNormal else 0
        val bottomInset = if (isPressed) 0 else heightNormal
        
        // Layer 0: Side (Darker Shadow)
        val sideColor = darkenColor(color, 0.6f) 
        val sideLayer = android.graphics.drawable.GradientDrawable().apply {
            setColor(sideColor)
            if (isOval) shape = android.graphics.drawable.GradientDrawable.OVAL
            else {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = radius
            }
        }
        
        // Layer 1: Face (Main Color with GRADIENT logic)
        /* User Req: 
           "Top 10% is white gradient to specified color".
           This implies a Vertical Gradient: Start=White, End=Color.
           Since we can't easily control the "Stop" at 10% without API 24 arrays,
           we will simulate it by adding a Highlight Layer *on top* of a Solid Face.
        */
        
        // Base Face (Solid Color)
        val faceBaseLayer = android.graphics.drawable.GradientDrawable().apply {
            setColor(color) 
            // setStroke((1 * density).toInt(), Color.WHITE) // "Have a border" - applied here?
            if (isOval) shape = android.graphics.drawable.GradientDrawable.OVAL
            else {
                 shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                 cornerRadius = radius
            }
        }
        
        // Highlight Layer (Top 20% White Gradient)
        // We use a multi-stop gradient.
        // Array Size determines the "step" size. 
        // Size 6 => Gap is 1/(6-1) = 1/5 = 20%? No, GradientDrawable distributes evenly.
        // If passed 6 colors: Pos 0.0, 0.2, 0.4, 0.6, 0.8, 1.0.
        // Stop 0 = White. Stop 1 = Color. Transition White->Color is 0.0 to 0.2 (20%).
        // To tune this: Change 'layers' size.
        // Size 11 => 10% (0.0 to 0.1).
        // Size 6 => 20% (0.0 to 0.2).
        val layers = 6 // <--- Modify this to change ratio (e.g. 11 for 10%, 6 for 20%)
        val stops = IntArray(layers)
        stops[0] = Color.WHITE
        for (i in 1 until layers) stops[i] = color
        
        val highlightLayer = android.graphics.drawable.GradientDrawable(
             android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
             stops
        ).apply {
             gradientType = android.graphics.drawable.GradientDrawable.LINEAR_GRADIENT
             if (isOval) shape = android.graphics.drawable.GradientDrawable.OVAL
             else {
                 shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                 cornerRadius = radius
             }
             // Border: Silver Gray (#C0C0C0), Thinner (1dp)
             val borderWidth = kotlin.math.max(1, (1 * density).toInt())
             setStroke(borderWidth, Color.parseColor("#C0C0C0")) 
        }

        // Just use highlightLayer as the Face for now if it contains the color logic.
        // Wait, if I replace the solid face with this gradient face:
        val faceLayer = highlightLayer
        
        // Gloss Layer (Additional shine? Or is the gradient enough?) 
        // User asked for specific gradient. I'll rely on faceLayer being the main gradient.
        // But the "Top 10%" part suggests a sharp transition. 
        // I will add a discrete "Gloss Cap" layer if needed, but let's stick to the Gradient Face for simplicity and robustness.

        // Composite
        val layerDrawables = arrayOf(sideLayer, faceLayer)
        val layerDrawable = android.graphics.drawable.LayerDrawable(layerDrawables)
        
        val sideTopInset = if (isPressed) heightNormal else 0
        layerDrawable.setLayerInset(0, 0, sideTopInset, 0, 0)
        layerDrawable.setLayerInset(1, 0, topInset, 0, bottomInset)
        
        return layerDrawable
    }
    
    // Updated darken color helper
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

        etText?.setText(data.props["text"] ?: data.label)
        etText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { onUpdate("text", s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        val iconMap = mapOf(
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

        // --- 1. Shape Spinner ---
        val spShape = panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.spPropShape)
        val shapeLabels = listOf(
            context.getString(R.string.val_shape_rounded),
            context.getString(R.string.val_shape_circle),
            context.getString(R.string.val_shape_rect)
        )
        val shapeAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, shapeLabels)
        spShape.setAdapter(shapeAdapter)
        
        val currentShape = data.props["shape"] ?: "pill"
        spShape.setText(when(currentShape) {
            "circle" -> context.getString(R.string.val_shape_circle)
            "rect" -> context.getString(R.string.val_shape_rect)
            else -> context.getString(R.string.val_shape_rounded)
        }, false)
        
        spShape.setOnItemClickListener { _, _, position, _ ->
            val code = when(position) {
                1 -> "circle"
                2 -> "rect"
                else -> "pill"
            }
            onUpdate("shape", code)
        }

        // --- 4. Press Payload ---
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

        // --- 5. Button Mode ---
        val toggleTrigger = panelView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.togglePropTrigger)
        val tvTriggerDesc = panelView.findViewById<TextView>(R.id.tvPropTriggerDesc)
        val containerReleaseOnly = panelView.findViewById<View>(R.id.containerReleaseOnly)
        val containerTimerMode = panelView.findViewById<View>(R.id.containerTimerMode)
        val etTimer = panelView.findViewById<EditText>(R.id.etPropTimer)
        
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
                else -> {
                    containerReleaseOnly?.visibility = View.GONE 
                    containerTimerMode?.visibility = View.GONE
                    tvTriggerDesc?.text = context.getString(R.string.desc_mode_tap)
                }
            }
        }

        val currTrigMode = data.props["triggerMode"] ?: "Standard"
        if (toggleTrigger != null) {
            when(currTrigMode.lowercase()) {
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
        updateVisibility(currTrigMode)
        
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
        etTimer?.setText(data.props["timerDuration"] ?: "1000")
        etTimer?.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) { onUpdate("timerDuration", s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // --- 6. Color Palette ---
        // Use Global History
        val recentColors = com.example.mqttpanelcraft.data.ColorHistoryManager.load(context)
        
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
        panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener {
             val cur = data.props["color"] ?: "#a573bc"
             var tempColor = cur 
             com.example.mqttpanelcraft.ui.ColorPickerDialog(
                 context, cur, true,
                 onColorSelected = { tempColor = it; onUpdate("color", it) },
                 onDismiss = {
                     if (tempColor != cur) {
                         com.example.mqttpanelcraft.data.ColorHistoryManager.save(context, tempColor)
                          val updated = com.example.mqttpanelcraft.data.ColorHistoryManager.load(context)
                          colorViews.forEachIndexed { i, v ->
                              if (i < updated.size) {
                                  try {
                                      v?.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(updated[i]))
                                      v?.setOnClickListener { onUpdate("color", updated[i]) }
                                  } catch(e: Exception) {}
                              }
                          }
                      }
                 }
             ).show(it)
        }
        
        fun setupDropdownAutoSize(tv: TextView?, gravity: Int = Gravity.CENTER) {
            if (tv == null) return
            tv.gravity = gravity
            tv.maxLines = 1
            tv.ellipsize = android.text.TextUtils.TruncateAt.END 
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeWithDefaults(tv, androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM)
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tv, 8, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        setupDropdownAutoSize(spApprMode, Gravity.START or Gravity.CENTER_VERTICAL)
        setupDropdownAutoSize(spShape, Gravity.START or Gravity.CENTER_VERTICAL)
    }

    private fun findButtonIn(container: FrameLayout): Button? {
        for (i in 0 until container.childCount) {
             val child = container.getChildAt(i)
             if (child is Button) return child
        }
        return null
    }

    // 4. Runtime Behavior
    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        val container = view as? FrameLayout ?: return
        val button = findButtonIn(container) ?: return
        
        button.setOnTouchListener { v, event ->
            val overlayIcon = container.findViewWithTag<ImageView>("ICON_OVERLAY")
            val mode = data.props["mode"] ?: "text"
            val density = v.resources.displayMetrics.density
            val shift = (8 * density).toInt() 
            
            // Re-calc layout vars
            val isTextIcon = (mode == "text_icon")
            val h = data.height
            val shadowHeight = (8 * density).toInt()
            
            // "Icon too top" -> We want center to be lower than Face Center.
            // Face is H - shadowHeight. Face Center is (H - 8) / 2.
            // We want to push it down. 
            // Let's add a fixed percentage of Face Height as "Nudge".
            val faceHeight = if (h > 0) h - shadowHeight else (40 * density).toInt()
            val nudge = (faceHeight * 0.10f).toInt() // 10% down from face center
            
            // Normal State Padding
            val normalTop = nudge
            val normalBot = shadowHeight // PROTECT THE SHADOW
            
            // Pressed State Padding (Face moves down by shadowHeight)
            val pressedTop = normalTop + shadowHeight
            val pressedBot = 0 
            
             fun setVisualState(pressed: Boolean) {
                if (pressed) {
                    v.isPressed = true
                    if (data.props["haptic"] == "true") {
                         v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                    v.alpha = 0.7f
                    if (mode == "icon") {
                        overlayIcon?.translationY = (normalTop + shadowHeight).toFloat() // Shift down
                    } else {
                        v.setPadding(0, pressedTop, 0, pressedBot)
                    }
                } else {
                    v.isPressed = false
                    v.alpha = 1.0f
                    if (mode == "icon") {
                        overlayIcon?.translationY = normalTop.toFloat() // Reset
                    } else {
                        v.setPadding(0, normalTop, 0, normalBot)
                    }
                }
                v.invalidate()
            }

            val triggerMode = data.props["triggerMode"] ?: "Standard"
            val topic = data.topicConfig
            
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                if (triggerMode == "Timer") {
                     setVisualState(true)
                     if (topic.isNotEmpty()) {
                         val pPress = data.props["payloadPressed"] ?: data.props["payload"] ?: "1"
                         if (pPress.isNotEmpty()) sendMqtt(topic, pPress)
                     }
                     val duration = (data.props["timerDuration"] ?: "1000").toLongOrNull() ?: 1000L
                     v.handler?.postDelayed({
                         setVisualState(false)
                         if (topic.isNotEmpty()) {
                             val pRelease = data.props["payloadReleased"]
                             if (!pRelease.isNullOrEmpty()) sendMqtt(topic, pRelease)
                         }
                     }, duration)
                } else {
                    setVisualState(true)
                    if (topic.isNotEmpty()) {
                         val pPress = data.props["payloadPressed"] ?: data.props["payload"] ?: "1"
                         if (pPress.isNotEmpty()) sendMqtt(topic, pPress)
                    }
                }
                return@setOnTouchListener true
            } else if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                if (triggerMode != "Timer") {
                    setVisualState(false)
                    if (topic.isNotEmpty()) {
                        val pRelease = data.props["payloadReleased"]
                        if (!pRelease.isNullOrEmpty()) sendMqtt(topic, pRelease)
                    }
                }
            }
            true
        }
    }
    
    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {
        // Optional: Update Button state/color if needed? 
        // For now, no specific receive behavior defined for standard button.
    }
}
