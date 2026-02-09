package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.ColorUtils
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.google.android.material.button.MaterialButtonToggleGroup

object SwitchDefinition : com.example.mqttpanelcraft.ui.components.IComponentDefinition {

    object Mode {
        const val TWO_WAY = "switch_2"
        const val THREE_WAY = "switch_3"
        const val MULTI = "multi"
    }

    object Style {
        const val CLASSIC = "classic"
        const val LEVER = "lever"
    }

    override val type = "SWITCH"
    override val defaultSize = android.util.Size(120, 70)
    override val labelPrefix = "switch"
    override val iconResId = R.drawable.ic_grid_view
    override val group = "CONTROL"

    override fun createView(
            context: android.content.Context,
            isEditMode: Boolean
    ): android.view.View {
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

    override fun onUpdateView(
            view: android.view.View,
            data: com.example.mqttpanelcraft.model.ComponentData
    ) {
        val root = (view as? ViewGroup)?.getChildAt(0) as? FrameLayout ?: return
        val mode = resolveMode(data)
        val style = data.props["style"] ?: Style.CLASSIC
        val signature = "mode:$mode|style:$style"

        if (root.tag != signature) {
            val steps = if (mode == Mode.TWO_WAY) 2 else 3
            var savedState: Int? = (root.getChildAt(0)?.tag as? Int)
            root.removeAllViews()
            val inflater = LayoutInflater.from(root.context)
            inflater.inflate(R.layout.layout_component_switch_tristate, root, true)
            val triRoot = root.getChildAt(0)
            if (savedState != null && savedState < steps) {
                triRoot.tag = savedState
            }
            root.tag = signature
        }

        val color = resolveColor(data)
        val triContainer = root.getChildAt(0) as? ConstraintLayout ?: return
        updateTriStateVisuals(triContainer, data, color, mode, style, animate = false)
    }

    private fun updateTriStateVisuals(
            container: ConstraintLayout,
            data: ComponentData,
            color: Int,
            mode: String,
            style: String,
            animate: Boolean = false
    ) {
        val density = container.resources.displayMetrics.density
        val slate200 = Color.parseColor("#E2E8F0")

        val track = container.findViewById<View>(R.id.viewTrack)
        val cvThumb = container.findViewById<CardView>(R.id.cvThumb)
        val innerIndicator = container.findViewById<View>(R.id.innerIndicator)

        track.visibility = View.VISIBLE
        cvThumb.visibility = View.VISIBLE

        val set = ConstraintSet()
        set.clone(container.context, R.layout.layout_component_switch_tristate)

        val cvThumbId = R.id.cvThumb
        val trackId = R.id.viewTrack

        // 1. OUTER BORDER EFFECT: Inset the track from the component boundary
        val outerMargin = (6 * density).toInt()
        set.connect(trackId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        set.connect(trackId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        set.connect(trackId, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT)
        set.connect(trackId, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT)
        set.setMargin(trackId, ConstraintSet.TOP, outerMargin)
        set.setMargin(trackId, ConstraintSet.BOTTOM, outerMargin)
        set.setMargin(trackId, ConstraintSet.LEFT, outerMargin)
        set.setMargin(trackId, ConstraintSet.RIGHT, outerMargin)
        set.constrainWidth(trackId, 0)
        set.constrainHeight(trackId, 0)

        val trackBg = GradientDrawable()
        trackBg.shape = GradientDrawable.RECTANGLE
        val dotParams = innerIndicator.layoutParams as FrameLayout.LayoutParams
        dotParams.gravity = Gravity.CENTER

        when (style) {
            Style.LEVER -> {
                // 2. INNER FLOATING EFFECT: Thumb is smaller than the track
                set.constrainWidth(cvThumbId, 0)
                set.constrainHeight(cvThumbId, 0)
                set.setDimensionRatio(cvThumbId, "1:1")
                set.connect(cvThumbId, ConstraintSet.TOP, trackId, ConstraintSet.TOP)
                set.connect(cvThumbId, ConstraintSet.BOTTOM, trackId, ConstraintSet.BOTTOM)

                val innerGap = (8 * density).toInt()
                set.setMargin(cvThumbId, ConstraintSet.TOP, innerGap)
                set.setMargin(cvThumbId, ConstraintSet.BOTTOM, innerGap)

                cvThumb.radius = 4 * density
                trackBg.cornerRadius = 8 * density

                // Dynamic Proportion: Tall & Thin
                // View: Fixed 12dp W / 60% H
                cvThumb.post {
                    if (!cvThumb.isAttachedToWindow) return@post
                    val h = cvThumb.height
                    if (h > 0) {
                        val lp = innerIndicator.layoutParams
                        lp.width = (12 * density).toInt() // Fixed width for tight glow
                        lp.height = (h * 0.60f).toInt() // 60% Height
                        innerIndicator.layoutParams = lp
                        innerIndicator.requestLayout()
                    }
                }

                dotParams.width = (12 * density).toInt()
                dotParams.height = (20 * density).toInt()

                // Position: Centered
                dotParams.gravity = Gravity.CENTER
                dotParams.rightMargin = 0

                innerIndicator.layoutParams = dotParams

                innerIndicator.setBackgroundResource(R.drawable.bg_switch_thumb_lever)
                innerIndicator.visibility = View.VISIBLE
            }
            else -> {
                set.constrainWidth(cvThumbId, 0)
                set.constrainHeight(cvThumbId, 0)
                set.setDimensionRatio(cvThumbId, "1:1")
                set.connect(cvThumbId, ConstraintSet.TOP, trackId, ConstraintSet.TOP)
                set.connect(cvThumbId, ConstraintSet.BOTTOM, trackId, ConstraintSet.BOTTOM)
                val m = (5 * density).toInt()
                set.setMargin(cvThumbId, ConstraintSet.TOP, m)
                set.setMargin(cvThumbId, ConstraintSet.BOTTOM, m)
                cvThumb.radius = 100 * density
                trackBg.cornerRadius = 100 * density
                innerIndicator.setBackgroundResource(R.drawable.bg_track_rounded)
                innerIndicator.visibility = View.GONE

                // Reset standard gravity/margins
                dotParams.gravity = Gravity.CENTER
                dotParams.rightMargin = 0
            }
        }
        innerIndicator.layoutParams = dotParams

        val steps = if (mode == Mode.TWO_WAY) 2 else 3
        var state =
                (container.tag as? Int)
                        ?: data.props["state"]?.toIntOrNull() ?: (if (steps == 2) 0 else 1)

        if (mode == Mode.TWO_WAY && state == 1) state = 0
        container.tag = state

        set.connect(cvThumbId, ConstraintSet.LEFT, trackId, ConstraintSet.LEFT)
        set.connect(cvThumbId, ConstraintSet.RIGHT, trackId, ConstraintSet.RIGHT)

        // Compensate bias for the inset margins to center better at extremes
        val bias =
                when (state) {
                    0 -> 0.08f
                    2 -> 0.92f
                    else -> 0.5f
                }
        set.setHorizontalBias(cvThumbId, bias)

        if (animate && container.isAttachedToWindow) {
            val transition = ChangeBounds()
            transition.duration = 250
            transition.interpolator = AccelerateDecelerateInterpolator()
            TransitionManager.beginDelayedTransition(container, transition)
        }
        set.applyTo(container)

        cvThumb.setCardBackgroundColor(Color.WHITE)
        cvThumb.cardElevation = 6f * density

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)

        val uiMode = container.context.resources.configuration.uiMode
        val isDark =
                (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // Reset track alpha before state logic
        track.alpha = 1.0f

        when (state) {
            0 -> {
                // User Spec v8: 60% Transparent = 40% Opacity (Alpha 102)
                trackBg.setColor(ColorUtils.setAlphaComponent(color, 102))
                track.alpha = 0.40f

                // User spec: Switch should have colored border even when OFF
                val strokeColor = if (isDark) color else ColorUtils.setAlphaComponent(color, 180)
                trackBg.setStroke((1.5f * density).toInt(), strokeColor)

                if (style == Style.LEVER) {
                    updateNeonColors(innerIndicator, state, color, isDark)
                }
            }
            1 -> {
                val hslDim = hsl.copyOf()
                if (isDark) {
                    hslDim[1] = hslDim[1] * 0.5f
                    hslDim[2] = 0.20f
                } else {
                    hslDim[1] = hslDim[1] * 0.6f
                    hslDim[2] = 0.40f
                }
                trackBg.setColor(ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hslDim), 150))
                track.alpha = 0.6f

                val strokeColor = if (isDark) color else ColorUtils.setAlphaComponent(color, 180)
                trackBg.setStroke((1.5f * density).toInt(), strokeColor)

                if (style == Style.LEVER) {
                    updateNeonColors(innerIndicator, state, color, isDark)
                }
            }
            2 -> {
                // User Spec v8: 20% Transparent = 80% Opacity (Alpha 204)
                // "變得更深一些" -> Higher opacity shows the color more purely
                val hslOn = hsl.copyOf()
                hslOn[1] = Math.min(1.0f, hslOn[1] * 1.1f)
                hslOn[2] = if (isDark) 0.15f else 0.40f
                trackBg.setColor(ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hslOn), 204))
                track.alpha = 0.80f

                val strokeW = if (isDark) (1.5f * density).toInt() else (2 * density).toInt()
                trackBg.setStroke(strokeW, color)

                if (style == Style.LEVER) {
                    updateNeonColors(innerIndicator, state, color, isDark)
                }
            }
        }
        track.background = trackBg
    }

    override val propertiesLayoutId = R.layout.layout_prop_switch

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

        val toggleMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleMode)
        val mode = resolveMode(data)

        when (mode) {
            Mode.TWO_WAY -> toggleMode.check(R.id.btnMode2Way)
            Mode.THREE_WAY -> toggleMode.check(R.id.btnMode3Way)
        }

        fun updateSubPanels(m: String) {
            val containerTri = panelView.findViewById<View>(R.id.containerTriStateConfig)
            val tilCenter = panelView.findViewById<View>(R.id.tilPayloadCenter)
            val spaceCenter = panelView.findViewById<View>(R.id.spacePayloadCenter)
            val isThreeWay = (m == Mode.THREE_WAY)
            containerTri?.visibility = View.VISIBLE
            tilCenter?.visibility = if (isThreeWay) View.VISIBLE else View.GONE
            spaceCenter?.visibility = if (isThreeWay) View.VISIBLE else View.GONE
            panelView.findViewById<TextView>(R.id.tvPayloadLabel)?.text =
                    context.getString(
                            if (isThreeWay) R.string.prop_label_messages_lcr
                            else R.string.prop_label_messages_lr
                    )
        }
        updateSubPanels(mode)

        val spStyle = panelView.findViewById<AutoCompleteTextView>(R.id.spPropStyle)
        val styles =
                listOf(
                        context.getString(R.string.val_style_classic),
                        context.getString(R.string.val_style_lever)
                )
        val adapterS = ArrayAdapter(context, R.layout.list_item_dropdown, styles)
        spStyle.setAdapter(adapterS)
        val curStyle = data.props["style"] ?: Style.CLASSIC
        val display =
                if (curStyle == Style.LEVER) context.getString(R.string.val_style_lever)
                else context.getString(R.string.val_style_classic)
        spStyle.setText(display, false)

        toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val m = if (checkedId == R.id.btnMode2Way) Mode.TWO_WAY else Mode.THREE_WAY
                updateSubPanels(m)
                onUpdate("mode", m)
            }
        }
        spStyle.setOnItemClickListener { _, _, position, _ ->
            val key = if (position == 1) Style.LEVER else Style.CLASSIC
            onUpdate("style", key)
        }

        listOf(
                        R.id.etPayloadLeft to "payloadLeft",
                        R.id.etPayloadCenter to "payloadCenter",
                        R.id.etPayloadRight to "payloadRight"
                )
                .forEach { (id, prop) ->
                    val et = panelView.findViewById<EditText>(id) ?: return@forEach
                    et.setText(
                            data.props[prop]
                                    ?: if (prop == "payloadCenter") "1"
                                    else if (prop.endsWith("Left")) "0" else "2"
                    )
                    et.setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) onUpdate(prop, et.text.toString())
                    }
                }

        val colorViews =
                listOf(R.id.vColor1, R.id.vColor2, R.id.vColor3, R.id.vColor4, R.id.vColor5).map {
                    panelView.findViewById<View>(it)
                }
        fun refreshColors() {
            val recent = ColorHistoryManager.load(context)
            colorViews.forEachIndexed { i, v ->
                if (i < recent.size) {
                    val c = Color.parseColor(recent[i])
                    v?.backgroundTintList = ColorStateList.valueOf(c)
                    v?.setOnClickListener { onUpdate("color", recent[i]) }
                }
            }
        }
        refreshColors()

        panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener { anchor ->
            val cur = data.props["color"] ?: "#2196F3"
            var temp = cur
            ColorPickerDialog(
                            context,
                            cur,
                            true,
                            {
                                temp = it
                                onUpdate("color", it)
                            },
                            {
                                if (temp != cur) {
                                    ColorHistoryManager.save(context, temp)
                                    refreshColors()
                                }
                            }
                    )
                    .show(anchor)
        }
    }

    override fun attachBehavior(
            view: android.view.View,
            data: com.example.mqttpanelcraft.model.ComponentData,
            sendMqtt: (topic: String, payload: String) -> Unit,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val root = (view as? ViewGroup)?.getChildAt(0) as? FrameLayout ?: return
        val tri = root.getChildAt(0) as? ConstraintLayout ?: return
        val mode = resolveMode(data)
        val color = resolveColor(data)
        val style = data.props["style"] ?: Style.CLASSIC

        fun updateState(newState: Int) {
            tri.tag = newState
            onUpdateProp("state", newState.toString()) // Sync state for persistence
            updateTriStateVisuals(tri, data, color, mode, style, animate = true)
            val payload =
                    when (newState) {
                        0 -> data.props["payloadLeft"] ?: "0"
                        2 -> data.props["payloadRight"] ?: "2"
                        else -> data.props["payloadCenter"] ?: "1"
                    }
            sendMqtt(data.topicConfig, payload)
        }

        tri.findViewById<android.view.View>(R.id.zoneLeft).setOnClickListener { updateState(0) }
        tri.findViewById<android.view.View>(R.id.zoneRight).setOnClickListener { updateState(2) }
        tri.findViewById<android.view.View>(R.id.zoneCenter).setOnClickListener {
            if (mode == Mode.TWO_WAY) {
                val cur = tri.tag as? Int ?: 0
                updateState(if (cur == 0) 2 else 0)
            } else {
                updateState(1)
            }
        }
    }

    override fun onMqttMessage(
            view: android.view.View,
            data: com.example.mqttpanelcraft.model.ComponentData,
            payload: String,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val root = (view as? ViewGroup)?.getChildAt(0) as? FrameLayout ?: return
        val tri = root.getChildAt(0) as? ConstraintLayout ?: return
        val mode = resolveMode(data)
        val color = resolveColor(data)
        val style = data.props["style"] ?: Style.CLASSIC
        val newState =
                when (payload) {
                    data.props["payloadLeft"] ?: "0" -> 0
                    data.props["payloadRight"] ?: "2" -> 2
                    else -> 1
                }
        tri.tag = newState
        onUpdateProp("state", newState.toString()) // Persist MQTT state
        view.post { updateTriStateVisuals(tri, data, color, mode, style, animate = true) }
    }

    private fun updateNeonColors(view: View, state: Int, color: Int, isDark: Boolean) {
        val ld = view.background as? LayerDrawable ?: return
        if (ld.numberOfLayers < 2) return

        val glowLayer = ld.getDrawable(0) as? GradientDrawable ?: return // Index 0: Glow
        val coreLayer = ld.getDrawable(1) as? GradientDrawable ?: return // Index 1: Slot/Core

        when (state) {
            0 -> { // OFF
                glowLayer.setColor(Color.TRANSPARENT)
                // Slot Dark to emulate a physical slot
                coreLayer.setColor(Color.parseColor("#40000000"))
            }
            1 -> { // DIM/TRANSITION
                // Use Linear Gradient (Left-Right) so it glows along the strip
                glowLayer.gradientType = GradientDrawable.LINEAR_GRADIENT
                glowLayer.orientation = GradientDrawable.Orientation.LEFT_RIGHT
                glowLayer.colors =
                        intArrayOf(
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(color, 40),
                                Color.TRANSPARENT
                        )
                coreLayer.setColor(ColorUtils.setAlphaComponent(color, 120))
            }
            2 -> { // ON
                // Linear Gradient Glow (Halo) along the strip
                glowLayer.gradientType = GradientDrawable.LINEAR_GRADIENT
                glowLayer.orientation = GradientDrawable.Orientation.LEFT_RIGHT
                // Center burst, fade to transparent edges
                glowLayer.colors =
                        intArrayOf(
                                Color.TRANSPARENT,
                                ColorUtils.setAlphaComponent(color, 128),
                                Color.TRANSPARENT
                        )

                coreLayer.setColor(color)
            }
        }
    }

    private fun resolveMode(data: ComponentData): String {
        val m = data.props["mode"] ?: Mode.TWO_WAY
        return if (m == Mode.MULTI) Mode.THREE_WAY else m
    }

    private fun resolveColor(data: ComponentData): Int =
            try {
                Color.parseColor(data.props["color"] ?: "#2196F3")
            } catch (e: Exception) {
                Color.parseColor("#2196F3")
            }
}
