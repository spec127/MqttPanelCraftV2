package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

/**
 * 文字展示元件 (TextDefinition)
 *
 * Design Intent:
 * 多媒體展示文字元件，專注於自訂標題、動態資訊呈現與排版，支援字體調整與文字顏色修改。
 */
object TextDefinition : IComponentDefinition {

    override val type = "TEXT"
    override val defaultSize = Size(180, 80)
    override val labelPrefix = "text"
    override val iconResId = R.drawable.ic_text_fields
    override val group = "DISPLAY"

    override val propertiesLayoutId = R.layout.layout_prop_text

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "default_text" to "Text Display",
        "text_size" to "18",
        "text_align" to "CENTER",
        "text_weight" to "NORMAL",
        "color" to "#FFFFFF",
        "text_effect" to "NONE",
        "show_background" to "false",
        "bg_color" to "#33000000"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val tv = TextView(context).apply {
            tag = "target_text"
            text = "Text Display"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(tv, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val tv = container.findViewWithTag<TextView>("target_text") ?: return

        // 1. Text Content
        data.props["default_text"]?.let {
            if (it.isNotEmpty()) tv.text = it
        }

        // 2. Typography
        data.props["text_size"]?.toFloatOrNull()?.let { size ->
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        }
        
        val align = data.props["text_align"] ?: "CENTER"
        tv.gravity = when (align) {
            "LEFT" -> Gravity.START or Gravity.CENTER_VERTICAL
            "RIGHT" -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.CENTER
        }
        
        val isBold = (data.props["text_weight"] ?: "NORMAL") == "BOLD"
        tv.setTypeface(null, if (isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        // 3. Colors & Effects
        val textColorHex = data.props["color"] ?: "#FFFFFF"
        val textColor = try { Color.parseColor(textColorHex) } catch (e: Exception) { Color.WHITE }
        tv.setTextColor(textColor)
        
        val effect = data.props["text_effect"] ?: "NONE"
        when (effect) {
            "NEON" -> tv.setShadowLayer(15f, 0f, 0f, textColor)
            "SHADOW" -> tv.setShadowLayer(6f, 3f, 3f, Color.parseColor("#80000000"))
            else -> tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }

        // 4. Background
        val showBg = (data.props["show_background"] ?: "false") == "true"
        if (showBg) {
            val bgColorHex = data.props["bg_color"] ?: "#33000000"
            val bgColor = try { Color.parseColor(bgColorHex) } catch (e: Exception) { Color.parseColor("#33000000") }
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 8f * container.resources.displayMetrics.density
            }
            tv.background = drawable
        } else {
            tv.background = null
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context
        val binder = com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder

        // Content
        binder.bindEditText(panelView, R.id.etTextContent, "default_text", data, onUpdate, "Text Display")

        // Typography
        binder.bindDropdown(
            panelView, R.id.spTextSize, "text_size", data, onUpdate,
            listOf("12", "14", "16", "18", "24", "32", "48", "64"),
            defaultValue = "18"
        )
        binder.bindDropdown(
            panelView, R.id.spTextAlign, "text_align", data, onUpdate,
            listOf("靠左", "置中", "靠右"),
            mapOf("靠左" to "LEFT", "置中" to "CENTER", "靠右" to "RIGHT"),
            defaultValue = "CENTER"
        )
        binder.bindToggleGroup(
            panelView, R.id.toggleTextWeight, "text_weight", data, onUpdate,
            mapOf(R.id.btnWeightNormal to "NORMAL", R.id.btnWeightBold to "BOLD")
        )

        // Color & Effects
        binder.bindColorPalette(panelView, R.id.containerTextColorPalette, "color", data, onUpdate, defaultColor = "#FFFFFF")
        
        binder.bindDropdown(
            panelView, R.id.spTextEffect, "text_effect", data, onUpdate,
            listOf("無特效", "霓虹發光", "柔和陰影"),
            mapOf("無特效" to "NONE", "霓虹發光" to "NEON", "柔和陰影" to "SHADOW"),
            defaultValue = "NONE"
        )

        // Background
        var showBg = (data.props["show_background"] ?: "false") == "true"
        val checkShowBg = panelView.findViewById<android.widget.ImageView>(R.id.checkShowBackground)
        val bgPaletteContainer = panelView.findViewById<View>(R.id.containerBgColorPalette)
        
        checkShowBg?.visibility = if (showBg) View.VISIBLE else View.INVISIBLE
        bgPaletteContainer?.visibility = if (showBg) View.VISIBLE else View.GONE
        
        panelView.findViewById<View>(R.id.itemShowBackground)?.setOnClickListener {
            showBg = !showBg
            checkShowBg?.visibility = if (showBg) View.VISIBLE else View.INVISIBLE
            bgPaletteContainer?.visibility = if (showBg) View.VISIBLE else View.GONE
            onUpdate("show_background", showBg.toString())
        }

        binder.bindColorPalette(
            panelView, R.id.containerBgColorPalette, "bg_color", data, onUpdate,
            label = "背景顏色", defaultColor = "#33000000"
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
