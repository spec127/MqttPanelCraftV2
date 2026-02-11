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
    override val defaultSize = Size(160, 160)
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
        joystick.axes = data.props["axes"] ?: "4-Way"
        joystick.direction2Way = data.props["dir"] ?: "Horizontal"
        joystick.interval = (data.props["interval"] ?: "100").toLongOrNull() ?: 100L
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

        // 0. Topic Config (Shared)
        panelView.rootView.findViewById<EditText>(R.id.etPropTopicConfig)?.apply {
            setText(data.topicConfig)
            addTextChangedListener(TextWatcherAdapter { data.topicConfig = it })
        }

        // 1. Mode
        val toggleMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleJoystickMode)
        val containerInterval = panelView.findViewById<View>(R.id.containerJoystickInterval)
        val curMode = data.props["mode"] ?: "Joystick"

        containerInterval?.visibility = if (curMode == "Buttons") View.GONE else View.VISIBLE
        toggleMode?.check(if (curMode == "Buttons") R.id.btnModeButtons else R.id.btnModeStandard)

        // Style Selector reference (for dynamic update)
        val styleAuto =
                panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.tvJoystickStyle)

        toggleMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = if (checkedId == R.id.btnModeButtons) "Buttons" else "Joystick"
                onUpdate("mode", newMode)
                containerInterval?.visibility =
                        if (newMode == "Buttons") View.GONE else View.VISIBLE

                // Refresh style list when mode changes
                updateStyleAdapter(panelView, newMode, data, onUpdate)
            }
        }

        // 2. Axes
        val toggleAxes = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleJoystickAxes)
        val containerDir = panelView.findViewById<View>(R.id.containerJoystickDir)
        val curAxes = data.props["axes"] ?: "4-Way"
        toggleAxes?.check(if (curAxes == "2-Way") R.id.btnAxes2Way else R.id.btnAxes4Way)
        containerDir?.visibility = if (curAxes == "2-Way") View.VISIBLE else View.GONE

        toggleAxes?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newAxes = if (checkedId == R.id.btnAxes2Way) "2-Way" else "4-Way"
                onUpdate("axes", newAxes)
                containerDir?.visibility = if (newAxes == "2-Way") View.VISIBLE else View.GONE
            }
        }

        // 3. Direction (2-Way)
        val toggleDir = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleJoystickDir)
        val curDir = data.props["dir"] ?: "Horizontal"
        toggleDir?.check(if (curDir == "Vertical") R.id.btnDirVert else R.id.btnDirHorz)
        toggleDir?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                onUpdate("dir", if (checkedId == R.id.btnDirVert) "Vertical" else "Horizontal")
            }
        }

        // 4. Interval
        panelView.findViewById<EditText>(R.id.etPropInterval)?.apply {
            setText(data.props["interval"] ?: "100")
            addTextChangedListener(TextWatcherAdapter { onUpdate("interval", it) })
        }

        // 5. Initial Style Selector setup
        updateStyleAdapter(panelView, curMode, data, onUpdate)

        // 6. Color Palette
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
