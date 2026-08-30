package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder


object TextDefinition : IComponentDefinition {
    override val type: String = "TEXT"
    override val defaultSize: Size = Size(100, 60)
    override val labelPrefix: String = "text"
    override val iconResId: Int = R.drawable.ic_text_fields
    override val group: String = "DISPLAY"
    override val propertiesLayoutId: Int = R.layout.layout_prop_text

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "default_text" to "Text",
        "text_size" to "16",
        "text_align" to "CENTER",
        "text_weight" to "NORMAL",
        "text_italic" to "false",
        "font_family" to "sans-serif",
        "color" to "#7B1FA2",
        "bg_color" to "#E1BEE7",
        "show_background" to "false",
        "text_effect" to "NONE"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val tv = TextView(context).apply {
            tag = "target_text"
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        container.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val tv = container.findViewWithTag<TextView>("target_text") ?: return
        val ctx = view.context

        tv.text = data.props["default_text"] ?: "Text"
        
        val sizeStr = data.props["text_size"] ?: "16"
        tv.textSize = sizeStr.toFloatOrNull() ?: 16f
        
        tv.gravity = when (data.props["text_align"]) {
            "LEFT" -> Gravity.START or Gravity.CENTER_VERTICAL
            "RIGHT" -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.CENTER
        }
        
        val isBold = data.props["text_weight"] == "BOLD"
        val isItalic = data.props["text_italic"] == "true"
        val style = when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        
        val fontName = data.props["font_family"] ?: "sans-serif"
        val tf = when (fontName) {
            "sans-serif" -> Typeface.SANS_SERIF
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            "cursive" -> Typeface.create("cursive", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
        tv.setTypeface(tf, style)

        val textColorHex = data.props["color"] ?: "#7B1FA2"
        try {
            tv.setTextColor(Color.parseColor(textColorHex))
        } catch (e: Exception) {
            tv.setTextColor(Color.BLACK)
        }
        
        val showBg = (data.props["show_background"] ?: "false") == "true"
        if (showBg) {
            val bgColorHex = data.props["bg_color"] ?: "#E1BEE7"
            try {
                tv.setBackgroundColor(Color.parseColor(bgColorHex))
            } catch (e: Exception) {
                tv.setBackgroundColor(Color.TRANSPARENT)
            }
        } else {
            tv.setBackgroundColor(Color.TRANSPARENT)
        }
        
        val effect = data.props["text_effect"] ?: "NONE"
        when (effect) {
            "NEON" -> {
                tv.setShadowLayer(10f, 0f, 0f, tv.currentTextColor)
                            }
            "SHADOW" -> {
                tv.setShadowLayer(4f, 2f, 2f, Color.parseColor("#80000000"))
                            }
            "OUTLINE" -> {
                tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                            }
            else -> {
                tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                            }
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val binder = CommonPropBinder
        val ctx = panelView.context

        // Text Align
        val tgTextAlign = panelView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.tgTextAlign)
        when (data.props["text_align"]) {
            "LEFT" -> tgTextAlign?.check(R.id.btnAlignLeft)
            "RIGHT" -> tgTextAlign?.check(R.id.btnAlignRight)
            else -> tgTextAlign?.check(R.id.btnAlignCenter)
        }
        tgTextAlign?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val align = when (checkedId) {
                    R.id.btnAlignLeft -> "LEFT"
                    R.id.btnAlignRight -> "RIGHT"
                    else -> "CENTER"
                }
                onUpdate("text_align", align)
            }
        }

        // Content
        binder.bindEditText(panelView, R.id.etTextContent, "default_text", data, onUpdate)

        // Font Family
        binder.bindDropdown(
            panelView, R.id.spFontFamily, "font_family", data, onUpdate,
            listOf(ctx.getString(R.string.font_sans), ctx.getString(R.string.font_serif), ctx.getString(R.string.font_mono), ctx.getString(R.string.font_cursive)),
            mapOf(ctx.getString(R.string.font_sans) to "sans-serif", ctx.getString(R.string.font_serif) to "serif", ctx.getString(R.string.font_mono) to "monospace", ctx.getString(R.string.font_cursive) to "cursive"),
            defaultValue = "sans-serif"
        )

        // Text Size
        binder.bindEditText(panelView, R.id.etTextSize, "text_size", data, onUpdate)

        // Text Style (Bold / Italic)
        val tgTextStyle = panelView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.tgTextStyle)
        val isBold = data.props["text_weight"] == "BOLD"
        val isItalic = data.props["text_italic"] == "true"
        if (isBold) tgTextStyle?.check(R.id.btnStyleBold)
        if (isItalic) tgTextStyle?.check(R.id.btnStyleItalic)
        tgTextStyle?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (checkedId == R.id.btnStyleBold) {
                onUpdate("text_weight", if (isChecked) "BOLD" else "NORMAL")
            } else if (checkedId == R.id.btnStyleItalic) {
                onUpdate("text_italic", if (isChecked) "true" else "false")
            }
        }

        // Show Background
        val itemShowBackground = panelView.findViewById<View>(R.id.itemShowBackground)
        val vShowBgEnabled = panelView.findViewById<View>(R.id.vShowBgEnabled)
        val containerBgColorRow = panelView.findViewById<View>(R.id.containerBgColorRow)
        
        val updateBgUI = {
            val showBg = data.props["show_background"] == "true"
            vShowBgEnabled?.visibility = if (showBg) View.VISIBLE else View.INVISIBLE
            containerBgColorRow?.visibility = if (showBg) View.VISIBLE else View.GONE
        }
        updateBgUI()
        itemShowBackground?.setOnClickListener {
            val showBg = data.props["show_background"] == "true"
            val newVal = if (showBg) "false" else "true"
            data.props["show_background"] = newVal
            updateBgUI()
            onUpdate("show_background", newVal)
        }

        // Color
        binder.bindColorPalette(panelView, R.id.propTextColor, "color", data, onUpdate, label = ctx.getString(R.string.properties_section_appearance), defaultColor = "#7B1FA2")
        binder.bindColorPalette(panelView, R.id.propBgColor, "bg_color", data, onUpdate, label = ctx.getString(R.string.prop_text_show_bg), defaultColor = "#E1BEE7")

                // Effect
        binder.bindDropdown(
            panelView, R.id.spTextEffect, "text_effect", data, onUpdate,
            listOf("NONE", "NEON", "SHADOW", "OUTLINE"),
            mapOf("NONE" to "NONE", "NEON" to "NEON", "SHADOW" to "SHADOW", "OUTLINE" to "OUTLINE"),
            defaultValue = "NONE"
        )
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
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val container = view as? FrameLayout ?: return
        val tv = container.findViewWithTag<TextView>("target_text") ?: return
        tv.text = payload
    }
}
