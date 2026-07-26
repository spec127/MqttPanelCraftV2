package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.*
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject

/**
 * 折線圖元件 (LineChartDefinition)
 *
 * Design Intent:
 * 支援單線/多線序列展示、自訂單位、10~1000筆歷史取樣、各線色表與 Key 名稱配置，以及時間戳記說明與數值標籤顯示。
 */
object LineChartDefinition : IComponentDefinition {

    override val type: String = "CHART"
    override val defaultSize: Size = Size(280, 180)
    override val labelPrefix: String = "chart"
    override val iconResId: Int = android.R.drawable.ic_menu_report_image
    override val group: String = "SENSOR"

    override val propertiesLayoutId: Int = R.layout.layout_prop_line_chart

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "title" to "折線圖",
        "has_timestamp" to "false",
        "max_points" to "50",
        "series_mode" to "SINGLE",
        "series_count" to "1",
        "unit" to "",
        "chart_style" to "Solid",
        "grid_color" to "#D84315",
        "show_dots" to "true",
        "show_values" to "false",
        "series_key_1" to "value1",
        "series_key_2" to "value2",
        "series_color_1" to "#00BCD4",
        "series_color_2" to "#00E676"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val compositeView = LineChartCompositeView(context).apply {
            tag = "target_chart"
            this.isEditMode = isEditMode
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(compositeView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val compositeView = (if (view is LineChartCompositeView) view else view.findViewWithTag<LineChartCompositeView>("target_chart")) ?: return
        compositeView.isEditMode = false
        val mode = data.props["series_mode"] ?: "SINGLE"
        val isPreview = data.props["is_preview"] == "true"
        compositeView.bottomBar.visibility = if (isPreview) View.GONE else View.VISIBLE
        val count = if (isPreview || mode == "SINGLE") 1 else ((data.props["series_count"] ?: "1").toIntOrNull()?.coerceIn(1, 8) ?: 1)
        val colors = mutableListOf<Int>()
        val keys = mutableListOf<String>()
        for (i in 1..count) {
            val cHex = data.props["series_color_$i"] ?: when(i) {
                1 -> "#00E5FF"
                2 -> "#00E676"
                3 -> "#FFAB00"
                4 -> "#F50057"
                5 -> "#D500F9"
                6 -> "#3D5AFE"
                7 -> "#FFEA00"
                else -> "#00E5FF"
            }
            colors.add(try { Color.parseColor(cHex) } catch (_: Exception) { Color.parseColor("#00E5FF") })
            keys.add(data.props["series_key_$i"] ?: "value$i")
        }
        val listMode = data.props["series_list_mode"] ?: "KEY"
        if (!isPreview) {
            compositeView.updateSeriesLabels(count, keys, colors, listMode)
            compositeView.showLegend = (data.props["show_multi_legend"] != "false")
            compositeView.updateLegendVisibility()
        }

        val chartView = compositeView.chartCanvas
        chartView.setSeriesCountAndColors(count, colors)
        chartView.maxPoints = (data.props["max_points"] ?: "50").toIntOrNull()?.coerceIn(10, 1000) ?: 50
        chartView.unitStr = data.props["unit"] ?: ""
        chartView.chartStyle = data.props["chart_style"] ?: "Solid"
        chartView.gridColor = try { Color.parseColor(data.props["grid_color"] ?: "#D84315") } catch (_: Exception) { Color.parseColor("#D84315") }
        chartView.showDots = (data.props["show_dots"] ?: "true") == "true"
        chartView.showValues = (data.props["show_values"] ?: "false") == "true"
        chartView.usePhoneTime = (data.props["has_timestamp"] ?: "false") == "true"
        chartView.yMaxCustom = data.props["y_max"]?.toFloatOrNull()
        chartView.yMinCustom = data.props["y_min"]?.toFloatOrNull()
        chartView.isPreviewMode = isPreview
        
        if (isPreview) {
            chartView.addPoint(0, 10f, "0", 0L)
            chartView.addPoint(0, 30f, "1", 1L)
            chartView.addPoint(0, 20f, "2", 2L)
            chartView.addPoint(0, 40f, "3", 3L)
            chartView.addPoint(0, 25f, "4", 4L)
        }
        
        chartView.invalidate()
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // 1. 資料數上限 (單行輸入框 10~1000 筆)
        val etMaxPts = panelView.findViewById<TextInputEditText>(R.id.etMaxPoints)
        etMaxPts?.setText(data.props["max_points"] ?: "50")
        etMaxPts?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val pts = etMaxPts.text.toString().toIntOrNull()?.coerceIn(10, 1000) ?: 50
                etMaxPts.setText(pts.toString())
                onUpdate("max_points", pts.toString())
            }
        }

