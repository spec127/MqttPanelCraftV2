package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object LevelIndicatorDefinition : IComponentDefinition {

    override val type = "LEVEL"
    override val defaultSize = Size(60, 200) // Vertical
    override val labelPrefix = "lvl"
    override val iconResId =
            android.R.drawable.ic_lock_power_off // Placeholder icon for Level? or Sort?
    override val group =
            "SENSOR" // Sensor? Display? User said "Sensor contains temperature and LED etc". Level
    // is similar.

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        // Vertical ProgressBar
        // Using built-in vertical attribute requires style or rotation.
        // Simplest: Rotation.
        val pb =
                ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = 50
                    progressTintList = android.content.res.ColorStateList.valueOf(Color.BLUE)
                    // Vertical Layout Trick
                    rotation = -90f
                    // Layout Params need to swap W/H roughly or be large enough
                    // But matching parent in rotation is tricky.
                    // Let's assume square or fit center.
                    layoutParams =
                            FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                    )
                                    .apply { gravity = Gravity.CENTER }
                }
        container.addView(pb, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val pb = container.getChildAt(0) as? ProgressBar ?: return

        data.props["color"]?.let { colorCode ->
            try {
                val color = android.graphics.Color.parseColor(colorCode)
                pb.progressTintList = android.content.res.ColorStateList.valueOf(color)
            } catch (_: Exception) {}
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
    ) {}

    override fun onMqttMessage(
            view: View,
            data: ComponentData,
            payload: String,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val container = view as? FrameLayout ?: return
        val pb = container.getChildAt(0) as? ProgressBar ?: return
        try {
            pb.progress = payload.toFloat().toInt().coerceIn(0, 100)
        } catch (_: Exception) {}
    }
}
