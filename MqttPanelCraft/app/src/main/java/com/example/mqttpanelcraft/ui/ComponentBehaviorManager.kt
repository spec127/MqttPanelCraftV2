package com.example.mqttpanelcraft.ui

import android.content.Context
import android.view.View
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.behaviors.IComponentBehavior
import com.example.mqttpanelcraft.ui.behaviors.ButtonBehavior

/**
 * Registry and dispatcher for Component Behaviors.
 * Strategy Pattern: Delegates logic to IComponentBehavior implementations.
 */
class ComponentBehaviorManager(
    private val context: Context,
    private val projectIdProvider: () -> String?,
    private val sendMqtt: (topic: String, payload: String) -> Unit
) {
    // Registry
    private val behaviors = mapOf<String, IComponentBehavior>(
        "BUTTON" to ButtonBehavior(),
        // "SWITCH" to SwitchBehavior(),
        // "GAUGE" to GaugeBehavior(),
        // ... Add new behaviors here
    )

    fun attachBehavior(view: View, data: ComponentData) {
        behaviors[data.type]?.onAttach(view, data, sendMqtt)
    }

    fun onMqttMessageReceived(view: View, data: ComponentData, payload: String) {
        behaviors[data.type]?.onMqttMessage(view, payload)
    }
}
