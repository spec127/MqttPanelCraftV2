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
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object ButtonDefinition : IComponentDefinition {
    
    // 1. Identity
    override val type = "BUTTON"
    override val defaultSize = Size(120, 60)
    override val labelPrefix = "btn"

    // 2. Factory (Appearance)
    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        
        val button = Button(context).apply { 
            text = "BTN"
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }
        // Add content at index 0 (behind handle)
        container.addView(button, 0)
        
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val button = findButtonIn(container) ?: return
        
        data.props["color"]?.let { colorCode ->
             try {
                 val color = android.graphics.Color.parseColor(colorCode)
                 button.setBackgroundColor(color)
             } catch(_: Exception) {}
        }
    }

    // 3. Properties (Binder)
    override val propertiesLayoutId = R.layout.layout_props_button

    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
        val etPayload = panelView.findViewById<EditText>(R.id.etPayload) ?: return
        
        // Initial Value
        val currentPayload = data.props["payload"] ?: "1" // Default payload
        if (etPayload.text.toString() != currentPayload) {
            etPayload.setText(currentPayload)
        }

        // Listener (Remove old one if needed? Normally Binder is fresh)
        // With simple implementation we just add listener.
        etPayload.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onUpdate("payload", s.toString())
            }
        })
    }

    // 4. Runtime Behavior
    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        val container = view as? FrameLayout ?: return
        val button = findButtonIn(container) ?: return
        
        // Update Appearance based on Data (if needed)
        // e.g. Label on button? 
        // button.text = data.label 
        
        button.setOnClickListener {
             // Logic
             val topic = data.topicConfig
             // Allow user to set empty topic? Maybe check
             if (topic.isNotEmpty()) {
                 val payload = data.props["payload"] ?: "1"
                 sendMqtt(topic, payload)
             }
        }
    }

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {
        // Button usually doesn't react to messages, but could change color?
        // For now, no-op
    }

    private fun findButtonIn(container: FrameLayout): Button? {
        for (i in 0 until container.childCount) {
             val child = container.getChildAt(i)
             if (child is Button) return child
        }
        return null
    }
}
