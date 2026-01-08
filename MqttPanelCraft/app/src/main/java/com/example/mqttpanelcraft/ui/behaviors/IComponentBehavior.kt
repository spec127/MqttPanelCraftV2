package com.example.mqttpanelcraft.ui.behaviors

import android.view.View
import com.example.mqttpanelcraft.model.ComponentData

/**
 * Strategy Interface for Component Logic.
 * Each component type (BUTTON, SLIDER, etc.) will have its own Behavior class.
 */
interface IComponentBehavior {
    /**
     * Called when the component is created or bound.
     * Use this to set up ClickListeners, Initial State, etc.
     */
    fun onAttach(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit)

    /**
     * Called when an MQTT message is received for this component's topic.
     * Update the View appearance here.
     */
    fun onMqttMessage(view: View, payload: String)
}
