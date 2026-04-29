package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
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

object LedDefinition : IComponentDefinition {
    override val type: String = "LED"
    override val defaultSize: Size = Size(80, 80)
    override val labelPrefix: String = "led"
    override val iconResId: Int = R.drawable.ic_led_orb
    override val group: String = "SENSOR"

    private val timerMap = mutableMapOf<String, Runnable>()

    private fun getLed(view: View): LedView? {
        val root = view as? FrameLayout ?: return null
        return root.getChildAt(0) as? LedView
    }

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val led = LedView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
                val margin = (8 * context.resources.displayMetrics.density).toInt()
                setMargins(margin, margin, margin, margin)
            }
        }
        container.addView(led, 0)
        return container
    }

    override val propertiesLayoutId: Int = R.layout.layout_prop_led

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context
        
        // --- 顏色變更預覽邏輯 ---
        val onUpdateWithPreview: (String, String) -> Unit = { key, value ->
            onUpdate(key, value)
            if (key == "active_color" || key == "idle_color" || key == "rgb_states") {
                val led = getLed(panelView.rootView.findViewWithTag("component_preview") ?: panelView) 
                // Note: findViewWithTag("component_preview") is a guess, but we can just use the active view in Dashboard
                // Actually, the panelView is usually in a BottomSheet. We need to find the view on the canvas.
                // For now, if we can't find it easily, we'll just rely on the next onMqttMessage logic or a simple invalidation.
                // A better way: the DashboardActivity has the selected view.
            }
        }
        val containerStandard = panelView.findViewById<View>(R.id.containerStandardMode)
        val containerRgb = panelView.findViewById<View>(R.id.containerRgbMode)
        val containerLedTimer = panelView.findViewById<View>(R.id.containerLedTimer)
        val tvLogicDesc = panelView.findViewById<TextView>(R.id.tvLogicModeDescription)
        val tvActionDesc = panelView.findViewById<TextView>(R.id.tvActionModeDescription)

        // ----- 資料 Migration #1: led_logic_mode -----
        if (data.props["led_logic_mode"] == null) {
            val oldKeywords = data.props["keywords"] ?: ""
            if (oldKeywords.contains(":")) {
                data.props["led_logic_mode"] = "RGB"
                val chunks = oldKeywords.split(",").filter { it.isNotEmpty() }
                data.props["rgb_state_count"] = chunks.size.coerceAtLeast(1).toString()
                data.props["rgb_match_type"] = if (oldKeywords.contains("CONTAINS:")) "CONTAINS" else "EXACT"
                val rgbList = chunks.mapNotNull {
                    val parts = it.split(":", limit = 3)
                    if (parts.size >= 2) parts[0] to parts[1] else null
                }
                data.props["rgb_states"] = rgbList.joinToString(",") { "${it.first}|${it.second}" }
            } else {
                data.props["led_logic_mode"] = "STANDARD"
            }
        }

        fun updateLogicVisibility(logicMode: String, actionMode: String) {
            val containerRestoreKeyword = panelView.findViewById<View>(R.id.containerRestoreKeyword)
            if (logicMode == "RGB") {
                containerStandard?.visibility = View.GONE
                containerRgb?.visibility = View.VISIBLE
                tvLogicDesc?.text = context.getString(R.string.desc_led_logic_rgb)
                tvActionDesc?.visibility = View.GONE
            } else {
                containerStandard?.visibility = View.VISIBLE
                containerRgb?.visibility = View.GONE
                tvLogicDesc?.text = context.getString(R.string.desc_led_logic_standard_detail)
                tvActionDesc?.visibility = View.VISIBLE
                tvActionDesc?.text = if (actionMode == "TIMER") context.getString(R.string.desc_led_action_timer) else context.getString(R.string.desc_led_action_latch)
            }
            
            if (actionMode == "TIMER") {
                containerLedTimer?.visibility = View.VISIBLE
                containerRestoreKeyword?.visibility = View.GONE
            } else {
                containerLedTimer?.visibility = View.GONE
                containerRestoreKeyword?.visibility = View.VISIBLE
            }
            
            val containerActiveColor = panelView.findViewById<View>(R.id.containerActiveColor)
            containerActiveColor?.visibility = if (logicMode == "RGB") View.GONE else View.VISIBLE
        }

        val tgLogic = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleLedLogicMode)
        val currentLogic = data.props["led_logic_mode"] ?: "STANDARD"
        tgLogic?.check(if (currentLogic == "RGB") R.id.btnLogicRgb else R.id.btnLogicStandard)
        tgLogic?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = if (checkedId == R.id.btnLogicRgb) "RGB" else "STANDARD"
                onUpdate("led_logic_mode", mode)
                updateLogicVisibility(mode, data.props["action_mode"] ?: "LATCH")
            }
        }

        val tgAction = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleLedActionMode)
        val currentAction = data.props["action_mode"] ?: "LATCH"
        tgAction?.check(if (currentAction == "TIMER") R.id.btnActionTimer else R.id.btnActionLatch)
        tgAction?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val act = if (checkedId == R.id.btnActionTimer) "TIMER" else "LATCH"
                onUpdate("action_mode", act)
                updateLogicVisibility(data.props["led_logic_mode"] ?: "STANDARD", act)
            }
        }

        CommonPropBinder.bindEditText(panelView, R.id.etLedTimer, "timer_ms", data, onUpdate, "3000")
        updateLogicVisibility(currentLogic, currentAction)

        // ----------- STANDARD MODE LOGIC (Trigger Keywords) -----------
        val spStdMatch = panelView.findViewById<AutoCompleteTextView>(R.id.spStandardMatchType)
        val matchOptions = listOf(context.getString(R.string.val_match_exact_alt), context.getString(R.string.val_match_contains_alt))
        val matchAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, matchOptions)
        spStdMatch?.setAdapter(matchAdapter)
        spStdMatch?.setText(matchOptions[0], false)

        fun getSelectedMatchKey(): String = if (spStdMatch?.text?.toString() == context.getString(R.string.val_match_contains_alt)) "CONTAINS" else "EXACT"

        fun parseKeywords(): List<Pair<String, String>> {
            val raw = data.props["keywords"] ?: "ON|EXACT,1|EXACT,TRUE|EXACT"
            if (raw.isEmpty()) return emptyList()
            return raw.split(",").filter { it.isNotEmpty() }.map {
                val parts = it.split("|")
                (parts.getOrElse(0) { "" }) to (parts.getOrElse(1) { "EXACT" })
            }
        }

        fun serializeKeywords(list: List<Pair<String, String>>): String =
            list.filter { it.first.isNotEmpty() }.joinToString(",") { "${it.first}|${it.second}" }

        val cgKeywords = panelView.findViewById<ChipGroup>(R.id.cgLedKeywords)

        fun buildChips() {
            cgKeywords?.removeAllViews()
            val keywords = parseKeywords()
            keywords.forEachIndexed { index, (kw, matchType) ->
                val chip = Chip(context).apply {
                    text = context.getString(if (matchType == "CONTAINS") R.string.chip_match_contains else R.string.chip_match_exact, kw)
                    isCloseIconVisible = true
                    val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    if (isDark) {
                        chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#1A237E"))
                        setTextColor(Color.WHITE)
                        closeIconTint = ColorStateList.valueOf(Color.WHITE)
                    } else {
                        chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E3F2FD"))
                        setTextColor(Color.parseColor("#0D47A1"))
                        closeIconTint = ColorStateList.valueOf(Color.parseColor("#0D47A1"))
                    }
                    setOnCloseIconClickListener {
                        val updated = parseKeywords().toMutableList()
                        updated.removeAt(index)
                        onUpdate("keywords", serializeKeywords(updated))
                        buildChips()
                    }
                }
                cgKeywords?.addView(chip)
            }
        }
        buildChips()

        fun tryAddKeyword(etView: TextInputEditText?, til: com.google.android.material.textfield.TextInputLayout?) {
            val newKw = etView?.text?.toString()?.trim() ?: return
            if (newKw.isEmpty()) return
            val currentList = parseKeywords()
            if (currentList.any { it.first == newKw }) {
                til?.error = context.getString(R.string.error_duplicate)
            } else {
                til?.error = null
                val newList = currentList + (newKw to getSelectedMatchKey())
                onUpdate("keywords", serializeKeywords(newList))
                etView.text = null
                buildChips()
            }
        }

        val etKeywordAdd = panelView.findViewById<TextInputEditText>(R.id.etKeywordAdd)
        val tilKeyword = panelView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.containerKeywordInputReal)
        panelView.findViewById<android.widget.ImageView>(R.id.btnKeywordAdd)?.setOnClickListener { tryAddKeyword(etKeywordAdd, tilKeyword) }
        etKeywordAdd?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { tryAddKeyword(etKeywordAdd, tilKeyword); true } else false
        }

        // ----------- RESTORE KEYWORDS LOGIC (復原關鍵字) -----------
        fun parseRestoreKeywords(): List<Pair<String, String>> {
            val raw = data.props["restore_keywords"] ?: "OFF|EXACT,0|EXACT,FALSE|EXACT"
            if (raw.isEmpty()) return emptyList()
            return raw.split(",").filter { it.isNotEmpty() }.map {
                val parts = it.split("|")
                (parts.getOrElse(0) { "" }) to "EXACT"
            }
        }

        fun serializeRestoreKeywords(list: List<Pair<String, String>>): String =
            list.filter { it.first.isNotEmpty() }.joinToString(",") { "${it.first}|EXACT" }

        val cgRestore = panelView.findViewById<ChipGroup>(R.id.cgLedRestoreKeywords)

        fun buildRestoreChips() {
            cgRestore?.removeAllViews()
            val keywords = parseRestoreKeywords()
            keywords.forEachIndexed { index, (kw, _) ->
                val chip = Chip(context).apply {
                    text = kw
                    isCloseIconVisible = true
                    val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    if (isDark) {
                        chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#37474F"))
                        setTextColor(Color.WHITE)
                        closeIconTint = ColorStateList.valueOf(Color.WHITE)
                    } else {
                        chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FCE4EC"))
                        setTextColor(Color.parseColor("#880E4F"))
                        closeIconTint = ColorStateList.valueOf(Color.parseColor("#880E4F"))
                    }
                    setOnCloseIconClickListener {
                        val updated = parseRestoreKeywords().toMutableList()
                        updated.removeAt(index)
                        onUpdate("restore_keywords", serializeRestoreKeywords(updated))
                        buildRestoreChips()
                    }
                }
                cgRestore?.addView(chip)
            }
        }
        buildRestoreChips()

        fun tryAddRestoreKeyword(etView: TextInputEditText?, til: com.google.android.material.textfield.TextInputLayout?) {
            val newKw = etView?.text?.toString()?.trim() ?: return
            if (newKw.isEmpty()) return
            val currentList = parseRestoreKeywords()
            if (currentList.any { it.first == newKw }) {
                til?.error = context.getString(R.string.error_duplicate)
            } else {
                til?.error = null
                val newList = currentList + (newKw to "EXACT")
                onUpdate("restore_keywords", serializeRestoreKeywords(newList))
                etView.text = null
                buildRestoreChips()
            }
        }

        val etRestoreAdd = panelView.findViewById<TextInputEditText>(R.id.etRestoreKeyword)
        val tilRestore = panelView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.containerRestoreInputReal)
        panelView.findViewById<android.widget.ImageView>(R.id.btnRestoreKwAdd)?.setOnClickListener { tryAddRestoreKeyword(etRestoreAdd, tilRestore) }
        etRestoreAdd?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { tryAddRestoreKeyword(etRestoreAdd, tilRestore); true } else false
        }

        // ----------- RGB MODE LOGIC -----------
        val matchToggleRgb = panelView.findViewById<MaterialButtonToggleGroup>(R.id.containerMatchMode)
        val currentMatch = data.props["rgb_match_type"] ?: "EXACT"
        matchToggleRgb?.check(if (currentMatch == "CONTAINS") R.id.btnRgbMatchContain else R.id.btnRgbMatchEqual)
        matchToggleRgb?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                onUpdate("rgb_match_type", if (checkedId == R.id.btnRgbMatchContain) "CONTAINS" else "EXACT")
            }
        }

        val containerRgbStates = panelView.findViewById<LinearLayout>(R.id.containerRgbStates)

        fun parseRgbStates(): MutableList<Pair<String, String>> {
            val str = data.props["rgb_states"] ?: ""
            if (str.isEmpty()) return mutableListOf()
            return str.split(",").mapNotNull { 
                val parts = it.split("|")
                if (parts.size >= 2) parts[0] to parts[1] else null
            }.toMutableList()
        }

        fun saveRgbStates(list: List<Pair<String, String>>) {
            onUpdate("rgb_states", list.joinToString(",") { "${it.first}|${it.second}" })
        }

        fun updateRgbStateRows() {
            containerRgbStates?.removeAllViews()
            val countStr = data.props["rgb_state_count"] ?: "2"
            val count = countStr.toIntOrNull() ?: 2

            val currentStates = parseRgbStates()
            val rainbowColors = listOf("#F44336","#FF9800","#FFEB3B","#4CAF50","#2196F3","#9C27B0")
            while (currentStates.size < count) {
                val idx = currentStates.size % rainbowColors.size
                currentStates.add("L${currentStates.size + 1}" to rainbowColors[idx])
                saveRgbStates(currentStates)
                
                // Trigger preview on add
                val led = getLed(panelView.rootView.findViewWithTag("component_preview") ?: panelView)
                // (Flash logic handled by onUpdateView)
            }
            while (currentStates.size > count) {
                currentStates.removeLast()
                saveRgbStates(currentStates)
            }

            val density = context.resources.displayMetrics.density
            val rowHeightPx = (40 * density).toInt()
            val marginPx = (8 * density).toInt()

            currentStates.forEachIndexed { index, statePair ->
                val rowLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setBackgroundResource(R.drawable.bg_input_outline)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeightPx).apply { bottomMargin = marginPx }
                }

                val paletteContainer = LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, rowHeightPx, 0.6f)
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                    val pad4 = (4 * density).toInt()
                    setPadding(pad4, 0, pad4, 0)
                }

                val recent = com.example.mqttpanelcraft.data.ColorHistoryManager.load(context)
                for (i in 0 until 5) {
                    val frame = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f) }
                    val circle = View(context).apply {
                        val cSize = (22 * density).toInt()
                        layoutParams = FrameLayout.LayoutParams(cSize, cSize, android.view.Gravity.CENTER)
                        setBackgroundResource(R.drawable.shape_circle_color)
                        val colorStr = if (i < recent.size) recent[i] else null
                        if (colorStr != null) {
                            backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorStr))
                            setOnClickListener {
                                currentStates[index] = currentStates[index].first to colorStr
                                saveRgbStates(currentStates)
                                updateRgbStateRows()
                            }
                        } else { visibility = View.INVISIBLE }
                    }
                    frame.addView(circle)
                    paletteContainer.addView(frame)
                }

                val pickerFrame = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f) }
                val picker = android.widget.ImageView(context).apply {
                    val pSize = (22 * density).toInt()
                    layoutParams = FrameLayout.LayoutParams(pSize, pSize, android.view.Gravity.CENTER)
                        setImageResource(R.drawable.ic_palette_open)
                    imageTintList = ColorStateList.valueOf(Color.parseColor(statePair.second))
                    setOnClickListener { anchor ->
                        var latest = statePair.second
                        ColorPickerDialog(context, latest, true, { c ->
                            latest = c
                            currentStates[index] = currentStates[index].first to c
                            saveRgbStates(currentStates)
                            onUpdate("rgb_states", currentStates.joinToString(",") { "${it.first}|${it.second}" })
                            imageTintList = ColorStateList.valueOf(Color.parseColor(c))
                        }, {
                            com.example.mqttpanelcraft.data.ColorHistoryManager.save(context, latest)
                            CommonPropBinder.notifyHistoryChanged()
                            updateRgbStateRows()
                        }).show(anchor)
                    }
                }
                pickerFrame.addView(picker)
                paletteContainer.addView(pickerFrame)

                val divider = View(context).apply {
                    val dHeight = (22 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams((1 * density).toInt(), dHeight)
                    setBackgroundColor(Color.parseColor("#E0E0E0"))
                }

                val et = android.widget.EditText(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.4f)
                    setText(statePair.first)
                    textSize = 13f
                    maxLines = 1
                    setSingleLine()
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((12 * density).toInt(), 0, 4, 0)
                    background = null
                    hint = context.getString(R.string.prop_label_led_keyword)
                    setTextColor(Color.parseColor("#334155"))
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            currentStates[index] = (s?.toString() ?: "") to currentStates[index].second
                            saveRgbStates(currentStates)
                        }
                    })
                }

                rowLayout.addView(paletteContainer)
                rowLayout.addView(divider)
                rowLayout.addView(et)
                containerRgbStates?.addView(rowLayout)
            }
        }

        val containerStepper = panelView.findViewById<LinearLayout>(R.id.containerRgbStepper)
        if (containerStepper != null && containerStepper.childCount == 0) {
            val btnMinus = android.widget.ImageView(context).apply { 
                setImageResource(R.drawable.ic_remove)
                imageTintList = ColorStateList.valueOf(Color.parseColor("#796383"))
                setPadding(18, 18, 18, 18)
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }
            val tvCount = TextView(context).apply { 
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setPadding(32, 8, 32, 8)
                setTextColor(Color.parseColor("#334155"))
            }
            val btnPlus = android.widget.ImageView(context).apply { 
                setImageResource(R.drawable.ic_add)
                imageTintList = ColorStateList.valueOf(Color.parseColor("#796383"))
                setPadding(18, 18, 18, 18)
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }
            containerStepper.addView(btnMinus); containerStepper.addView(tvCount); containerStepper.addView(btnPlus)
            
            fun updateStepperCountDisplay() { tvCount.text = (data.props["rgb_state_count"] ?: "2") }
            btnMinus.setOnClickListener {
                val c = (data.props["rgb_state_count"] ?: "2").toIntOrNull() ?: 2
                if (c > 1) { onUpdate("rgb_state_count", (c - 1).toString()); updateStepperCountDisplay(); updateRgbStateRows() }
            }
            btnPlus.setOnClickListener {
                val c = (data.props["rgb_state_count"] ?: "2").toIntOrNull() ?: 2
                if (c < 10) { onUpdate("rgb_state_count", (c + 1).toString()); updateStepperCountDisplay(); updateRgbStateRows() }
            }
            updateStepperCountDisplay()
        }
        updateRgbStateRows()

        CommonPropBinder.bindColorPalette(panelView, R.id.containerActiveColor, "active_color", data, onUpdate, context.getString(R.string.prop_label_led_color_active), "#4CAF50")
        CommonPropBinder.bindColorPalette(panelView, R.id.containerIdleColor, "idle_color", data, onUpdate, context.getString(R.string.properties_label_default), "#808080")

        val tgEffect = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleLedEffect)
        tgEffect?.check(if ((data.props["effect"] ?: "NONE") == "NONE") R.id.btnEffectNone else R.id.btnEffectBlink)
        tgEffect?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) onUpdate("effect", if (checkedId == R.id.btnEffectNone) "NONE" else "BLINK")
        }

        val styleOptions = listOf(context.getString(R.string.val_style_orb), context.getString(R.string.val_shape_circle_style), context.getString(R.string.val_shape_rounded_rect), context.getString(R.string.val_joystick_style_neon))
        val containerApprModeRow = panelView.findViewById<View>(R.id.containerApprModeRow)
        val containerNeonLabel = panelView.findViewById<View>(R.id.containerNeonLabel)
        
        fun updateStyleVisibility(styleKey: String) { 
            val isNeon = styleKey == "NEON_TEXT"
            containerApprModeRow?.visibility = if (styleKey == "ORB" || isNeon) View.GONE else View.VISIBLE 
            containerNeonLabel?.visibility = if (isNeon) View.VISIBLE else View.GONE
        }
        
        val styleMap = mapOf(context.getString(R.string.val_style_orb) to "ORB", context.getString(R.string.val_shape_circle_style) to "CONCENTRIC", context.getString(R.string.val_shape_rounded_rect) to "RADIUS_XL", context.getString(R.string.val_joystick_style_neon) to "NEON_TEXT")
        CommonPropBinder.bindDropdown(panelView, R.id.spLedStyle, "style", data, { k, v -> onUpdate(k, v); if (k == "style") updateStyleVisibility(v) }, styleOptions, styleMap, "ORB")
        updateStyleVisibility(data.props["style"] ?: "ORB")

        val spApprMode = panelView.findViewById<AutoCompleteTextView>(R.id.spPropApprMode)
        val modeOptions = listOf(context.getString(R.string.val_content_text), context.getString(R.string.val_content_icon), context.getString(R.string.val_content_both))
        spApprMode?.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, modeOptions))
        val curApprMode = data.props["appearance_mode"] ?: "text"
        spApprMode?.setText(modeOptions[when (curApprMode) { "icon" -> 1; "text_icon" -> 2; else -> 0 }], false)
        val containerText = panelView.findViewById<View>(R.id.containerPropText)
        val containerIcon = panelView.findViewById<View>(R.id.containerPropIcon)
        fun updateApprVisibility(mode: String) {
            containerText?.visibility = if (mode == "icon") View.GONE else View.VISIBLE
            containerIcon?.visibility = if (mode == "text") View.GONE else View.VISIBLE
            
            // V21.11: Gap between text and icon in text_icon mode
            val iconLayout = containerIcon?.layoutParams as? LinearLayout.LayoutParams
            iconLayout?.topMargin = if (mode == "text_icon") (6 * context.resources.displayMetrics.density).toInt() else (2 * context.resources.displayMetrics.density).toInt()
            containerIcon?.layoutParams = iconLayout
        }
        updateApprVisibility(curApprMode)
        spApprMode?.setOnItemClickListener { _, _, position, _ ->
            val m = when (position) { 1 -> "icon"; 2 -> "text_icon"; else -> "text" }
            onUpdate("appearance_mode", m); updateApprVisibility(m)
        }
        CommonPropBinder.bindEditText(panelView, R.id.etLedLabel, "label", data, onUpdate, "")
        CommonPropBinder.bindEditText(panelView, R.id.etNeonLabel, "label", data, onUpdate, "")
        val iconMap = mapOf(R.id.iconPreviewLED1 to "ic_btn_power", R.id.iconPreviewLED2 to "ic_btn_lighting", R.id.iconPreviewLED3 to "ic_btn_fan", R.id.iconPreviewLED4 to "ic_btn_play", R.id.iconPreviewLED5 to "ic_btn_tune", R.id.iconPreviewLED6 to "ic_btn_energy")
        iconMap.forEach { (id, key) -> panelView.findViewById<View>(id)?.setOnClickListener { onUpdate("icon", key) } }
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val led = getLed(view) ?: return
        led.style = when (data.props["style"]) {
            "CONCENTRIC" -> LedView.Style.CONCENTRIC
            "RADIUS_XL" -> LedView.Style.RADIUS_XL
            "NEON_TEXT" -> LedView.Style.NEON_TEXT
            "ICON_GLOW" -> LedView.Style.ICON_GLOW
            else -> LedView.Style.ORB
        }
        
        val isFirstLoad = view.getTag(R.id.tag_is_initialized) == null
        val oldRgbStates = view.getTag(R.id.tag_old_rgb_states) as? String
        val currentRgbStates = data.props["rgb_states"] ?: ""
        val oldActive = led.activeColor
        
        led.activeColor = try { Color.parseColor(data.props["active_color"] ?: "#4CAF50") } catch (e: Exception) { Color.parseColor("#4CAF50") }
        led.idleColor = try { Color.parseColor(data.props["idle_color"] ?: "#808080") } catch (e: Exception) { Color.parseColor("#808080") }

        // V21.12: Refined Preview Flash logic
        val logicMode = data.props["logic_mode"] ?: "standard"
        var shouldFlash = false
        var flashColor = led.activeColor
        
        if (!isFirstLoad) {
            if (logicMode == "standard" && led.activeColor != oldActive) {
                shouldFlash = true
                flashColor = led.activeColor
            } else if (logicMode == "rgb" && currentRgbStates != oldRgbStates) {
                shouldFlash = true
                // Try to find the color that was just edited (simplification: flash the latest updated logic state color)
                val states = currentRgbStates.split("|").filter { it.contains(":") }
                if (states.isNotEmpty()) {
                    val lastState = states.last()
                    val parts = lastState.split(":")
                    if (parts.size >= 2) {
                        flashColor = try { Color.parseColor(parts[1]) } catch(e: Exception) { led.activeColor }
                    }
                }
            }
        }

        if (shouldFlash) {
            val originalActive = led.activeColor
            led.activeColor = flashColor
            led.isActive = true
            previewRunnables[view]?.let { view.removeCallbacks(it) }
            val r = Runnable {
                led.isActive = false
                led.activeColor = originalActive // Restore
                view.invalidate()
                previewRunnables.remove(view)
            }
            previewRunnables[view] = r
            view.postDelayed(r, 600) // V21.12: Reduced from 3000 to 600 (One blink)
        }
        
        if (isFirstLoad) view.setTag(R.id.tag_is_initialized, true)
        view.setTag(R.id.tag_old_rgb_states, currentRgbStates)
        
        val apprMode = data.props["appearance_mode"] ?: "text"
        val isNeon = led.style == LedView.Style.NEON_TEXT
        
        if (isNeon || apprMode == "text" || apprMode == "text_icon") {
            led.label = data.props["label"] ?: ""
        } else {
            led.label = ""
        }
        
        if (!isNeon && (apprMode == "icon" || apprMode == "text_icon")) {
            led.iconResId = when (data.props["icon"]) {
                "ic_btn_power" -> R.drawable.ic_btn_power
                "ic_btn_lighting" -> R.drawable.ic_btn_lighting
                "ic_btn_fan" -> R.drawable.ic_btn_fan
                "ic_btn_play" -> R.drawable.ic_btn_play
                "ic_btn_tune" -> R.drawable.ic_btn_tune
                "ic_btn_energy" -> R.drawable.ic_btn_energy
                else -> 0
            }
        } else {
            led.iconResId = 0
        }
        
        val eff = data.props["effect"] ?: "NONE"
        led.effect = if (eff == "BLINK") LedView.Effect.BLINK else LedView.Effect.NONE
        
        led.invalidate()
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val led = getLed(view) ?: return
        val logicMode = data.props["led_logic_mode"] ?: "STANDARD"
        val actionMode = data.props["action_mode"] ?: "LATCH"
        val timerMs = (data.props["timer_ms"] ?: "3000").toLongOrNull() ?: 3000L
        val idStr = data.id.toString()
        val offRunnable = Runnable { led.isActive = false; view.invalidate() }

        var isMatched = false
        var matchedKwColor = data.props["active_color"] ?: "#4CAF50"

        if (logicMode == "STANDARD") {
            val kwsRaw = data.props["keywords"] ?: "ON|EXACT,1|EXACT,TRUE|EXACT"
            if (kwsRaw.isNotEmpty()) {
                for (entry in kwsRaw.split(",")) {
                    val parts = entry.split("|")
                    val kw = parts.getOrElse(0) { "" }
                    val matchType = parts.getOrElse(1) { "EXACT" }
                    if (kw.isEmpty()) continue
                    if (if (matchType == "CONTAINS") payload.contains(kw) else payload == kw) {
                        isMatched = true
                        matchedKwColor = data.props["active_color"] ?: "#4CAF50"
                        break
                    }
                }
            }
        } else {
            val rgbStatesStr = data.props["rgb_states"] ?: ""
            val matchType = data.props["rgb_match_type"] ?: "EXACT"
            if (rgbStatesStr.isNotEmpty()) {
                for (kwTuple in rgbStatesStr.split(",")) {
                    val parts = kwTuple.split("|")
                    if (parts.size >= 2) {
                        val keywordText = parts[0]; val color = parts[1]
                        if (if (matchType == "EXACT") payload == keywordText else payload.contains(keywordText)) {
                            isMatched = true; matchedKwColor = color; break
                        }
                    }
                }
            }
        }

        if (isMatched) {
            led.activeColor = try { Color.parseColor(matchedKwColor) } catch (e: Exception) { Color.parseColor("#4CAF50") }
            led.isActive = true
            if (actionMode == "TIMER") {
                timerMap[idStr]?.let { view.removeCallbacks(it) }
                view.postDelayed(offRunnable, timerMs)
                timerMap[idStr] = offRunnable
            } else { timerMap[idStr]?.let { view.removeCallbacks(it) } }
        } else {
            if (actionMode == "LATCH") {
                val restoreKws = data.props["restore_keywords"] ?: "OFF|EXACT,0|EXACT,FALSE|EXACT"
                if (restoreKws.isNotEmpty()) {
                    for (entry in restoreKws.split(",")) {
                        val parts = entry.split("|")
                        val kw = parts.getOrElse(0) { "" }
                        val matchType = parts.getOrElse(1) { "EXACT" }
                        if (kw.isEmpty()) continue
                        if (if (matchType == "CONTAINS") payload.contains(kw) else payload == kw) {
                            led.isActive = false; break
                        }
                    }
                }
            }
        }
        view.invalidate()
    }

    private val previewRunnables = mutableMapOf<View, Runnable>()
}
