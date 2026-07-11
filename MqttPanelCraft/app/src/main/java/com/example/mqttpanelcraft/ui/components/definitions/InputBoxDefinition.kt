package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.util.Size
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.InputBoxView

object InputBoxDefinition : IComponentDefinition {

    override val type = "INPUTBOX"
    override val defaultSize = Size(200, 50)
    override val labelPrefix = "sendbox"
    override val iconResId = R.drawable.ic_edit // Generic edit icon
    override val group = "CONTROL"

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "color" to "#FF2196F3",
        "style" to "Capsule",
        "font_style" to "NORMAL"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val inputView =
                InputBoxView(context).apply {
                    tag = "target"
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                            )
                }
        container.addView(inputView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val inputView = view.findViewWithTag<InputBoxView>("target") ?: return

        inputView.style = data.props["style"] ?: "Capsule"
        inputView.fontStyle = data.props["font_style"] ?: "NORMAL"

        val colorHex = data.props["color"] ?: "#2196F3"
        try {
            inputView.themeColor = Color.parseColor(colorHex)
        } catch (e: Exception) {
            inputView.themeColor = Color.parseColor("#2196F3")
        }

        inputView.clearOnSend = (data.props["clear_on_send"] ?: "true") == "true"
        inputView.enterAsSend = (data.props["enter_as_send"] ?: "false") == "true"
    }

    override val propertiesLayoutId = R.layout.layout_prop_input_box

    override fun bindPropertiesPanel(
            panelView: View,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // 1. Style Selector
        val spStyle = panelView.findViewById<AutoCompleteTextView>(R.id.spPropStyle)
        val styles =
                listOf(
                        context.getString(R.string.val_style_text_capsule) to "Capsule",
                        context.getString(R.string.val_style_text_infinity) to "Infinity",
                        context.getString(R.string.val_style_text_glass) to "Glass",
                        context.getString(R.string.val_style_text_note) to "Note"
                )
        // Map internal value to display string
        val currentStyle = data.props["style"] ?: "Capsule"
        val displayStyle = styles.find { it.second == currentStyle }?.first ?: styles[0].first

        val adapter =
                android.widget.ArrayAdapter(
                        context,
                        R.layout.list_item_dropdown,
                        styles.map { it.first }
                )
        spStyle.setAdapter(adapter)
        spStyle.setText(displayStyle, false)

        spStyle.setOnItemClickListener { _, _, position, _ ->
            onUpdate("style", styles[position].second)
        }

        // Font Toggle (Standard / Handwriting)
        panelView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleFont)?.let { toggleFont ->
            val fontStyle = data.props["font_style"] ?: "NORMAL"
            val checkedId = if (fontStyle == "HANDWRITING") R.id.btnFontHandwriting else R.id.btnFontNormal
            toggleFont.check(checkedId)
            toggleFont.addOnButtonCheckedListener { _, id, isChecked ->
                if (isChecked) {
                    val valStr = if (id == R.id.btnFontHandwriting) "HANDWRITING" else "NORMAL"
                    onUpdate("font_style", valStr)
                }
            }
        }

        // 2. Color Picker (Full Palette)
        CommonPropBinder.bindColorPalette(
            panelView,
            R.id.containerColorPalette,
            "color",
            data,
            onUpdate,
            context.getString(R.string.prop_label_theme_color),
            "#FF2196F3"
        )

        // 3. Toggles (Clear on Send)
        val itemClear = panelView.findViewById<LinearLayout>(R.id.itemClearOnSend)
        val checkClear = panelView.findViewById<ImageView>(R.id.checkClearOnSend)
        var isClear = (data.props["clear_on_send"] ?: "true") == "true"
        updateCheck(checkClear, isClear)

        itemClear.setOnClickListener {
            isClear = !isClear
            onUpdate("clear_on_send", isClear.toString())
            updateCheck(checkClear, isClear)
        }

        // 4. Toggles (Enter as Send)
        val itemEnter = panelView.findViewById<LinearLayout>(R.id.itemEnterAsSend)
        val checkEnter = panelView.findViewById<ImageView>(R.id.checkEnterAsSend)
        var isEnter = (data.props["enter_as_send"] ?: "false") == "true"
        updateCheck(checkEnter, isEnter)

        itemEnter.setOnClickListener {
            isEnter = !isEnter
            onUpdate("enter_as_send", isEnter.toString())
            updateCheck(checkEnter, isEnter)
        }
    }

    private fun updateCheck(view: ImageView, isChecked: Boolean) {
        view.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE
    }

    override fun attachBehavior(
            view: View,
            data: ComponentData,
            sendMqtt: (topic: String, payload: String) -> Unit,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val inputView = view.findViewWithTag<InputBoxView>("target") ?: return

        inputView.onSend = { text ->
            if (data.topicConfig.isNotEmpty()) {
                sendMqtt(data.topicConfig, text)
            }
        }
    }

    override fun onMqttMessage(
            view: View,
            data: ComponentData,
            payload: String,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // Input Box generally sends, but could potentially receive updates to its text?
        // For now, let's keep it strictly as an input control.
        // If we wanted it to be bi-directional, we'd update the EditText here.
    }
}
