package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.util.Size
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

object TextDefinition : IComponentDefinition {

    override val type = "TEXT"
    override val defaultSize = Size(160, 100)
    override val labelPrefix = "txt"
    override val iconResId = android.R.drawable.ic_menu_sort_by_size // 'Abc' equivalent
    override val group = "DISPLAY"

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val tv =
                TextView(context).apply {
                    text = "Text"
                    gravity = Gravity.CENTER
                    layoutParams =
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                            )
                }
        container.addView(tv, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val tv = container.getChildAt(0) as? TextView ?: return

        data.props["color"]?.let { colorCode ->
            try {
                val color = android.graphics.Color.parseColor(colorCode)
                tv.setTextColor(color) // Text Color instead of Background
            } catch (_: Exception) {}
        }
    }

    override val propertiesLayoutId = R.layout.layout_prop_generic_color

    override fun bindPropertiesPanel(
            panelView: View,
            data: ComponentData,
            onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context
        val colorViews =
                listOf(R.id.vColor1, R.id.vColor2, R.id.vColor3, R.id.vColor4, R.id.vColor5).map {
                    panelView.findViewById<View>(it)
                }
        fun refreshColors() {
            val recent = ColorHistoryManager.load(context)
            colorViews.forEachIndexed { i, v ->
                if (i < recent.size) {
                    val c = android.graphics.Color.parseColor(recent[i])
                    v?.backgroundTintList = android.content.res.ColorStateList.valueOf(c)
                    v?.setOnClickListener { onUpdate("color", recent[i]) }
                }
            }
        }
        refreshColors()

        panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener { anchor ->
            val cur = data.props["color"] ?: "#2196F3"
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
                    )
                    .show(anchor)
        }
    }

    override fun attachBehavior(
            view: View,
            data: ComponentData,
            sendMqtt: (topic: String, payload: String) -> Unit,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // Text usually just displays MQTT payload
    }

    override fun onMqttMessage(
            view: View,
            data: ComponentData,
            payload: String,
            onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val container = view as? FrameLayout ?: return
        val tv = container.getChildAt(0) as? TextView ?: return
        tv.text = payload
    }
}
