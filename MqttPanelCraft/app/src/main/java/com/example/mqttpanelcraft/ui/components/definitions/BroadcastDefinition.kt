package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.View
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.BroadcastView

/**
 * 語音廣播定義元件 (BroadcastDefinition)
 * 位於「多媒體」分類中，專門負責將收到的 MQTT 文字進行 TTS 語音播報與記錄呈現。
 */
object BroadcastDefinition : IComponentDefinition {
    override val type: String = "BROADCAST"
    override val defaultSize: Size = Size(200, 80)
    override val labelPrefix: String = "broadcast"
    override val iconResId: Int = android.R.drawable.ic_lock_silent_mode_off
    override val group: String = "DISPLAY"
    override val propertiesLayoutId: Int = R.layout.layout_prop_broadcast

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "title" to "語音廣播",
        "topic" to "home/tts/say",
        "speech_rate" to "1.0",
        "speech_pitch" to "1.0"
    )

    override fun createView(
        context: Context,
        isEditMode: Boolean
    ): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val broadcastView = BroadcastView(context).apply {
            tag = "target_broadcast"
            this.isEditMode = isEditMode
        }
        container.addView(broadcastView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val broadcastView = view.findViewWithTag<BroadcastView>("target_broadcast") ?: return
        val rate = (data.props["speech_rate"] ?: "1.0").toFloatOrNull() ?: 1.0f
        val pitch = (data.props["speech_pitch"] ?: "1.0").toFloatOrNull() ?: 1.0f
        broadcastView.speechRate = rate
        broadcastView.speechPitch = pitch
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        CommonPropBinder.bindEditText(
            panelView,
            R.id.etSpeechRate,
            "speech_rate",
            data,
            onUpdate,
            "1.0"
        )
        CommonPropBinder.bindEditText(
            panelView,
            R.id.etSpeechPitch,
            "speech_pitch",
            data,
            onUpdate,
            "1.0"
        )
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val broadcastView = view.findViewWithTag<BroadcastView>("target_broadcast") ?: return
        broadcastView.isEditMode = false
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val broadcastView = view.findViewWithTag<BroadcastView>("target_broadcast") ?: return
        broadcastView.speak(payload)
    }
}
