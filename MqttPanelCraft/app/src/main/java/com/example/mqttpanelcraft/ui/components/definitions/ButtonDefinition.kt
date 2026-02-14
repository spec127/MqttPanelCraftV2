package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

object ButtonDefinition : IComponentDefinition {

    override val type = "BUTTON"
    override val defaultSize = Size(120, 70)
    override val labelPrefix = "button"
    override val iconResId = R.drawable.ic_btn_power
    override val group = "CONTROL"

    override fun createView(context: Context, isEditMode: Boolean): View {
        // Set default blue color if not set
        // Note: data is not available here, will be set in onUpdateView
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val button =
                AppCompatButton(context).apply {
                    text = "button1"
                    stateListAnimator = null
                    elevation = 0f
                    textSize = 16f
                    isAllCaps = false
                    tag = "target"
                    layoutParams =
                            FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                    )
                                    .apply {
                                        gravity = Gravity.CENTER
                                        setMargins(12, 12, 12, 12)
                                    }
                }
        container.addView(button, 0)

        val density = context.resources.displayMetrics.density
        val iconView =
                ImageView(context).apply {
                    tag = "ICON_OVERLAY"
                    visibility = View.GONE
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isClickable = false
                    isFocusable = false
                    elevation = 20f * density // Ensure it's above button (max ~12dp)
                }
        container.addView(iconView)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val button = container.findViewWithTag<Button>("target") ?: return
        val iconView = container.findViewWithTag<ImageView>("ICON_OVERLAY") ?: return

        val colorHex = data.props["color"] ?: "#2196F3"
        val color =
                try {
                    Color.parseColor(colorHex)
                } catch (e: Exception) {
                    Color.parseColor("#2196F3")
                }

        val shapeMode = data.props["shape"] ?: "pill"
        val cornerRadius =
                when (shapeMode) {
                    "rect" -> 12f * view.resources.displayMetrics.density
                    else -> 100f * view.resources.displayMetrics.density
                }
        val isOval = (shapeMode == "circle")

        // Dynamic depth based on height (approx 8%)
        val depth = (data.height.toFloat() * 0.08f) * view.resources.displayMetrics.density
        button.background = createTactileDrawable(color, isOval, cornerRadius, depth)

        // Dynamic elevation
        val dynamicElevation =
                (data.height.toFloat() * 0.08f) * view.resources.displayMetrics.density
        button.elevation =
                dynamicElevation.coerceIn(
                        2f * view.resources.displayMetrics.density,
                        12f * view.resources.displayMetrics.density
                )

        val isLight = ColorUtils.calculateLuminance(color) > 0.6
        val contentColor = if (isLight) Color.BLACK else Color.WHITE
        button.setTextColor(contentColor)

        // Dynamic Scaling: Content occupies ~60% of button height
        val density = view.resources.displayMetrics.density
        // Use data.height (source of truth) to prevent startup layout glitches where view.height
        // might be unstable
        val dim = data.height
        val targetH = dim * 0.6f

        // Helper for text size
        fun safeTextSize(px: Float): Float = (px / density).coerceIn(8f, 60f)

        button.text = ""
        button.setCompoundDrawables(null, null, null, null)
        iconView.visibility = View.GONE

        val apprMode = data.props["appearance_mode"] ?: "text"
        val iconKey = data.props["icon"] ?: "power"
        val iconRes = getIconRes(iconKey)

