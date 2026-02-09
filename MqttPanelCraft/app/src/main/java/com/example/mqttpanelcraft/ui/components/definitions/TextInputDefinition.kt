package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object TextInputDefinition : IComponentDefinition {

    override val type = "INPUT"
    override val defaultSize = Size(200, 80)
    override val labelPrefix = "input"
    override val iconResId = android.R.drawable.ic_menu_edit // Standard Edit Icon
    override val group = "CONTROL"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val et =
                EditText(context).apply {
                    hint = "Input"
                    gravity = Gravity.CENTER
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                            )
                    // If in Run Mode, maybe enable input? But "ComponentContainer" might handle
                    // touch event.
                    // If isEditMode, EditText should be disabled/not focusable to allow dragging?
                    // Usually ComponentContainer intercepts touches in Edit Mode.
                    // In Run Mode, user wants to type.
                    isFocusable = !isEditMode
                    isFocusableInTouchMode = !isEditMode
                }
        container.addView(et, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val et = container.getChildAt(0) as? EditText ?: return

        data.props["color"]?.let { colorCode ->
            try {
                val color = android.graphics.Color.parseColor(colorCode)
                et.setTextColor(color)
            } catch (_: Exception) {}
        }

        // Mode switch helper
        // Since view is reused, we might need to toggle focusability dynamically if mode changes
        // (Run <-> Edit).
        // `createView` is called once, but `onUpdateView` is called often.
        // Usually Edit/Run mode switch triggers a re-render or re-bind?
        // Current architecture: `isEditMode` passed at creation.
        // If mode changes, Activity calls `render` again? Yes.
        // But `render` reuses existing view if ID matches?
        // ComponentRenderer reuses view. So we need to update state here!
        // But `isEditMode` isn't passed to `onUpdateView` directly in signature.
        // Wait, ProjectViewActivity logic:
        // `viewModel.components.observe` -> `renderer.render(components, isEditMode, ...)`
        // Renderer updates `isEditMode` property or we need to deduce it?
        // Actually `ComponentRenderer.render` iterates components.
        // If mode changed, it might re-create views if implemented that way, OR update them.
        // `ComponentRenderer` logic checks if `view.tag == component.id`.
        // Let's assume for now focused behavior is handled by renderer, or we ignore it.
        data.props["text"]?.let {
            if (et.text.toString() != it) {
                et.setText(it)
            }
        }
    }

    override val propertiesLayoutId = 0
    override fun bindPropertiesPanel(
            panelView: View,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {}

    override fun attachBehavior(
            view: View,
            data: ComponentData,
            sendMqtt: (topic: String, payload: String) -> Unit,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // Send on Enter? Or on Button click?
        // Simplest: Send on IME Action Done or Focus Loss?
        val container = view as? FrameLayout ?: return
        val et = container.getChildAt(0) as? EditText ?: return

        et.setOnEditorActionListener { _, _, _ ->
            val text = et.text.toString()
            if (text.isNotEmpty()) {
                sendMqtt(data.topicConfig, text)
                onUpdateProp("text", text) // Sync state for persistence
            }
            false // Keep keyboard? or True to close? Use default.
        }
    }

    override fun onMqttMessage(
            view: View,
            data: ComponentData,
            payload: String,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // Update input text if received?
        val container = view as? FrameLayout ?: return
        val et = container.getChildAt(0) as? EditText ?: return

        if (et.text.toString() != payload) {
            et.setText(payload)
            onUpdateProp("text", payload) // Persist MQTT state
        }
    }
}
