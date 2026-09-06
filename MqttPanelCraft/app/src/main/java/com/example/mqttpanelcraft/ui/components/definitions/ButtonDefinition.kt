package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import androidx.core.widget.TextViewCompat
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.components.prop.PropertyOption

object ButtonDefinition : IComponentDefinition {

    override val type = "BUTTON"
    override val defaultSize = Size(120, 70)
    override val labelPrefix = "button"
    override val displayNameResId: Int = R.string.component_label_button
    override val iconResId = R.drawable.ic_btn_power
    override val group = ComponentGroup.CONTROL

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "color" to "#2196F3",
        "colorOn" to "#2196F3",
        "colorOff" to "#BDBDBD"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
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
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    tag = "target"
                    layoutParams =
                            FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                    )
                                    .apply {
                                        gravity = Gravity.CENTER
                                        setMargins(dp(4), dp(4), dp(4), dp(4))
                                    }
                }
        container.addView(button, 0)

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

        // Component dimensions are already pixels; never multiply them by density again.
        val depth = data.height.toFloat() * 0.08f
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

        val density = view.resources.displayMetrics.density
        val apprMode = data.props["appearance_mode"] ?: "text"
        val iconKey = data.props["icon"] ?: "power"
        val iconRes = getIconRes(iconKey)

        fun applyContent() {
            button.text = ""
            button.setCompoundDrawables(null, null, null, null)
            iconView.visibility = View.GONE
            val fallbackHeight =
                    (data.height - 8 * density).toInt().coerceAtLeast((12 * density).toInt())
            val availableHeight =
                    ((if (button.height > 0) button.height else fallbackHeight) -
                                    button.paddingTop - button.paddingBottom)
                            .coerceAtLeast((8 * density).toInt())

            when (apprMode) {
                "icon" -> {
                    iconView.visibility = View.VISIBLE
                    iconView.setImageResource(iconRes)
                    iconView.imageTintList = ColorStateList.valueOf(contentColor)
                    val size = (availableHeight * 0.7f).toInt().coerceAtLeast((8 * density).toInt())
                    iconView.layoutParams =
                            (iconView.layoutParams as FrameLayout.LayoutParams).apply {
                                width = size
                                height = size
                                gravity = Gravity.CENTER
                            }
                }
                "text_icon" -> {
                    button.text = data.props["text"] ?: "button1"
                    val maxTextPx = (availableHeight * 0.27f).coerceAtLeast(6 * density)
                    val maxTextSp = (maxTextPx / density).toInt().coerceAtLeast(6)
                    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            button,
                            6,
                            maxTextSp,
                            1,
                            TypedValue.COMPLEX_UNIT_SP
                    )
                    button.setTextSize(TypedValue.COMPLEX_UNIT_PX, maxTextPx)
                    val fontHeight = button.paint.fontMetrics.run { bottom - top }
                    val gap = (2 * density).toInt()
                    val iconSize =
                            (availableHeight - fontHeight - gap)
                                    .toInt()
                                    .coerceAtLeast((6 * density).toInt())
                    val drawable = ContextCompat.getDrawable(view.context, iconRes)?.mutate()
                    drawable?.setBounds(0, 0, iconSize, iconSize)
                    drawable?.setTint(contentColor)
                    button.setCompoundDrawables(null, drawable, null, null)
                    button.compoundDrawablePadding = gap
                }
                else -> {
                    button.text = data.props["text"] ?: "button1"
                    val maxTextSp =
                            ((availableHeight * 0.5f) / density).toInt().coerceIn(6, 60)
                    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            button,
                            6,
                            maxTextSp,
                            1,
                            TypedValue.COMPLEX_UNIT_SP
                    )
                }
            }
        }
        applyContent()
        button.doOnLayout { applyContent() }
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

        CommonPropBinder.bindEditText(
                panelView,
                R.id.etPropText,
                "text",
                data,
                onUpdate,
                "button1"
        )

        val shapeOptions =
                listOf(
                        PropertyOption("pill", R.string.val_shape_rounded_rect),
                        PropertyOption("rect", R.string.val_shape_square),
                        PropertyOption("circle", R.string.val_shape_circle_style)
                )
        CommonPropBinder.bindLocalizedDropdown(
                panelView,
                R.id.spPropShape,
                "shape",
                data,
                onUpdate,
                shapeOptions,
                "pill"
        )

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

        // Release Payload (Standard/Hold Mode) - NOW WITH DROPDOWN
        val etRelease = panelView.findViewById<AutoCompleteTextView>(R.id.etPropPayloadRelease)
        etRelease?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, payloadOptions))
        etRelease?.setText(data.props["payload_release"] ?: "OFF", false)

        etRelease?.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        onUpdate("payload_release", s.toString())
                    }
                    override fun beforeTextChanged(i: CharSequence?, s: Int, d: Int, a: Int) {}
                    override fun onTextChanged(i: CharSequence?, s: Int, b: Int, c: Int) {}
                }
        )
        etRelease?.setOnItemClickListener { _, _, _, _ ->
            onUpdate("payload_release", etRelease.text.toString())
        }

        // Release Payload (Timer Mode Reference) - NOW WITH DROPDOWN
        val etReleaseRef =
                panelView.findViewById<AutoCompleteTextView>(R.id.etPropPayloadReleaseRef)
        etReleaseRef?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, payloadOptions))
        etReleaseRef?.setText(data.props["payload_release"] ?: "OFF", false)

        etReleaseRef?.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        onUpdate("payload_release", s.toString())
                    }
                    override fun beforeTextChanged(i: CharSequence?, s: Int, d: Int, a: Int) {}
                    override fun onTextChanged(i: CharSequence?, s: Int, b: Int, c: Int) {}
                }
        )
        etReleaseRef?.setOnItemClickListener { _, _, _, _ ->
            onUpdate("payload_release", etReleaseRef.text.toString())
        }

        // Timer Duration
        CommonPropBinder.bindEditText(
                panelView,
                R.id.etPropTimer,
                "timer_ms",
                data,
                onUpdate,
                "1000"
        )

        // Appearance Mode (Text/Icon/Both)
        val modeOptions =
                listOf(
                        PropertyOption("text", R.string.properties_mode_text),
                        PropertyOption("icon", R.string.properties_mode_icon),
                        PropertyOption("text_icon", R.string.properties_mode_text_icon)
                )
        val curApprMode = data.props["appearance_mode"] ?: "text"

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

        CommonPropBinder.bindLocalizedDropdown(
                panelView,
                R.id.spPropApprMode,
                "appearance_mode",
                data,
                { key, value ->
                    onUpdate(key, value)
                    updateApprVisibility(value)
                },
                modeOptions,
                "text"
        )

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
                // Toast removed per user request
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

        var isTimerActive = false

        button.setOnTouchListener { v, event ->
            if (isTimerActive && mode == "timer") return@setOnTouchListener true

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    if (mode == "hold" || mode == "timer") {
                        sendMqtt(data.topicConfig, mainPayload)
                    }
                    if (mode == "timer") {
                        isTimerActive = true
                        v.postDelayed(
                                {
                                    sendMqtt(data.topicConfig, releasePayload)
                                    v.animate()
                                            .scaleX(1.0f)
                                            .scaleY(1.0f)
                                            .setDuration(100)
                                            .withEndAction {
                                                isTimerActive = false
                                                v.isPressed = false
                                            }
                                            .start()
                                },
                                timerMs
                        )
                    }
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mode != "timer") {
                        v.isPressed = false
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    }
                    if (event.action == MotionEvent.ACTION_UP) {
                        when (mode) {
                            "tap" -> sendMqtt(data.topicConfig, mainPayload)
                        }
                    }
                    return@setOnTouchListener true
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
