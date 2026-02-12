package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.util.Size
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.views.ColorPaletteView
import com.example.mqttpanelcraft.utils.TextWatcherAdapter
import com.google.android.material.button.MaterialButtonToggleGroup
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

                palette.controllerStyle = data.props["style"] ?: "Arc Ring"
                palette.colorMode = data.props["mode"] ?: "Full Color"
                palette.controlTarget = data.props["target"] ?: "Brightness"
                palette.showHexCode = (data.props["show_code"] ?: "true") == "true"
                palette.showHue = (data.props["show_hue"] ?: "true") == "true"
                palette.showSaturation = (data.props["show_sat"] ?: "true") == "true"
                palette.showBrightness = (data.props["show_bri"] ?: "true") == "true"
                palette.baseHue = (data.props["hue"] ?: "0").toFloat()
                palette.currentV = (data.props["brightness"] ?: "0").toFloat() / 100f
                palette.currentS = (data.props["saturation"] ?: "100").toFloat() / 100f
        }

        override val propertiesLayoutId = R.layout.layout_prop_color_palette

        override fun bindPropertiesPanel(
                panelView: View,
                data: ComponentData,
                onUpdate: (String, String) -> Unit
        ) {
                val context = panelView.context

                val curStyle = data.props["style"] ?: "Arc Ring"
                val curMode = data.props["mode"] ?: "Full Color"
                val curTarget = data.props["target"] ?: "Brightness"

                // 1. Find all Toggle Groups and Views first to avoid scope issues
                val toggleStyle =
                        panelView.findViewById<
                                com.google.android.material.button.MaterialButtonToggleGroup>(
                                R.id.togglePaletteStyle
                        )
                val toggleMode =
                        panelView.findViewById<
                                com.google.android.material.button.MaterialButtonToggleGroup>(
                                R.id.togglePaletteMode
                        )

                // Visibility Logic for other containers
                val containerHue = panelView.findViewById<View>(R.id.containerPaletteHue)
                val containerValue = panelView.findViewById<View>(R.id.containerPaletteValue)
                val containerThemeColor =
                        panelView.findViewById<View>(R.id.containerPaletteThemeColor)
                val menuTarget = panelView.findViewById<View>(R.id.menuControlTarget)
                val containerInterval = panelView.findViewById<View>(R.id.containerPaletteInterval)

                var currentTarget = curTarget

                fun updateSharedVisibility(style: String, mode: String, target: String) {
                        containerHue?.visibility = View.GONE
                        containerThemeColor?.visibility = View.GONE
                        containerValue?.visibility = View.GONE
                        val isMonoArc = style == "Arc Ring" && mode == "Monochrome"
                        menuTarget?.visibility = if (isMonoArc) View.VISIBLE else View.GONE

                        // Premium Appearance Toggles - Visibility based on category
                        val itemHex = panelView.findViewById<View>(R.id.itemShowHex)
                        val itemHue = panelView.findViewById<View>(R.id.itemShowHue)
                        val itemSat = panelView.findViewById<View>(R.id.itemShowSaturation)
                        val itemBri = panelView.findViewById<View>(R.id.itemShowBrightness)

                        // 1. Always show Hex
                        itemHex?.visibility = View.VISIBLE

                        if (style == "Arc Ring") {
                                if (mode == "Full Color") {
                                        // 全彩 + 環形：僅顯示「顯示色碼」與「顯示色相」
                                        itemHue?.visibility = View.VISIBLE
                                        itemSat?.visibility = View.GONE
                                        itemBri?.visibility = View.GONE
                                } else {
                                        // 單色 + 環形：僅顯示「顯示色碼」與（飽和度或亮度，依控制目標而定）
                                        itemHue?.visibility = View.GONE
                                        // Use passed target instead of data.props snapshot
                                        itemSat?.visibility =
                                                if (target == "Saturation") View.VISIBLE
                                                else View.GONE
                                        itemBri?.visibility =
                                                if (target == "Brightness") View.VISIBLE
                                                else View.GONE
                                }
                        } else {
                                // Square Pad
                                if (mode == "Full Color") {
                                        // 全彩 + 方形：僅顯示「顯示色碼」與「顯示亮度」
                                        itemHue?.visibility = View.GONE
                                        itemSat?.visibility = View.GONE
                                        itemBri?.visibility = View.VISIBLE
                                } else {
                                        // 單色 + 方形：色碼 + 亮度 + 飽和
                                        itemHue?.visibility = View.GONE
                                        itemSat?.visibility = View.VISIBLE
                                        itemBri?.visibility = View.VISIBLE
                                }
                        }
                }

                // 2. Setup Listeners
                toggleStyle?.apply {
                        check(
                                if (curStyle == "Square Pad") R.id.btnStyleSquare
                                else R.id.btnStyleArc
                        )
                        addOnButtonCheckedListener { _, checkedId, isChecked ->
                                if (isChecked) {
                                        val nextStyle =
                                                if (checkedId == R.id.btnStyleSquare) "Square Pad"
                                                else "Arc Ring"
                                        onUpdate("style", nextStyle)
                                        val currentMode =
                                                if (toggleMode?.checkedButtonId == R.id.btnModeMono)
                                                        "Monochrome"
                                                else "Full Color"
                                        updateSharedVisibility(
                                                nextStyle,
                                                currentMode,
                                                currentTarget
                                        )
                                }
                        }
                }

                toggleMode?.apply {
                        check(if (curMode == "Monochrome") R.id.btnModeMono else R.id.btnModeFull)
                        addOnButtonCheckedListener { _, checkedId, isChecked ->
                                if (isChecked) {
                                        val nextMode =
                                                if (checkedId == R.id.btnModeMono) "Monochrome"
                                                else "Full Color"
                                        onUpdate("mode", nextMode)
                                        val currentStyle =
                                                if (toggleStyle?.checkedButtonId ==
                                                                R.id.btnStyleSquare
                                                )
                                                        "Square Pad"
                                                else "Arc Ring"
                                        updateSharedVisibility(
                                                currentStyle,
                                                nextMode,
                                                currentTarget
                                        )
                                }
                        }
                }

                updateSharedVisibility(curStyle, curMode, currentTarget)

                // 3. Appearance Switches
                val itemShowHex = panelView.findViewById<LinearLayout>(R.id.itemShowHex)
                val checkShowHex = panelView.findViewById<ImageView>(R.id.checkShowHex)
                var showHex = (data.props["show_code"] ?: "true") == "true"
                checkShowHex?.visibility = if (showHex) View.VISIBLE else View.INVISIBLE
                itemShowHex?.setOnClickListener {
                        showHex = !showHex
                        onUpdate("show_code", showHex.toString())
                        checkShowHex?.visibility = if (showHex) View.VISIBLE else View.INVISIBLE
                }

                val itemShowHue = panelView.findViewById<LinearLayout>(R.id.itemShowHue)
                val checkShowHue = panelView.findViewById<ImageView>(R.id.checkShowHue)
                var showHue = (data.props["show_hue"] ?: "true") == "true"
                checkShowHue?.visibility = if (showHue) View.VISIBLE else View.INVISIBLE
                itemShowHue?.setOnClickListener {
                        showHue = !showHue
                        onUpdate("show_hue", showHue.toString())
                        checkShowHue?.visibility = if (showHue) View.VISIBLE else View.INVISIBLE
                }

                val itemShowSat = panelView.findViewById<LinearLayout>(R.id.itemShowSaturation)
                val checkShowSat = panelView.findViewById<ImageView>(R.id.checkShowSaturation)
                var showSat = (data.props["show_sat"] ?: "true") == "true"
                checkShowSat?.visibility = if (showSat) View.VISIBLE else View.INVISIBLE
                itemShowSat?.setOnClickListener {
                        showSat = !showSat
                        onUpdate("show_sat", showSat.toString())
                        checkShowSat?.visibility = if (showSat) View.VISIBLE else View.INVISIBLE
                }

                val itemShowBri = panelView.findViewById<LinearLayout>(R.id.itemShowBrightness)
                val checkShowBri = panelView.findViewById<ImageView>(R.id.checkShowBrightness)
                var showBri = (data.props["show_bri"] ?: "true") == "true"
                checkShowBri?.visibility = if (showBri) View.VISIBLE else View.INVISIBLE
                itemShowBri?.setOnClickListener {
                        showBri = !showBri
                        onUpdate("show_bri", showBri.toString())
                        checkShowBri?.visibility = if (showBri) View.VISIBLE else View.INVISIBLE
                }

                // 4. Control Target Dropdown
                val tvTarget =
                        panelView.findViewById<android.widget.AutoCompleteTextView>(
                                R.id.tvControlTarget
                        )
                val targetOptions =
                        listOf(
                                context.getString(R.string.val_palette_target_brightness) to
                                        "Brightness",
                                context.getString(R.string.val_palette_target_saturation) to
                                        "Saturation"
                        )
                val targetAdapter =
                        android.widget.ArrayAdapter(
                                context,
                                android.R.layout.simple_dropdown_item_1line,
                                targetOptions.map { it.first }
                        )
                tvTarget?.setAdapter(targetAdapter)
                tvTarget?.setText(
                        targetOptions
                                .find { it.second == (data.props["target"] ?: "Brightness") }
                                ?.first,
                        false
                )
                tvTarget?.setOnItemClickListener { _, _, position, _ ->
                        val nextTarget = targetOptions[position].second
                        currentTarget = nextTarget
                        onUpdate("target", nextTarget)

                        val currentStyle =
                                if (toggleStyle?.checkedButtonId == R.id.btnStyleSquare)
                                        "Square Pad"
                                else "Arc Ring"
                        val currentMode =
                                if (toggleMode?.checkedButtonId == R.id.btnModeMono) "Monochrome"
                                else "Full Color"

                        updateSharedVisibility(currentStyle, currentMode, nextTarget)
                }

                // 5. Send Strategy
                val tvStrategy =
                        panelView.findViewById<android.widget.AutoCompleteTextView>(
                                R.id.tvSendStrategy
                        )
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
                containerInterval?.visibility =
                        if (curStrategy == "Continuous") View.VISIBLE else View.GONE

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
                        panelView.findViewById<android.widget.AutoCompleteTextView>(
                                R.id.tvDataFormat
                        )
                val formatOptions =
                        listOf(
                                context.getString(R.string.val_format_hex) to "Hex String",
                                context.getString(R.string.val_format_json_hsv) to "JSON (HSV)",
                                context.getString(R.string.val_format_json_rgb) to "JSON (RGB)"
                        )
                val formatAdapter =
                        android.widget.ArrayAdapter(
                                context,
                                android.R.layout.simple_dropdown_item_1line,
                                formatOptions.map { it.first }
                        )
                tvFormat?.setAdapter(formatAdapter)
                tvFormat?.setText(
                        formatOptions
                                .find { it.second == (data.props["format"] ?: "Hex String") }
                                ?.first,
                        false
                )
                tvFormat?.setOnItemClickListener { _, _, position, _ ->
                        onUpdate("format", formatOptions[position].second)
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

                palette.onThemeColorSelect = { color ->
                        val hex = String.format("#%06X", (0xFFFFFF and color))
                        onUpdateProp("theme_color", hex)
                }

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

                val format = data.props["format"] ?: "Hex String"
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
                // Simple reactive update if we receive a color? Not implemented for now to keep it
                // thin.
        }
}
