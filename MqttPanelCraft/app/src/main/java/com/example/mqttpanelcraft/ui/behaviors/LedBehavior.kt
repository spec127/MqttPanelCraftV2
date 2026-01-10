package com.example.mqttpanelcraft.ui.behaviors

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.model.ComponentData

class LedBehavior : IComponentBehavior {
    
    override fun onAttach(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        // LED doesn't usually send data on click, but if needed we can add it.
        // For now, it's a passive indicator.
    }

    override fun onMqttMessage(view: View, payload: String) {
        if (view !is FrameLayout) return
        
        // Find the "LED" view (it's likely the first child that is NOT the resize handle)
        // ComponentFactory structure: Container -> [Content, ResizeHandle]
        val ledView = view.getChildAt(0) // Safe assumption based on Factory
        
        val bg = ledView.background as? GradientDrawable ?: return
        
        // Logic: 1, on, true -> Green. Else -> Red.
        val isOn = when(payload.lowercase()) {
            "1", "on", "true" -> true
            else -> false
        }
        
        val color = if (isOn) Color.GREEN else Color.RED
        bg.setColor(color)
        ledView.invalidate()
    }
}
