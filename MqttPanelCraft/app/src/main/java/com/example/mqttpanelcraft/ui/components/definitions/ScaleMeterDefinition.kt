package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.Size
import android.view.View
import com.example.mqttpanelcraft.ui.components.findComponentTarget
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.components.prop.PropertyOption
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.views.ScaleMeterView

object ScaleMeterDefinition : IComponentDefinition {
    override val type: String = "SCALE_METER"
    override val defaultSize: Size = Size(200, 70)
    override val labelPrefix: String = "meter"
    override val displayNameResId: Int = R.string.component_label_scale_meter
    override val iconResId: Int = android.R.drawable.ic_menu_sort_by_size
    override val group = ComponentGroup.SENSOR
    override val propertiesLayoutId: Int = R.layout.layout_prop_scale_meter

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "value" to "70",
        "unit" to "%",
        "style" to "SEGMENTED",
        "orientation" to "HORIZONTAL",
        "feedback" to "Ticks",
        "show_ticks" to "true",
        "show_bubble" to "false",
        "show_value" to "false",
        "theme_color" to "#FF9800"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val meter = ScaleMeterView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            tag = "target"
            this.isEditMode = isEditMode
        }
        container.addView(meter)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val meter = view.findComponentTarget<ScaleMeterView>() ?: return

        meter.minValue = data.props["min"]?.toFloatOrNull() ?: 0f
        meter.maxValue = data.props["max"]?.toFloatOrNull() ?: 100f
        meter.value = data.props["value"]?.toFloatOrNull() ?: meter.minValue

        meter.unit = data.props["unit"] ?: ""

        val styleStr = data.props["style"] ?: "SOLID"
        meter.style = try { ScaleMeterView.Style.valueOf(styleStr) } catch (e: Exception) { ScaleMeterView.Style.SOLID }

        val orientStr = data.props["orientation"] ?: "VERTICAL"
        meter.meterOrientation = try { ScaleMeterView.Orientation.valueOf(orientStr) } catch (e: Exception) { ScaleMeterView.Orientation.VERTICAL }

        meter.showValue = (data.props["show_value"] ?: "false").toBoolean()
        
        // 刻度與氣泡整合反饋屬性 (與 Slider 屬性同源，支援舊專案自動相容升級)
        val feedbackStr = data.props["feedback"] ?: run {
            val hasTicks = (data.props["show_ticks"] ?: "false").toBoolean()
            val hasBubble = (data.props["show_bubble"] ?: "false").toBoolean()
            when {
                hasTicks && hasBubble -> "Both"
                hasTicks -> "Ticks"
                hasBubble -> "Bubble"
                else -> "None"
            }
        }
        meter.showTicks = feedbackStr == "Ticks" || feedbackStr == "Both"
        meter.showBubble = feedbackStr == "Bubble" || feedbackStr == "Both"

        data.props["theme_color"]?.let { c -> try { meter.themeColor = Color.parseColor(c) } catch (e: Exception) {} }

        // Threshold Logic
        meter.thresholdMode = (data.props["threshold_mode"] ?: "false").toBoolean()
        val effectStr = data.props["threshold_effect"] ?: "VALUE_CHANGE"
        meter.thresholdEffect = try { ScaleMeterView.ThresholdEffect.valueOf(effectStr) } catch (e: Exception) { ScaleMeterView.ThresholdEffect.VALUE_CHANGE }

        // Parse thresholds
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
        try {
            val ctx = panelView.context
            
            // Bounds (now in Interaction section)
            CommonPropBinder.bindEditText(panelView, R.id.etMin, "min", data, onUpdate, "0")
            CommonPropBinder.bindEditText(panelView, R.id.etMax, "max", data, onUpdate, "100")
            CommonPropBinder.bindEditText(panelView, R.id.etScaleUnit, "unit", data, onUpdate, "")
            CommonPropBinder.bindColorPalette(
                    panelView,
                    R.id.containerThemeColor,
                    "theme_color",
                    data,
                    onUpdate,
                    panelView.context.getString(R.string.properties_label_theme_color),
                    "#FF9800"
            )

            // Feedback Checkboxes
            fun bindCheckbox(itemId: Int, checkId: Int, key: String) {
                val item = panelView.findViewById<View>(itemId)
                val check = panelView.findViewById<android.widget.ImageView>(checkId)
                var isChecked = (data.props[key] ?: "false").toBoolean()
                check?.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE
                item?.setOnClickListener {
                    isChecked = !isChecked
                    check?.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE
                    onUpdate(key, isChecked.toString())
                }
            }

            bindCheckbox(R.id.itemShowValue, R.id.checkShowValue, "show_value")

            // Threshold Mode Toggle
            val thresholdModeKey = "threshold_mode"
            val itemThresholdMode = panelView.findViewById<View>(R.id.itemThresholdMode)
            val checkThresholdMode = panelView.findViewById<android.widget.ImageView>(R.id.checkThresholdMode)
            val containerThresholdOptions = panelView.findViewById<View>(R.id.containerThresholdOptions)
            
            var isThresholdOn = (data.props[thresholdModeKey] ?: "false").toBoolean()
            checkThresholdMode?.visibility = if (isThresholdOn) View.VISIBLE else View.INVISIBLE
            containerThresholdOptions?.visibility = if (isThresholdOn) View.VISIBLE else View.GONE

            // 宣告抽取出來的門檻值子視圖綁定函數，以實現點擊後的「即時渲染更新」與「高質感重構」
            fun bindThresholdSubViews() {
                val density = ctx.resources.displayMetrics.density

                // 1. 綁定左右同排切換按鈕，用於切換「全變 (Solid)」與「漸變 (Gradient)」
                val toggleEffect = panelView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleThresholdEffect)
                val currentEffect = data.props["threshold_effect"] ?: "VALUE_CHANGE"
                toggleEffect?.check(if (currentEffect == "GRADIENT") R.id.btnEffectGradient else R.id.btnEffectSolid)
                
                toggleEffect?.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (isChecked) {
                        val effect = if (checkedId == R.id.btnEffectGradient) "GRADIENT" else "VALUE_CHANGE"
                        onUpdate("threshold_effect", effect)
                    }
                }
                
                // Thresholds List
                val containerRgbStates = panelView.findViewById<android.widget.LinearLayout>(R.id.containerRgbStates)
                
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

                // 2. 數量控制強制重構：最少為 2 個區段，範圍 2..5
                var count = (data.props["rgb_state_count"] ?: "2").toIntOrNull() ?: 2
                if (count < 2) {
                    count = 2
                    onUpdate("rgb_state_count", "2")
                }

                fun updateThresholdRows() {
                    containerRgbStates?.removeAllViews()
                    val currentStates = parseThresholds()
                    val defaultThemeColor = data.props["theme_color"] ?: "#FF9800"
                    val defaultColors = listOf("#FF9800", "#4CAF50", "#2196F3", "#9C27B0", "#F44336")
                    
                    while (currentStates.size < count) {
                        val size = currentStates.size
                        // 修正：新增區段時，預設數值直接跟隨「上一個區段的數值」，不再盲目往上加 50 導致超過上限
                        val defaultVal = if (currentStates.isEmpty()) "50" else currentStates.last().first
                        val idx = size % defaultColors.size
                        currentStates.add(defaultVal to defaultColors[idx])
                    }
                    while (currentStates.size > count) { currentStates.removeAt(currentStates.size - 1) }
                    saveThresholds(currentStates)

                    currentStates.forEachIndexed { index, statePair ->
                        val rowHeightPx = (40 * density).toInt()
                        // 區段卡片行：完全參考 LED 設計 (移除 premium 圓角白底，改為統一框線)
                        val rowLayout = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            setBackgroundResource(R.drawable.bg_input_outline)
                            layoutParams = android.widget.LinearLayout.LayoutParams(-1, rowHeightPx).apply { bottomMargin = (8 * density).toInt() }
                        }
                        
                        // LED 同款五色盤色盤容器 (左側 60% 寬)
                        val paletteContainer = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, rowHeightPx, 0.6f)
                            val pad4 = (4 * density).toInt()
                            setPadding(pad4, 0, pad4, 0)
                        }
                        
                        // 預置 LED RGB 色塊列表：灰、紅、綠、藍、黃
                        val ledColors = listOf("#64748B", "#F44336", "#4CAF50", "#2196F3", "#FFEB3B")
                        val activeColor = statePair.second.uppercase()
                        
                        // 動態繪製這 5 個小色塊，點擊秒速換色，並加上精美的選取白邊與半透明外框
                        ledColors.forEach { colorStr ->
                            val frame = android.widget.FrameLayout(ctx).apply { layoutParams = android.widget.LinearLayout.LayoutParams(0, -1, 1f) }
                            val colorBall = View(ctx).apply {
                                val isSelected = activeColor == colorStr.uppercase()
                                val cSize = (18 * density).toInt()
                                layoutParams = android.widget.FrameLayout.LayoutParams(cSize, cSize, android.view.Gravity.CENTER)
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
                        
                        // 自訂色盤 ImageView (最右側)
                        val pickerFrame = android.widget.FrameLayout(ctx).apply { layoutParams = android.widget.LinearLayout.LayoutParams(0, -1, 1f) }
                        val btnColorCustom = android.widget.ImageView(ctx).apply {
                            val pSize = (18 * density).toInt()
                            layoutParams = android.widget.FrameLayout.LayoutParams(pSize, pSize, android.view.Gravity.CENTER)
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
                            layoutParams = android.widget.LinearLayout.LayoutParams((1 * density).toInt(), dHeight)
                            setBackgroundColor(Color.parseColor("#E0E0E0"))
                        }
                        
                        // 右側階梯數值區間配置容器 (右側 40% 寬)
                        val rangeContainer = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER
                            layoutParams = android.widget.LinearLayout.LayoutParams(0, -1, 0.4f).apply { marginStart = (8 * density).toInt() }
                        }
                        
                        val textWrapper = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER
                            layoutParams = android.widget.LinearLayout.LayoutParams(-2, -1)
                        }
                        
                        // 階梯起訖文字 (不能的下限，黑色)
                        val tvRangePrefix = android.widget.TextView(ctx).apply {
                            textSize = 12f
                            setTextColor(Color.parseColor("#1E293B")) // 改為黑色
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            // 設置 unique Tag，以實現上一行 EditText 打字時秒速連動
                            tag = "tv_range_$index"
                            
                            val startVal = if (index == 0) "0" else {
                                currentStates.getOrNull(index - 1)?.first ?: "0"
                            }
                            text = "$startVal ~ "
                        }
                        
                        // 輸入框移除外框線，與文字無縫融合並置中 (可以設定的上限，藍色)
                        val et = android.widget.EditText(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams((40 * density).toInt(), -1)
                            val maxValue = data.props["max_value"] ?: "100"
                            if (index == count - 1) {
                                setText(maxValue)
                                isEnabled = false
                                currentStates[index] = maxValue to currentStates[index].second
                            } else {
                                setText(statePair.first)
                                isEnabled = true
                            }
                            textSize = 12f
                            background = null
                            setTextColor(Color.parseColor("#2196F3")) // 改為藍色
                            gravity = android.view.Gravity.CENTER
                            hint = context.getString(R.string.meter_limit_max)
                            setHintTextColor(Color.parseColor("#94A3B8"))
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                            addTextChangedListener(object : android.text.TextWatcher {
                                override fun afterTextChanged(s: android.text.Editable?) {
                                    val newVal = s?.toString() ?: ""
                                    currentStates[index] = newVal to currentStates[index].second
                                    saveThresholds(currentStates)
                                    
                                    // 秒速即時連動刷新：尋找下一行的 Range TextView，更新其起點，保持打字不中斷！
                                    val nextTag = "tv_range_${index + 1}"
                                    val nextTv = containerRgbStates?.findViewWithTag<android.widget.TextView>(nextTag)
                                    if (nextTv != null) {
                                        nextTv.text = "${newVal} ~ "
                                    }
                                }
                                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                            })
                        }
                        
                        // 防呆驗證：失去焦點時，限制不能小於上一個值，且不能超過最大值
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
                                
                                val nextTv = containerRgbStates?.findViewWithTag<android.widget.TextView>("tv_range_${index + 1}")
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

                // 3. 數量控制 Stepper 簡化：寬度縮小，僅顯示純數字 count，精緻、物聯網感，與左右切換同排！
                val containerStepper = panelView.findViewById<android.widget.LinearLayout>(R.id.containerRgbStepper)
                containerStepper?.removeAllViews()
                containerStepper?.setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
                
                // 減號按鈕 (圓形 Pill TextView)
                val btnMinus = android.widget.TextView(ctx).apply {
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
                        if (c > 2) { // 限制最少 2 個區段，符合漸變色開啟要求 (Design Pattern: Kaizen)
                            onUpdate("rgb_state_count", (c - 1).toString())
                            bindThresholdSubViews() 
                        }
                    }
                }
                
                // 中間純數字
                val tvCount = android.widget.TextView(ctx).apply {
                    text = count.toString()
                    textSize = 13f
                    setTextColor(Color.parseColor("#1E293B"))
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, -1, 1f)
                }
                
                // 加號按鈕
                val btnPlus = android.widget.TextView(ctx).apply {
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
                        if (c < 5) { // 限制最大 5 個區段
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

            // 若目前為開啟狀態，則在初始化面板時直接綁定子視圖
            if (isThresholdOn) {
                bindThresholdSubViews()
            }

            // 門檻值開關點擊監聽器：點擊時立刻更新 UI，並無縫切換子面板顯示/隱藏與綁定！
            itemThresholdMode?.setOnClickListener {
                isThresholdOn = !isThresholdOn
                checkThresholdMode?.visibility = if (isThresholdOn) View.VISIBLE else View.INVISIBLE
                containerThresholdOptions?.visibility = if (isThresholdOn) View.VISIBLE else View.GONE
                
                onUpdate(thresholdModeKey, isThresholdOn.toString())
                
                if (isThresholdOn) {
                    bindThresholdSubViews()
                }
            }

            // SECTION 2: APPEARANCE
            CommonPropBinder.bindLocalizedDropdown(
                    panelView,
                    R.id.spScaleStyle,
                    "style",
                    data,
                    onUpdate,
                    listOf(
                            PropertyOption("SOLID", R.string.scale_style_solid),
                            PropertyOption("SEGMENTED", R.string.scale_style_segmented),
                            PropertyOption("THERMOMETER", R.string.scale_style_thermometer)
                    ),
                    "SOLID"
            )

            // 方向選擇器切換按鈕 (VERTICAL / HORIZONTAL)
            val toggleOrientation = panelView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleOrientation)
            val currentOrient = data.props["orientation"] ?: "VERTICAL"
            if (toggleOrientation != null) {
                toggleOrientation.check(
                    if (currentOrient.equals("VERTICAL", ignoreCase = true)) R.id.btnOrientationVert else R.id.btnOrientationHoriz
                )
                toggleOrientation.addOnButtonCheckedListener { _, checkedId, isChecked ->
                    if (isChecked) {
                        val newOrientation = if (checkedId == R.id.btnOrientationVert) "VERTICAL" else "HORIZONTAL"
                        val oldOrientation = data.props["orientation"] ?: "VERTICAL"
                        if (!newOrientation.equals(oldOrientation, ignoreCase = true)) {
                            val oldW = data.width
                            val oldH = data.height
                            onUpdate("w", oldH.toString())
                            onUpdate("h", oldW.toString())
                        }
                        onUpdate("orientation", newOrientation)
                    }
                }
            }

            // 綁定「刻度與氣泡」勾選框 (與 ColorPalette Premium Row 100% 統一)
            val checkShowTicks = panelView.findViewById<android.widget.ImageView>(R.id.checkShowTicks)
            val checkShowBubble = panelView.findViewById<android.widget.ImageView>(R.id.checkShowBubble)

            val currentFeedback = data.props["feedback"] ?: run {
                val t = (data.props["show_ticks"] ?: "false").toBoolean()
                val b = (data.props["show_bubble"] ?: "false").toBoolean()
                when {
                    t && b -> "Both"
                    t -> "Ticks"
                    b -> "Bubble"
                    else -> "None"
                }
            }

            var hasTicks = currentFeedback == "Ticks" || currentFeedback == "Both"
            var hasBubble = currentFeedback == "Bubble" || currentFeedback == "Both"

            fun updateFeedbackProps() {
                val fb = when {
                    hasTicks && hasBubble -> "Both"
                    hasTicks -> "Ticks"
                    hasBubble -> "Bubble"
                    else -> "None"
                }
                onUpdate("feedback", fb)
            }

            checkShowTicks?.visibility = if (hasTicks) View.VISIBLE else View.INVISIBLE
            panelView.findViewById<View>(R.id.itemShowTicks)?.setOnClickListener {
                hasTicks = !hasTicks
                checkShowTicks?.visibility = if (hasTicks) View.VISIBLE else View.INVISIBLE
                updateFeedbackProps()
            }

            checkShowBubble?.visibility = if (hasBubble) View.VISIBLE else View.INVISIBLE
            panelView.findViewById<View>(R.id.itemShowBubble)?.setOnClickListener {
                hasBubble = !hasBubble
                checkShowBubble?.visibility = if (hasBubble) View.VISIBLE else View.INVISIBLE
                updateFeedbackProps()
            }


        } catch (e: Exception) {
            android.util.Log.e("ScaleMeterDef", "Error binding", e)
        }
    }

    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (String, String) -> Unit, onUpdateProp: (String, String) -> Unit) {}

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val meter = view.findComponentTarget<ScaleMeterView>() ?: return
        try {
            val v = payload.toFloat()
            val min = data.props["min"]?.toFloatOrNull() ?: 0f
            val max = data.props["max"]?.toFloatOrNull() ?: 100f
            if (v in min..max) {
                meter.value = v
                onUpdateProp("value", v.toString())
            }
        } catch (_: Exception) {}
    }
}
