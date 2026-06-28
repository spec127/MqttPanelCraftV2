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
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.TextDisplayView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TextDisplayDefinition : IComponentDefinition {
    override val type: String = "TEXT_DISPLAY"
    override val defaultSize: Size = Size(200, 60)
    override val labelPrefix: String = "receivebox"
    override val iconResId: Int = android.R.drawable.ic_menu_sort_alphabetically
    override val group: String = "SENSOR"
    override val propertiesLayoutId: Int = R.layout.layout_prop_text_display

    private fun getActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
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
        val styleStr = data.props["style"] ?: "GLASS"
        tvView.currentStyle = try {
            TextDisplayView.Style.valueOf(styleStr)
        } catch (e: Exception) { TextDisplayView.Style.GLASS }

        val themeColor = data.props["theme_color"] ?: "#6681D4FA"
        try { tvView.bgColor = Color.parseColor(themeColor) } catch (e: Exception) {}

        val textColor = data.props["text_color"] ?: "#FFFF9800"
        try { tvView.themeColor = Color.parseColor(textColor) } catch (e: Exception) {}

        val mode = data.props["display_mode"] ?: "SINGLE"
        val isLog = mode == "LOG"
        
        val alignStr = data.props["text_align"] ?: "LEFT"
        tvView.textView.gravity = when (alignStr) {
            "CENTER" -> if (isLog) Gravity.CENTER_HORIZONTAL or Gravity.TOP else Gravity.CENTER
            "RIGHT" -> if (isLog) Gravity.END or Gravity.TOP else Gravity.END or Gravity.CENTER_VERTICAL
            else -> if (isLog) Gravity.START or Gravity.TOP else Gravity.START or Gravity.CENTER_VERTICAL
        }

        val fontStr = data.props["font_style"] ?: "NORMAL"
        tvView.fontStyle = fontStr

        // Logic pass to view
        tvView.isLogMode = isLog
        tvView.displayLines = data.props["display_lines"]?.toIntOrNull() ?: 5
        tvView.isScrollable = data.props["scrollable"] == "true"
        
        // Display Mode defaults
        if (tvView.textView.text.isEmpty() && mode != "LOG") {
            tvView.textView.text = data.props["default_text"] ?: "Waiting for data..."
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
        var isScrollable = data.props["scrollable"] == "true"
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
        val spinnerPrefixType = panelView.findViewById<AutoCompleteTextView>(R.id.spinnerPrefixType)
        val etPrefixPreview = panelView.findViewById<TextInputEditText>(R.id.etPrefixPreview)
        
        val prefixTypes = arrayOf(
            ctx.getString(R.string.prop_val_prefix_name),
            ctx.getString(R.string.prop_val_prefix_topic),
            ctx.getString(R.string.prop_val_prefix_time),
            ctx.getString(R.string.prop_val_prefix_custom)
        )
        val pAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, prefixTypes)
        spinnerPrefixType.setAdapter(pAdapter)
        
        val pTypeMap = mapOf(
            "NAME" to prefixTypes[0], "TOPIC" to prefixTypes[1], 
            "TIME" to prefixTypes[2], "CUSTOM" to prefixTypes[3]
        )
        val pTypeRevMap = pTypeMap.entries.associate { (k, v) -> v to k }
        val currentPType = data.props["prefix_type"] ?: "NAME"
        spinnerPrefixType.setText(pTypeMap[currentPType], false)

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

        spinnerPrefixType.setOnItemClickListener { _, _, position, _ ->
            val pKey = pTypeRevMap[prefixTypes[position]] ?: "NAME"
            onUpdate("prefix_type", pKey)
            updatePrefixPreview(pKey)
        }

        // Linked Components
        val containerLinked = panelView.findViewById<LinearLayout>(R.id.containerLinkedComponents)
        containerLinked.removeAllViews()
        val linkedString = data.props["linked_components"] ?: ""
        val linkedSet = linkedString.split(",").filter { it.isNotEmpty() }.toMutableSet()
        
        val activity = getActivity(ctx) as? ViewModelStoreOwner
        val viewModel = activity?.let { ViewModelProvider(it)[ProjectViewModel::class.java] }
        val components = viewModel?.components?.value ?: emptyList()
        
        // Add self as first item (checked, but intercept click instead of disabled to keep it blue, alpha = 0.5f)
        val ownCb = CheckBox(ctx).apply {
            text = "${data.label} (${data.topicConfig})"
            isChecked = true
            alpha = 0.5f
            // Intercept click to prevent unchecking, keeps the visual blue color
            setOnCheckedChangeListener { buttonView, isChecked ->
                if (!isChecked) {
                    buttonView.isChecked = true
                }
            }
        }
        containerLinked.addView(ownCb)
        
        components.filter { it.id != data.id }.forEach { comp ->
            val cb = CheckBox(ctx).apply {
                text = "${comp.label} (${comp.topicConfig})"
                isChecked = linkedSet.contains(comp.id.toString())
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) linkedSet.add(comp.id.toString()) else linkedSet.remove(comp.id.toString())
                    onUpdate("linked_components", linkedSet.joinToString(","))
                }
            }
            containerLinked.addView(cb)
        }
        if (containerLinked.childCount == 0) {
            containerLinked.addView(TextView(ctx).apply { 
                text = "No other components found." 
                textSize = 12f
            })
        }

        // Text Align (now a Toggle Group)
        val toggleTextAlign = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleTextAlign)
        val align = data.props["text_align"] ?: "LEFT"
        toggleTextAlign.check(when(align) {
            "CENTER" -> R.id.btnAlignCenter
            "RIGHT" -> R.id.btnAlignRight
            else -> R.id.btnAlignLeft
        })
        toggleTextAlign.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newAlign = when (checkedId) {
                    R.id.btnAlignCenter -> "CENTER"
                    R.id.btnAlignRight -> "RIGHT"
                    else -> "LEFT"
                }
                onUpdate("text_align", newAlign)
            }
        }

        // Style
        val spinnerStyle = panelView.findViewById<AutoCompleteTextView>(R.id.spinnerStyle)
        val styles = arrayOf(
            ctx.getString(R.string.prop_val_style_glass),
            ctx.getString(R.string.prop_val_style_note)
        )
        val sAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, styles)
        spinnerStyle.setAdapter(sAdapter)
        
        val sTypeMap = mapOf("GLASS" to styles[0], "NOTE" to styles[1])
        val sTypeRevMap = sTypeMap.entries.associate { (k, v) -> v to k }
        spinnerStyle.setText(sTypeMap[data.props["style"] ?: "GLASS"], false)
        spinnerStyle.setOnItemClickListener { _, _, position, _ ->
            onUpdate("style", sTypeRevMap[styles[position]] ?: "GLASS")
        }

        // Font
        val spinnerFont = panelView.findViewById<AutoCompleteTextView>(R.id.spinnerFont)
        val fonts = arrayOf(
            ctx.getString(R.string.prop_val_font_normal),
            ctx.getString(R.string.prop_val_font_handwriting)
        )
        val fAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, fonts)
        spinnerFont.setAdapter(fAdapter)
        
        val fTypeMap = mapOf("NORMAL" to fonts[0], "HANDWRITING" to fonts[1])
        val fTypeRevMap = fTypeMap.entries.associate { (k, v) -> v to k }
        spinnerFont.setText(fTypeMap[data.props["font_style"] ?: "NORMAL"], false)
        spinnerFont.setOnItemClickListener { _, _, position, _ ->
            onUpdate("font_style", fTypeRevMap[fonts[position]] ?: "NORMAL")
        }

        // Colors
        val defaultTheme = if (data.props["style"] == "NOTE") "#FFF9E6" else "#6681D4FA"
        val defaultText = if (data.props["style"] == "NOTE") "#333333" else "#FFFF9800"
        CommonPropBinder.bindColorPalette(panelView, R.id.containerBgColor, "theme_color", data, onUpdate, ctx.getString(R.string.prop_label_theme_color), defaultTheme)
        CommonPropBinder.bindColorPalette(panelView, R.id.containerTextColor, "text_color", data, onUpdate, ctx.getString(R.string.prop_label_text_color), defaultText)
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
            tvView.textView.text = formattedText
            tvView.prefixLength = prefixStr.length // For drawing vertical line later
        } else {
            val maxLines = data.props["max_lines"]?.toIntOrNull() ?: 50
            tvView.appendText(formattedText, maxLines)
            // Log mode may not easily support vertical prefix line per-row if it's just one big string, 
            // but we can pass the prefix length to View to handle if needed.
            tvView.prefixLength = prefixStr.length
        }
    }
}
