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
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object ButtonDefinition : IComponentDefinition {
    
    // 1. Identity
    override val type = "BUTTON"
    override val defaultSize = Size(120, 60)
    override val labelPrefix = "btn"
    override val iconResId = android.R.drawable.ic_media_play
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
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val button = findButtonIn(container) ?: return
        
        // 1. Color
        val colorHex = data.props["color"] ?: "#7C3AED" // Default Vivid Purple
        val color = try { android.graphics.Color.parseColor(colorHex) } catch(e: Exception) { android.graphics.Color.LTGRAY }
        
        // 2. Shape
        val shapeMode = data.props["shape"] ?: "pill" // circle, rect, pill
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
             setColor(color)
             when(shapeMode) {
                 "circle" -> shape = android.graphics.drawable.GradientDrawable.OVAL
                 "rect" -> {
                     shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                     cornerRadius = 16f // Small radius
                 }
                 else -> { // pill
                     shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                     cornerRadius = 100f // Large radius
                 }
             }
        }
        button.background = bgDrawable
        
        // 3. Mode (Text vs Icon)
        val mode = data.props["mode"] ?: "text" // text, icon
        
        if (mode == "icon") {
            button.text = ""
            val iconKey = data.props["icon"]
             val iconRes = when(iconKey) {
                "plus" -> R.drawable.ic_action_add_large 
                "delete" -> R.drawable.ic_close_large
                "send" -> R.drawable.ic_send
                "edit" -> R.drawable.ic_edit
                "info" -> R.drawable.ic_info
                "mic" -> android.R.drawable.btn_star 
                else -> android.R.drawable.ic_menu_help
            }
            val d = androidx.core.content.ContextCompat.getDrawable(button.context, iconRes)
            
            // Contrast
            if (isLightColor(color)) d?.setTint(android.graphics.Color.BLACK) else d?.setTint(android.graphics.Color.WHITE)
            
            // Icon Position
            // For simple centered icon without text, standard logic:
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null)
            
            // Fix padding (was causing build error)
            button.setPadding(button.paddingLeft, 0, button.paddingRight, 0)
            
        } else {
            // Text Mode
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            val btnText = data.props["text"] ?: data.label
            button.text = btnText
            
            if (isLightColor(color)) {
                button.setTextColor(android.graphics.Color.BLACK)
            } else {
                button.setTextColor(android.graphics.Color.WHITE)
            }
        }
    }
    
    private fun isLightColor(color: Int): Boolean {
        return androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5
    }

    // 3. Properties (Binder)
    override val propertiesLayoutId = R.layout.layout_prop_button
    
    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
        val context = panelView.context
        
        // --- 1. Shape Spinner ---
        val spShape = panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.spPropShape)
        val shapeAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, listOf("Rounded", "Circle", "Rect"))
        spShape.setAdapter(shapeAdapter)
        
        // Init Value
        val currentShape = data.props["shape"] ?: "pill" // Mapped to code: pill=Rounded, circle=Circle, rect=Rect
        spShape.setText(when(currentShape) {
            "circle" -> "Circle"
            "rect" -> "Rect"
            else -> "Rounded"
        }, false)
        
        spShape.setOnItemClickListener { _, _, position, _ ->
            val sel = shapeAdapter.getItem(position) ?: "Rounded"
            val code = when(sel) {
                "Circle" -> "circle"
                "Rect" -> "rect"
                else -> "pill"
            }
            onUpdate("shape", code)
        }

        // --- 2. Color Picker (3 Dots + Custom) ---
        // Dot 1: Props Primary (#7C3AED / #a573bc)
        val dot1 = panelView.findViewById<View>(R.id.vColor1)
        dot1.setOnClickListener { onUpdate("color", "#a573bc") } 
        
        // Dot 2: Blue (#2196F3)
        val dot2 = panelView.findViewById<View>(R.id.vColor2)
        dot2.setOnClickListener { onUpdate("color", "#2196F3") }
        
        // Dot 3: Green (#4CAF50)
        val dot3 = panelView.findViewById<View>(R.id.vColor3)
        dot3.setOnClickListener { onUpdate("color", "#4CAF50") }
        
        // Custom
        panelView.findViewById<View>(R.id.btnColorCustom).setOnClickListener {
             val cur = data.props["color"] ?: "#a573bc"
             com.example.mqttpanelcraft.ui.ColorPickerDialog(context, cur, true) {
                 onUpdate("color", it)
             }.show(it)
        }

        // --- 3. Interaction: Trigger Mode ---
        val spTrigger = panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.spPropTriggerMode)
        val triggerAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, listOf("Standard", "Momentary", "Timer"))
        spTrigger.setAdapter(triggerAdapter)
        
        // Init Value
        spTrigger.setText(data.props["triggerMode"] ?: "Standard", false)
        
        spTrigger.setOnItemClickListener { _, _, position, _ ->
            val sel = triggerAdapter.getItem(position) ?: "Standard"
            onUpdate("triggerMode", sel)
        }

        // --- 4. Payloads (Release only here, Press is Generic) ---
        val etRelease = panelView.findViewById<EditText>(R.id.etPropPayloadRelease)
        etRelease.setText(data.props["payloadReleased"] ?: "0")
        
        etRelease.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                 onUpdate("payloadReleased", s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // Note: 'text' (Button Label) and 'icon' properties were previously in tabs.
        // If we want to support them, we need to decide where they go. 
        // For now, based on the layout, there is no Text/Icon switcher in the simplified version 2.
        // So we default to Text mode unless logic implies otherwise.
        // Wait, Layout V2 removed the Appearance Tab. 
        // User requested: "Simple content". 
        // But ButtonDefinition logic relies on "mode" property.
        // Keep it simple: Button always shows generic label property as text. 
        // If user wants Icon, we might need to add it back later or use a dedicated "Icon Button" component.
        // For now, removing binding for deleted Text/Icon Views.
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
