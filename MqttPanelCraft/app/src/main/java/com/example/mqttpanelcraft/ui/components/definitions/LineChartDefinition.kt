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
    override val iconResId: Int = android.R.drawable.ic_menu_sort_by_size
    override val group: String = "DISPLAY"

    override val propertiesLayoutId: Int = R.layout.layout_prop_line_chart

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "title" to "折線圖",
        "has_timestamp" to "false",
        "max_points" to "50",
        "series_mode" to "SINGLE",
        "series_count" to "1",
        "unit" to "",
        "chart_style" to "Solid",
        "grid_color" to "#3A4659",
        "show_dots" to "true",
        "show_values" to "false",
        "series_key_1" to "value",
        "series_color_1" to "#00BCD4"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val chartView = SimpleLineChartView(context).apply {
            tag = "target_chart"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(chartView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val chartView = (view as? FrameLayout)?.findViewWithTag<SimpleLineChartView>("target_chart") ?: return
        val count = (data.props["series_count"] ?: "1").toIntOrNull()?.coerceIn(1, 8) ?: 1
        val colors = mutableListOf<Int>()
        for (i in 1..count) {
            val cHex = data.props["series_color_$i"] ?: "#00BCD4"
            colors.add(try { Color.parseColor(cHex) } catch (_: Exception) { Color.parseColor("#00BCD4") })
        }
        chartView.seriesColors = colors
        chartView.maxPoints = (data.props["max_points"] ?: "50").toIntOrNull()?.coerceIn(10, 1000) ?: 50
        chartView.unitStr = data.props["unit"] ?: ""
        chartView.chartStyle = data.props["chart_style"] ?: "Solid"
        chartView.gridColor = try { Color.parseColor(data.props["grid_color"] ?: "#3A4659") } catch (_: Exception) { Color.parseColor("#3A4659") }
        chartView.showDots = (data.props["show_dots"] ?: "true") == "true"
        chartView.showValues = (data.props["show_values"] ?: "false") == "true"
        chartView.invalidate()
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // 1. 有無時間戳記 (勾選 + 說明)
        var hasTimestamp = (data.props["has_timestamp"] ?: "false") == "true"
        val checkTs = panelView.findViewById<ImageView>(R.id.checkHasTimestamp)
        checkTs?.visibility = if (hasTimestamp) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemHasTimestamp)?.setOnClickListener {
            hasTimestamp = !hasTimestamp
            checkTs?.visibility = if (hasTimestamp) View.VISIBLE else View.INVISIBLE
            onUpdate("has_timestamp", hasTimestamp.toString())
        }

        // 2. 資料數上限 (單行輸入框 10~1000)
        val etMaxPts = panelView.findViewById<TextInputEditText>(R.id.etMaxPoints)
        etMaxPts?.setText(data.props["max_points"] ?: "50")
        etMaxPts?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val pts = etMaxPts.text.toString().toIntOrNull()?.coerceIn(10, 1000) ?: 50
                etMaxPts.setText(pts.toString())
                onUpdate("max_points", pts.toString())
            }
        }

        // 3. 單線多線資料
        val toggleMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleSeriesMode)
        val curMode = data.props["series_mode"] ?: "SINGLE"
        toggleMode?.check(if (curMode == "MULTI") R.id.btnModeMulti else R.id.btnModeSingle)

        // 4. 單位與系列數量
        val etUnit = panelView.findViewById<TextInputEditText>(R.id.etChartUnit)
        etUnit?.setText(data.props["unit"] ?: "")
        etUnit?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { onUpdate("unit", s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        var sCount = (data.props["series_count"] ?: "1").toIntOrNull()?.coerceIn(1, 8) ?: 1
        val tvCount = panelView.findViewById<TextView>(R.id.tvSeriesCount)
        val llSeries = panelView.findViewById<LinearLayout>(R.id.llSeriesContainer)

        fun renderSeriesRows() {
            llSeries?.removeAllViews()
            for (i in 1..sCount) {
                val row = LayoutInflater.from(context).inflate(R.layout.layout_chart_series_row, llSeries, false)
                val curColorView = row.findViewById<View>(R.id.vSeriesColorCurrent)
                val etKey = row.findViewById<TextInputEditText>(R.id.etSeriesKey)

                val cHex = data.props["series_color_$i"] ?: "#00BCD4"
                curColorView.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    try { Color.parseColor(cHex) } catch (_: Exception) { Color.parseColor("#00BCD4") }
                )
                etKey.setText(data.props["series_key_$i"] ?: if (i == 1) "value" else "series$i")

                etKey.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { onUpdate("series_key_$i", s?.toString() ?: "") }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })

                val palette = listOf("#00E5FF", "#00E676", "#FFAB00", "#F50057", "#AA00FF")
                val colorViews = listOf(
                    row.findViewById<View>(R.id.vSeriesColor1),
                    row.findViewById<View>(R.id.vSeriesColor2),
                    row.findViewById<View>(R.id.vSeriesColor3),
                    row.findViewById<View>(R.id.vSeriesColor4),
                    row.findViewById<View>(R.id.vSeriesColor5)
                )
                colorViews.forEachIndexed { idx, v ->
                    v?.setOnClickListener {
                        val picked = palette[idx]
                        curColorView.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(picked))
                        onUpdate("series_color_$i", picked)
                    }
                }
                llSeries?.addView(row)
            }
        }

        tvCount?.text = "$sCount"
        renderSeriesRows()

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
                if (mode == "SINGLE") {
                    sCount = 1
                    tvCount?.text = "1"
                    onUpdate("series_count", "1")
                    renderSeriesRows()
                }
            }
        }

        // Style dropdown
        val spStyle = panelView.findViewById<AutoCompleteTextView>(R.id.spChartStyle)
        val styleList = listOf("Solid", "Smooth", "Area")
        val styleNames = listOf("折線 (Solid)", "曲線 (Smooth)", "面積 (Area)")
        spStyle?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, styleNames))
        val curStyle = data.props["chart_style"] ?: "Solid"
        val sIdx = styleList.indexOf(curStyle).coerceAtLeast(0)
        spStyle?.setText(styleNames[sIdx], false)
        spStyle?.setOnItemClickListener { _, _, pos, _ ->
            onUpdate("chart_style", styleList[pos])
        }

        // Grid colors
        val gridPalette = listOf("#3A4659", "#546E7A", "#78909C", "#CFD8DC")
        val gridViews = listOf(
            panelView.findViewById<View>(R.id.vGridColor1),
            panelView.findViewById<View>(R.id.vGridColor2),
            panelView.findViewById<View>(R.id.vGridColor3),
            panelView.findViewById<View>(R.id.vGridColor4)
        )
        gridViews.forEachIndexed { idx, v ->
            v?.setOnClickListener { onUpdate("grid_color", gridPalette[idx]) }
        }

        // Show dots
        var hasDots = (data.props["show_dots"] ?: "true") == "true"
        val checkDots = panelView.findViewById<ImageView>(R.id.checkShowDots)
        checkDots?.visibility = if (hasDots) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemShowDots)?.setOnClickListener {
            hasDots = !hasDots
            checkDots?.visibility = if (hasDots) View.VISIBLE else View.INVISIBLE
            onUpdate("show_dots", hasDots.toString())
        }

        // Show values
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
        val chartView = (view as? FrameLayout)?.findViewWithTag<SimpleLineChartView>("target_chart") ?: return
        val raw = payload.trim()
        if (raw.startsWith("{")) {
            try {
                val json = JSONObject(raw)
                val key1 = data.props["series_key_1"] ?: "value"
                if (json.has(key1)) {
                    val v = json.getDouble(key1).toFloat()
                    chartView.addPoint(0, v)
                }
            } catch (_: Exception) {}
        } else {
            raw.toFloatOrNull()?.let { chartView.addPoint(0, it) }
        }
    }

    private class SimpleLineChartView(context: Context) : View(context) {
        private val seriesPoints = mutableMapOf<Int, MutableList<Float>>()
        var seriesColors = listOf(Color.parseColor("#00BCD4"))
        var maxPoints = 50
        var unitStr: String = ""
        var chartStyle: String = "Solid"
        var gridColor: Int = Color.parseColor("#3A4659")
        var showDots: Boolean = true
        var showValues: Boolean = false

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
        private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER }
        private val gridPaint = Paint().apply { strokeWidth = 2f }
        private val path = Path()
        private val areaPath = Path()

        init {
            seriesPoints[0] = mutableListOf(22f, 26f, 24f, 28f, 25f, 29f, 27f)
        }

        fun addPoint(seriesIdx: Int, value: Float) {
            val list = seriesPoints.getOrPut(seriesIdx) { mutableListOf() }
            list.add(value)
            while (list.size > maxPoints) { list.removeAt(0) }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.parseColor("#141820"))

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            gridPaint.color = gridColor
            for (i in 1..4) {
                val y = h * i / 5f
                canvas.drawLine(0f, y, w, y, gridPaint)
            }

            val allPts = seriesPoints.values.flatten()
            if (allPts.isEmpty()) return
            val minVal = (allPts.minOrNull() ?: 0f) - 5f
            val maxVal = (allPts.maxOrNull() ?: 100f) + 5f
            val range = if (maxVal == minVal) 100f else maxVal - minVal

            seriesPoints.entries.forEachIndexed { sIdx, entry ->
                val pts = entry.value
                if (pts.isEmpty()) return@forEachIndexed
                val c = seriesColors.getOrElse(sIdx) { Color.parseColor("#00BCD4") }
                linePaint.color = c
                dotPaint.color = c

                path.reset()
                areaPath.reset()

                pts.forEachIndexed { i, valPoint ->
                    val x = if (pts.size == 1) w / 2f else i * w / (pts.size - 1)
                    val y = h - ((valPoint - minVal) / range * (h * 0.75f) + h * 0.15f)
                    if (i == 0) {
                        path.moveTo(x, y)
                        areaPath.moveTo(x, h)
                        areaPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        areaPath.lineTo(x, y)
                    }
                }

                if (chartStyle == "Area" && pts.isNotEmpty()) {
                    val lastX = if (pts.size == 1) w / 2f else w
                    areaPath.lineTo(lastX, h)
                    areaPath.close()
                    areaPaint.shader = LinearGradient(
                        0f, 0f, 0f, h,
                        ColorUtilsAlpha(c, 120),
                        ColorUtilsAlpha(c, 5),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawPath(areaPath, areaPaint)
                }

                canvas.drawPath(path, linePaint)

                if (showDots) {
                    pts.forEachIndexed { i, valPoint ->
                        val x = if (pts.size == 1) w / 2f else i * w / (pts.size - 1)
                        val y = h - ((valPoint - minVal) / range * (h * 0.75f) + h * 0.15f)
                        canvas.drawCircle(x, y, 6f, dotPaint)
                    }
                }

                if (showValues) {
                    pts.forEachIndexed { i, valPoint ->
                        val x = if (pts.size == 1) w / 2f else i * w / (pts.size - 1)
                        val y = h - ((valPoint - minVal) / range * (h * 0.75f) + h * 0.15f)
                        val label = String.format("%.1f", valPoint) + (if (unitStr.isNotEmpty()) " $unitStr" else "")
                        canvas.drawText(label, x, y - 14f, textPaint)
                    }
                }
            }
        }

        private fun ColorUtilsAlpha(color: Int, alpha: Int): Int {
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
