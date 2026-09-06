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
        private val onTriggerLinked: (source: ComponentData, value: String) -> Unit
) {
    // Registry (Legacy behaviors removed)
    // private val behaviors = mapOf<String, IComponentBehavior>(...)

    fun attachBehavior(view: View, data: ComponentData) {
        val def =
                com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
        if (def != null) {
            def.attachBehavior(view, data, sendMqtt) { key, value ->
                onUpdateProp(data.id, key, value)
            }
            if (def is com.example.mqttpanelcraft.ui.components.LocalComponentTriggerSource) {
                def.attachLocalTrigger(view, data, onTriggerLinked)
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

    fun triggerLinkedComponent(view: View, data: ComponentData, value: String) {
        val def =
                com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
                        ?: return
        def.onLinkedTrigger(view, data, value, sendMqtt) { key, updatedValue ->
            onUpdateProp(data.id, key, updatedValue)
        }
    }
}
