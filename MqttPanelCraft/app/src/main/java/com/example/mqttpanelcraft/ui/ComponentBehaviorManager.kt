package com.example.mqttpanelcraft.ui

import android.view.View
import com.example.mqttpanelcraft.model.ComponentData

/**
 * Registry and dispatcher for Component Behaviors. Strategy Pattern: Delegates logic to
 * IComponentBehavior implementations.
 */
class ComponentBehaviorManager(
        private val sendMqtt: (topic: String, payload: String) -> Unit,
        private val onUpdateProp: (id: Int, key: String, value: String) -> Unit,
        private val onPublish: (id: Int, topic: String, payload: String) -> Unit = { _, _, _ -> }
) {
    fun attachBehavior(view: View, data: ComponentData) {
        val def =
                com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
        if (def != null) {
            def.attachBehavior(view, data, { topic, payload ->
                onPublish(data.id, topic, payload)
                sendMqtt(topic, payload)
            }) { key, value ->
                onUpdateProp(data.id, key, value)
            }
        }
    }

    fun onMqttMessageReceived(view: View, data: ComponentData, payload: String) {
        val def =
                com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
        if (def != null) {
            def.onMqttMessage(view, data, payload) { key, value ->
                onUpdateProp(data.id, key, value)
            }
        }
    }

    fun applyMqttSnapshot(view: View, data: ComponentData, payload: String) {
        val def = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
        def?.onMqttSnapshot(view, data, payload) { key, value ->
            onUpdateProp(data.id, key, value)
        }
    }

}
