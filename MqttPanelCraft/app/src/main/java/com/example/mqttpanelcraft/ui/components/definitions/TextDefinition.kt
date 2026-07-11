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
    override val labelPrefix = "txt"
    override val iconResId = android.R.drawable.ic_menu_sort_by_size
    override val group = "DISPLAY"

    override val propertiesLayoutId = R.layout.layout_prop_generic_color

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "color" to "#FFFFFF",
        "theme_color" to "#FFFFFF",
        "text_size" to "18",
        "default_text" to "Text Display"
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

        data.props["default_text"]?.let {
            if (it.isNotEmpty()) tv.text = it
        }

        data.props["color"]?.let { colorCode ->
            try {
                tv.setTextColor(Color.parseColor(colorCode))
            } catch (_: Exception) {}
        }

        data.props["text_size"]?.toFloatOrNull()?.let { size ->
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context
        val colorViews = listOf(R.id.vColor1, R.id.vColor2, R.id.vColor3, R.id.vColor4, R.id.vColor5).map {
            panelView.findViewById<View>(it)
        }
        fun refreshColors() {
            val recent = ColorHistoryManager.load(context)
            colorViews.forEachIndexed { i, v ->
                if (i < recent.size) {
                    val c = Color.parseColor(recent[i])
                    v?.backgroundTintList = android.content.res.ColorStateList.valueOf(c)
                    v?.setOnClickListener { onUpdate("color", recent[i]) }
                }
            }
        }
        refreshColors()

        panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener { anchor ->
            val cur = data.props["color"] ?: "#FFFFFF"
            var temp = cur
            ColorPickerDialog(
                context,
                cur,
                true,
                {
                    temp = it
                    onUpdate("color", it)
                },
                {
                    if (temp != cur) {
                        ColorHistoryManager.save(context, temp)
                        refreshColors()
                    }
                }
            ).show(anchor)
        }
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
