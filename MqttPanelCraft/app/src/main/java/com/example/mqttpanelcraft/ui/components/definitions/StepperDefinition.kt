package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Size
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.components.prop.PropertyOption
import com.example.mqttpanelcraft.ui.views.StepperView
import com.example.mqttpanelcraft.utils.TextWatcherAdapter
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * 數值步進控制器 (StepperDefinition) - v0.11.5
 *
 * Design Intent:
 * 1. 移除與通用卡片重複的名稱與 Topic 輸入框。
 * 2. 屬性面板保留「互動功能」與「視覺外觀」兩個核心區塊。
 * 3. 支援用戶指定的三格風格：「標準 < > (Standard)」、「分離方塊 (Block)」、「圓滑 (Smooth)」。
 * 4. 遵守純鬆手發送 (Release-Only Trigger) 的 MQTT 控制規範。
 */
object StepperDefinition : IComponentDefinition {

    override val type: String = "STEPPER"
    override val defaultSize: Size = Size(150, 70)
    override val labelPrefix: String = "stepper"
    override val displayNameResId: Int = R.string.component_label_stepper
    override val iconResId: Int = android.R.drawable.ic_input_add
    override val group = ComponentGroup.CONTROL

    override val propertiesLayoutId: Int = R.layout.layout_prop_stepper

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "color" to "#6366F1",
        "theme_color" to "#6366F1",
        "min" to "0",
        "max" to "100",
        "step" to "1",
        "value" to "50",
        "long_press" to "true",
        "style" to "Standard"
    )

    override fun isFixedAspectRatio(data: ComponentData): Boolean = false

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val stepperView = StepperView(context).apply {
            tag = "target_stepper_view"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(stepperView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val stepperView = view.findViewWithTag<StepperView>("target_stepper_view") ?: return

        stepperView.minValue = (data.props["min"] ?: "0").toFloatOrNull() ?: 0f
        stepperView.maxValue = (data.props["max"] ?: "100").toFloatOrNull() ?: 100f
        stepperView.stepValue = (data.props["step"] ?: "1").toFloatOrNull() ?: 1f
        stepperView.currentValue = (data.props["value"] ?: "50").toFloatOrNull() ?: 50f
        stepperView.longPressEnabled = (data.props["long_press"] ?: "true") == "true"
        stepperView.visualStyle = data.props["style"] ?: "Standard"
        stepperView.orientation = data.props["orientation"] ?: "Horizontal"

        val colorHex = data.props["color"] ?: "#6366F1"
        try {
            stepperView.themeColor = Color.parseColor(colorHex)
        } catch (_: Exception) {
            stepperView.themeColor = Color.parseColor("#6366F1")
        }
        stepperView.invalidate()
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // 1. 數值範圍與步進、當前值 (互動功能)
        CommonPropBinder.bindEditText(panelView, R.id.etPropMin, "min", data, onUpdate, "0")
        CommonPropBinder.bindEditText(panelView, R.id.etPropMax, "max", data, onUpdate, "100")
        CommonPropBinder.bindEditText(panelView, R.id.etPropStep, "step", data, onUpdate, "1")

        // 2. 長按連續步進 Toggle
        val toggleLongPress = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleLongPress)
        val isLongPress = (data.props["long_press"] ?: "true") == "true"
        if (isLongPress) {
            toggleLongPress?.check(R.id.btnLongPressTrue)
        } else {
            toggleLongPress?.check(R.id.btnLongPressFalse)
        }
        toggleLongPress?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val enabled = (checkedId == R.id.btnLongPressTrue)
                onUpdate("long_press", if (enabled) "true" else "false")
            }
        }

        // 方向切換按鈕 (Horizontal / Vertical)
        val toggleOrientation = panelView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleOrientation)
        val currentOrient = data.props["orientation"] ?: "Horizontal"
        if (toggleOrientation != null) {
            toggleOrientation.check(
                if (currentOrient.equals("Vertical", ignoreCase = true)) R.id.btnOrientationVert else R.id.btnOrientationHoriz
            )
            toggleOrientation.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val newOrient = if (checkedId == R.id.btnOrientationVert) "Vertical" else "Horizontal"
                    val oldOrient = data.props["orientation"] ?: "Horizontal"
                    if (!newOrient.equals(oldOrient, ignoreCase = true)) {
                        val oldW = data.width
                        val oldH = data.height
                        onUpdate("w", oldH.toString())
                        onUpdate("h", oldW.toString())
                    }
                    onUpdate("orientation", newOrient)
                }
            }
        }

        // 3. 外觀風格 (視覺外觀)
        CommonPropBinder.bindLocalizedDropdown(
            panelView,
            R.id.tvStepperStyle,
            "style",
            data,
            onUpdate,
            listOf(
                PropertyOption("Standard", R.string.val_stepper_style_standard),
                PropertyOption("Block", R.string.val_stepper_style_block),
                PropertyOption("Smooth", R.string.val_stepper_style_smooth)
            ),
            "Standard"
        )

        // 4. 顏色調色盤
        val colorViews = listOf(
            R.id.vColor1, R.id.vColor2, R.id.vColor3, R.id.vColor4, R.id.vColor5
        ).map { panelView.findViewById<View>(it) }

        fun refreshColors() {
            val recent = ColorHistoryManager.load(context)
            colorViews.forEachIndexed { i, v ->
                if (v != null && i < recent.size) {
                    v.backgroundTintList = ColorStateList.valueOf(Color.parseColor(recent[i]))
                    v.setOnClickListener { onUpdate("color", recent[i]) }
                }
            }
        }
        refreshColors()

        panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener { anchor ->
            val cur = data.props["color"] ?: "#6366F1"
            var tempColor = cur
            ColorPickerDialog(
                context,
                cur,
                true,
                {
                    tempColor = it
                    onUpdate("color", it)
                },
                {
                    ColorHistoryManager.save(context, tempColor)
                    refreshColors()
                }
            ).show(anchor)
        }
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val stepperView = view.findViewWithTag<StepperView>("target_stepper_view") ?: return
        stepperView.onValueCommit = { finalValStr ->
            onUpdateProp("value", finalValStr)
            if (data.topicConfig.isNotEmpty()) {
                sendMqtt(data.topicConfig, finalValStr)
            }
        }
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val stepperView = view.findViewWithTag<StepperView>("target_stepper_view") ?: return
        payload.toFloatOrNull()?.let { valFloat ->
            stepperView.currentValue = valFloat
            onUpdateProp("value", stepperView.formatValue(valFloat))
        }
    }
}