        // Y 軸上下限與單位
        com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder.bindEditText(panelView, R.id.etYMax, "y_max", data, onUpdate, "")
        com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder.bindEditText(panelView, R.id.etYMin, "y_min", data, onUpdate, "")
        com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder.bindEditText(panelView, R.id.etChartUnit, "unit", data, onUpdate, "")

        // 2. 單線多線資料
        val toggleMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleSeriesMode)
        val curMode = data.props["series_mode"] ?: "SINGLE"
        toggleMode?.check(if (curMode == "MULTI") R.id.btnModeMulti else R.id.btnModeSingle)

        // 3. 序列數量與顯示模式切換邏輯
        val toggleListMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleSeriesListMode)
        val listMode = data.props["series_list_mode"] ?: "KEY"
        toggleListMode?.check(if (listMode == "COLOR_ONLY") R.id.btnListModeColorOnly else R.id.btnListModeKey)
        toggleListMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                onUpdate("series_list_mode", if (checkedId == R.id.btnListModeColorOnly) "COLOR_ONLY" else "KEY")
            }
        }
        
        val containerSeriesCount = panelView.findViewById<View>(R.id.containerSeriesCount)
        containerSeriesCount?.visibility = if (curMode == "MULTI") View.VISIBLE else View.GONE

        var sCount = (data.props["series_count"] ?: "1").toIntOrNull()?.coerceIn(1, 8) ?: 1
        val tvCount = panelView.findViewById<TextView>(R.id.tvSeriesCount)
        val llSeries = panelView.findViewById<LinearLayout>(R.id.llSeriesContainer)

        fun renderSeriesRows() {
            llSeries?.removeAllViews()
            val density = context.resources.displayMetrics.density
            val rowHeightPx = (40 * density).toInt()
            val marginPx = (8 * density).toInt()

            for (i in 1..sCount) {
                val curColorHex = data.props["series_color_$i"] ?: when(i) {
                    1 -> "#00E5FF"
                    2 -> "#00E676"
                    3 -> "#FFAB00"
                    4 -> "#F50057"
                    else -> "#AA00FF"
                }
                val rowLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setBackgroundResource(R.drawable.bg_input_outline)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeightPx).apply { bottomMargin = marginPx }
                }

                val paletteContainer = LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, rowHeightPx, 0.6f)
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                    val pad4 = (4 * density).toInt()
                    setPadding(pad4, 0, pad4, 0)
                }

                val recent = com.example.mqttpanelcraft.data.ColorHistoryManager.load(context)
                for (idx in 0 until 5) {
                    val frame = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f) }
                    val circle = View(context).apply {
                        val cSize = (22 * density).toInt()
                        layoutParams = FrameLayout.LayoutParams(cSize, cSize, android.view.Gravity.CENTER)
                        setBackgroundResource(R.drawable.shape_circle_color)
                        val colorStr = if (idx < recent.size) recent[idx] else null
                        if (colorStr != null) {
                            backgroundTintList = android.content.res.ColorStateList.valueOf(try { Color.parseColor(colorStr) } catch(_: Exception) { Color.GRAY })
                            setOnClickListener {
                                onUpdate("series_color_$i", colorStr)
                                renderSeriesRows()
                            }
                        } else { visibility = View.INVISIBLE }
                    }
                    frame.addView(circle)
                    paletteContainer.addView(frame)
                }

                val pickerFrame = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f) }
                val picker = android.widget.ImageView(context).apply {
                    val pSize = (22 * density).toInt()
                    layoutParams = FrameLayout.LayoutParams(pSize, pSize, android.view.Gravity.CENTER)
                    setImageResource(R.drawable.ic_palette_open)
                    imageTintList = android.content.res.ColorStateList.valueOf(try { Color.parseColor(curColorHex) } catch(_: Exception) { Color.parseColor("#00E5FF") })
                    setOnClickListener { anchor ->
                        var latest = curColorHex
                        com.example.mqttpanelcraft.ui.ColorPickerDialog(context, latest, true, { c ->
                            latest = c
                            onUpdate("series_color_$i", c)
                            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(c))
                        }, {
                            com.example.mqttpanelcraft.data.ColorHistoryManager.save(context, latest)
                            com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder.notifyHistoryChanged()
                            renderSeriesRows()
                        }).show(anchor)
                    }
                }
                pickerFrame.addView(picker)
                paletteContainer.addView(pickerFrame)

                val divider = View(context).apply {
                    val dHeight = (22 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams((1 * density).toInt(), dHeight)
                    setBackgroundColor(Color.parseColor("#E0E0E0"))
                }

                val etKey = android.widget.EditText(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.4f)
                    setText(data.props["series_key_$i"] ?: "value$i")
                    textSize = 13f
                    maxLines = 1
                    setSingleLine()
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((12 * density).toInt(), 0, 4, 0)
                    background = null
                    hint = context.getString(R.string.prop_chart_series_key_hint)
                    setTextColor(Color.parseColor("#334155"))
                    addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) { onUpdate("series_key_$i", s?.toString() ?: "") }
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    })
                }

                rowLayout.addView(paletteContainer)
                rowLayout.addView(divider)
                rowLayout.addView(etKey)
                
                paletteContainer.layoutParams = LinearLayout.LayoutParams(0, rowHeightPx, 0.6f)
                llSeries?.addView(rowLayout)
            }
        }

        tvCount?.text = "$sCount"
        renderSeriesRows()

        toggleListMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = if (checkedId == R.id.btnListModeColorOnly) "COLOR_ONLY" else "KEY"
                onUpdate("series_list_mode", newMode)
                renderSeriesRows()
            }
        }

        panelView.findViewById<View>(R.id.btnSeriesDec)?.setOnClickListener {
            if (sCount > 1) {
                sCount--
                tvCount?.text = "$sCount"
                onUpdate("series_count", sCount.toString())
                renderSeriesRows()
            }
        }
        panelView.findViewById<View>(R.id.btnSeriesInc)?.setOnClickListener {
            if (sCount < 8) {
                sCount++
                tvCount?.text = "$sCount"
                onUpdate("series_count", sCount.toString())
                renderSeriesRows()
            }
        }

        toggleMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = if (checkedId == R.id.btnModeMulti) "MULTI" else "SINGLE"
                onUpdate("series_mode", mode)
                containerSeriesCount?.visibility = if (mode == "MULTI") View.VISIBLE else View.GONE
                if (mode == "SINGLE") {
                    sCount = 1
                    tvCount?.text = "1"
                    onUpdate("series_count", "1")
                    renderSeriesRows()
                }
            }
        }

        // 4. 有無時間戳記 (移動到多線的顏色下面)
        var hasTimestamp = (data.props["has_timestamp"] ?: "false") == "true"
        val checkTs = panelView.findViewById<ImageView>(R.id.checkHasTimestamp)
        checkTs?.visibility = if (hasTimestamp) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemHasTimestamp)?.setOnClickListener {
            hasTimestamp = !hasTimestamp
            checkTs?.visibility = if (hasTimestamp) View.VISIBLE else View.INVISIBLE
            onUpdate("has_timestamp", hasTimestamp.toString())
        }

        // 5. 風格下拉選單
        val spStyle = panelView.findViewById<AutoCompleteTextView>(R.id.spChartStyle)
        val styleList = listOf("Solid", "Smooth", "Area")
        val styleNames = listOf(
            context.getString(R.string.prop_chart_style_solid),
            context.getString(R.string.prop_chart_style_smooth),
            context.getString(R.string.prop_chart_style_area)
        )
        spStyle?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, styleNames))
        val curStyle = data.props["chart_style"] ?: "Solid"
        val sIdx = styleList.indexOf(curStyle).coerceAtLeast(0)
        spStyle?.setText(styleNames[sIdx], false)
        spStyle?.setOnItemClickListener { _, _, pos, _ ->
            onUpdate("chart_style", styleList[pos])
        }

        // 6. 網格顏色選單
        com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder.bindColorPalette(
            panelView,
            R.id.containerGridColor,
            "grid_color",
            data,
            onUpdate,
            label = context.getString(R.string.prop_chart_grid_color),
            defaultColor = "#D84315"
        )

        // 7. 顯示標點
        var hasDots = (data.props["show_dots"] ?: "true") == "true"
        val checkDots = panelView.findViewById<ImageView>(R.id.checkShowDots)
        checkDots?.visibility = if (hasDots) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemShowDots)?.setOnClickListener {
            hasDots = !hasDots
            checkDots?.visibility = if (hasDots) View.VISIBLE else View.INVISIBLE
            onUpdate("show_dots", hasDots.toString())
        }

        // 8. 顯示數值
        var hasVals = (data.props["show_values"] ?: "false") == "true"
        val checkVals = panelView.findViewById<ImageView>(R.id.checkShowValues)
        checkVals?.visibility = if (hasVals) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemShowValues)?.setOnClickListener {
            hasVals = !hasVals
            checkVals?.visibility = if (hasVals) View.VISIBLE else View.INVISIBLE
            onUpdate("show_values", hasVals.toString())
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
        val compositeView = (if (view is LineChartCompositeView) view else view.findViewWithTag<LineChartCompositeView>("target_chart")) ?: return
        val chartView = compositeView.chartCanvas
        val usePhoneTime = (data.props["has_timestamp"] ?: "false") == "true"
        
        val timeStr = if (usePhoneTime) {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        } else {
            var extracted: String? = null
            val rawTrim = payload.trim()
            if (rawTrim.startsWith("{")) {
                try {
                    val json = JSONObject(rawTrim)
                    if (json.has("t")) extracted = json.getString("t")
                    else if (json.has("time")) extracted = json.getString("time")
                    else if (json.has("timestamp")) extracted = json.getString("timestamp")
                } catch (_: Exception) {}
            }
            extracted ?: java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        }
        val timeMs = System.currentTimeMillis()

        val raw = payload.trim()
        if (raw.startsWith("{")) {
            try {
                val json = JSONObject(raw)
                val mode = data.props["series_mode"] ?: "SINGLE"
                val count = if (mode == "SINGLE") 1 else ((data.props["series_count"] ?: "1").toIntOrNull()?.coerceIn(1, 8) ?: 1)
                var addedAny = false
                for (i in 1..count) {
                    val key = data.props["series_key_$i"] ?: "value$i"
                    if (json.has(key)) {
                        val v = json.getDouble(key).toFloat()
                        chartView.addPoint(i - 1, v, timeStr, timeMs)
                        addedAny = true
                    }
                }
                if (!addedAny && json.has("value")) {
                    chartView.addPoint(0, json.getDouble("value").toFloat(), timeStr, timeMs)
                    addedAny = true
                }
                if (!addedAny) {
                    logError(view.context, view.context.getString(R.string.chart_err_format, data.label, raw))
                }
            } catch (e: Exception) {
                logError(view.context, view.context.getString(R.string.chart_err_parse, data.label, e.message ?: ""))
            }
        } else {
            val v = raw.toFloatOrNull()
            if (v != null) {
                chartView.addPoint(0, v, timeStr, timeMs)
            } else {
                logError(view.context, view.context.getString(R.string.chart_err_number, data.label, raw))
            }
        }
    }

    private fun getActivity(context: Context): android.app.Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun logError(context: Context, msg: String) {
        val owner = getActivity(context) as? androidx.lifecycle.ViewModelStoreOwner
        if (owner != null) {
            val vm = androidx.lifecycle.ViewModelProvider(owner)[com.example.mqttpanelcraft.ProjectViewModel::class.java]
            vm.addLog(msg)
        }
    }

    private class LineChartCompositeView(context: Context) : LinearLayout(context) {
        val chartCanvas: SimpleLineChartView
        val bottomBar: LinearLayout
        val seriesLabelsContainer: LinearLayout
        val deleteButton: ImageView
        var isEditMode: Boolean = false
        var showLegend: Boolean = true

        init {
            orientation = VERTICAL
            chartCanvas = SimpleLineChartView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f)
            }
            addView(chartCanvas)

            bottomBar = LinearLayout(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                orientation = HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val isDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                setBackgroundColor(if (isDark) Color.parseColor("#331E293B") else Color.parseColor("#4DE2E8F0"))
                val padH = (6 * resources.displayMetrics.density).toInt()
                val padV = (2 * resources.displayMetrics.density).toInt()
                setPadding(padH, padV, padH, padV)
            }

            seriesLabelsContainer = LinearLayout(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
                orientation = HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val scrollView = HorizontalScrollView(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
                isHorizontalScrollBarEnabled = false
                addView(seriesLabelsContainer)
            }
            bottomBar.addView(scrollView)

            deleteButton = ImageView(context).apply {
                val w = (36 * resources.displayMetrics.density).toInt()
                val h = (20 * resources.displayMetrics.density).toInt()
                layoutParams = LayoutParams(w, h).apply { marginStart = (4 * resources.displayMetrics.density).toInt() }
                setImageResource(android.R.drawable.ic_menu_delete)
                setColorFilter(Color.parseColor("#EF5350"))
                setBackgroundResource(R.drawable.bg_card_unselected)
                val p = (2 * resources.displayMetrics.density).toInt()
                setPadding(p, p, p, p)
                setOnClickListener {
                    chartCanvas.clearAllPoints()
                }
            }
            bottomBar.addView(deleteButton)
            addView(bottomBar)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            bottomBar.visibility = View.VISIBLE
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        fun updateLegendVisibility() {
            bottomBar.visibility = View.VISIBLE
        }

        fun updateSeriesLabels(count: Int, keys: List<String>, colors: List<Int>, listMode: String = "KEY") {
            seriesLabelsContainer.removeAllViews()
            val density = resources.displayMetrics.density
            for (i in 0 until count) {
                val keyName = if (i < keys.size && keys[i].isNotBlank()) keys[i] else "value${i+1}"
                val c = if (i < colors.size) colors[i] else Color.parseColor("#00BCD4")
                val itemLayout = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
                }
                val colorBlock = View(context).apply {
                    val bs = (10 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(bs, bs).apply {
                        marginEnd = (6 * density).toInt()
                    }
                    setBackgroundColor(c)
                }
                val chip = TextView(context).apply {
                    text = keyName
                    textSize = 11f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(c) // Use the same color as the series
                }
                
                if (listMode == "COLOR_ONLY") {
                    itemLayout.addView(colorBlock)
                } else {
                    itemLayout.addView(chip)
                }
                
                seriesLabelsContainer.addView(itemLayout)
                if (i < count - 1) {
                    val sep = TextView(context).apply {
                        text = " | "
                        textSize = 11f
                        setTextColor(Color.parseColor("#90A4AE"))
                    }
                    seriesLabelsContainer.addView(sep)
                }
            }
            updateLegendVisibility()
        }
    }

    private class SimpleLineChartView(context: Context) : View(context) {
        private val seriesPoints = mutableMapOf<Int, MutableList<Triple<Float, String, Long>>>()
        var seriesColors = listOf(Color.parseColor("#00BCD4"))
        var seriesCount = 1
        var maxPoints = 50
        var unitStr: String = ""
        var chartStyle: String = "Solid"
        var gridColor: Int = Color.parseColor("#D84315")
        var showDots: Boolean = true
        var showValues: Boolean = false
        var usePhoneTime: Boolean = false
        var yMaxCustom: Float? = null
        var yMinCustom: Float? = null
        var isPreviewMode: Boolean = false

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
        private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER }
        private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B0B8C4"); textSize = 26f }
        private val gridPaint = Paint().apply { strokeWidth = 2f }
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val path = Path()
        private val areaPath = Path()

        init {
            val nowStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val nowMs = System.currentTimeMillis()
            seriesPoints[0] = mutableListOf(
                Triple(22f, nowStr, nowMs - 4000),
                Triple(26f, nowStr, nowMs - 3000),
                Triple(24f, nowStr, nowMs - 2000),
                Triple(28f, nowStr, nowMs - 1000),
                Triple(25f, nowStr, nowMs)
            )
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        val r = 12f * resources.displayMetrics.density
                        outline.setRoundRect(0, 0, view.width, view.height, r)
                    }
                }
            }
        }

        fun clearAllPoints() {
            seriesPoints.clear()
            invalidate()
        }

        fun trimExtraSeries(count: Int) {
            seriesCount = count
            seriesPoints.keys.filter { it >= count }.forEach { seriesPoints.remove(it) }
            invalidate()
        }

        fun setSeriesCountAndColors(count: Int, colors: List<Int>) {
            seriesCount = count
            seriesColors = colors
            trimExtraSeries(count)
        }

        fun addPoint(seriesIdx: Int, value: Float, timestamp: String, timeMs: Long = System.currentTimeMillis()) {
            if (seriesIdx >= seriesCount) return
            val list = seriesPoints.getOrPut(seriesIdx) { mutableListOf() }
            list.add(Triple(value, timestamp, timeMs))
            while (list.size > maxPoints) { list.removeAt(0) }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val density = resources.displayMetrics.density
            val r = 12f * density

            // 底色跟 cam 元件一樣的毛玻璃 (不再是黑底)
            val isDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            bgPaint.color = if (isDark) Color.parseColor("#18FFFFFF") else Color.parseColor("#0B000000")
            canvas.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)

            try {
                gridPaint.color = gridColor
                val leftMargin = if (isPreviewMode) 4f * density else 48f * density
                val bottomMargin = if (isPreviewMode) 4f * density else 22f * density
                val chartW = w - leftMargin - (if (isPreviewMode) 4f * density else 12f * density)
                val chartH = h - bottomMargin - (if (isPreviewMode) 4f * density else 12f * density)
                val topMargin = if (isPreviewMode) 4f * density else 12f * density

                val allPts = seriesPoints.entries.filter { it.key < seriesCount }.flatMap { it.value.map { p -> p.first } }
                val minVal = yMinCustom ?: (if (allPts.isEmpty()) 0f else (allPts.minOrNull() ?: 0f) - 2f)
                val maxVal = yMaxCustom ?: (if (allPts.isEmpty()) 100f else (allPts.maxOrNull() ?: 100f) + 2f)
                val range = if (maxVal == minVal) 100f else maxVal - minVal

                if (!isPreviewMode) {
                    // 繪製網格與縱座標數值
                    axisPaint.textAlign = Paint.Align.RIGHT
                    axisPaint.textSize = 9f * density
                    axisPaint.color = Color.parseColor("#B0B8C4")

                    for (i in 0..4) {
                        val y = topMargin + chartH * i / 4f
                        if (i > 0 && i < 4) {
                            canvas.drawLine(leftMargin, y, leftMargin + chartW, y, gridPaint)
                        }
                        val valAtTick = maxVal - (range * (i / 4f))
                        val label = String.format("%.1f", valAtTick)
                        canvas.drawText(label, leftMargin - (6f * density), y + (3f * density), axisPaint)
                    }

                    // 縱坐標單位標籤
                    if (unitStr.isNotEmpty()) {
                        axisPaint.textAlign = Paint.Align.LEFT
                        canvas.drawText(unitStr, 8f * density, topMargin + (10f * density), axisPaint)
                    }

                    // 繪製橫坐標軸與時間戳 (從 0s 開始至最多秒/毫秒數)
                    canvas.drawLine(leftMargin, topMargin + chartH, leftMargin + chartW, topMargin + chartH, gridPaint)
                    val maxPtsCount = seriesPoints.entries.filter { it.key < seriesCount }.maxOfOrNull { it.value.size } ?: 0
                    if (maxPtsCount > 0) {
                        axisPaint.textAlign = Paint.Align.CENTER
                        val firstPt = seriesPoints.entries.filter { it.key < seriesCount }.mapNotNull { it.value.firstOrNull() }.minByOrNull { it.third }
                        val lastPt = seriesPoints.entries.filter { it.key < seriesCount }.mapNotNull { it.value.lastOrNull() }.maxByOrNull { it.third }
                        val startStr = "0s"
                        val endStr = if (firstPt != null && lastPt != null && lastPt.third > firstPt.third) {
                            val diffMs = lastPt.third - firstPt.third
                            if (diffMs >= 1000) String.format("%.1fs", diffMs / 1000f) else "${diffMs}ms"
                        } else {
                            lastPt?.second ?: "0s"
                        }
                        canvas.drawText(startStr, leftMargin, h - (4f * density), axisPaint)
                        if (maxPtsCount > 1) {
                            canvas.drawText(endStr, leftMargin + chartW, h - (4f * density), axisPaint)
                        }
                    }
                }

                if (allPts.isEmpty()) return

                seriesPoints.entries.forEach { entry ->
                    val seriesIdx = entry.key
                    if (seriesIdx >= seriesCount) return@forEach
                    val pts = entry.value
                    if (pts.isEmpty()) return@forEach
                    val c = seriesColors.getOrElse(seriesIdx) { Color.parseColor("#00BCD4") }
                    linePaint.color = c
                    dotPaint.color = c

                    path.reset()
                    areaPath.reset()

                    pts.forEachIndexed { i, valTrip ->
                        val valPoint = valTrip.first
                        val x = if (pts.size == 1) leftMargin + chartW / 2f else leftMargin + i * chartW / (pts.size - 1)
                        val y = topMargin + chartH - ((valPoint - minVal) / range * chartH)
                        if (i == 0) {
                            path.moveTo(x, y)
                            areaPath.moveTo(x, topMargin + chartH)
                            areaPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            areaPath.lineTo(x, y)
                        }
                    }

                    if (chartStyle == "Area" && pts.isNotEmpty()) {
                        val lastX = if (pts.size == 1) leftMargin + chartW / 2f else leftMargin + chartW
                        areaPath.lineTo(lastX, topMargin + chartH)
                        areaPath.close()
                        areaPaint.shader = LinearGradient(
                            0f, topMargin, 0f, topMargin + chartH,
                            ColorUtilsAlpha(c, 120),
                            ColorUtilsAlpha(c, 5),
                            Shader.TileMode.CLAMP
                        )
                        canvas.drawPath(areaPath, areaPaint)
                    }

                    canvas.drawPath(path, linePaint)

                    if (showDots && !isPreviewMode) {
                        pts.forEachIndexed { i, valTrip ->
                            val valPoint = valTrip.first
                            val x = if (pts.size == 1) leftMargin + chartW / 2f else leftMargin + i * chartW / (pts.size - 1)
                            val y = topMargin + chartH - ((valPoint - minVal) / range * chartH)
                            canvas.drawCircle(x, y, 6f, dotPaint)
                        }
                    }

                    if (showValues && !isPreviewMode) {
                        textPaint.color = c
                        pts.forEachIndexed { i, valTrip ->
                            val valPoint = valTrip.first
                            val x = if (pts.size == 1) leftMargin + chartW / 2f else leftMargin + i * chartW / (pts.size - 1)
                            val y = topMargin + chartH - ((valPoint - minVal) / range * chartH)
                            val label = String.format("%.1f", valPoint) + (if (unitStr.isNotEmpty()) " $unitStr" else "")
                            textPaint.textSize = 10f * density
                            canvas.drawText(label, x, y - (8f * density), textPaint)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        private fun ColorUtilsAlpha(color: Int, alpha: Int): Int {
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
