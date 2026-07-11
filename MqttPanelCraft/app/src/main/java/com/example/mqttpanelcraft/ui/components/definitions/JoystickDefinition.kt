package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Size
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.views.JoystickView
import com.example.mqttpanelcraft.utils.TextWatcherAdapter
import com.google.android.material.button.MaterialButtonToggleGroup

object JoystickDefinition : IComponentDefinition {

    override val type = "JOYSTICK"
    override val defaultSize = Size(200, 200)
    override val labelPrefix = "joystick"
    override val iconResId = R.drawable.ic_joystick
    override val group = "CONTROL"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val joystick =
                JoystickView(context).apply {
                    tag = "target"
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                            )
                }
        container.addView(joystick, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val joystick = view.findViewWithTag<JoystickView>("target") ?: return

        joystick.joystickMode = data.props["mode"] ?: "Joystick"

        // Consolidated Axis Mode
        // Default to "4-Way" if not set.
        // If legacy "axes" exists, we could map it, but for now let's just use the new property
        // "axisMode"
        // effectively migrating by default or user choice.
        joystick.axisMode = data.props["axisMode"] ?: "4-Way"

        joystick.interval = (data.props["interval"] ?: "100").toLongOrNull() ?: 100L

        // Range
        joystick.minVal = (data.props["min"] ?: "-100").toFloatOrNull() ?: -100f
        joystick.maxVal = (data.props["max"] ?: "100").toFloatOrNull() ?: 100f

        // Messages
        // Messages: Use defaults if not set (matches UI defaults)
        val msgRelease = data.props["msg_release"]
        joystick.msgRelease = if (msgRelease.isNullOrEmpty()) "stop" else msgRelease

        val msgUp = data.props["msg_up"]
        joystick.msgUp = if (msgUp.isNullOrEmpty()) "up" else msgUp

        val msgDown = data.props["msg_down"]
        joystick.msgDown = if (msgDown.isNullOrEmpty()) "down" else msgDown

        val msgLeft = data.props["msg_left"]
        joystick.msgLeft = if (msgLeft.isNullOrEmpty()) "left" else msgLeft

        val msgRight = data.props["msg_right"]
        joystick.msgRight = if (msgRight.isNullOrEmpty()) "right" else msgRight

        // Scale Unit for Rounding
        joystick.scaleUnit = (data.props["scale_unit"] ?: "1").toFloatOrNull() ?: 1f

        val rawStyle = data.props["style"] ?: "Neon"
        joystick.visualStyle =
                when (rawStyle) {
                    "Puck" -> "Neon"
                    "FlatArrow" -> "Arrow"
                    else -> rawStyle
                }

        val colorHex = data.props["color"] ?: "#6366F1"
        try {
            joystick.color = Color.parseColor(colorHex)
        } catch (e: Exception) {
            joystick.color = Color.parseColor("#6366F1")
        }
    }

    override val propertiesLayoutId = R.layout.layout_prop_joystick

    override fun bindPropertiesPanel(
            panelView: View,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // 1. Mode
        val toggleMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleJoystickMode)
        val containerInterval = panelView.findViewById<View>(R.id.containerJoystickInterval)
        val containerButtonMessages = panelView.findViewById<View>(R.id.containerButtonMessages)
        val curMode = data.props["mode"] ?: "Joystick"

        val updateVisibility = { mode: String ->
            containerInterval?.visibility = if (mode == "Buttons") View.GONE else View.VISIBLE
            containerButtonMessages?.visibility = if (mode == "Buttons") View.VISIBLE else View.GONE
            // Hide Range/Precision in Buttons mode (Req 2)
            val containerRange = panelView.findViewById<View>(R.id.containerRangePrecision)
            containerRange?.visibility = if (mode == "Buttons") View.GONE else View.VISIBLE
        }

        updateVisibility(curMode)
        toggleMode?.check(if (curMode == "Buttons") R.id.btnModeButtons else R.id.btnModeStandard)

        toggleMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = if (checkedId == R.id.btnModeButtons) "Buttons" else "Joystick"
                onUpdate("mode", newMode)
                updateVisibility(newMode)
                updateStyleAdapter(panelView, newMode, data, onUpdate)
            }
        }

        // 2. Axis Mode (Consolidated)
        val toggleAxisMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleAxisMode)
        val curAxisMode = data.props["axisMode"] ?: "4-Way" // Default

        // Visibility logic for message inputs based on axis mode
        val containerMsgUpDown = panelView.findViewById<View>(R.id.containerMsgUpDown)
        val containerMsgLeftRight = panelView.findViewById<View>(R.id.containerMsgLeftRight)

        val updateMsgVisibility = { axes: String ->
            containerMsgUpDown?.visibility =
                    if (axes == "2-Way Horizontal") View.GONE else View.VISIBLE
            containerMsgLeftRight?.visibility =
                    if (axes == "2-Way Vertical") View.GONE else View.VISIBLE
        }

        updateMsgVisibility(curAxisMode)

        when (curAxisMode) {
            "2-Way Horizontal" -> toggleAxisMode?.check(R.id.btnAxis2WayH)
            "2-Way Vertical" -> toggleAxisMode?.check(R.id.btnAxis2WayV)
            else -> toggleAxisMode?.check(R.id.btnAxis4Way)
        }

        toggleAxisMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newAxisMode =
                        when (checkedId) {
                            R.id.btnAxis2WayH -> "2-Way Horizontal"
                            R.id.btnAxis2WayV -> "2-Way Vertical"
                            else -> "4-Way"
                        }
                onUpdate("axisMode", newAxisMode)
                updateMsgVisibility(newAxisMode)
            }
        }

        // 3. Range & Precision
        val etPropMin = panelView.findViewById<EditText>(R.id.etPropMin)
        val etPropMax = panelView.findViewById<EditText>(R.id.etPropMax)

        etPropMin?.setText(data.props["min"] ?: "-100")
        etPropMin?.addTextChangedListener(TextWatcherAdapter { onUpdate("min", it) })

        etPropMax?.setText(data.props["max"] ?: "100")
        etPropMax?.addTextChangedListener(TextWatcherAdapter { onUpdate("max", it) })

        val tvPropPrecision =
                panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.tvPropPrecision)
        // Scale Unit (0.1, 1, 10) - Controls output step/granularity
        val precisionItems = listOf("0.1", "1", "10")
        val precisionAdapter =
                android.widget.ArrayAdapter(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        precisionItems
                )
        tvPropPrecision?.setAdapter(precisionAdapter)
        tvPropPrecision?.setText(data.props["scale_unit"] ?: "1", false)

        tvPropPrecision?.setOnItemClickListener { _, _, position, _ ->
            val valueStr = precisionItems[position]
            // Scale Unit only sets the granularity property, DOES NOT affect Min/Max range
            onUpdate("scale_unit", valueStr)
        }

        // 4. Button Messages
        // Improved: Pre-fill defaults so user sees what will be sent (Req: "No default values
        // written")
        val bindMsg = { id: Int, key: String, defVal: String ->
            panelView.findViewById<EditText>(id)?.apply {
                val currentVal = data.props[key]
                val displayVal = if (currentVal.isNullOrEmpty()) defVal else currentVal
                setText(displayVal)
                addTextChangedListener(TextWatcherAdapter { onUpdate(key, it) })

                // If it was empty/null, ensure we save the default back to props?
                // No, only save if user modifies or we want to persist defaults.
                // Assuming we just want to show it. But if we show it, and user doesn't touch it,
                // and then saves, it might assume what's in the box is the value.
                // PropertySheetManager usually reads from the inputs on save.
                // If we setText here, and the user clicks Save (check button), the Manager reads
                // the EditText.
                // So this effectively sets the default.
            }
        }

        bindMsg(R.id.etMsgRelease, "msg_release", "stop")
        bindMsg(R.id.etMsgUp, "msg_up", "up")
        bindMsg(R.id.etMsgDown, "msg_down", "down")
        bindMsg(R.id.etMsgLeft, "msg_left", "left")
        bindMsg(R.id.etMsgRight, "msg_right", "right")

        // 5. Interval
        panelView.findViewById<EditText>(R.id.etPropInterval)?.apply {
            setText(data.props["interval"] ?: "100")
            addTextChangedListener(TextWatcherAdapter { onUpdate("interval", it) })
        }

        // 6. Initial Style Selector setup
        updateStyleAdapter(panelView, curMode, data, onUpdate)

        // 7. Color Palette
        val colorViews =
                listOf(R.id.vColor1, R.id.vColor2, R.id.vColor3, R.id.vColor4, R.id.vColor5).map {
                    panelView.findViewById<View>(it)
                }

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
                    )
                    .show(anchor)
        }
    }

    override fun attachBehavior(
            view: View,
            data: ComponentData,
            sendMqtt: (topic: String, payload: String) -> Unit,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val joystick = view.findViewWithTag<JoystickView>("target") ?: return
        joystick.onJoystickChange = { payload ->
            if (data.topicConfig.isNotEmpty()) {
                sendMqtt(data.topicConfig, payload)
            }
        }
    }

    override fun isFixedAspectRatio(data: ComponentData): Boolean {
        // Only 4-Way joystick needs to be square
        return (data.props["axes"] ?: "4-Way") == "4-Way"
    }

    override fun onMqttMessage(
            view: View,
            data: ComponentData,
            payload: String,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // Joystick typically doesn't react to incoming messages in this simplified version
    }

    private fun updateStyleAdapter(
            panelView: View,
            mode: String,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context
        val styleAuto =
                panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.tvJoystickStyle)
                        ?: return

        val styleItems =
                if (mode == "Joystick") {
                    listOf(
                            context.getString(R.string.val_joystick_style_neon) to "Neon",
                            context.getString(R.string.val_joystick_style_arrow) to "Arrow"
                    )
                } else {
                    listOf(
                            context.getString(R.string.val_joystick_style_sharp) to "Neon",
                            context.getString(R.string.val_joystick_style_smooth) to "Beveled"
                    )
                }

        val styleAdapter =
                android.widget.ArrayAdapter(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        styleItems.map { it.first }
                )
        styleAuto.setAdapter(styleAdapter)

        // Set current or default
        val curStyle = data.props["style"] ?: "Neon"
        val matched = styleItems.find { it.second == curStyle } ?: styleItems[0]
        styleAuto.setText(matched.first, false)
        if (data.props["style"] == null || styleItems.none { it.second == data.props["style"] }) {
            onUpdate("style", matched.second)
        }

        styleAuto.setOnItemClickListener { _, _, position, _ ->
            onUpdate("style", styleItems[position].second)
        }
    }
}
