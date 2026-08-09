package com.example.mqttpanelcraft.ui.components.definitions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.util.Size
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.example.mqttpanelcraft.ProjectViewModel
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.BroadcastView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * 語音廣播定義元件 (BroadcastDefinition)
 * 位於「多媒體」分類中，專門負責將收到的 MQTT 文字進行 TTS 語音播報、警報前奏與記錄呈現。
 */
object BroadcastDefinition : IComponentDefinition {
    override val type: String = "BROADCAST"
    override val defaultSize: Size = Size(150, 50)
    override val labelPrefix: String = "broadcast"
    override val iconResId: Int = android.R.drawable.ic_lock_silent_mode_off
    override val group: String = "SENSOR"
    override val propertiesLayoutId: Int = R.layout.layout_prop_broadcast

    private fun getActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "title" to "語音警報廣播",
        "topic" to "home/tts/say",
        "broadcast_mode" to "TTS_ONLY",
        "alert_type" to "Chime",
        "speech_rate" to "1.0",
        "speech_pitch" to "1.0",
        "speech_voice_preset" to "NATURAL",
        "chart_style" to "Capsule",
        "color" to "#FF9800",
        "show_text" to "true"
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
        val rate = ((data.props["speech_rate"] ?: "1.0").toFloatOrNull() ?: 1.0f).coerceIn(0.1f, 3.0f)
        val pitch = (data.props["speech_pitch"] ?: "1.05").toFloatOrNull() ?: 1.05f
        val preset = data.props["speech_voice_preset"] ?: "NATURAL"
        broadcastView.setVoiceSettings(preset, pitch, rate)
        broadcastView.broadcastMode = data.props["broadcast_mode"] ?: "TTS_ONLY"
        broadcastView.alertType = data.props["alert_type"] ?: "Chime"
        broadcastView.chartStyle = data.props["chart_style"] ?: "Capsule"
        broadcastView.colorStr = data.props["color"] ?: "#FF9800"
        broadcastView.showText = (data.props["show_text"] ?: "true") == "true"
        
        val savedText = data.props["value"]
        if (!savedText.isNullOrEmpty()) {
            broadcastView.setMessageQuietly(savedText)
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        fun updateSectionVisibility(mode: String) {
            val tilAlert = panelView.findViewById<View>(R.id.tilAlertType)
            tilAlert?.visibility = if (mode == "ALERT_ONLY" || mode == "ALERT_AND_TTS") View.VISIBLE else View.GONE
        }

        // 1. Broadcast Mode Toggle
        val toggleMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleBroadcastMode)
        val curMode = data.props["broadcast_mode"] ?: "TTS_ONLY"
        toggleMode?.check(when(curMode) {
            "ALERT_ONLY" -> R.id.btnModeAlert
            "ALERT_AND_TTS" -> R.id.btnModeBoth
            else -> R.id.btnModeTts
        })
        val tvModeTip = panelView.findViewById<TextView>(R.id.tvModeTip)
        tvModeTip?.visibility = if (curMode == "ALERT_AND_TTS") View.VISIBLE else View.GONE
        updateSectionVisibility(curMode)

        toggleMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when(checkedId) {
                    R.id.btnModeAlert -> "ALERT_ONLY"
                    R.id.btnModeBoth -> "ALERT_AND_TTS"
                    else -> "TTS_ONLY"
                }
                tvModeTip?.visibility = if (mode == "ALERT_AND_TTS") View.VISIBLE else View.GONE
                updateSectionVisibility(mode)
                onUpdate("broadcast_mode", mode)
            }
        }

        // 2. Alert Type Dropdown
        val spAlert = panelView.findViewById<AutoCompleteTextView>(R.id.spAlertType)
        val alertList = listOf("Chime", "Siren", "Buzzer")
        val alertNames = listOf(
            context.getString(R.string.val_alert_alarm_classic),
            context.getString(R.string.val_alert_siren),
            context.getString(R.string.val_alert_synthesizer)
        )
        spAlert?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, alertNames))
        val curAlert = data.props["alert_type"] ?: "Chime"
        val aIdx = alertList.indexOf(curAlert).coerceAtLeast(0)
        spAlert?.setText(alertNames[aIdx], false)
        spAlert?.setOnItemClickListener { _, _, pos, _ ->
            onUpdate("alert_type", alertList[pos])
        }

        val togglePitch = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleVoicePitch)
        val curPreset = data.props["speech_voice_preset"] ?: "NATURAL"
        togglePitch?.check(when(curPreset) {
            "LOW" -> R.id.btnVoiceLow
            "BRISK" -> R.id.btnVoiceBrisk
            else -> R.id.btnVoiceNatural
        })
        togglePitch?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val preset = when (checkedId) {
                    R.id.btnVoiceLow -> "LOW"
                    R.id.btnVoiceBrisk -> "BRISK"
                    else -> "NATURAL"
                }
                onUpdate("speech_voice_preset", preset)
                val pitch = when (preset) {
                    "LOW" -> "0.8"
                    "BRISK" -> "1.3"
                    else -> "1.0"
                }
                onUpdate("speech_pitch", pitch)
            }
        }

        val styleLabels = listOf(
            context.getString(R.string.val_style_text_capsule),
            context.getString(R.string.val_style_text_infinity),
            context.getString(R.string.val_style_text_glass)
        )
        val styleMap = mapOf(
            context.getString(R.string.val_style_text_capsule) to "Capsule",
            context.getString(R.string.val_style_text_infinity) to "Infinity",
            context.getString(R.string.val_style_text_glass) to "Glass"
        )
        CommonPropBinder.bindDropdown(
            panelView,
            R.id.spStyle,
            "chart_style",
            data,
            onUpdate,
            styleLabels,
            styleMap
        )

        CommonPropBinder.bindColorPalette(
            panelView,
            R.id.containerColor,
            "color",
            data,
            onUpdate,
            label = "色表",
            defaultColor = "#FF9800"
        )
        
        val itemShowText = panelView.findViewById<LinearLayout>(R.id.itemShowText)
        val checkShowText = panelView.findViewById<ImageView>(R.id.checkShowText)
        val isShowText = (data.props["show_text"] ?: "true") == "true"
        checkShowText?.visibility = if (isShowText) View.VISIBLE else View.INVISIBLE
        itemShowText?.setOnClickListener {
            val newState = !((data.props["show_text"] ?: "true") == "true")
            onUpdate("show_text", newState.toString())
            checkShowText?.visibility = if (newState) View.VISIBLE else View.INVISIBLE
        }

        // 4. Speech Rate Input
        CommonPropBinder.bindEditText(
            panelView,
            R.id.etSpeechRate,
            "speech_rate",
            data,
            onUpdate,
            "1.0"
        )

        // 5. Linked Components (掛勾接收對象)
        val containerLinked = panelView.findViewById<LinearLayout>(R.id.containerLinkedComponents)
        if (containerLinked != null) {
            containerLinked.removeAllViews()
            val linkedString = data.props["linked_components"] ?: ""
            val linkedSet = linkedString.split(",").filter { it.isNotEmpty() }.toMutableSet()

            val activity = getActivity(context) as? ViewModelStoreOwner
            val viewModel = activity?.let { ViewModelProvider(it)[ProjectViewModel::class.java] }
            val components = viewModel?.components?.value ?: emptyList()

            // Add self as first item (checked, alpha = 0.5f)
            val ownCb = CheckBox(context).apply {
                text = "${data.label} (${data.topicConfig})"
                isChecked = true
                alpha = 0.5f
                setOnCheckedChangeListener { buttonView, isChecked ->
                    if (!isChecked) {
                        buttonView.isChecked = true
                    }
                }
            }
            containerLinked.addView(ownCb)

            components.filter { it.id != data.id }.forEach { comp ->
                val cb = CheckBox(context).apply {
                    text = "${comp.label} (${comp.topicConfig})"
                    isChecked = linkedSet.contains(comp.id.toString())
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) linkedSet.add(comp.id.toString()) else linkedSet.remove(comp.id.toString())
                        onUpdate("linked_components", linkedSet.joinToString(","))
                    }
                }
                containerLinked.addView(cb)
            }
            if (containerLinked.childCount == 1 && components.size <= 1) {
                containerLinked.addView(TextView(context).apply {
                    text = context.getString(R.string.broadcast_no_linked)
                    textSize = 12f
                    setTextColor(Color.parseColor("#B0B8C4"))
                })
            }
        }
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
        onLinkedMqttMessage(view, data, payload, data)
        onUpdateProp("value", payload)
    }

    fun onLinkedMqttMessage(view: View, data: ComponentData, payload: String, sourceComp: ComponentData) {
        val broadcastView = view.findViewWithTag<BroadcastView>("target_broadcast") ?: return
        broadcastView.speak(payload)
    }
}
