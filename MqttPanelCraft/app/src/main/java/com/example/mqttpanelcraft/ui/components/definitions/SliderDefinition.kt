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
        slider.feedback = data.props["feedback"] ?: "None"

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

        // Appearance - Style Selection
        val spStyle = panelView.findViewById<AutoCompleteTextView>(R.id.spPropStyle)

        val context = panelView.context
        val styleOptions =
                listOf(
                        context.getString(R.string.val_slider_style_classic),
                        context.getString(R.string.val_slider_style_capsule)
                )
        val styleKeys = listOf("Classic", "Capsule")
        spStyle?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, styleOptions))

        val currentStyle = data.props["sliderStyle"] ?: "Classic"
        val styleIndex = styleKeys.indexOf(currentStyle).coerceAtLeast(0)
        spStyle?.setText(styleOptions[styleIndex], false)

        spStyle?.setOnItemClickListener { _, _, position, _ ->
            val newStyle = styleKeys[position]
            onUpdate("sliderStyle", newStyle)
        }

        // Orientation
        val spOrient = panelView.findViewById<AutoCompleteTextView>(R.id.spOrientation)
        val orientKeys = listOf("Horizontal", "Vertical")
        val orientOptions =
                listOf(
                        context.getString(R.string.val_orient_horizontal),
                        context.getString(R.string.val_orient_vertical)
                )

        spOrient?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, orientOptions))
        val currentOrient = data.props["orientation"] ?: "Horizontal"
        val orientIndex = orientKeys.indexOf(currentOrient).coerceAtLeast(0)
        spOrient?.setText(orientOptions[orientIndex], false)

        spOrient?.setOnItemClickListener { _, _, position, _ ->
            val newOrientation = orientKeys[position]
            val oldOrientation = data.props["orientation"] ?: "Horizontal"
            if (newOrientation != oldOrientation) {
                // Swap W and H to reflect rotation in the editor
                val oldW = data.width
                val oldH = data.height
                onUpdate("w", oldH.toString())
                onUpdate("h", oldW.toString())
            }
            onUpdate("orientation", newOrientation)
        }

        val spShape = panelView.findViewById<AutoCompleteTextView>(R.id.spPropShape)
        val shapeOptions =
                listOf(
                        context.getString(R.string.val_shape_square),
                        context.getString(R.string.val_shape_circle)
                )
        val shapeKeys = listOf("Square", "Circle")
        spShape?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, shapeOptions))
        val currentShape = data.props["shape"] ?: "Circle"
        val shapeIndex = shapeKeys.indexOf(currentShape).coerceAtLeast(0)
        spShape?.setText(shapeOptions[shapeIndex], false)
        spShape?.setOnItemClickListener { _, _, position, _ ->
            onUpdate("shape", shapeKeys[position])
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

        // --- Bottom Row: Hint & Color ---
        val currentFeedback = data.props["feedback"] ?: "None"
        val feedbackDropdown = panelView.findViewById<AutoCompleteTextView>(R.id.spFeedback)
        val feedbackKeys = listOf("None", "Ticks", "Bubble", "Both")
        val feedbackOptions =
                listOf(
                        context.getString(R.string.val_feedback_none),
                        context.getString(R.string.val_feedback_ticks),
                        context.getString(R.string.val_feedback_bubble),
                        context.getString(R.string.val_feedback_both)
                )

        feedbackDropdown?.setAdapter(
                ArrayAdapter(context, R.layout.list_item_dropdown, feedbackOptions)
        )
        val fbIndex = feedbackKeys.indexOf(currentFeedback).coerceAtLeast(0)
        feedbackDropdown?.setText(feedbackOptions[fbIndex], false)

        feedbackDropdown?.setOnItemClickListener { _, _, position, _ ->
            onUpdate("feedback", feedbackKeys[position])
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
            val finalVal = v.coerceIn(slider.minValue, slider.maxValue)
            slider.value = finalVal
            onUpdateProp("value", finalVal.toString()) // Persist MQTT state
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
