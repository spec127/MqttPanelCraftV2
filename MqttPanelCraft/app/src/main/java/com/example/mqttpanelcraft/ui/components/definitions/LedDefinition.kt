package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object LedDefinition : IComponentDefinition {

    override val type = "LED"
    override val defaultSize = Size(80, 80) // Specific Default
    override val labelPrefix = "led"
    override val iconResId = android.R.drawable.star_on
    override val group = "SENSOR"

    // Appearance
    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)

        val ledView =
                View(context).apply {
                    background =
                            GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.RED) // Default Off Color
                            }
                    // LED fills container usually or centered?
                    // Factory used View(context).apply { ... } directly as content?
                    // Let's make it fill match_parent with margin
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                            )
                }
        container.addView(ledView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val ledView = container.getChildAt(0) ?: return

        data.props["color"]?.let { colorCode ->
            try {
                val color = android.graphics.Color.parseColor(colorCode)
                val bg = ledView.background as? GradientDrawable
                bg?.setColor(color) // Set Initial/Base Color
            } catch (_: Exception) {}
        }
    }

    // Properties: Generic Color
    override val propertiesLayoutId = R.layout.layout_prop_generic_color

    override fun bindPropertiesPanel(
            panelView: View,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {
        val vPropColorPreview =
                panelView.findViewById<android.widget.TextView>(R.id.vPropColorPreview)

        // Initial State
        val currentColor = data.props["color"] ?: ""
        updateColorView(vPropColorPreview, currentColor)

        vPropColorPreview.setOnClickListener {
            com.example.mqttpanelcraft.ui.ColorPickerDialog(
                            context = panelView.context,
                            initialColor =
                                    if (currentColor.isEmpty()) "#FFFFFFFF" else currentColor,
                            showAlpha = true,
                            onColorSelected = { selectedHex ->
                                onUpdate("color", selectedHex)
                                updateColorView(vPropColorPreview, selectedHex)
                            }
                    )
                    .show(vPropColorPreview)
        }
    }

    private fun updateColorView(view: android.widget.TextView, hex: String) {
        try {
            val context = view.context
            val defaultStr = context.getString(R.string.properties_label_default)
            if (hex.isEmpty() || hex == "Default" || hex == defaultStr) {
                view.text = defaultStr
                view.setTextColor(android.graphics.Color.BLACK)
                view.setShadowLayer(0f, 0f, 0f, 0)

                val bg =
                        view.background as? android.graphics.drawable.GradientDrawable
                                ?: android.graphics.drawable.GradientDrawable()
                bg.setColor(android.graphics.Color.WHITE)
                val density = context.resources.displayMetrics.density
                bg.setStroke(
                        (1 * density).toInt(),
                        android.graphics.Color.LTGRAY,
                        (5 * density).toFloat(),
                        (3 * density).toFloat()
                ) // Dashed border
                bg.cornerRadius = (12 * density)
                view.background = bg
                return
            }

            val color =
                    try {
                        android.graphics.Color.parseColor(hex)
                    } catch (e: Exception) {
                        android.graphics.Color.LTGRAY
                    }

            // Update View Background
            val bg =
                    view.background as? android.graphics.drawable.GradientDrawable
                            ?: android.graphics.drawable.GradientDrawable()
            bg.setColor(color)
            val density = context.resources.displayMetrics.density
            bg.setStroke(
                    (2 * density).toInt(),
                    android.graphics.Color.parseColor("#808080")
            ) // Solid border
            bg.cornerRadius = (12 * density)
            view.background = bg

            // Update Text
            view.text = hex

            // Contrast Logic
            val alpha = android.graphics.Color.alpha(color)
            val luminescence =
                    (0.299 * android.graphics.Color.red(color) +
                            0.587 * android.graphics.Color.green(color) +
                            0.114 * android.graphics.Color.blue(color))

            val contrastColor =
                    if (alpha < 180 || luminescence > 186) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE

            view.setTextColor(contrastColor)
            view.setShadowLayer(0f, 0f, 0f, 0) // Ensure no shadow
        } catch (e: Exception) {}
    }

    // Behavior
    override fun attachBehavior(
            view: View,
            data: ComponentData,
            sendMqtt: (topic: String, payload: String) -> Unit,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val container = view as? FrameLayout ?: return
        val ledView = container.getChildAt(0) ?: return

        // Initial State?
        // ComponentBehaviorManager logic was usually Update-only on message.
        // But maybe LED has click to toggle? (Usually not, LED is output)
        // Let's assume Output Only.
    }

    override fun onMqttMessage(
            view: View,
            data: ComponentData,
            payload: String,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val container = view as? FrameLayout ?: return
        val ledView = container.getChildAt(0) ?: return

        val bg = ledView.background as? GradientDrawable ?: return

        // Simple logic for now matching old behavior
        if (payload == "1" || payload.equals("on", ignoreCase = true)) {
            bg.setColor(Color.GREEN)
        } else {
            bg.setColor(Color.RED)
        }
    }
}
