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
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.components.prop.PropertyOption
import com.example.mqttpanelcraft.ui.views.InputBoxView

object InputBoxDefinition : IComponentDefinition {

    override val type = "INPUTBOX"
    override val defaultSize = Size(200, 50)
    override val labelPrefix = "sendbox"
    override val displayNameResId: Int = R.string.component_label_inputbox
    override val iconResId = R.drawable.ic_edit // Generic edit icon
    override val group = ComponentGroup.CONTROL

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
        val styles =
                listOf(
                        PropertyOption("Capsule", R.string.val_style_text_capsule),
                        PropertyOption("Infinity", R.string.val_style_text_infinity),
                        PropertyOption("Glass", R.string.val_style_text_glass),
                        PropertyOption("Note", R.string.val_style_text_note)
                )
        CommonPropBinder.bindLocalizedDropdown(
                panelView,
                R.id.spPropStyle,
                "style",
                data,
                onUpdate,
                styles,
                "Capsule"
        )

        // Font Toggle (Standard / Handwriting)
        CommonPropBinder.bindToggleGroup(
                panelView,
                R.id.toggleFont,
                "font_style",
                data,
                onUpdate,
                mapOf(R.id.btnFontNormal to "NORMAL", R.id.btnFontHandwriting to "HANDWRITING")
        )

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
        CommonPropBinder.bindCheckCard(
                panelView,
                R.id.itemClearOnSend,
                R.id.checkClearOnSend,
                "clear_on_send",
                data,
                onUpdate,
                true
        )

        // 4. Toggles (Enter as Send)
        CommonPropBinder.bindCheckCard(
                panelView,
                R.id.itemEnterAsSend,
                R.id.checkEnterAsSend,
                "enter_as_send",
                data,
                onUpdate,
                false
        )
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
