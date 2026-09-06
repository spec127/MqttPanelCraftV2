package com.example.mqttpanelcraft.ui.components

import android.content.Context
import android.util.Size
import android.view.View
import androidx.annotation.StringRes
import com.example.mqttpanelcraft.model.ComponentData

internal fun resolveLinkedTriggerPayload(data: ComponentData, triggerValue: String): String =
        data.props["payload"]
                ?: data.props["payloadRight"]
                ?: data.props["value"]
                ?: triggerValue

/**
 * The "Soul" of a component. Encapsulates its Identity (Type, Size), Appearance (Factory),
 * Properties Logic (Binder), and Runtime Behavior (Behavior).
 */
interface IComponentDefinition {
        // 1. Identity & Defaults
        val type: String
        val defaultSize: Size // Ensure unify 100x100 if needed
        val labelPrefix: String // e.g. "button"

        @get:StringRes
        val displayNameResId: Int

        // 1.5 Sidebar Presentation
        val iconResId: Int // e.g. R.drawable.ic_button
        val group: ComponentGroup

        // 2. Appearance (Factory)
        fun createView(context: Context, isEditMode: Boolean): View

        // 2.5 Dynamic Updates (Appearance changes after creation, e.g. Color)
        fun onUpdateView(view: View, data: ComponentData)

        // 3. Properties (Binder)
        val propertiesLayoutId: Int // Resource ID for specific properties (0 if none)
        fun bindPropertiesPanel(
                panelView: View,
                data: ComponentData,
                onUpdate: (String, String) -> Unit
        )

        // 4. Runtime Behavior (BehaviorManager)
        fun attachBehavior(
                view: View,
                data: ComponentData,
                sendMqtt: (topic: String, payload: String) -> Unit,
                onUpdateProp: (key: String, value: String) -> Unit
        )
        fun onMqttMessage(
                view: View,
                data: ComponentData,
                payload: String,
                onUpdateProp: (key: String, value: String) -> Unit
        )

        /** Applies a cached background value when UI returns. Override for side-effecting views. */
        fun onMqttSnapshot(
                view: View,
                data: ComponentData,
                payload: String,
                onUpdateProp: (key: String, value: String) -> Unit
        ) = onMqttMessage(view, data, payload, onUpdateProp)

        /**
         * Handles an in-app trigger from another component. The source component never publishes
         * MQTT itself; the linked target owns the topic and payload used here.
         */
        fun onLinkedTrigger(
                view: View,
                data: ComponentData,
                triggerValue: String,
                sendMqtt: (topic: String, payload: String) -> Unit,
                onUpdateProp: (key: String, value: String) -> Unit
        ) {
                if (data.topicConfig.isBlank()) return
                sendMqtt(data.topicConfig, resolveLinkedTriggerPayload(data, triggerValue))
        }

        /**
         * Returns standard default properties for this component type.
         */
        fun getDefaultProps(): Map<String, String> {
                val groupColor = when (group) {
                        ComponentGroup.CONTROL -> "#FF2196F3"
                        ComponentGroup.SENSOR -> "#FFEB3B"
                        ComponentGroup.DISPLAY -> "#FF9800"
                }
                return mapOf("color" to groupColor, "theme_color" to groupColor)
        }

        /** Context-aware defaults for user-visible initial content. Persisted values stay user data. */
        fun getDefaultProps(context: Context): Map<String, String> = getDefaultProps()

        /**
         * Returns true if the component should maintain its aspect ratio during resizing. Default
         * is false (free resizing).
         */
        fun isFixedAspectRatio(data: ComponentData): Boolean = false
}

/** Implemented by components that emit local trigger events without publishing MQTT directly. */
interface LocalComponentTriggerSource {
        fun attachLocalTrigger(
                view: View,
                data: ComponentData,
                onTriggerLinked: (source: ComponentData, value: String) -> Unit
        )
}

/**
 * Standardized extension for finding the core interactive/display view within a component
 * container.
 */
fun <T : View> View.findComponentTarget(): T? = this.findViewWithTag<T>("target")
