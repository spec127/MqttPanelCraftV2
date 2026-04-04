package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Size
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.LedView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.ConcurrentHashMap

object LedDefinition : IComponentDefinition {
    override val type: String = "led"
    override val defaultSize: Size = Size(100, 100)
    override val labelPrefix: String = "LED"

    override val iconResId: Int = R.drawable.ic_btn_power // Or any generic icon
    override val group: String = "SENSOR"

    private val timerMap = ConcurrentHashMap<String, Runnable>()

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val led = LedView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(led, 0)
        return container
    }

    private fun getLed(view: View): LedView? {
        if (view is LedView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is LedView) return child
            }
        }
        return null
    }

    override val propertiesLayoutId: Int = R.layout.layout_prop_led

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        // Init Props if empty
        val defaultProps = mapOf(
            "trigger_mode" to "KEYWORD",
            "action_mode" to "LATCH",
            "timer_ms" to "1500",
            "keywords" to "",
            "active_color" to "#8BC34A",
            "idle_color" to "#424242",
            "effect" to "NONE",
            "style" to "ORB",
            "appearance_mode" to "text",
            "label" to "LED",
            "icon" to ""
        )
        // If the data doesn't have default props, initialize them. (Handled via getOrDefault locally in binder)

        val tvTriggerDesc = panelView.findViewById<TextView>(R.id.tvTriggerModeDescription)
        val containerMatchRow = panelView.findViewById<View>(R.id.containerMatchRow)
        val cgLedKeywordsWrapper = panelView.findViewById<View>(R.id.cgLedKeywords)
        val containerLedTimer = panelView.findViewById<View>(R.id.containerLedTimer)

        fun updateInteractiveVisibility(triggerMode: String, actionMode: String) {
            if (triggerMode == "KEYWORD") {
                containerMatchRow?.visibility = View.VISIBLE
                cgLedKeywordsWrapper?.visibility = View.VISIBLE
                tvTriggerDesc?.text = "關鍵字模式：比對 MQTT 主題收到的文字內容決定是否觸發。"
            } else {
                containerMatchRow?.visibility = View.GONE
                cgLedKeywordsWrapper?.visibility = View.GONE
                tvTriggerDesc?.text = "全捕捉模式：只要此 MQTT 主題收到任何訊息即觸發。"
            }

            val tvDesc = panelView.findViewById<TextView>(R.id.tvActionModeDescription)
            if (actionMode == "TIMER") {
                containerLedTimer?.visibility = View.VISIBLE
                tvDesc?.text = "計時關閉：觸發後經過指定時間自動熄滅。"
            } else {
                containerLedTimer?.visibility = View.GONE
                tvDesc?.text = "常駐亮起：觸發後恆亮直到收到解除訊號。"
            }
        }

        val tgMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleLedTriggerMode)
        val currentTrigger = data.props["trigger_mode"] ?: defaultProps["trigger_mode"]!!
        tgMode?.check(if (currentTrigger == "KEYWORD") R.id.btnModeKeyword else R.id.btnModeAny)
        tgMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = if (checkedId == R.id.btnModeKeyword) "KEYWORD" else "ANY"
                onUpdate("trigger_mode", mode)
                updateInteractiveVisibility(mode, data.props["action_mode"] ?: "LATCH")
            }
        }

        val tgAction = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleLedActionMode)
        val currentAction = data.props["action_mode"] ?: defaultProps["action_mode"]!!
        tgAction?.check(if (currentAction == "LATCH") R.id.btnActionLatch else R.id.btnActionTimer)
        tgAction?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val act = if (checkedId == R.id.btnActionLatch) "LATCH" else "TIMER"
                onUpdate("action_mode", act)
                updateInteractiveVisibility(data.props["trigger_mode"] ?: "KEYWORD", act)
            }
        }

        CommonPropBinder.bindEditText(panelView, R.id.etLedTimer, "timer_ms", data, onUpdate, "3000")

        updateInteractiveVisibility(currentTrigger, currentAction)

        val etKeywordAdd = panelView.findViewById<TextInputEditText>(R.id.etKeywordAdd)
        val cgKeywords = panelView.findViewById<ChipGroup>(R.id.cgLedKeywords)
        val spKeywordMatchType = panelView.findViewById<AutoCompleteTextView>(R.id.spKeywordMatchType)
        val matchTypeAdapter = ArrayAdapter(panelView.context, android.R.layout.simple_dropdown_item_1line, listOf("完全相符", "包含"))
        spKeywordMatchType?.setAdapter(matchTypeAdapter)
        spKeywordMatchType?.setText("完全相符", false)

        fun updateChips() {
            cgKeywords?.removeAllViews()
            val keywordsStr = data.props["keywords"] ?: ""
            if (keywordsStr.isEmpty()) return

            val keywordsList = keywordsStr.split(",")
            keywordsList.forEach { keywordTuple ->
                val parts = keywordTuple.split(":", limit = 3)
                if (parts.size >= 2) {
                    val matchType = parts[0]
                    val keywordText = parts[1]
                    val colorHex = if (parts.size >= 3) parts[2] else "#8BC34A"

                    val prefix = if (matchType == "EXACT") "[相符]" else "[包含]"

                    val customOnUpdateRef = { key: String, value: String ->
                        // Delegate to the inner function that will be defined below
                        onUpdate(key, value)
                        if (key == "active_color") {
                            val vActiveColorPreview = panelView.findViewById<View>(R.id.vActiveColorPreview)
                            try { vActiveColorPreview?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(value)) } catch (e: Exception) {}
                        }
                    }

                    val chip = Chip(panelView.context).apply {
                        text = "$prefix $keywordText"
                        isCloseIconVisible = true
                        setOnCloseIconClickListener {
                            val newList = keywordsList.filter { it != keywordTuple }.joinToString(",")
                            onUpdate("keywords", newList)
                            updateChips()
                        }
                        setOnClickListener {
                            val typeStr = if (matchType == "EXACT") "完全相符" else "包含"
                            spKeywordMatchType?.setText(typeStr, false)
                            etKeywordAdd?.setText(keywordText)
                            customOnUpdateRef("active_color", colorHex)
                        }
                        chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(colorHex))
                        setTextColor(Color.WHITE)
                    }
                    cgKeywords?.addView(chip)
                }
            }
        }
        updateChips()

        etKeywordAdd?.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                
                val newKeyword = v.text.toString().trim()
                if (newKeyword.isNotEmpty()) {
                    val currentKeywords = data.props["keywords"] ?: ""
                    val keywordsList = currentKeywords.split(",").filter { it.isNotEmpty() }
                    
                    val isDuplicate = keywordsList.any { it.split(":").getOrNull(1) == newKeyword }
                    val tilKeywordInput = panelView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.containerKeywordInputReal)

                    if (isDuplicate) {
                        tilKeywordInput?.error = panelView.context.getString(R.string.msg_invalid_topic).replace("字元不合法！", "相同關鍵字！")
                    } else {
                        tilKeywordInput?.error = null
                        val matchTypeStr = spKeywordMatchType?.text.toString()
                        val matchTypeEnum = if (matchTypeStr == "包含") "CONTAINS" else "EXACT"
                        val selectedColor = data.props["active_color"] ?: "#8BC34A"
                        
                        val tuple = "$matchTypeEnum:$newKeyword:$selectedColor"
                        
                        val newKeywordsList = if (currentKeywords.isEmpty()) tuple else "$currentKeywords,$tuple"
                        
                        onUpdate("keywords", newKeywordsList)
                        v.text = ""
                        updateChips()
                    }
                }
                true
            } else {
                false
            }
        }

        val vActiveColorPreview = panelView.findViewById<View>(R.id.vActiveColorPreview)
        val initialActiveColor = data.props["active_color"] ?: "#8BC34A"
        try { vActiveColorPreview?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(initialActiveColor)) } catch (e: Exception) {}

        val customOnUpdate = { key: String, value: String ->
            onUpdate(key, value)
            if (key == "active_color") {
                try { vActiveColorPreview?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(value)) } catch (e: Exception) {}
            }
        }

        CommonPropBinder.bindColorPalette(
            panelView = panelView,
            containerId = R.id.containerActiveColor,
            propKey = "active_color",
            data = data,
            onUpdate = customOnUpdate
        )

        CommonPropBinder.bindColorPalette(
            panelView = panelView,
            containerId = R.id.containerIdleColor,
            propKey = "idle_color",
            data = data,
            onUpdate = onUpdate
        )

        val tgEffect = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleLedEffect)
        val currentEffect = data.props["effect"] ?: "NONE"
        tgEffect?.check(if (currentEffect == "NONE") R.id.btnEffectNone else R.id.btnEffectBlink)
        tgEffect?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val eff = if (checkedId == R.id.btnEffectNone) "NONE" else "BLINK"
                onUpdate("effect", eff)
            }
        }

        val styleOptions = listOf("光球(Orb)", "圖示發光(Icon)", "同心圓(Concentric)", "大型(Radius XL)", "霓虹字(Neon)")
        val styleMap = mapOf(
            "光球(Orb)" to "ORB",
            "圖示發光(Icon)" to "ICON_GLOW",
            "同心圓(Concentric)" to "CONCENTRIC",
            "大型(Radius XL)" to "RADIUS_XL",
            "霓虹字(Neon)" to "NEON_TEXT"
        )
        CommonPropBinder.bindDropdown(panelView, R.id.spLedStyle, "style", data, onUpdate, styleOptions, styleMap, "ORB")

        // Appearance Mode (Text/Icon/Both)
        val spApprMode = panelView.findViewById<AutoCompleteTextView>(R.id.spPropApprMode)
        val modeOptions = listOf("文字", "圖示", "兩者")
        spApprMode?.setAdapter(ArrayAdapter(panelView.context, android.R.layout.simple_dropdown_item_1line, modeOptions))

        val curApprMode = data.props["appearance_mode"] ?: "text"
        val initialIndex = when (curApprMode) {
            "icon" -> 1
            "text_icon" -> 2
            else -> 0
        }
        spApprMode?.setText(modeOptions[initialIndex], false)

        val containerText = panelView.findViewById<View>(R.id.containerPropText)
        val containerIcon = panelView.findViewById<View>(R.id.containerPropIcon)

        fun updateApprVisibility(mode: String) {
            when (mode) {
                "text" -> {
                    containerText?.visibility = View.VISIBLE
                    containerIcon?.visibility = View.GONE
                }
                "icon" -> {
                    containerText?.visibility = View.GONE
                    containerIcon?.visibility = View.VISIBLE
                }
                else -> { // text_icon
                    containerText?.visibility = View.VISIBLE
                    containerIcon?.visibility = View.VISIBLE
                }
            }
        }
        updateApprVisibility(curApprMode)

        spApprMode?.setOnItemClickListener { _, _, position, _ ->
            val modeStr = when (position) {
                1 -> "icon"
                2 -> "text_icon"
                else -> "text"
            }
            onUpdate("appearance_mode", modeStr)
            updateApprVisibility(modeStr)
        }

        CommonPropBinder.bindEditText(panelView, R.id.etLedLabel, "label", data, onUpdate, "")

        // --- ICON GRID LOGIC ---
        val iconMap = mapOf(
            R.id.iconPreviewLED1 to "ic_btn_power",
            R.id.iconPreviewLED2 to "ic_btn_lighting",
            R.id.iconPreviewLED3 to "ic_btn_fan",
            R.id.iconPreviewLED4 to "ic_btn_play",
            R.id.iconPreviewLED5 to "ic_btn_tune",
            R.id.iconPreviewLED6 to "ic_btn_energy"
        )
        iconMap.forEach { (id, key) ->
            panelView.findViewById<View>(id)?.setOnClickListener {
                onUpdate("icon", key)
            }
        }
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val led = getLed(view) ?: return

        led.style = when (data.props["style"]) {
            "ICON_GLOW" -> LedView.Style.ICON_GLOW
            "CONCENTRIC" -> LedView.Style.CONCENTRIC
            "RADIUS_XL" -> LedView.Style.RADIUS_XL
            "NEON_TEXT" -> LedView.Style.NEON_TEXT
            else -> LedView.Style.ORB
        }

        val iColorStr = data.props["idle_color"] ?: "#424242"
        led.idleColor = try { Color.parseColor(iColorStr) } catch (e: Exception) { Color.parseColor("#424242") }
        
        if (data.props["trigger_mode"] == "ANY") {
            val aColorStr = data.props["active_color"] ?: "#8BC34A"
            led.activeColor = try { Color.parseColor(aColorStr) } catch (e: Exception) { Color.parseColor("#8BC34A") }
        }

        led.effect = when (data.props["effect"]) {
            "BLINK" -> LedView.Effect.BLINK
            else -> LedView.Effect.NONE
        }

        val apprMode = data.props["appearance_mode"] ?: "text"
        if (apprMode == "icon") {
            led.label = ""
        } else {
            led.label = data.props["label"] ?: "LED"
        }
        
        if (apprMode == "text") {
            led.iconResId = 0
        } else {
            val iconResName = data.props["icon"]
            if (!iconResName.isNullOrEmpty()) {
                val resId = view.context.resources.getIdentifier(iconResName, "drawable", view.context.packageName)
                if (resId != 0) {
                    led.iconResId = resId
                } else {
                    led.iconResId = 0
                }
            } else {
                led.iconResId = 0
            }
        }
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        view.isClickable = false 
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val led = getLed(view) ?: return
        val triggerMode = data.props["trigger_mode"] ?: "KEYWORD"
        val actionMode = data.props["action_mode"] ?: "LATCH"
        val timerMs = (data.props["timer_ms"] ?: "1500").toLongOrNull() ?: 1500L

        val offRunnable = Runnable { led.isActive = false }
        val idStr = data.id.toString()

        if (triggerMode == "ANY") {
            val aColorStr = data.props["active_color"] ?: "#8BC34A"
            led.activeColor = try { Color.parseColor(aColorStr) } catch (e: Exception) { Color.parseColor("#8BC34A") }
            led.isActive = true

            if (actionMode == "TIMER") {
                timerMap[idStr]?.let { view.removeCallbacks(it) }
                view.postDelayed(offRunnable, timerMs)
                timerMap[idStr] = offRunnable
            }
        } else {
            val keywordsStr = data.props["keywords"] ?: ""
            if (keywordsStr.isEmpty()) return

            val keywordsList = keywordsStr.split(",")
            var matchedKwColor = ""

            for (kwTuple in keywordsList) {
                val parts = kwTuple.split(":", limit = 3)
                if (parts.size >= 2) {
                    val matchType = parts[0]
                    val keywordText = parts[1]
                    val color = if (parts.size >= 3) parts[2] else "#8BC34A"

                    if ((matchType == "EXACT" && payload == keywordText) ||
                        (matchType == "CONTAINS" && payload.contains(keywordText))) {
                        matchedKwColor = color
                        break
                    }
                }
            }

            if (matchedKwColor.isNotEmpty()) {
                led.activeColor = try { Color.parseColor(matchedKwColor) } catch (e: Exception) { Color.parseColor("#8BC34A") }
                led.isActive = true

                if (actionMode == "TIMER") {
                    timerMap[idStr]?.let { view.removeCallbacks(it) }
                    view.postDelayed(offRunnable, timerMs)
                    timerMap[idStr] = offRunnable
                }
            } else {
                if (actionMode == "LATCH") {
                    led.isActive = false
                }
            }
        }
    }
}
