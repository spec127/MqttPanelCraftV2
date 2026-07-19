package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.Size
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.views.PanelSliderView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout

object SliderDefinition : IComponentDefinition {

    override val type = "SLIDER"
    override val defaultSize = Size(200, 70)
    override val labelPrefix = "slider"
    override val iconResId = android.R.drawable.ic_menu_preferences
    override val group = "CONTROL"

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "value" to "35",
        "color" to "#2196F3"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        // V17.0: Allow bubble to overflow
        container.clipChildren = false
        container.clipToPadding = false

        val slider =
                com.example.mqttpanelcraft.ui.views.PanelSliderView(context).apply {
                    minValue = 0f
                    maxValue = 10f
                    value = 0f
                    stepSize = 1.0f
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                            )
                }
        container.addView(slider, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        view.tag = data // V15.2: Update tag with latest properties
        val container = view as? FrameLayout ?: return
        val slider = findSliderIn(container) ?: return

        slider.minValue = data.props["min"]?.toFloatOrNull() ?: 0f
        slider.maxValue = data.props["max"]?.toFloatOrNull() ?: 100f
        slider.stepSize = data.props["step"]?.toFloatOrNull() ?: 1.0f

        slider.orientation = data.props["orientation"] ?: "Horizontal"
        slider.sliderStyle = data.props["sliderStyle"] ?: "Classic"
        slider.shape = data.props["shape"] ?: "Circle"
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
        slider.feedback = feedbackStr

        // Advanced props

        data.props["color"]?.let { colorCode ->
            try {
                slider.color = Color.parseColor(colorCode)
            } catch (_: Exception) {}
        }

        slider.value = data.props["value"]?.toFloatOrNull() ?: slider.minValue
    }

    override val propertiesLayoutId = R.layout.layout_prop_slider

    override fun bindPropertiesPanel(
            panelView: View,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {
        val watcher = { key: String ->
            object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    onUpdate(key, s?.toString() ?: "")
                }
                override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                ) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
        }

        // Range
        panelView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMin)
                ?.apply {
                    setText(data.props["min"] ?: "0")
                    addTextChangedListener(watcher("min"))
                }
        panelView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMax)
                ?.apply {
                    setText(data.props["max"] ?: "100")
                    addTextChangedListener(watcher("max"))
                }
        panelView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etStep)
                ?.apply {
                    setText(data.props["step"] ?: "1")
                    addTextChangedListener(watcher("step"))
                }

        // Interaction
        val toggleSendMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleSendMode)
        val tilInterval = panelView.findViewById<TextInputLayout>(R.id.tilInterval)
        val etInterval =
                panelView.findViewById<com.google.android.material.textfield.TextInputEditText>(
                        R.id.etInterval
                )

        val currentMode = data.props["sendMode"] ?: "release"
        toggleSendMode.check(
                if (currentMode == "continuous") R.id.btnModeContinuous else R.id.btnModeRelease
        )
        tilInterval.visibility = if (currentMode == "continuous") View.VISIBLE else View.GONE

        toggleSendMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = if (checkedId == R.id.btnModeContinuous) "continuous" else "release"
                onUpdate("sendMode", mode)
                tilInterval.visibility = if (mode == "continuous") View.VISIBLE else View.GONE
            }
        }

        etInterval?.apply {
            setText(data.props["interval"] ?: "100")
            addTextChangedListener(watcher("interval"))
        }

        // Appearance - Orientation Toggle Button
        val toggleOrientation = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleOrientation)
        val currentOrient = data.props["orientation"] ?: "Horizontal"
        if (toggleOrientation != null) {
            toggleOrientation.check(
                if (currentOrient.equals("Vertical", ignoreCase = true)) R.id.btnOrientationVert else R.id.btnOrientationHoriz
            )
            toggleOrientation.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val newOrientation = if (checkedId == R.id.btnOrientationVert) "Vertical" else "Horizontal"
                    val oldOrientation = data.props["orientation"] ?: "Horizontal"
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

        // Appearance - Combined Style & Shape Selection
        val spStyle = panelView.findViewById<AutoCompleteTextView>(R.id.spPropStyle)
        val context = panelView.context
        val styleOptions = listOf("經典 (Classic)", "膠囊 (Capsule)", "撥桿 (Toggle)")
        val styleKeys = listOf("Classic", "Capsule", "Toggle")
        spStyle?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, styleOptions))

        val currentStyle = data.props["sliderStyle"] ?: "Classic"
        val currentShape = data.props["shape"] ?: "Circle"
        val initialKey = when {
            currentStyle == "Capsule" && currentShape == "Square" -> "Toggle"
            currentStyle == "Capsule" -> "Capsule"
            else -> "Classic"
        }
        val styleIndex = styleKeys.indexOf(initialKey).coerceAtLeast(0)
        spStyle?.setText(styleOptions[styleIndex], false)

        spStyle?.setOnItemClickListener { _, _, position, _ ->
            when (styleKeys[position]) {
                "Classic" -> {
                    onUpdate("sliderStyle", "Classic")
                    onUpdate("shape", "Circle")
                }
                "Capsule" -> {
                    onUpdate("sliderStyle", "Capsule")
                    onUpdate("shape", "Circle")
                }
                "Toggle" -> {
                    onUpdate("sliderStyle", "Capsule")
                    onUpdate("shape", "Square")
                }
            }
        }

        // Feedback Checkboxes (Ticks & Bubble) - Premium row design
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

        val checkShowTicks = panelView.findViewById<android.widget.ImageView>(R.id.checkShowTicks)
        val checkShowBubble = panelView.findViewById<android.widget.ImageView>(R.id.checkShowBubble)

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

        // Color Palette Logic
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
    }

    // V8: Throttling state is now managed per-instance inside attachBehavior

    override fun attachBehavior(
            view: View,
            data: ComponentData,
            sendMqtt: (topic: String, payload: String) -> Unit,
            onUpdateProp: (String, String) -> Unit
    ) {
        val container = view as? FrameLayout ?: return
        val slider = findSliderIn(container) ?: return

        // REMOVED: Local variables caused reset on re-attach
        // var lastSendTime = 0L
        // var isFirstMoveSinceActionUp = true

        // V17.3: Use onActionDown to start continuous sending loop
        slider.onActionDown = {
            val currentData = (view.tag as? ComponentData) ?: data
            val sendMode = currentData.props["sendMode"] ?: "release"

            if (sendMode == "continuous") {
                // Min interval 50ms as requested
                val interval =
                        (currentData.props["interval"]?.toLongOrNull() ?: 200L).coerceAtLeast(50L)

                android.util.Log.d("SliderMode", "🔄 START Loop: interval=$interval ms")

                // Start loop: Sends immediately, then every interval
                slider.startRepeatingTask(interval) {
                    val topic = currentData.topicConfig
                    val valToSend = slider.value.toInt().toString()
                    if (topic.isNotEmpty()) {
                        android.util.Log.d("SliderMode", "✅ SEND: Loop ($valToSend)")
                        sendMqtt(topic, valToSend)
                    }
                }
            }
        }

        slider.onValueChange = { value ->
            // Just update model, NO MQTT here for continuous mode
            onUpdateProp("value", value.toString())
        }

        slider.onActionUp = {
            val currentData = (view.tag as? ComponentData) ?: data
            val currentValue = slider.value

            // Stop continuous loop
            slider.stopRepeatingTask()
            android.util.Log.d("SliderMode", "🛑 STOP Loop")

            // Always update model on release
            onUpdateProp("value", currentValue.toString())

            // Always send final value on action up (ensures last value is sent)
            val topic = currentData.topicConfig
            if (topic.isNotEmpty()) {
                android.util.Log.d("SliderMode", "✅ SEND: Final/Release ($currentValue)")
                sendMqtt(topic, currentValue.toInt().toString())
            } else {
                android.util.Log.w("SliderMode", "⚠️ Release but topic is empty")
            }
        }
    }

    override fun onMqttMessage(
            view: View,
            data: ComponentData,
            payload: String,
            onUpdateProp: (String, String) -> Unit
    ) {
        val container = view as? FrameLayout ?: return
        val slider = findSliderIn(container) ?: return
        try {
            val v = payload.toFloat()
            if (v in slider.minValue..slider.maxValue) {
                slider.value = v
                onUpdateProp("value", v.toString()) // Persist MQTT state
            }
        } catch (_: Exception) {}
    }

    private fun findSliderIn(container: FrameLayout): PanelSliderView? {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is PanelSliderView) return child
        }
        return null
    }
}
