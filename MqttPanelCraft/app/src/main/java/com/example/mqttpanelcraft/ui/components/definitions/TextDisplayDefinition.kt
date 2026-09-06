package com.example.mqttpanelcraft.ui.components.definitions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.example.mqttpanelcraft.ProjectViewModel
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.components.prop.PropertyOption
import com.example.mqttpanelcraft.ui.views.TextDisplayView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TextDisplayDefinition : IComponentDefinition {
    override val type: String = "TEXT_DISPLAY"
    override val defaultSize: Size = Size(200, 50)
    override val labelPrefix: String = "receivebox"
    override val displayNameResId: Int = R.string.component_label_text_display
    override val iconResId: Int = android.R.drawable.ic_menu_sort_alphabetically
    override val group = ComponentGroup.SENSOR
    override val propertiesLayoutId: Int = R.layout.layout_prop_text_display

    private fun getActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "theme_color" to "#FF9800",
        "style" to "CAPSULE",
        "display_mode" to "SINGLE",
        "display_lines" to "1",
        "scrollable" to "false",
        "default_text" to "loading ..."
    )

    override fun getDefaultProps(context: Context): Map<String, String> =
        getDefaultProps().toMutableMap().apply {
            put("default_text", context.getString(R.string.default_received_text))
        }

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val tvView = TextDisplayView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(tvView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val tvView = container.getChildAt(0) as? TextDisplayView ?: return

        // Appearance
        val styleStr = data.props["style"] ?: "CAPSULE"
        tvView.currentStyle = try {
            TextDisplayView.Style.valueOf(styleStr)
        } catch (e: Exception) { TextDisplayView.Style.CAPSULE }

        val themeColor = data.props["theme_color"] ?: "#FF9800"
        val parsedColor = try { Color.parseColor(themeColor) } catch (e: Exception) { Color.parseColor("#FF9800") }
        tvView.bgColor = parsedColor
        tvView.themeColor = parsedColor

        val mode = data.props["display_mode"] ?: "SINGLE"
        val isLog = mode == "LOG"
        
        tvView.textView.gravity = if (isLog) Gravity.START or Gravity.TOP else Gravity.START or Gravity.CENTER_VERTICAL

        val fontStr = data.props["font_style"] ?: "NORMAL"
        tvView.fontStyle = fontStr

        // Logic pass to view
        tvView.isLogMode = isLog
        tvView.displayLines = if (isLog) (data.props["display_lines"]?.toIntOrNull() ?: 5) else 1
        tvView.isScrollable = if (isLog) (data.props["scrollable"] != "false") else false
        
        // Display Mode defaults
        if (tvView.textView.text.isEmpty() || tvView.textView.text == "Waiting for data..." || tvView.textView.text == "loading..." || tvView.textView.text == "loading ...") {
            val defText = data.props["default_text"] ?: "loading ..."
            tvView.textView.text = if (!isLog) defText.replace("\n", " ").replace("\r", "") else defText
        }
    }
    
    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val ctx = panelView.context

        // Display Mode
        val toggleDisplayMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleDisplayMode)
        val mode = data.props["display_mode"] ?: "SINGLE"
        toggleDisplayMode.check(if (mode == "SINGLE") R.id.btnModeSingle else R.id.btnModeLog)

        // Lines
        val layoutLines = panelView.findViewById<LinearLayout>(R.id.layoutLines)
        val layoutMaxLines = panelView.findViewById<TextInputLayout>(R.id.layoutMaxLines)
        
        // Scrollable
        val itemScrollable = panelView.findViewById<LinearLayout>(R.id.itemScrollable)
        val checkScrollable = panelView.findViewById<ImageView>(R.id.checkScrollable)
        var isScrollable = data.props["scrollable"] != "false"
        checkScrollable.visibility = if (isScrollable) View.VISIBLE else View.INVISIBLE
        itemScrollable.setOnClickListener {
            isScrollable = !isScrollable
            checkScrollable.visibility = if (isScrollable) View.VISIBLE else View.INVISIBLE
            layoutMaxLines?.visibility = if (isScrollable) View.VISIBLE else View.GONE
            onUpdate("scrollable", isScrollable.toString())
        }

        val etDisplayLines = panelView.findViewById<TextInputEditText>(R.id.etDisplayLines)
        val etMaxLines = panelView.findViewById<TextInputEditText>(R.id.etMaxLines)
        
        etDisplayLines.setText(data.props["display_lines"] ?: "5")
        etDisplayLines.doAfterTextChanged { onUpdate("display_lines", it.toString()) }
        
        etMaxLines.setText(data.props["max_lines"] ?: "50")
        etMaxLines.doAfterTextChanged { onUpdate("max_lines", it.toString()) }

        fun updateVisibility(m: String) {
            layoutLines.visibility = if (m == "LOG") View.VISIBLE else View.GONE
            itemScrollable.visibility = if (m == "LOG") View.VISIBLE else View.GONE
            layoutMaxLines?.visibility = if (m == "LOG" && isScrollable) View.VISIBLE else View.GONE
        }
        updateVisibility(mode)

        toggleDisplayMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = if (checkedId == R.id.btnModeSingle) "SINGLE" else "LOG"
                onUpdate("display_mode", newMode)
                if (newMode == "LOG" && data.props["display_lines"] == null) {
                    onUpdate("display_lines", "5")
                }
                updateVisibility(newMode)
            }
        }

        // Prefix Toggle
        val itemEnablePrefix = panelView.findViewById<LinearLayout>(R.id.itemEnablePrefix)
        val checkEnablePrefix = panelView.findViewById<ImageView>(R.id.checkEnablePrefix)
        val layoutPrefixConfig = panelView.findViewById<LinearLayout>(R.id.layoutPrefixConfig)
        
        var isPrefixEnabled = data.props["enable_prefix"] == "true"
        checkEnablePrefix.visibility = if (isPrefixEnabled) View.VISIBLE else View.INVISIBLE
        layoutPrefixConfig.visibility = if (isPrefixEnabled) View.VISIBLE else View.GONE
        
        itemEnablePrefix.setOnClickListener {
            isPrefixEnabled = !isPrefixEnabled
            checkEnablePrefix.visibility = if (isPrefixEnabled) View.VISIBLE else View.INVISIBLE
            layoutPrefixConfig.visibility = if (isPrefixEnabled) View.VISIBLE else View.GONE
            onUpdate("enable_prefix", isPrefixEnabled.toString())
        }

        // Prefix Type / Preview
        val etPrefixPreview = panelView.findViewById<TextInputEditText>(R.id.etPrefixPreview)
        val currentPType = data.props["prefix_type"] ?: "NAME"

        fun updatePrefixPreview(pType: String) {
            when (pType) {
                "NAME" -> {
                    etPrefixPreview.setText("[${data.label}] ")
                    etPrefixPreview.isEnabled = false
                }
                "TOPIC" -> {
                    etPrefixPreview.setText("[${data.topicConfig.substringAfterLast("/")}] ")
                    etPrefixPreview.isEnabled = false
                }
                "TIME" -> {
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    etPrefixPreview.setText("[$timeStr] ")
                    etPrefixPreview.isEnabled = false
                }
                "CUSTOM" -> {
                    etPrefixPreview.setText(data.props["prefix_custom"] ?: "")
                    etPrefixPreview.isEnabled = true
                }
            }
        }
        updatePrefixPreview(currentPType)
        
        etPrefixPreview.doAfterTextChanged { 
            if (etPrefixPreview.isEnabled) {
                onUpdate("prefix_custom", it.toString())
            }
        }

        CommonPropBinder.bindLocalizedDropdown(
            panelView,
            R.id.spinnerPrefixType,
            "prefix_type",
            data,
            { key, value -> onUpdate(key, value); updatePrefixPreview(value) },
            listOf(
                PropertyOption("NAME", R.string.prop_val_prefix_name),
                PropertyOption("TOPIC", R.string.prop_val_prefix_topic),
                PropertyOption("TIME", R.string.prop_val_prefix_time),
                PropertyOption("CUSTOM", R.string.prop_val_prefix_custom)
            ),
            "NAME"
        )

        // Linked Components
        val activity = getActivity(ctx) as? ViewModelStoreOwner
        val viewModel = activity?.let { ViewModelProvider(it)[ProjectViewModel::class.java] }
        val components = viewModel?.components?.value ?: emptyList()
        
        CommonPropBinder.bindLinkedComponents(
            panelView,
            R.id.containerLinkedComponents,
            data,
            components,
            onUpdate,
            emptyTextResId = R.string.linked_no_other_components
        )

        // Style
        CommonPropBinder.bindLocalizedDropdown(
            panelView,
            R.id.spinnerStyle,
            "style",
            data,
            onUpdate,
            listOf(
                PropertyOption("CAPSULE", R.string.val_style_text_capsule),
                PropertyOption("INFINITY", R.string.val_style_text_infinity),
                PropertyOption("GLASS", R.string.val_style_text_glass),
                PropertyOption("NOTE", R.string.val_style_text_note)
            ),
            "CAPSULE"
        )

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

        // Colors
        val defaultTheme = if (data.props["style"] == "NOTE") "#FFF9E6" else "#FF2196F3"
        CommonPropBinder.bindColorPalette(panelView, R.id.containerBgColor, "theme_color", data, onUpdate, ctx.getString(R.string.prop_label_theme_color), defaultTheme)
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
        updateProp: (key: String, value: String) -> Unit
    ) {
        onLinkedMqttMessage(view, data, payload, data)
    }
    
    fun onLinkedMqttMessage(view: View, data: ComponentData, payload: String, sourceComp: ComponentData) {
        val container = view as? FrameLayout ?: return
        val tvView = container.getChildAt(0) as? TextDisplayView ?: return

        // Build Prefix
        val isPrefixEnabled = data.props["enable_prefix"] == "true"
        var prefixStr = ""
        
        if (isPrefixEnabled) {
            val pType = data.props["prefix_type"] ?: "NAME"
            prefixStr = when (pType) {
                "NAME" -> "[${sourceComp.label}] "
                "TOPIC" -> "[${sourceComp.topicConfig.substringAfterLast("/")}] "
                "TIME" -> {
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    "[$timeStr] "
                }
                "CUSTOM" -> data.props["prefix_custom"] ?: ""
                else -> ""
            }
        }

        val formattedText = "$prefixStr$payload"

        val mode = data.props["display_mode"] ?: "SINGLE"
        if (mode == "SINGLE") {
            tvView.isLogMode = false
            tvView.textView.text = formattedText.replace("\n", " ").replace("\r", "")
            tvView.prefixLength = prefixStr.length // For drawing vertical line later
        } else {
            tvView.isLogMode = true
            val maxLines = data.props["max_lines"]?.toIntOrNull() ?: 50
            tvView.appendText(formattedText, maxLines)
            tvView.prefixLength = prefixStr.length
        }
    }
}
