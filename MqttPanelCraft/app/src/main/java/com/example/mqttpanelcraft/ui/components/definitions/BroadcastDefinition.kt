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
    override val defaultSize: Size = Size(200, 80)
    override val labelPrefix: String = "broadcast"
    override val iconResId: Int = android.R.drawable.ic_lock_silent_mode_off
    override val group: String = "DISPLAY"
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
        "speech_pitch" to "1.05",
        "speech_voice_preset" to "F1"
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
        val pitch = (data.props["speech_pitch"] ?: "1.05").toFloatOrNull() ?: 1.05f
        val preset = data.props["speech_voice_preset"] ?: when (data.props["speech_pitch"]) {
            "0.8" -> "M1"
            "0.95" -> "M2"
            "1.3" -> "F2"
            else -> "F1"
        }
        broadcastView.setVoiceSettings(preset, pitch, rate)
        broadcastView.broadcastMode = data.props["broadcast_mode"] ?: "TTS_ONLY"
        broadcastView.alertType = data.props["alert_type"] ?: "Chime"
        
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

        val tilAlert = panelView.findViewById<View>(R.id.tilAlertType)
        val sectionTts = panelView.findViewById<View>(R.id.sectionTtsContainer)

        fun updateSectionVisibility(mode: String) {
            tilAlert?.visibility = if (mode == "ALERT_ONLY" || mode == "ALERT_AND_TTS") View.VISIBLE else View.GONE
            sectionTts?.visibility = if (mode == "TTS_ONLY" || mode == "ALERT_AND_TTS") View.VISIBLE else View.GONE
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
        val alertList = listOf("Chime", "Beep", "Emergency", "Siren", "Buzzer")
        val alertNames = listOf(
            context.getString(R.string.val_alert_alarm_classic),
            context.getString(R.string.val_alert_beep_long),
            context.getString(R.string.val_alert_warning_pulse),
            context.getString(R.string.val_alert_siren),
            context.getString(R.string.val_alert_synthesizer)
        )
        spAlert?.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, alertNames))
        val curAlert = data.props["alert_type"] ?: "Chime"
        val aIdx = alertList.indexOf(curAlert).coerceAtLeast(0)
        spAlert?.setText(alertNames[aIdx], false)
        spAlert?.setOnClickListener { spAlert.showDropDown() }
        tilAlert?.setOnClickListener { spAlert?.showDropDown() }
        spAlert?.setOnItemClickListener { _, _, pos, _ ->
            onUpdate("alert_type", alertList[pos])
        }

        // 3. Voice Pitch Presets (4 buttons: btnPitchM1 男1, btnPitchF1 女1, btnPitchM2 男2, btnPitchF2 女2)
        fun updatePitchUI(preset: String) {
            val btnM1 = panelView.findViewById<MaterialButton>(R.id.btnPitchM1)
            val btnF1 = panelView.findViewById<MaterialButton>(R.id.btnPitchF1)
            val btnM2 = panelView.findViewById<MaterialButton>(R.id.btnPitchM2)
            val btnF2 = panelView.findViewById<MaterialButton>(R.id.btnPitchF2)
            listOf(btnM1, btnF1, btnM2, btnF2).forEach {
                it?.setBackgroundColor(Color.TRANSPARENT)
                it?.setTextColor(Color.parseColor("#4CAF50"))
            }
            val activeBtn = when (preset) {
                "M1" -> btnM1
                "M2" -> btnM2
                "F2" -> btnF2
                else -> btnF1
            }
            activeBtn?.setBackgroundColor(Color.parseColor("#334CAF50"))
            activeBtn?.setTextColor(Color.WHITE)
        }
        val curPreset = data.props["speech_voice_preset"] ?: when (data.props["speech_pitch"]) {
            "0.8" -> "M1"
            "0.95" -> "M2"
            "1.3" -> "F2"
            else -> "F1"
        }
        updatePitchUI(curPreset)

        panelView.findViewById<View>(R.id.btnPitchM1)?.setOnClickListener {
            onUpdate("speech_voice_preset", "M1")
            onUpdate("speech_pitch", "0.8")
            updatePitchUI("M1")
        }
        panelView.findViewById<View>(R.id.btnPitchF1)?.setOnClickListener {
            onUpdate("speech_voice_preset", "F1")
            onUpdate("speech_pitch", "1.05")
            updatePitchUI("F1")
        }
        panelView.findViewById<View>(R.id.btnPitchM2)?.setOnClickListener {
            onUpdate("speech_voice_preset", "M2")
            onUpdate("speech_pitch", "0.95")
            updatePitchUI("M2")
        }
        panelView.findViewById<View>(R.id.btnPitchF2)?.setOnClickListener {
            onUpdate("speech_voice_preset", "F2")
            onUpdate("speech_pitch", "1.3")
            updatePitchUI("F2")
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
