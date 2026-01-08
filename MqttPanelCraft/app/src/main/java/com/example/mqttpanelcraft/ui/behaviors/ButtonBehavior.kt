package com.example.mqttpanelcraft.ui.behaviors

import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import com.example.mqttpanelcraft.model.ComponentData

class ButtonBehavior : IComponentBehavior {
    override fun onAttach(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        // The factory wraps content in FrameLayout. The Button is the first child usually.
        // Or we can find it by type.
        if (view !is FrameLayout) return
        
        val button = findButtonIn(view) ?: return

        // Set Text from props or label
        // button.text = data.label // Optional: Button usually has fixed text "BTN" or icon

        button.setOnClickListener {
            // Simple Toggle Logic or Push Logic
            // For now, send "TOGGLE" or "ON"
            val topic = data.topicConfig
            if (topic.isNotEmpty()) {
                sendMqtt(topic, "TOGGLE")
            }
        }
    }

    override fun onMqttMessage(view: View, payload: String) {
        // Update button state if needed (e.g. Color)
    }

    private fun findButtonIn(container: FrameLayout): Button? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is Button) return child
        }
        return null
    }
}
