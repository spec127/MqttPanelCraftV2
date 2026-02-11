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
import com.example.mqttpanelcraft.ui.views.ColorPaletteView
import com.example.mqttpanelcraft.utils.TextWatcherAdapter
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject

object ColorPaletteDefinition : IComponentDefinition {

    override val type = "PALETTE"
    override val defaultSize = Size(200, 200)
    override val labelPrefix = "palette"
    override val iconResId = R.drawable.ic_palette
    override val group = "CONTROL"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val palette =
                ColorPaletteView(context).apply {
                    tag = "target"
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                            )
                }
        container.addView(palette, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val palette = view.findViewWithTag<ColorPaletteView>("target") ?: return

        palette.controllerStyle = data.props["style"] ?: "Square Pad"
        palette.colorMode = data.props["mode"] ?: "Full Color"
        palette.controlTarget = data.props["target"] ?: "Brightness"
        palette.showHexCode = (data.props["show_code"] ?: "true") == "true"

        val colorHex = data.props["theme_color"] ?: "#3B82F6"
        try {
            palette.themeColor = Color.parseColor(colorHex)
        } catch (e: Exception) {
            palette.themeColor = Color.parseColor("#3B82F6")
        }
    }

    override val propertiesLayoutId = R.layout.layout_prop_color_palette

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

        // 1. Controller Style
        val toggleStyle = panelView.findViewById<MaterialButtonToggleGroup>(R.id.togglePaletteStyle)
        val curStyle = data.props["style"] ?: "Square Pad"
        toggleStyle?.check(if (curStyle == "Arc Ring") R.id.btnStyleArc else R.id.btnStyleSquare)

        // 2. Color Mode
        val toggleMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.togglePaletteMode)
        val curMode = data.props["mode"] ?: "Full Color"
        toggleMode?.check(if (curMode == "Monochrome") R.id.btnModeMono else R.id.btnModeFull)

        // Conditional Containers
        val containerThemeColor = panelView.findViewById<View>(R.id.containerPaletteThemeColor)
        val menuTarget = panelView.findViewById<View>(R.id.menuControlTarget)
        val containerInterval = panelView.findViewById<View>(R.id.containerPaletteInterval)

        fun updateVisibility(style: String, mode: String) {
            // Theme Color: Mono + Square
            containerThemeColor?.visibility =
                    if (style == "Square Pad" && mode == "Monochrome") View.VISIBLE else View.GONE
            // Control Target: Mono + Arc
            menuTarget?.visibility =
                    if (style == "Arc Ring" && mode == "Monochrome") View.VISIBLE else View.GONE
        }
        updateVisibility(curStyle, curMode)

        toggleStyle?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newStyle = if (checkedId == R.id.btnStyleArc) "Arc Ring" else "Square Pad"
                onUpdate("style", newStyle)
                updateVisibility(newStyle, data.props["mode"] ?: "Full Color")
            }
        }

        toggleMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = if (checkedId == R.id.btnModeMono) "Monochrome" else "Full Color"
                onUpdate("mode", newMode)
                updateVisibility(data.props["style"] ?: "Square Pad", newMode)
            }
        }

        // 3. Theme Color Picker (Mono Square)
        val colorViews =
                listOf(R.id.vColor1, R.id.vColor2, R.id.vColor3, R.id.vColor4, R.id.vColor5).map {
                    panelView.findViewById<View>(it)
                }
        fun refreshColors() {
            val recent = ColorHistoryManager.load(context)
            colorViews.forEachIndexed { i, v ->
                if (v != null && i < recent.size) {
                    v.backgroundTintList = ColorStateList.valueOf(Color.parseColor(recent[i]))
                    v.setOnClickListener { onUpdate("theme_color", recent[i]) }
                }
            }
        }
        refreshColors()
        panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener { anchor ->
            val cur = data.props["theme_color"] ?: "#3B82F6"
            var tempColor = cur
            ColorPickerDialog(
                            context,
                            cur,
                            true,
                            {
                                tempColor = it
                                onUpdate("theme_color", it)
                            },
                            {
                                ColorHistoryManager.save(context, tempColor)
                                refreshColors()
                            }
                    )
                    .show(anchor)
        }

        // 4. Control Target Dropdown
        val tvTarget =
                panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.tvControlTarget)
        val targetOptions =
                listOf(
                        context.getString(R.string.val_palette_target_brightness) to "Brightness",
                        context.getString(R.string.val_palette_target_saturation) to "Saturation"
                )
        val targetAdapter =
                android.widget.ArrayAdapter(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        targetOptions.map { it.first }
                )
        tvTarget?.setAdapter(targetAdapter)
        tvTarget?.setText(
                targetOptions.find { it.second == (data.props["target"] ?: "Brightness") }?.first,
                false
        )
        tvTarget?.setOnItemClickListener { _, _, position, _ ->
            onUpdate("target", targetOptions[position].second)
        }

        // 5. Send Strategy
        val tvStrategy =
                panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.tvSendStrategy)
        val strategyOptions =
                listOf(
                        context.getString(R.string.val_strategy_release) to "On Release",
                        context.getString(R.string.val_strategy_continuous) to "Continuous"
                )
        val strategyAdapter =
                android.widget.ArrayAdapter(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        strategyOptions.map { it.first }
                )
        tvStrategy?.setAdapter(strategyAdapter)
        val curStrategy = data.props["strategy"] ?: "On Release"
        tvStrategy?.setText(strategyOptions.find { it.second == curStrategy }?.first, false)
        containerInterval?.visibility = if (curStrategy == "Continuous") View.VISIBLE else View.GONE

        tvStrategy?.setOnItemClickListener { _, _, position, _ ->
            val newStrategy = strategyOptions[position].second
            onUpdate("strategy", newStrategy)
            containerInterval?.visibility =
                    if (newStrategy == "Continuous") View.VISIBLE else View.GONE
        }

        // 6. Interval
        panelView.findViewById<EditText>(R.id.etPaletteInterval)?.apply {
            setText(data.props["interval"] ?: "100")
            addTextChangedListener(TextWatcherAdapter { onUpdate("interval", it) })
        }

        // 7. Data Format
        val tvFormat =
                panelView.findViewById<android.widget.AutoCompleteTextView>(R.id.tvDataFormat)
        val formatOptions =
                listOf(
                        context.getString(R.string.val_format_json_rgb) to "JSON (RGB)",
                        context.getString(R.string.val_format_json_hsv) to "JSON (HSV)",
                        context.getString(R.string.val_format_hex) to "Hex String",
                        context.getString(R.string.val_format_raw) to "Raw Value"
                )
        val formatAdapter =
                android.widget.ArrayAdapter(
                        context,
                        android.R.layout.simple_dropdown_item_1line,
                        formatOptions.map { it.first }
                )
        tvFormat?.setAdapter(formatAdapter)
        tvFormat?.setText(
                formatOptions.find { it.second == (data.props["format"] ?: "JSON (RGB)") }?.first,
                false
        )
        tvFormat?.setOnItemClickListener { _, _, position, _ ->
            onUpdate("format", formatOptions[position].second)
        }

        // 8. Show Hex Code
        panelView.findViewById<SwitchMaterial>(R.id.swShowHexCode)?.apply {
            isChecked = (data.props["show_code"] ?: "true") == "true"
            setOnCheckedChangeListener { _, checked -> onUpdate("show_code", checked.toString()) }
        }
    }

    override fun attachBehavior(
            view: View,
            data: ComponentData,
            sendMqtt: (topic: String, payload: String) -> Unit,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val palette = view.findViewWithTag<ColorPaletteView>("target") ?: return

        val strategy = data.props["strategy"] ?: "On Release"
        val interval = (data.props["interval"] ?: "100").toLongOrNull() ?: 100L
        var lastSendTime = 0L

        palette.onColorChange = { h, s, v, isFinal ->
            val now = System.currentTimeMillis()
            if (strategy == "Continuous") {
                if (isFinal || now - lastSendTime >= interval) {
                    emit(data, h, s, v, sendMqtt)
                    lastSendTime = now
                }
            } else {
                // On Release
                if (isFinal) {
                    emit(data, h, s, v, sendMqtt)
                }
            }
        }
    }

    // Helper to emit based on format
    private fun emit(
            data: ComponentData,
            h: Float,
            s: Float,
            v: Float,
            sendMqtt: (String, String) -> Unit
    ) {
        if (data.topicConfig.isEmpty()) return

        val format = data.props["format"] ?: "JSON (RGB)"
        val colorInt = Color.HSVToColor(floatArrayOf(h, s, v))

        val payload =
                when (format) {
                    "JSON (RGB)" -> {
                        val json = JSONObject()
                        json.put("r", Color.red(colorInt))
                        json.put("g", Color.green(colorInt))
                        json.put("b", Color.blue(colorInt))
                        json.toString()
                    }
                    "JSON (HSV)" -> {
                        val json = JSONObject()
                        json.put("h", h.toInt())
                        json.put("s", (s * 100).toInt())
                        json.put("v", (v * 100).toInt())
                        json.toString()
                    }
                    "Hex String" -> String.format("#%06X", (0xFFFFFF and colorInt))
                    "Raw Value" -> {
                        // For Arc Mono, send 0-100 of the target
                        if (data.props["style"] == "Arc Ring" && data.props["mode"] == "Monochrome"
                        ) {
                            val target = data.props["target"] ?: "Brightness"
                            if (target == "Brightness") (v * 100).toInt().toString()
                            else (s * 100).toInt().toString()
                        } else {
                            (v * 100).toInt().toString() // Default to value
                        }
                    }
                    else -> colorInt.toString()
                }
        sendMqtt(data.topicConfig, payload)
    }

    override fun onMqttMessage(
            view: View,
            data: ComponentData,
            payload: String,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // Simple reactive update if we receive a color? Not implemented for now to keep it thin.
    }
}
