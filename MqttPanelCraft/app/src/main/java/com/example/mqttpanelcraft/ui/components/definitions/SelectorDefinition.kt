package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
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
        const val SEGMENTED = "segmented"
    }

    override val type = "SELECTOR"
    override val defaultSize = android.util.Size(200, 60)
    override val labelPrefix = "selector"
    override val iconResId = R.drawable.ic_selector_thumb
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
        // Signature includes style/orient but NOT segment count/value to allow optimized reuse
        val signature = "style:$style|orient:$orient"

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
        val style = data.props["style"] ?: Style.ROUNDED
        val isCircle = (style == Style.CIRCLE)
        val isVertical = (orientation == "vert")
        val segments = parseSegments(data.props["segments"] ?: "")
        if (segments.isEmpty()) segments.add(Segment("Err", "0"))

        // 1. Ensure Container Properties
        container.orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        container.layoutTransition = null
        container.clipChildren = false
        container.clipToPadding = false

        // 2. Rebuild Structure (Immediate)
        rebuildSegments(container, segments, isCircle)

        // 3. Apply Background
        val density = container.resources.displayMetrics.density
        val bg =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius =
                            if (isCircle || style == Style.SEGMENTED) 999 * density
                            else 16 * density
                    val isDark =
                            (container.context.resources.configuration.uiMode and
                                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val bgColor =
                            when {
                                style == Style.SEGMENTED ->
                                        if (isDark)
                                                Color.parseColor("#E61A2234") // Increased from CC
                                        else Color.parseColor("#EBDDE4ED") // Increased from BB
                                isDark -> Color.parseColor("#1E293B")
                                else -> Color.parseColor("#EEF2F7")
                            }
                    setColor(bgColor)
                    val strokeColor =
                            if (isDark) Color.parseColor("#3D4759")
                            else ColorUtils.setAlphaComponent(Color.parseColor("#94A3B8"), 100)
                    setStroke(
                            (if (style == Style.SEGMENTED) 1.5 * density else 2 * density).toInt(),
                            strokeColor
                    )
                }
        container.background = bg
        val containerPad = (if (style == Style.SEGMENTED) 2 * density else 4 * density).toInt()
        container.setPadding(containerPad, containerPad, containerPad, containerPad)

        // 4. Schedule Layout (Immediate for Dragged Previews)
        fun performLayout() {
            layoutSegments(container, segments, data, color, style, orientation, null, null)
        }

        if ((container.width > 0 && container.height > 0) || data.id == -1) {
            performLayout()
        } else {
            container.post { performLayout() }
        }
    }

    private fun rebuildSegments(
            container: LinearLayout,
            segments: List<Segment>,
            isCircle: Boolean
    ) {
        val count = segments.size
        val needsRebuild =
                if (container.childCount != count) true
                else {
                    val first = container.getChildAt(0)
                    if (isCircle) first !is FrameLayout else first !is TextView
                }

        if (needsRebuild) {
            container.removeAllViews()
            segments.forEach { _ ->
                if (isCircle) {
                    val wrapper = FrameLayout(container.context)
                    val btn =
                            TextView(container.context).apply {
                                gravity = Gravity.CENTER
                                textSize = 10f
                                typeface = Typeface.DEFAULT_BOLD
                                isAllCaps = true
                            }
                    wrapper.addView(
                            btn,
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    Gravity.CENTER
                            )
                    )
                    container.addView(wrapper, LinearLayout.LayoutParams(0, 0))
                } else {
                    val btn =
                            TextView(container.context).apply {
                                gravity = Gravity.CENTER
                                textSize = 13f
                                typeface = Typeface.DEFAULT_BOLD
                                isAllCaps = true
                            }
                    container.addView(btn, LinearLayout.LayoutParams(0, 0))
                }
            }
        }
    }

    private fun layoutSegments(
            container: LinearLayout,
            segments: List<Segment>,
            data: ComponentData,
            color: Int,
            style: String,
            orientation: String,
            sendMqtt: ((topic: String, payload: String) -> Unit)? = null,
            onUpdateProp: ((key: String, value: String) -> Unit)? = null
    ) {
        val density = container.resources.displayMetrics.density
        val isVertical = (orientation == "vert")
        val isCircle = (style == Style.CIRCLE)
        val isSegmented = (style == Style.SEGMENTED)
        val count = segments.size

        val wSize =
                (if (container.width > 0) container.width else data.width) -
                        container.paddingLeft -
                        container.paddingRight
        val hSize =
                (if (container.height > 0) container.height else data.height) -
                        container.paddingTop -
                        container.paddingBottom
        if (wSize <= 0 || hSize <= 0) return

        // 1. Side Padding for Capsule if Circle - REMOVED per Phase 51
        // if (isCircle && !isVertical) {
        //    tl.setPadding(sideMargin, container.paddingTop, sideMargin, container.paddingBottom)
        // }

        val availableW = container.width - container.paddingLeft - container.paddingRight
        val availableH = container.height - container.paddingTop - container.paddingBottom

        val gapSize = (if (style == Style.ROUNDED) 8 * density else 0f).toInt()
        val totalSpace =
                if (isVertical) availableH - (gapSize * (count - 1))
                else availableW - (gapSize * (count - 1))
        val itemSize = (totalSpace.toFloat() / count).toInt()

        // Final cross dimension for capping
        val maxCross = if (isVertical) availableW.toFloat() else availableH.toFloat()
        val finalItemSize = kotlin.math.min(itemSize.toFloat(), maxCross).toInt()

        segments.forEachIndexed { index, seg ->
            val childView = container.getChildAt(index)
            val btn =
                    if (childView is FrameLayout) childView.getChildAt(0) as TextView
                    else childView as TextView

            // Meta
            childView.tag = seg
            btn.tag = seg

            // V21.4: Layout listener to fix icon deformation when size becomes known
            btn.addOnLayoutChangeListener {
                    v,
                    left,
                    top,
                    right,
                    bottom,
                    oldLeft,
                    oldTop,
                    oldRight,
                    oldBottom ->
                val nw = right - left
                val nh = bottom - top
                if (nw > 0 && nh > 0 && (nw != (oldRight - oldLeft) || nh != (oldBottom - oldTop))
                ) {
                    // Update visuals for this button
                    val currentVal = data.props["value"] ?: ""
                    updateMultiSegmentButtons(container, currentVal, color, style, finalItemSize)
                }
            }

            // INTERACTION: Dynamic re-attachment
            if (sendMqtt != null && onUpdateProp != null) {
                btn.isClickable = true
                btn.setOnClickListener {
                    sendMqtt(data.topicConfig, seg.value)
                    onUpdateProp("value", seg.value)
                    updateMultiSegmentButtons(container, seg.value, color, style, finalItemSize)
                }
            }

            // Layout Params
            val lp = childView.layoutParams as LinearLayout.LayoutParams
            if (isVertical) {
                lp.width = LinearLayout.LayoutParams.MATCH_PARENT
                lp.height = 0
                lp.weight = 1f
                lp.topMargin = if (index > 0) gapSize else 0
                lp.leftMargin = 0
            } else {
                lp.width = 0
                lp.height = LinearLayout.LayoutParams.MATCH_PARENT
                lp.weight = 1f
                lp.leftMargin = if (index > 0) gapSize else 0
                lp.topMargin = 0
            }
            childView.layoutParams = lp

            // Inner Button Size if Circle
            if (isCircle) {
                val btnLp = btn.layoutParams as FrameLayout.LayoutParams
                btnLp.width = finalItemSize
                btnLp.height = finalItemSize
                btn.layoutParams = btnLp
                btn.textSize = 10f
            } else {
                btn.textSize = 13f
            }

            updateBtnContent(btn, seg, finalItemSize, container.context)
        }

        updateMultiSegmentButtons(container, data.props["value"], color, style, finalItemSize)
    }

    private fun updateBtnContent(btn: TextView, seg: Segment, itemSize: Int, context: Context) {
        btn.gravity = Gravity.CENTER
        btn.includeFontPadding = false
        if (seg.type == "icon") {
            btn.text = ""
        } else {
            btn.text = seg.label
        }
        btn.setCompoundDrawables(null, null, null, null)
        btn.setPadding(0, 0, 0, 0)
    }

    private fun updateMultiSegmentButtons(
            container: LinearLayout,
            selectedValue: String?,
            color: Int,
            style: String,
            size: Int
    ) {
        val density = container.resources.displayMetrics.density
        val isDark =
                (container.context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        val isCircle = (style == Style.CIRCLE)
        val isSegmented = (style == Style.SEGMENTED)

        for (i in 0 until container.childCount) {
            val childView = container.getChildAt(i)
            val child =
                    if (childView is FrameLayout) childView.getChildAt(0) as TextView
                    else childView as TextView
            val seg = child.tag as? Segment ?: continue

            val isSelected =
                    (selectedValue != null && seg.value == selectedValue) ||
                            (selectedValue == null && i == 0)

            val radius =
                    when {
                        isCircle -> size / 2f
                        isSegmented -> if (size > 0) size / 2f else 999 * density
                        else -> 12 * density
                    }

            val bgMain =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = radius
                    }

            val activeTextColor = Color.WHITE
            val inactiveTextColor =
                    if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")

            if (isSelected) {
                bgMain.setColor(color)
                val strokeColor =
                        if (isDark) ColorUtils.setAlphaComponent(color, 120)
                        else ColorUtils.setAlphaComponent(color, 200)
                bgMain.setStroke((1.5f * density).toInt(), strokeColor)

                child.setTextColor(activeTextColor)
                child.elevation = if (isDark) 12 * density else 6 * density
                child.translationZ = 4 * density
                if (isDark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    child.outlineSpotShadowColor = color
                    child.outlineAmbientShadowColor = color
                }
                child.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                child.animate().scaleX(0.95f).scaleY(0.95f).setDuration(50).start()
            } else {
                child.translationZ = 0f
                child.scaleX = 1f
                child.scaleY = 1f
                child.setTextColor(inactiveTextColor)

                if (isCircle) {
                    bgMain.setColor(if (isDark) Color.parseColor("#0F172A") else Color.WHITE)
                    // V21.2: Thematic border for circle
                    val strokeColor = ColorUtils.setAlphaComponent(color, if (isDark) 80 else 120)
                    bgMain.setStroke((1.2f * density).toInt(), strokeColor)
                    child.elevation = 1 * density
                    child.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                } else if (isSegmented) {
                    bgMain.setColor(Color.TRANSPARENT)
                    child.elevation = 0f
                    child.outlineProvider = null
                } else {
                    bgMain.setColor(if (isDark) Color.parseColor("#1E293B") else Color.WHITE)
                    // V21.2: Thematic border for rounded
                    val strokeColor = ColorUtils.setAlphaComponent(color, if (isDark) 60 else 100)
                    bgMain.setStroke((1 * density).toInt(), strokeColor)
                    child.elevation = 2 * density
                    child.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                }
            }

            if (seg.type == "icon") {
                val iconRes = getIconRes(seg.label)
                val iconDrawable = ContextCompat.getDrawable(container.context, iconRes)?.mutate()
                if (iconDrawable != null) {
                    val isVertical = container.orientation == LinearLayout.VERTICAL
                    val count = container.childCount.coerceAtLeast(1)
                    val gap = (if (style == Style.ROUNDED) 8 * density else 0f).toInt()

                    // V21.4: Robust dimension estimation with global fallback
                    val safeParentW = container.width.coerceAtLeast(0)
                    val safeParentH = container.height.coerceAtLeast(0)

                    val cw =
                            when {
                                child.width > 0 -> child.width
                                isVertical ->
                                        if (safeParentW > 0)
                                                safeParentW -
                                                        container.paddingLeft -
                                                        container.paddingRight
                                        else size
                                else ->
                                        if (safeParentW > 0)
                                                (safeParentW -
                                                        container.paddingLeft -
                                                        container.paddingRight -
                                                        (gap * (count - 1))) / count
                                        else size
                            }

                    val ch =
                            when {
                                child.height > 0 -> child.height
                                isVertical ->
                                        if (safeParentH > 0)
                                                (safeParentH -
                                                        container.paddingTop -
                                                        container.paddingBottom -
                                                        (gap * (count - 1))) / count
                                        else size
                                else ->
                                        if (safeParentH > 0)
                                                safeParentH -
                                                        container.paddingTop -
                                                        container.paddingBottom
                                        else size
                            }

                    iconDrawable.setTint(if (isSelected) activeTextColor else inactiveTextColor)
                    val actualDim =
                            if (cw > 0 && ch > 0)
                                    kotlin.math.min(cw.toFloat(), ch.toFloat()).toInt()
                            else size
                    val iconSize = (actualDim * 0.5f).toInt()

                    val layer = LayerDrawable(arrayOf(bgMain, iconDrawable))
                    val insetX = (cw - iconSize) / 2
                    val insetY = (ch - iconSize) / 2
                    layer.setLayerInset(1, insetX, insetY, insetX, insetY)
                    child.background = layer
                } else {
                    child.background = bgMain
                }
            } else {
                child.background = bgMain
            }
        }
    }

    override val propertiesLayoutId = R.layout.layout_prop_selector

    override fun bindPropertiesPanel(
            panelView: View,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // 0. Topic Config (Shared) - Handled by PropertiesSheetManager

        // Style
        val spStyle = panelView.findViewById<AutoCompleteTextView>(R.id.spPropStyle)
        val styles =
                listOf(
                        context.getString(R.string.val_style_rounded), // 0: Rects
                        context.getString(R.string.val_shape_circle_style), // 1: Circles
                        context.getString(R.string.val_style_segmented) // 2: Capsule
                )
        spStyle?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, styles))
        val curStyle = data.props["style"] ?: Style.ROUNDED
        val displayStyle =
                when (curStyle) {
                    Style.CIRCLE -> context.getString(R.string.val_shape_circle_style)
                    Style.SEGMENTED -> context.getString(R.string.val_style_segmented)
                    else -> context.getString(R.string.val_style_rounded)
                }
        spStyle?.setText(displayStyle, false)

        spStyle?.setOnItemClickListener { _, _, position, _ ->
            val key =
                    when (position) {
                        0 -> Style.ROUNDED
                        1 -> Style.CIRCLE
                        2 -> Style.SEGMENTED
                        else -> Style.ROUNDED
                    }
            onUpdate("style", key)
        }

        // Orientation
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

        // Colors (Simplified for brevity, assuming ColorHistoryManager exists)
        panelView.findViewById<View>(R.id.containerColorPalette)?.let { container ->
            // Reusing generic color logic if possible, or bind custom here
            // For now assume standard binding...
            val colorViews =
                    listOf(
                            container.findViewById<View>(R.id.vColor1),
                            container.findViewById<View>(R.id.vColor2),
                            container.findViewById<View>(R.id.vColor3),
                            container.findViewById<View>(R.id.vColor4),
                            container.findViewById<View>(R.id.vColor5)
                    )
            fun refreshColors() {
                val recent = com.example.mqttpanelcraft.data.ColorHistoryManager.load(context)
                colorViews.forEachIndexed { i, v ->
                    if (i < recent.size) {
                        v?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(recent[i]))
                        v?.setOnClickListener { onUpdate("color", recent[i]) }
                    }
                }
            }
            refreshColors()

            container.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener { anchor ->
                val cur = data.props["color"] ?: "#a573bc"
                var tempColor = cur
                ColorPickerDialog(
                                context,
                                cur,
                                true,
                                { c ->
                                    tempColor = c
                                    onUpdate("color", c)
                                },
                                {
                                    com.example.mqttpanelcraft.data.ColorHistoryManager.save(
                                            context,
                                            tempColor
                                    )
                                    refreshColors()
                                }
                        )
                        .show(anchor)
            }
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
                json.put(
                        JSONObject()
                                .put("label", it.label)
                                .put("value", it.value)
                                .put("type", it.type)
                )
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
                val btnToggle = row.findViewById<ImageView>(R.id.btnSegIconToggle)
                val llIconSelector = row.findViewById<LinearLayout>(R.id.llIconSelector)

                etLabel.setText(seg.label)
                etValue.setText(seg.value)

                // Toggle Icon State
                fun updateRefIcon() {
                    if (seg.type == "icon") {
                        btnToggle.setImageResource(R.drawable.ic_text_fields)
                        etLabel.visibility = View.GONE
                        llIconSelector.visibility = View.VISIBLE

                        // Update icon selection state
                        for (i in 0 until llIconSelector.childCount) {
                            val iconView = llIconSelector.getChildAt(i) as? ImageView ?: continue
                            val key = iconView.tag.toString()
                            if (key == seg.label) {
                                iconView.setColorFilter(
                                        Color.parseColor("#A855F7")
                                ) // Purple accent
                                iconView.alpha = 1.0f
                            } else {
                                iconView.setColorFilter(Color.parseColor("#94A3B8")) // Slate 400
                                iconView.alpha = 0.5f
                            }

                            iconView.setOnClickListener {
                                seg.label = key
                                save()
                                updateRefIcon()
                            }
                        }
                    } else {
                        btnToggle.setImageResource(R.drawable.ic_emoji_emotions)
                        etLabel.visibility = View.VISIBLE
                        llIconSelector.visibility = View.GONE
                        etLabel.hint = "Label"
                    }
                }
                updateRefIcon()

                btnToggle.setOnClickListener {
                    if (seg.type == "text") {
                        seg.type = "icon"
                        // If current label is not a valid icon key, default to 'power'
                        val validKeys = listOf("power", "light", "fan", "play", "tune", "energy")
                        if (seg.label !in validKeys) {
                            seg.label = "power"
                        }
                    } else {
                        seg.type = "text"
                        // V51: Revert to default label S* when switching back to text
                        seg.label = "S${index + 1}"
                    }
                    updateRefIcon()
                    save()
                }

                val watcher =
                        object : android.text.TextWatcher {
                            override fun afterTextChanged(s: android.text.Editable?) {
                                seg.label = etLabel.text.toString()
                                seg.value = etValue.text.toString()
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
                segmentList.add(Segment("S${segmentList.size+1}", "${segmentList.size+1}"))
                save()
                renderRows()
            } else {
                android.widget.Toast.makeText(
                                context,
                                "Max limits reached",
                                android.widget.Toast.LENGTH_SHORT
                        )
                        .show()
            }
        }
        panelView.findViewById<View>(R.id.btnSegRemove)?.setOnClickListener {
            if (segmentList.size > 2) {
                segmentList.removeAt(segmentList.lastIndex)
                save()
                renderRows()
            } else {
                android.widget.Toast.makeText(
                                context,
                                "Min limits reached",
                                android.widget.Toast.LENGTH_SHORT
                        )
                        .show()
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
        val child = root.getChildAt(0) as? LinearLayout ?: return
        val color = resolveColor(data)
        val style = data.props["style"] ?: Style.ROUNDED
        val orient = data.props["orientation"] ?: "horz"

        fun doLayout() {
            val segments = parseSegments(data.props["segments"] ?: "")
            layoutSegments(child, segments, data, color, style, orient, sendMqtt, onUpdateProp)
        }

        if (child.width > 0 && child.height > 0) {
            doLayout()
        } else {
            child.post { doLayout() }
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
            val count = child.childCount
            if (count > 0) {
                // Approximate size for button updates
                val orientation = data.props["orientation"] ?: "horz"
                val density = child.resources.displayMetrics.density
                val gapSize = (if (style == Style.ROUNDED) 8 * density else 0f).toInt()

                val w = child.width - child.paddingLeft - child.paddingRight
                val h = child.height - child.paddingTop - child.paddingBottom

                val perItem =
                        if (orientation == "vert") {
                            if (h > 0) (h - gapSize * (count - 1)) / count else 0
                        } else {
                            if (w > 0) (w - gapSize * (count - 1)) / count else 0
                        }

                updateMultiSegmentButtons(child, payload, color, style, perItem)
            }
        }
    }

    private fun resolveColor(data: ComponentData): Int =
            try {
                Color.parseColor(data.props["color"] ?: "#6366F1")
            } catch (e: Exception) {
                Color.parseColor("#6366F1")
            }

    data class Segment(var label: String, var value: String, var type: String = "text")
    private fun parseSegments(json: String): MutableList<Segment> {
        val list = mutableListOf<Segment>()
        if (json.isEmpty()) {
            return mutableListOf(
                    Segment("S1", "1"),
                    Segment("S2", "2"),
                    Segment("S3", "3"),
                    Segment("S4", "4")
            )
        }
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                        Segment(
                                obj.optString("label", "?"),
                                obj.optString("value", "0"),
                                obj.optString("type", "text")
                        )
                )
            }
        } catch (e: Exception) {
            return mutableListOf(Segment("Error", "0"))
        }
        return list
    }

    private fun getIconRes(key: String): Int =
            when (key) {
                "power" -> R.drawable.ic_btn_power
                "lighting" -> R.drawable.ic_btn_lighting
                "fan" -> R.drawable.ic_btn_fan
                "play" -> R.drawable.ic_btn_play
                "tune" -> R.drawable.ic_btn_tune
                "energy" -> R.drawable.ic_btn_energy
                else -> R.drawable.ic_btn_power
            }
}