        when (apprMode) {
            "icon" -> {
                iconView.visibility = View.VISIBLE
                iconView.setImageResource(iconRes)
                iconView.imageTintList = ColorStateList.valueOf(contentColor)

                // Icon Only: scaled to targetH
                val size = targetH.toInt().coerceAtLeast((16 * density).toInt())
                iconView.layoutParams =
                        (iconView.layoutParams as FrameLayout.LayoutParams).apply {
                            width = size
                            height = size
                            gravity = Gravity.CENTER
                        }
            }
            "text_icon" -> {
                button.text = data.props["text"] ?: "button1"
                // Stacked: Icon top (60%), Text bottom (40%) of targetH
                val iconSize = (targetH * 0.6f).toInt().coerceAtLeast((12 * density).toInt())
                val textSizePx = targetH * 0.25f
                val padding = (targetH * 0.02f).toInt().coerceAtLeast((1 * density).toInt())

                val d = ContextCompat.getDrawable(view.context, iconRes)?.mutate()
                d?.setBounds(0, 0, iconSize, iconSize)
                d?.setTint(contentColor)

                button.setCompoundDrawables(null, d, null, null)
                button.compoundDrawablePadding = padding
                button.includeFontPadding = false
                button.textSize = safeTextSize(textSizePx)
            }
            else -> { // "text"
                button.text = data.props["text"] ?: "button1"
                val textSizePx = targetH * 0.5f
                button.textSize = safeTextSize(textSizePx)
            }
        }
    }

    private fun createTactileDrawable(
            baseColor: Int,
            isOval: Boolean,
            radius: Float,
            depth: Float
    ): StateListDrawable {
        val sld = StateListDrawable()
        sld.addState(
                intArrayOf(android.R.attr.state_pressed),
                createTactileLayer(baseColor, isOval, radius, depth, true)
        )
        sld.addState(intArrayOf(), createTactileLayer(baseColor, isOval, radius, depth, false))
        return sld
    }

    private fun createTactileLayer(
            color: Int,
            isOval: Boolean,
            radius: Float,
            depth: Float,
            isPressed: Boolean
    ): LayerDrawable {
        val density = Resources.getSystem().displayMetrics.density

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)

        val mainColor =
                if (isPressed) {
                    val pressedHsl = hsl.copyOf()
                    pressedHsl[1] *= 0.8f
                    pressedHsl[2] *= 0.7f
                    ColorUtils.HSLToColor(pressedHsl)
                } else color

        // Halo/Glow Shadow Layer (Soft Blur look)
        val shadowLayer =
                GradientDrawable().apply {
                    val shadowColor =
                            if (isPressed) {
                                Color.TRANSPARENT
                            } else {
                                ColorUtils.setAlphaComponent(color, 40) // Soft themed glow
                            }
                    setColor(shadowColor)
                    if (isOval) shape = GradientDrawable.OVAL
                    else {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = radius + (4 * density) // Slightly larger for blur effect
                    }
                }

        val faceLayer =
                GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                if (isPressed) {
                                    intArrayOf(mainColor, mainColor)
                                } else {
                                    val lightHsl = hsl.copyOf()
                                    lightHsl[2] = Math.min(1.0f, lightHsl[2] * 1.15f)
                                    intArrayOf(ColorUtils.HSLToColor(lightHsl), mainColor)
                                }
                        )
                        .apply {
                            if (isOval) shape = GradientDrawable.OVAL
                            else {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = radius
                            }
                            val strokeColor =
                                    if (isPressed) ColorUtils.setAlphaComponent(Color.BLACK, 40)
                                    else ColorUtils.setAlphaComponent(Color.WHITE, 80)
                            setStroke((1.5f * density).toInt(), strokeColor)
                        }

        val layers = arrayOf(shadowLayer, faceLayer)
        val ld = LayerDrawable(layers)

        // Glow spread: 6dp for normal, 0dp for pressed
        val spread = if (isPressed) 0 else (6 * density).toInt()
        ld.setLayerInset(0, -spread, -spread, -spread, -spread)

        // Face position: slight vertical offset for 3D feel but softer
        val offset = if (isPressed) (2 * density).toInt() else 0
        ld.setLayerInset(1, 0, offset, 0, 0)

        return ld
    }

    override val propertiesLayoutId = R.layout.layout_prop_button

    override fun bindPropertiesPanel(
            panelView: View,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // 0. Topic Config (Shared)
        // 0. Topic Config (Shared) - Handled by PropertiesSheetManager
        // No manual binding needed here

        val etText = panelView.findViewById<EditText>(R.id.etPropText)
        etText?.setText(data.props["text"] ?: "button1")
        etText?.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        onUpdate("text", s.toString())
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
        )

        val spShape = panelView.findViewById<AutoCompleteTextView>(R.id.spPropShape)
        val shapeKeys = listOf("pill", "rect", "circle")
        val shapeLabels =
                listOf(
                        context.getString(R.string.val_shape_rounded_rect),
                        context.getString(R.string.val_shape_square),
                        context.getString(R.string.val_shape_circle_style)
                )
        spShape?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, shapeLabels))
        val curShape = data.props["shape"] ?: "pill"
        val displayShape = shapeLabels.getOrNull(shapeKeys.indexOf(curShape)) ?: shapeLabels[0]
        spShape?.setText(displayShape, false)
        spShape?.setOnItemClickListener { _, _, position, _ ->
            onUpdate("shape", shapeKeys[position])
        }

        // --- Press Payload with 2-step UX ---
        val etPress = panelView.findViewById<AutoCompleteTextView>(R.id.etPropPayloadPress)
        val payloadOptions = listOf("ON", "OFF", "1", "0", "TOGGLE")
        etPress?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, payloadOptions))
        etPress?.setText(data.props["payload"] ?: "ON", false)

        // Interaction: 1st click menu, 2nd click edit
        etPress?.isFocusableInTouchMode = false
        etPress?.setOnClickListener {
            if (!etPress.isPopupShowing) {
                etPress.showDropDown()
            }
            etPress.isFocusableInTouchMode = true
        }
        etPress?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                etPress.isFocusableInTouchMode = false
            }
        }
        etPress?.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        onUpdate("payload", s.toString())
                    }
                    override fun beforeTextChanged(i: CharSequence?, s: Int, c: Int, a: Int) {}
                    override fun onTextChanged(i: CharSequence?, s: Int, b: Int, c: Int) {}
                }
        )
        etPress?.setOnItemClickListener { _, _, _, _ ->
            onUpdate("payload", etPress.text.toString())
        }

        val colorViews =
                listOf(R.id.vColor1, R.id.vColor2, R.id.vColor3, R.id.vColor4, R.id.vColor5).map {
                    panelView.findViewById<View>(it)
                }
        fun refreshColors() {
            val recent = ColorHistoryManager.load(context)
            colorViews.forEachIndexed { i, v ->
                if (i < recent.size) {
                    v?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(recent[i]))
                    v?.setOnClickListener { onUpdate("color", recent[i]) }
                }
            }
        }
        refreshColors()

        panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener { anchor ->
            val cur = data.props["color"] ?: "#2196F3"
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

        // --- Trigger Mode Handling ---
        val toggleTrigger =
                panelView.findViewById<
                        com.google.android.material.button.MaterialButtonToggleGroup>(
                        R.id.togglePropTrigger
                )
        val containerRelease = panelView.findViewById<View>(R.id.containerReleaseOnly)
        val containerTimer = panelView.findViewById<View>(R.id.containerTimerMode)

        val curMode = data.props["trigger_mode"] ?: "tap"
        when (curMode) {
            "tap" -> toggleTrigger?.check(R.id.btnTriggerTap)
            "hold" -> toggleTrigger?.check(R.id.btnTriggerHold)
            "timer" -> toggleTrigger?.check(R.id.btnTriggerTimer)
        }

        fun updateModeVisibility(mode: String) {
            containerRelease?.visibility = if (mode == "hold") View.VISIBLE else View.GONE
            containerTimer?.visibility = if (mode == "timer") View.VISIBLE else View.GONE
        }
        updateModeVisibility(curMode)

        toggleTrigger?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode =
                        when (checkedId) {
                            R.id.btnTriggerHold -> "hold"
                            R.id.btnTriggerTimer -> "timer"
                            else -> "tap"
                        }
                onUpdate("trigger_mode", newMode)
                updateModeVisibility(newMode)
            }
        }

        // Release Payload (Standard/Hold Mode)
        val etRelease = panelView.findViewById<EditText>(R.id.etPropPayloadRelease)
        etRelease?.setText(data.props["payload_release"] ?: "OFF")
        etRelease?.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        onUpdate("payload_release", s.toString())
                    }
                    override fun beforeTextChanged(i: CharSequence?, s: Int, d: Int, a: Int) {}
                    override fun onTextChanged(i: CharSequence?, s: Int, b: Int, c: Int) {}
                }
        )

        // Release Payload (Timer Mode Reference)
        val etReleaseRef = panelView.findViewById<EditText>(R.id.etPropPayloadReleaseRef)
        etReleaseRef?.setText(data.props["payload_release"] ?: "OFF")
        etReleaseRef?.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        onUpdate("payload_release", s.toString())
                    }
                    override fun beforeTextChanged(i: CharSequence?, s: Int, d: Int, a: Int) {}
                    override fun onTextChanged(i: CharSequence?, s: Int, b: Int, c: Int) {}
                }
        )

        // Timer Duration
        val etTimer = panelView.findViewById<EditText>(R.id.etPropTimer)
        etTimer?.setText(data.props["timer_ms"] ?: "1000")
        etTimer?.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        onUpdate("timer_ms", s.toString())
                    }
                    override fun beforeTextChanged(i: CharSequence?, s: Int, c: Int, a: Int) {}
                    override fun onTextChanged(i: CharSequence?, s: Int, b: Int, c: Int) {}
                }
        )

        // Appearance Mode (Text/Icon/Both)
        val spApprMode = panelView.findViewById<AutoCompleteTextView>(R.id.spPropApprMode)
        val modeOptions =
                listOf(
                        context.getString(R.string.properties_mode_text),
                        context.getString(R.string.properties_mode_icon),
                        context.getString(R.string.properties_mode_text_icon)
                )
        spApprMode?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, modeOptions))

        // Map mode string to index
        val curApprMode = data.props["appearance_mode"] ?: "text"
        val initialIndex =
                when (curApprMode) {
                    "icon" -> 1
                    "text_icon" -> 2
                    else -> 0
                }
        spApprMode?.setText(modeOptions[initialIndex], false)

        val containerText = panelView.findViewById<View>(R.id.containerPropText)
        val containerIcon = panelView.findViewById<View>(R.id.containerPropIcon)

        fun updateApprVisibility(mode: String) {
            when (mode) {
                "text" -> {
                    containerText?.visibility = View.VISIBLE
                    containerIcon?.visibility = View.GONE
                }
                "icon" -> {
                    containerText?.visibility = View.GONE
                    containerIcon?.visibility = View.VISIBLE
                }
                else -> { // text_icon
                    containerText?.visibility = View.VISIBLE
                    containerIcon?.visibility = View.VISIBLE
                }
            }
        }
        updateApprVisibility(curApprMode)

        spApprMode?.setOnItemClickListener { _, _, position, _ ->
            val newMode =
                    when (position) {
                        1 -> "icon"
                        2 -> "text_icon"
                        else -> "text"
                    }
            onUpdate("appearance_mode", newMode)
            updateApprVisibility(newMode)
        }

        // Icon Grid Logic
        val iconMap =
                mapOf(
                        R.id.iconPreview1 to "power",
                        R.id.iconPreview2 to "lighting",
                        R.id.iconPreview3 to "fan",
                        R.id.iconPreview4 to "play",
                        R.id.iconPreview5 to "tune",
                        R.id.iconPreview6 to "energy"
                )
        iconMap.forEach { (id, key) ->
            panelView.findViewById<View>(id)?.setOnClickListener {
                onUpdate("icon", key)
                Toast.makeText(context, "Selected: $key", Toast.LENGTH_SHORT).show()
                // Simple visual feedback (optional but good)
                // For now just update data
            }
        }
    }

    override fun attachBehavior(
            view: View,
            data: ComponentData,
            sendMqtt: (topic: String, payload: String) -> Unit,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val container = view as? FrameLayout ?: return
        val button = container.findViewWithTag<Button>("target") ?: return

        val mode = data.props["trigger_mode"] ?: "tap"
        val mainPayload = data.props["payload"] ?: "ON"
        val releasePayload = data.props["payload_release"] ?: "OFF"
        val timerMs = (data.props["timer_ms"] ?: "1000").toLongOrNull() ?: 1000L

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    if (mode == "hold" || mode == "timer") {
                        sendMqtt(data.topicConfig, mainPayload)
                    }
                    if (mode == "timer") {
                        v.postDelayed({ sendMqtt(data.topicConfig, releasePayload) }, timerMs)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    if (event.action == MotionEvent.ACTION_UP) {
                        when (mode) {
                            "tap" -> sendMqtt(data.topicConfig, mainPayload)
                            "hold" -> sendMqtt(data.topicConfig, releasePayload)
                        }
                    }
                }
            }
            false
        }
    }

    override fun onMqttMessage(
            view: View,
            data: ComponentData,
            payload: String,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {}

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
