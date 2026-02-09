package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.google.android.material.button.MaterialButtonToggleGroup
import org.json.JSONArray
import org.json.JSONObject

/**
 * Selector Component Definition (Segmented Control) Extracted from Switch as a standalone
 * multi-state selector.
 */
object SelectorDefinition : IComponentDefinition {

    object Style {
        const val ROUNDED = "rounded"
        const val CIRCLE = "circle"
    }

    override val type = "SELECTOR"
    override val defaultSize = android.util.Size(240, 60)
    override val labelPrefix = "selector"
    override val iconResId = R.drawable.ic_grid_view
    override val group = "CONTROL"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val contentRoot =
                FrameLayout(context).apply {
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                            )
                }
        container.addView(contentRoot, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val root = (view as? ViewGroup)?.getChildAt(0) as? FrameLayout ?: return

        val style = data.props["style"] ?: Style.ROUNDED
        val orient = data.props["orientation"] ?: "horz"
        val segmentsJson = data.props["segments"] ?: ""

        val signature = "style:$style|orient:$orient|segs:${parseSegments(segmentsJson).size}"

        if (root.tag != signature) {
            root.removeAllViews()
            val inflater = LayoutInflater.from(root.context)
            val multiRoot =
                    inflater.inflate(R.layout.layout_component_switch_multisegment, root, false) as
                            LinearLayout
            root.addView(multiRoot)
            root.tag = signature
        }

        val color = resolveColor(data)
        val child = root.getChildAt(0) as? LinearLayout ?: return
        updateMultiVisuals(child, data, color, orient)
    }

    private fun updateMultiVisuals(
            container: LinearLayout,
            data: ComponentData,
            color: Int,
            orientation: String
    ) {
        val density = container.resources.displayMetrics.density
        val isVertical = (orientation == "vert")
        container.orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        container.layoutTransition = android.animation.LayoutTransition()

        val bg = GradientDrawable()
        val style = data.props["style"] ?: Style.ROUNDED
        val isCircle = (style == Style.CIRCLE)

        bg.setColor(Color.parseColor("#F8FAFC"))
        bg.setStroke((2 * density).toInt(), Color.parseColor("#94A3B8"))

        if (isCircle) {
            bg.shape = GradientDrawable.RECTANGLE
            bg.cornerRadius = (999 * density)
        } else {
            bg.shape = GradientDrawable.RECTANGLE
            bg.cornerRadius = (16 * density)
        }

        container.background = bg
        val pad = (4 * density).toInt()
        container.setPadding(pad, pad, pad, pad)

        val render = {
            if (container.width > 0 && container.height > 0) {
                container.removeAllViews()
                val segments = parseSegments(data.props["segments"] ?: "")
                if (segments.isEmpty()) segments.add(Segment("Err", "0"))

                val w = container.width - container.paddingLeft - container.paddingRight
                val h = container.height - container.paddingTop - container.paddingBottom
                val count = segments.size.coerceAtLeast(1)

                val maxItemDim = if (isVertical) h.toFloat() / count else w.toFloat() / count
                val crossDim = if (isVertical) w.toFloat() else h.toFloat()
                val itemSize = kotlin.math.min(maxItemDim, crossDim).toInt()

                if (itemSize > 0) {
                    val gapSize = (4 * density).toInt()

                    segments.forEachIndexed { index, seg ->
                        val btn = TextView(container.context)
                        btn.text = seg.label
                        btn.tag = seg
                        btn.gravity = Gravity.CENTER
                        btn.textSize = 11f
                        btn.typeface = Typeface.DEFAULT_BOLD
                        btn.isAllCaps = true

                        val lp = LinearLayout.LayoutParams(0, 0)
                        if (isCircle) {
                            lp.width = itemSize
                            lp.height = itemSize
                        } else {
                            lp.width = if (isVertical) LinearLayout.LayoutParams.MATCH_PARENT else 0
                            lp.height =
                                    if (isVertical) 0 else LinearLayout.LayoutParams.MATCH_PARENT
                            lp.weight = 1f
                        }

                        if (index > 0) {
                            if (isVertical) lp.topMargin = gapSize else lp.leftMargin = gapSize
                        }

                        btn.layoutParams = lp
                        container.addView(btn)
                    }
                    val savedValue = data.props["value"]
                    updateMultiSegmentButtons(container, savedValue, color, isCircle, itemSize)
                }
            }
        }

        if (container.width > 0 && container.height > 0 && !container.isLayoutRequested) {
            render()
        } else {
            container.post { if (container.isAttachedToWindow) render() }
        }
    }

    private fun updateMultiSegmentButtons(
            container: LinearLayout,
            selectedValue: String?,
            color: Int,
            isCircle: Boolean,
            size: Int
    ) {
        val density = container.resources.displayMetrics.density
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? TextView ?: continue
            val seg = child.tag as? Segment

            val isSelected =
                    (selectedValue != null && seg?.value == selectedValue) ||
                            (selectedValue == null && i == 0)

            val radius = if (isCircle) size / 2f else (12 * density)
            val bg = GradientDrawable()
            bg.shape = if (isCircle) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
            bg.cornerRadius = radius

            if (isSelected) {
                bg.setColor(color)
                bg.setStroke(0, 0)
                child.setTextColor(Color.WHITE)
                child.elevation = (4 * density)
                child.translationZ = (2 * density)
                child.animate().scaleX(0.95f).scaleY(0.95f).setDuration(120).start()
            } else {
                if (isCircle) {
                    bg.setColor(Color.WHITE)
                    bg.setStroke((1 * density).toInt(), Color.parseColor("#E2E8F0"))
                    child.setTextColor(Color.parseColor("#94A3B8"))
                    child.elevation = (1 * density)
                } else {
                    bg.setColor(Color.TRANSPARENT)
                    bg.setStroke(0, 0)
                    child.setTextColor(Color.parseColor("#64748B"))
                    child.elevation = 0f
                }
                child.translationZ = 0f
                child.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
            child.background = bg
        }
    }

    override val propertiesLayoutId = R.layout.layout_prop_selector

    override fun bindPropertiesPanel(
            panelView: View,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // 0. Topic Config (Shared)
        val etTopic = panelView.rootView.findViewById<EditText>(R.id.etPropTopicConfig)
        etTopic?.setText(data.topicConfig)
        etTopic?.addTextChangedListener(
                object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) {
                        data.topicConfig = s.toString()
                    }
                    override fun beforeTextChanged(i: CharSequence?, s: Int, c: Int, a: Int) {}
                    override fun onTextChanged(i: CharSequence?, s: Int, b: Int, c: Int) {}
                }
        )

        val spStyle = panelView.findViewById<AutoCompleteTextView>(R.id.spPropStyle)
        val styles =
                listOf(
                        context.getString(R.string.val_style_rounded),
                        context.getString(R.string.val_shape_circle_style)
                )
        val adapterS = ArrayAdapter(context, R.layout.list_item_dropdown, styles)
        spStyle.setAdapter(adapterS)
        val curStyle = data.props["style"] ?: Style.ROUNDED
        val displayStyle =
                if (curStyle == Style.CIRCLE) context.getString(R.string.val_shape_circle_style)
                else context.getString(R.string.val_style_rounded)
        spStyle.setText(displayStyle, false)

        spStyle.setOnItemClickListener { _, _, position, _ ->
            val key = if (position == 1) Style.CIRCLE else Style.ROUNDED
            onUpdate("style", key)
        }

        val toggleOrient = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleOrientation)
        toggleOrient.check(
                if (data.props["orientation"] == "vert") R.id.btnOrientVert else R.id.btnOrientHorz
        )
        toggleOrient.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val o = if (checkedId == R.id.btnOrientVert) "vert" else "horz"
                onUpdate("orientation", o)
                val w = data.width
                val h = data.height
                if ((o == "vert" && w > h) || (o == "horz" && h > w)) {
                    onUpdate("w", h.toString())
                    onUpdate("h", w.toString())
                }
            }
        }

        setupSegmentEditor(panelView, context, data, onUpdate)

        // Colors
        val colorViews =
                listOf(
                        panelView.findViewById<View>(R.id.vColor1),
                        panelView.findViewById<View>(R.id.vColor2),
                        panelView.findViewById<View>(R.id.vColor3),
                        panelView.findViewById<View>(R.id.vColor4),
                        panelView.findViewById<View>(R.id.vColor5)
                )

        fun refreshColors() {
            val recentColors = com.example.mqttpanelcraft.data.ColorHistoryManager.load(context)
            colorViews.forEachIndexed { index, view ->
                if (index < recentColors.size) {
                    val cHex = recentColors[index]
                    try {
                        val colorInt = Color.parseColor(cHex)
                        view?.backgroundTintList =
                                android.content.res.ColorStateList.valueOf(colorInt)
                        view?.setOnClickListener { onUpdate("color", cHex) }
                    } catch (e: Exception) {}
                }
            }
        }
        refreshColors()

        panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener { anchor ->
            val cur = data.props["color"] ?: "#a573bc"
            var tempColor = cur
            ColorPickerDialog(
                            context = context,
                            initialColor = cur,
                            showAlpha = true,
                            onColorSelected = {
                                tempColor = it
                                onUpdate("color", it)
                            },
                            onDismiss = {
                                if (tempColor != cur) {
                                    com.example.mqttpanelcraft.data.ColorHistoryManager.save(
                                            context,
                                            tempColor
                                    )
                                    refreshColors()
                                }
                            }
                    )
                    .show(anchor)
        }
    }

    private fun setupSegmentEditor(
            panelView: View,
            context: Context,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {
        val llSegs = panelView.findViewById<LinearLayout>(R.id.llSegmentsContainer)
        val tvCount = panelView.findViewById<TextView>(R.id.tvSegCount)
        val segmentList = parseSegments(data.props["segments"] ?: "")
        tvCount.text = "${segmentList.size}"

        fun save() {
            val json = JSONArray()
            segmentList.forEach {
                json.put(JSONObject().put("label", it.label).put("value", it.value))
            }
            onUpdate("segments", json.toString())
            tvCount.text = "${segmentList.size}"
        }

        fun renderRows() {
            llSegs.removeAllViews()
            segmentList.forEachIndexed { index, seg ->
                val row =
                        LayoutInflater.from(context)
                                .inflate(R.layout.layout_switch_segment_row, llSegs, false)
                val etLabel = row.findViewById<EditText>(R.id.etSegLabel)
                val etValue = row.findViewById<EditText>(R.id.etSegValue)
                etLabel.setText(seg.label)
                etValue.setText(seg.value)

                val watcher =
                        object : android.text.TextWatcher {
                            override fun afterTextChanged(s: android.text.Editable?) {
                                segmentList[index] =
                                        Segment(etLabel.text.toString(), etValue.text.toString())
                                save()
                            }
                            override fun beforeTextChanged(
                                    s: CharSequence?,
                                    start: Int,
                                    count: Int,
                                    after: Int
                            ) {}
                            override fun onTextChanged(
                                    s: CharSequence?,
                                    start: Int,
                                    before: Int,
                                    count: Int
                            ) {}
                        }
                etLabel.addTextChangedListener(watcher)
                etValue.addTextChangedListener(watcher)
                llSegs.addView(row)
            }
        }
        renderRows()

        panelView.findViewById<View>(R.id.btnSegAdd)?.setOnClickListener {
            if (segmentList.size < 8) {
                segmentList.add(Segment("${segmentList.size+1}", "${segmentList.size+1}"))
                save()
                renderRows()
            }
        }
        panelView.findViewById<View>(R.id.btnSegRemove)?.setOnClickListener {
            if (segmentList.size > 2) {
                segmentList.removeAt(segmentList.lastIndex)
                save()
                renderRows()
            }
        }
    }

    override fun attachBehavior(
            view: View,
            data: ComponentData,
            sendMqtt: (topic: String, payload: String) -> Unit,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val root = (view as? ViewGroup)?.getChildAt(0) as? FrameLayout ?: return
        view.post {
            val m = (root.getChildAt(0) as? LinearLayout) ?: return@post
            for (i in 0 until m.childCount) {
                val btn = m.getChildAt(i)
                val seg = btn.tag as? Segment
                btn.setOnClickListener {
                    if (seg != null) {
                        sendMqtt(data.topicConfig, seg.value)
                        onUpdateProp("value", seg.value) // Sync state for persistence
                        val color = resolveColor(data)
                        val style = data.props["style"] ?: Style.ROUNDED
                        updateMultiSegmentButtons(
                                m,
                                seg.value,
                                color,
                                style == Style.CIRCLE,
                                btn.width
                        )
                    }
                }
            }
        }
    }

    override fun onMqttMessage(
            view: View,
            data: ComponentData,
            payload: String,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val root = (view as? ViewGroup)?.getChildAt(0) as? FrameLayout ?: return
        val color = resolveColor(data)
        val child = root.getChildAt(0) as? LinearLayout ?: return
        val style = data.props["style"] ?: Style.ROUNDED
        onUpdateProp("value", payload) // Persist MQTT state
        view.post {
            val firstChild = child.getChildAt(0)
            if (firstChild != null) {
                updateMultiSegmentButtons(
                        child,
                        payload,
                        color,
                        style == Style.CIRCLE,
                        firstChild.width
                )
            }
        }
    }

    private fun resolveColor(data: ComponentData): Int {
        return try {
            Color.parseColor(data.props["color"] ?: "#a573bc")
        } catch (e: Exception) {
            Color.MAGENTA
        }
    }

    data class Segment(val label: String, val value: String)
    private fun parseSegments(json: String): MutableList<Segment> {
        val list = mutableListOf<Segment>()
        if (json.isEmpty()) {
            return mutableListOf(Segment("S1", "1"), Segment("S2", "2"), Segment("S3", "3"))
        }
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Segment(obj.getString("label"), obj.getString("value")))
            }
        } catch (e: Exception) {
            return mutableListOf(Segment("Error", "0"))
        }
        return list
    }
}
