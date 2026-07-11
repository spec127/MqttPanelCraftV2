package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日曆與時鐘元件 (CalendarClockDefinition)
 *
 * Design Intent:
 * 多媒體日曆時鐘元件，動態顯示當前年、月、日、星期幾與時間時鐘，亦可接收 MQTT 校時。
 */
object CalendarClockDefinition : IComponentDefinition {

    override val type: String = "CALENDAR"
    override val defaultSize: Size = Size(240, 120)
    override val labelPrefix: String = "clock"
    override val iconResId: Int = android.R.drawable.ic_menu_my_calendar
    override val group: String = "DISPLAY"

    override val propertiesLayoutId: Int = R.layout.layout_prop_generic_color

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "color" to "#E91E63",
        "theme_color" to "#E91E63"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val layout = LinearLayout(context).apply {
            tag = "target_clock_container"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1F1F1F"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val dateView = TextView(context).apply {
            tag = "date_view"
            setTextColor(Color.parseColor("#E91E63"))
            textSize = 14f
            gravity = Gravity.CENTER
        }

        val timeView = TextView(context).apply {
            tag = "time_view"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
        }

        layout.addView(dateView)
        layout.addView(timeView)
        container.addView(layout, 0)

        // 初始化顯示
        val dateFormat = SimpleDateFormat("yyyy-MM-dd (EEE)", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val now = Date()
        dateView.text = dateFormat.format(now)
        timeView.text = timeFormat.format(now)

        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val layout = container.findViewWithTag<LinearLayout>("target_clock_container") ?: return
        val dateView = layout.findViewWithTag<TextView>("date_view") ?: return

        data.props["color"]?.let { colorHex ->
            try {
                dateView.setTextColor(Color.parseColor(colorHex))
            } catch (_: Exception) {}
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {}

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
        val layout = container.findViewWithTag<LinearLayout>("target_clock_container") ?: return
        val timeView = layout.findViewWithTag<TextView>("time_view") ?: return
        timeView.text = payload
    }
}
