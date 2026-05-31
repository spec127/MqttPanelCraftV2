package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.Size
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.findComponentTarget
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.GaugeMeterView
import com.google.android.material.button.MaterialButtonToggleGroup

object GaugeMeterDefinition : IComponentDefinition {
    override val type: String = "GAUGE_METER"
    override val defaultSize: Size = Size(200, 200)
    override val labelPrefix: String = "gauge"
    override val iconResId: Int = android.R.drawable.ic_menu_compass
    override val group: String = "SENSOR"
    override val propertiesLayoutId: Int = R.layout.layout_prop_gauge_meter

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val meter = GaugeMeterView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            tag = "target"
        }
        container.addView(meter)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val meter = view.findComponentTarget<GaugeMeterView>() ?: return

        meter.minValue = data.props["min"]?.toFloatOrNull() ?: 0f
        meter.maxValue = data.props["max"]?.toFloatOrNull() ?: 100f
        meter.value = data.props["value"]?.toFloatOrNull() ?: meter.minValue
        meter.unit = data.props["unit"] ?: ""

        val styleStr = data.props["style"] ?: "NEEDLE"
        meter.meterStyle = try { GaugeMeterView.Style.valueOf(styleStr) } catch (e: Exception) { GaugeMeterView.Style.NEEDLE }

        val angleStr = data.props["angle"] ?: "ARC_270"
        meter.trackAngle = try { GaugeMeterView.TrackAngle.valueOf(angleStr) } catch (e: Exception) { GaugeMeterView.TrackAngle.ARC_270 }

        data.props["theme_color"]?.let { c -> try { meter.themeColor = Color.parseColor(c) } catch (e: Exception) {} }

        meter.showTicks = (data.props["show_ticks"] ?: "false").toBoolean()

        meter.thresholdMode = (data.props["threshold_mode"] ?: "false").toBoolean()
        val effectStr = data.props["threshold_effect"] ?: "VALUE_CHANGE"
        meter.thresholdEffect = try { GaugeMeterView.ThresholdEffect.valueOf(effectStr) } catch (e: Exception) { GaugeMeterView.ThresholdEffect.VALUE_CHANGE }

        val thresholds = mutableListOf<Pair<Float, Int>>()
        data.props["rgb_states"]?.split(",")?.forEach {
            val parts = it.split("|")
            if (parts.size == 2) {
                val th = parts[0].toFloatOrNull()
                val color = try { Color.parseColor(parts[1]) } catch (e: Exception) { null }
                if (th != null && color != null) {
                    thresholds.add(Pair(th, color))
                }
            }
        }
        meter.thresholds = thresholds
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val ctx = panelView.context

        // Basic Bounds & Unit
        CommonPropBinder.bindEditText(panelView, R.id.etMin, "min", data, onUpdate, "0")
        CommonPropBinder.bindEditText(panelView, R.id.etMax, "max", data, onUpdate, "100")
        CommonPropBinder.bindEditText(panelView, R.id.etUnit, "unit", data, onUpdate, "")
        
        CommonPropBinder.bindColorPalette(panelView, R.id.containerThemeColor, "theme_color", data, onUpdate, "主體顏色", "#4CAF50")

        // Threshold Mode Toggle
        val thresholdModeKey = "threshold_mode"
        val itemThresholdMode = panelView.findViewById<View>(R.id.itemThresholdMode)
        val checkThresholdMode = panelView.findViewById<ImageView>(R.id.checkThresholdMode)
        val containerThresholdOptions = panelView.findViewById<View>(R.id.containerThresholdOptions)
        
        var isThresholdOn = (data.props[thresholdModeKey] ?: "false").toBoolean()
        checkThresholdMode?.visibility = if (isThresholdOn) View.VISIBLE else View.INVISIBLE
        containerThresholdOptions?.visibility = if (isThresholdOn) View.VISIBLE else View.GONE

        fun bindThresholdSubViews() {
            val density = ctx.resources.displayMetrics.density

            val toggleEffect = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleThresholdEffect)
            val currentEffect = data.props["threshold_effect"] ?: "VALUE_CHANGE"
            toggleEffect?.check(if (currentEffect == "GRADIENT") R.id.btnEffectGradient else R.id.btnEffectSolid)
            
            toggleEffect?.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val effect = if (checkedId == R.id.btnEffectGradient) "GRADIENT" else "VALUE_CHANGE"
                    onUpdate("threshold_effect", effect)
                }
            }
            
            val containerRgbStates = panelView.findViewById<LinearLayout>(R.id.containerRgbStates)
            
            fun parseThresholds(): MutableList<Pair<String, String>> {
                val str = data.props["rgb_states"] ?: ""
                if (str.isEmpty()) return mutableListOf()
                return str.split(",").mapNotNull { 
                    val parts = it.split("|")
                    if (parts.size >= 2) parts[0] to parts[1] else null
                }.toMutableList()
            }

            fun saveThresholds(list: List<Pair<String, String>>) {
                onUpdate("rgb_states", list.joinToString(",") { "${it.first}|${it.second}" })
            }

            var count = (data.props["rgb_state_count"] ?: "2").toIntOrNull() ?: 2
            if (count < 2) {
                count = 2
                onUpdate("rgb_state_count", "2")
            }

            fun updateThresholdRows() {
                containerRgbStates?.removeAllViews()
                val currentStates = parseThresholds()
                val defaultThemeColor = data.props["theme_color"] ?: "#4CAF50"
                val defaultColors = listOf(defaultThemeColor, "#F44336", "#FFEB3B", "#2196F3", "#9C27B0")
                
                while (currentStates.size < count) {
                    val size = currentStates.size
                    val defaultVal = if (currentStates.isEmpty()) "50" else currentStates.last().first
                    val idx = size % defaultColors.size
                    currentStates.add(defaultVal to defaultColors[idx])
                }
                while (currentStates.size > count) { currentStates.removeAt(currentStates.size - 1) }
                saveThresholds(currentStates)

                currentStates.forEachIndexed { index, statePair ->
                    val rowHeightPx = (40 * density).toInt()
                    val rowLayout = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setBackgroundResource(R.drawable.bg_input_outline)
                        layoutParams = LinearLayout.LayoutParams(-1, rowHeightPx).apply { bottomMargin = (8 * density).toInt() }
                    }
                    
                    val paletteContainer = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, rowHeightPx, 0.6f)
                        val pad4 = (4 * density).toInt()
                        setPadding(pad4, 0, pad4, 0)
                    }
                    
                    val ledColors = listOf("#64748B", "#F44336", "#4CAF50", "#2196F3", "#FFEB3B")
                    val activeColor = statePair.second.uppercase()
                    
                    ledColors.forEach { colorStr ->
                        val frame = FrameLayout(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, -1, 1f) }
                        val colorBall = View(ctx).apply {
                            val isSelected = activeColor == colorStr.uppercase()
                            val cSize = (18 * density).toInt()
                            layoutParams = FrameLayout.LayoutParams(cSize, cSize, android.view.Gravity.CENTER)
                            setBackgroundResource(R.drawable.shape_circle_color)
                            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(colorStr))
                            
                            if (isSelected) {
                                val strokeW = (2 * density).toInt()
                                setPadding(strokeW, strokeW, strokeW, strokeW)
                                val sd = android.graphics.drawable.GradientDrawable().apply {
                                    shape = android.graphics.drawable.GradientDrawable.OVAL
                                    setColor(Color.parseColor(colorStr))
                                    setStroke((2 * density).toInt(), Color.WHITE)
                                }
                                background = sd
                            }
                            
                            setOnClickListener {
                                currentStates[index] = currentStates[index].first to colorStr
                                saveThresholds(currentStates)
                                updateThresholdRows()
                            }
                        }
                        frame.addView(colorBall)
                        paletteContainer.addView(frame)
                    }
                    
                    val pickerFrame = FrameLayout(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, -1, 1f) }
                    val btnColorCustom = ImageView(ctx).apply {
                        val pSize = (18 * density).toInt()
                        layoutParams = FrameLayout.LayoutParams(pSize, pSize, android.view.Gravity.CENTER)
                        setImageResource(R.drawable.ic_palette_open)
                        imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(statePair.second))
                        setOnClickListener { anchor ->
                            com.example.mqttpanelcraft.ui.ColorPickerDialog(ctx, statePair.second, true, { c ->
                                currentStates[index] = currentStates[index].first to c
                                saveThresholds(currentStates)
                                updateThresholdRows()
                            }).show(anchor)
                        }
                    }
                    pickerFrame.addView(btnColorCustom)
                    paletteContainer.addView(pickerFrame)
                    
                    val divider = View(ctx).apply {
                        val dHeight = (22 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams((1 * density).toInt(), dHeight)
                        setBackgroundColor(Color.parseColor("#E0E0E0"))
                    }
                    
                    val rangeContainer = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, -1, 0.4f).apply { marginStart = (8 * density).toInt() }
                    }
                    
                    val textWrapper = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(-2, -1)
                    }
                    
                    val tvRangePrefix = TextView(ctx).apply {
                        textSize = 12f
                        setTextColor(Color.parseColor("#1E293B"))
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        tag = "tv_range_$index"
                        
                        val startVal = if (index == 0) "0" else {
                            currentStates.getOrNull(index - 1)?.first ?: "0"
                        }
                        text = "$startVal ~ "
                    }
                    
                    val et = android.widget.EditText(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), -1)
                        setText(statePair.first)
                        textSize = 12f
                        background = null
                        setTextColor(Color.parseColor("#2196F3"))
                        gravity = android.view.Gravity.CENTER
                        hint = "上限"
                        setHintTextColor(Color.parseColor("#94A3B8"))
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun afterTextChanged(s: android.text.Editable?) {
                                val newVal = s?.toString() ?: ""
                                currentStates[index] = newVal to currentStates[index].second
                                saveThresholds(currentStates)
                                
                                val nextTag = "tv_range_${index + 1}"
                                val nextTv = containerRgbStates?.findViewWithTag<TextView>(nextTag)
                                if (nextTv != null) {
                                    nextTv.text = "${newVal} ~ "
                                }
                            }
                            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                        })
                    }
                    
                    et.setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) {
                            val input = et.text.toString().toFloatOrNull() ?: 0f
                            val minLimit = if (index == 0) (data.props["min"]?.toFloatOrNull() ?: 0f) 
                                           else currentStates.getOrNull(index - 1)?.first?.toFloatOrNull() ?: 0f
                            val maxLimit = data.props["max"]?.toFloatOrNull() ?: 100f
                            
                            val clamped = input.coerceIn(minLimit, maxLimit)
                            val finalStr = if (clamped % 1.0 == 0.0) clamped.toInt().toString() else clamped.toString()
                            
                            et.setText(finalStr)
                            currentStates[index] = finalStr to activeColor
                            saveThresholds(currentStates)
                            
                            val nextTv = containerRgbStates?.findViewWithTag<TextView>("tv_range_${index + 1}")
                            nextTv?.text = "$finalStr ~ "
                        }
                    }
                    
                    textWrapper.addView(tvRangePrefix)
                    textWrapper.addView(et)
                    rangeContainer.addView(textWrapper)
                    
                    rowLayout.addView(paletteContainer)
                    rowLayout.addView(divider)
                    rowLayout.addView(rangeContainer)
                    containerRgbStates?.addView(rowLayout)
                }
            }

            val containerStepper = panelView.findViewById<LinearLayout>(R.id.containerRgbStepper)
            containerStepper?.removeAllViews()
            containerStepper?.setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
            
            val btnMinus = TextView(ctx).apply {
                text = "—"
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(Color.parseColor("#475569"))
                gravity = android.view.Gravity.CENTER
                val btnPad = (8 * density).toInt()
                setPadding(btnPad, 0, btnPad, 0)
                val outVal = android.util.TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true)
                setBackgroundResource(outVal.resourceId)
                setOnClickListener { 
                    val c = (data.props["rgb_state_count"] ?: "2").toInt()
                    if (c > 2) {
                        onUpdate("rgb_state_count", (c - 1).toString())
                        bindThresholdSubViews() 
                    }
                }
            }
            
            val tvCount = TextView(ctx).apply {
                text = count.toString()
                textSize = 13f
                setTextColor(Color.parseColor("#1E293B"))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            }
            
            val btnPlus = TextView(ctx).apply {
                text = "＋"
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(Color.parseColor("#475569"))
                gravity = android.view.Gravity.CENTER
                val btnPad = (8 * density).toInt()
                setPadding(btnPad, 0, btnPad, 0)
                val outVal = android.util.TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outVal, true)
                setBackgroundResource(outVal.resourceId)
                setOnClickListener { 
                    val c = (data.props["rgb_state_count"] ?: "2").toInt()
                    if (c < 5) {
                        onUpdate("rgb_state_count", (c + 1).toString())
                        bindThresholdSubViews() 
                    }
                }
            }
            
            containerStepper?.addView(btnMinus)
            containerStepper?.addView(tvCount)
            containerStepper?.addView(btnPlus)
            
            updateThresholdRows()
        }

        if (isThresholdOn) {
            bindThresholdSubViews()
        }

        itemThresholdMode?.setOnClickListener {
            isThresholdOn = !isThresholdOn
            checkThresholdMode?.visibility = if (isThresholdOn) View.VISIBLE else View.INVISIBLE
            containerThresholdOptions?.visibility = if (isThresholdOn) View.VISIBLE else View.GONE
            
            onUpdate(thresholdModeKey, isThresholdOn.toString())
            
            if (isThresholdOn) {
                bindThresholdSubViews()
            }
        }

        // Style Selector
        val acStyle = panelView.findViewById<AutoCompleteTextView>(R.id.acMeterStyle)
        val styleAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, listOf("指針 (Needle)", "陣列 (Segmented)"))
        acStyle.setAdapter(styleAdapter)
        val currentStyle = data.props["style"] ?: "NEEDLE"
        acStyle.setText(if (currentStyle == "NEEDLE") "指針 (Needle)" else "陣列 (Segmented)", false)
        acStyle.setOnItemClickListener { _, _, pos, _ ->
            val newValue = if (pos == 0) "NEEDLE" else "SEGMENTED"
            onUpdate("style", newValue)
        }

        // Angle Selector
        val acAngle = panelView.findViewById<AutoCompleteTextView>(R.id.acTrackAngle)
        val angleAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, listOf("120°", "180°", "270°"))
        acAngle.setAdapter(angleAdapter)
        val currentAngle = data.props["angle"] ?: "ARC_270"
        val angleStr = when (currentAngle) {
            "ARC_120" -> "120°"
            "ARC_180" -> "180°"
            else -> "270°"
        }
        acAngle.setText(angleStr, false)
        acAngle.setOnItemClickListener { _, _, pos, _ ->
            val newValue = when (pos) {
                0 -> "ARC_120"
                1 -> "ARC_180"
                else -> "ARC_270"
            }
            onUpdate("angle", newValue)
        }

        // Ticks Selector
        val acGaugeTicks = panelView.findViewById<AutoCompleteTextView>(R.id.acGaugeTicks)
        val ticksAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, listOf("隱藏刻度", "顯示刻度"))
        if (acGaugeTicks != null) {
            acGaugeTicks.setAdapter(ticksAdapter)
            val currentTicks = data.props["show_ticks"] ?: "false"
            acGaugeTicks.setText(if (currentTicks == "true") "顯示刻度" else "隱藏刻度", false)
            acGaugeTicks.setOnItemClickListener { _, _, pos, _ ->
                val newValue = if (pos == 1) "true" else "false"
                onUpdate("show_ticks", newValue)
            }
        }
    }

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
        val meter = view.findComponentTarget<GaugeMeterView>() ?: return
        try {
            val v = payload.toFloat()
            meter.value = v
            onUpdateProp("value", v.toString())
        } catch (_: Exception) {}
    }
}
