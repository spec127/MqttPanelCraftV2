package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

/**
 * 聲音警示與播放感測元件 (AudioSensorDefinition)
 *
 * Design Intent:
 * 作為 Sensor 感測器類別下的聲音監控與聲音警報提示元件。當收到 MQTT 觸發訊息時可播放警示音或顯示聲音監控狀態。
 */
object AudioSensorDefinition : IComponentDefinition {

    override val type: String = "AUDIO_SENSOR"
    override val defaultSize: Size = Size(160, 100)
    override val labelPrefix: String = "audio"
    override val iconResId: Int = android.R.drawable.ic_lock_silent_mode_off
    override val group: String = "SENSOR"

    override val propertiesLayoutId: Int = R.layout.layout_prop_generic_color

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "color" to "#FFEB3B",
        "theme_color" to "#FFEB3B",
        "alarm_mode" to "BEEP"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#222222"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val iconView = ImageView(context).apply {
            tag = "audio_icon"
            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            setColorFilter(Color.parseColor("#FFEB3B"))
        }

        val statusText = TextView(context).apply {
            tag = "audio_status"
            text = "Sound / Alarm Ready"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        layout.addView(iconView)
        layout.addView(statusText)
        container.addView(layout, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val layout = (view as? FrameLayout)?.getChildAt(0) as? LinearLayout ?: return
        val icon = layout.findViewWithTag<ImageView>("audio_icon") ?: return

        data.props["color"]?.let { colorHex ->
            try {
                icon.setColorFilter(Color.parseColor(colorHex))
            } catch (_: Exception) {}
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        // 綁定通用顏色屬性面板
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // 音訊監控為被動接收或點擊測試警示音
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val layout = (view as? FrameLayout)?.getChildAt(0) as? LinearLayout ?: return
        val statusText = layout.findViewWithTag<TextView>("audio_status")

        statusText?.text = "Audio Payload: $payload"

        // 收到觸發訊息或非 0/OFF 數值時發出嗶聲提示
        val trimmed = payload.trim().uppercase()
        if (trimmed != "0" && trimmed != "OFF" && trimmed != "FALSE" && trimmed.isNotEmpty()) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
            } catch (_: Exception) {}
        }
    }
}
