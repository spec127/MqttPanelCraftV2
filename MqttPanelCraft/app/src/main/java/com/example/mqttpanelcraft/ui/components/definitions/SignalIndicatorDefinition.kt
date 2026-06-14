package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.Color
import android.util.Size
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.findComponentTarget
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.SignalIndicatorView
import com.google.android.material.button.MaterialButtonToggleGroup

object SignalIndicatorDefinition : IComponentDefinition {
    override val type: String = "SIGNAL_INDICATOR"
    override val defaultSize: Size = Size(125, 100)
    override val labelPrefix: String = "signal"
    override val iconResId: Int = android.R.drawable.ic_menu_sort_by_size
    override val group: String = "SENSOR"
    override val propertiesLayoutId: Int = R.layout.layout_prop_signal_indicator

    // Remove getDefaultProps since it is not part of IComponentDefinition

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val indicator = SignalIndicatorView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            tag = "target"
        }
        container.addView(indicator)
        
        // Provide defaults here if needed, but registry usually handles it
        // Initialize default props if not already set (for fresh creation)
        if (!isEditMode && indicator.value == 0f && indicator.iconStyle == SignalIndicatorView.IconStyle.BATTERY) {
            indicator.iconStyle = SignalIndicatorView.IconStyle.CELLULAR
        }
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val indicator = view.findComponentTarget<SignalIndicatorView>() ?: return

        indicator.minValue = data.props["min"]?.toFloatOrNull() ?: 0f
        indicator.maxValue = data.props["max"]?.toFloatOrNull() ?: 100f
        
        val mappingStr = data.props["value_mapping"] ?: "RATIO"
        indicator.valueMapping = try { SignalIndicatorView.ValueMapping.valueOf(mappingStr) } catch (e: Exception) { SignalIndicatorView.ValueMapping.RATIO }

        val isBackFive = indicator.iconStyle in listOf(
            SignalIndicatorView.IconStyle.ARROWS_LEFT,
            SignalIndicatorView.IconStyle.ARROWS_RIGHT,
            SignalIndicatorView.IconStyle.STARS,
            SignalIndicatorView.IconStyle.HEARTS,
            SignalIndicatorView.IconStyle.DROPS
        )
        val defaultMaxLevels = if (isBackFive) 5 else 4
        
        if (indicator.valueMapping == SignalIndicatorView.ValueMapping.ABSOLUTE) {
            indicator.maxLevels = data.props["maxLevels"]?.toIntOrNull() ?: defaultMaxLevels
        } else {
            indicator.maxLevels = data.props["maxLevels"]?.toIntOrNull() ?: defaultMaxLevels
        }

        indicator.value = data.props["value"]?.toFloatOrNull() ?: 60f
        indicator.showValue = (data.props["show_value"] ?: "false").toBoolean()

        val styleStr = data.props["icon_style"] ?: "CELLULAR"
        indicator.iconStyle = try { SignalIndicatorView.IconStyle.valueOf(styleStr) } catch (e: Exception) { SignalIndicatorView.IconStyle.CELLULAR }

        data.props["theme_color"]?.let { c -> try { indicator.themeColor = Color.parseColor(c) } catch (e: Exception) {} }
        data.props["color_start"]?.let { c -> try { indicator.colorStart = Color.parseColor(c) } catch (e: Exception) {} }
        data.props["color_end"]?.let { c -> try { indicator.colorEnd = Color.parseColor(c) } catch (e: Exception) {} }

        val cmStr = data.props["color_mode"] ?: "SOLID"
        indicator.colorMode = try { SignalIndicatorView.ColorMode.valueOf(cmStr) } catch (e: Exception) { SignalIndicatorView.ColorMode.SOLID }

        val alarmEnabled = (data.props["alarm_enabled"] ?: "false").toBoolean()
        if (alarmEnabled) {
            indicator.alarmType = try { SignalIndicatorView.AlarmType.valueOf(data.props["alarm_type"] ?: "LOW") } catch (e: Exception) { SignalIndicatorView.AlarmType.LOW }
            indicator.alarmThreshold = data.props["alarm_threshold"]?.toFloatOrNull() ?: 4f
            indicator.alarmDuration = data.props["alarm_duration"]?.toFloatOrNull() ?: 3f
            indicator.alarmEnabled = true
        } else {
            indicator.alarmEnabled = false
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val ctx = panelView.context

        val currentStyleStr = data.props["icon_style"] ?: "CELLULAR"
        
        val lastFiveStyles = listOf(
            SignalIndicatorView.IconStyle.ARROWS_LEFT,
            SignalIndicatorView.IconStyle.ARROWS_RIGHT,
            SignalIndicatorView.IconStyle.STARS,
            SignalIndicatorView.IconStyle.HEARTS,
            SignalIndicatorView.IconStyle.DROPS
        )
        val isBackFiveCurrent = (SignalIndicatorView.IconStyle.valueOf(currentStyleStr) in lastFiveStyles)

        val defaultMin = if (isBackFiveCurrent) "1" else "0"
        val defaultMax = if (isBackFiveCurrent) "5" else "100"

        CommonPropBinder.bindEditText(panelView, R.id.etMin, "min", data, onUpdate, defaultMin)
        CommonPropBinder.bindEditText(panelView, R.id.etMax, "max", data, onUpdate, defaultMax)

        // Icon Style Horizontal Selector
        val containerIconStyles = panelView.findViewById<LinearLayout>(R.id.containerIconStyles)
        containerIconStyles.removeAllViews()

        SignalIndicatorView.IconStyle.values().forEach { style ->
            val wrapper = FrameLayout(ctx).apply {
                val density = ctx.resources.displayMetrics.density
                val size = (32 * density).toInt() // smaller to fit 10
                val marginParams = LinearLayout.LayoutParams(0, size, 1f)
                layoutParams = marginParams
                
                val isSelected = style.name == currentStyleStr
                setBackgroundColor(Color.TRANSPARENT)
                setPadding((2*density).toInt(), (2*density).toInt(), (2*density).toInt(), (2*density).toInt())
                
                val iconView = SignalIndicatorView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(-1, -1).apply {
                        val p = (2 * density).toInt()
                        setMargins(p, p, p, p)
                    }
                    iconStyle = style
                    
                    if (style in lastFiveStyles) {
                        maxLevels = 1
                        value = 100f
                    } else {
                        maxLevels = 4
                        value = 100f
                    }
                    
                    themeColor = if (isSelected) Color.parseColor("#2196F3") else Color.parseColor("#757575")
                    colorMode = SignalIndicatorView.ColorMode.SOLID
                    showValue = false
                    alarmEnabled = false
                }
                addView(iconView)
                
                setOnClickListener {
                    onUpdate("icon_style", style.name)
                    val isBack = style in lastFiveStyles
                    onUpdate("maxLevels", if (isBack) "5" else "4")
                    onUpdate("min", if (isBack) "1" else "0")
                    onUpdate("max", if (isBack) "5" else "100")
                    
                    // Instantly update the UI colors
                    for (i in 0 until containerIconStyles.childCount) {
                        val w = containerIconStyles.getChildAt(i) as FrameLayout
                        val icon = w.getChildAt(0) as SignalIndicatorView
                        val s = SignalIndicatorView.IconStyle.values()[i]
                        icon.themeColor = if (s == style) Color.parseColor("#2196F3") else Color.parseColor("#757575")
                    }
                }
            }
            containerIconStyles.addView(wrapper)
        }

        // Value Mapping Toggle
        val toggleMapping = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleMapping)
        val currentMapping = data.props["value_mapping"] ?: "RATIO"
        val isAbsolute = currentMapping == "ABSOLUTE"
        
        toggleMapping.check(if (isAbsolute) R.id.btnMapAbsolute else R.id.btnMapRatio)
        
        val tvAbsoluteDesc = panelView.findViewById<TextView>(R.id.tvAbsoluteDesc)
        val isBackFiveFlag = (SignalIndicatorView.IconStyle.valueOf(data.props["icon_style"] ?: "CELLULAR") in lastFiveStyles)
        tvAbsoluteDesc.text = if (isBackFiveFlag) "絕對數值模式下，數值 1~5 將對應點亮 1~5 個圖示數量。" else "絕對數值模式下，數值 0~4 將對應圖示 5 個階段（0為全滅）。"
        val tvRatioDesc = panelView.findViewById<TextView>(R.id.tvRatioDesc)
        val containerRatioInputs = panelView.findViewById<View>(R.id.containerRatioInputs)
        
        val htmlStr = if (isBackFiveFlag) "最大最小值將均分為 <font color='#EF4444'>1~5</font> 共 5 個階段。" else "最大最小值將均分為 <font color='#EF4444'>0~4</font> 共 5 個階段。"
        tvRatioDesc.text = android.text.Html.fromHtml(htmlStr)
        
        tvAbsoluteDesc.visibility = if (isAbsolute) View.VISIBLE else View.GONE
        tvRatioDesc.visibility = if (isAbsolute) View.GONE else View.VISIBLE
        containerRatioInputs.visibility = if (isAbsolute) View.GONE else View.VISIBLE
        
        toggleMapping.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMapping = if (checkedId == R.id.btnMapAbsolute) "ABSOLUTE" else "RATIO"
                onUpdate("value_mapping", newMapping)
                val newAbs = newMapping == "ABSOLUTE"
                tvAbsoluteDesc.visibility = if (newAbs) View.VISIBLE else View.GONE
                tvRatioDesc.visibility = if (newAbs) View.GONE else View.VISIBLE
                containerRatioInputs.visibility = if (newAbs) View.GONE else View.VISIBLE
            }
        }

        // Show Value Switch
        val showValueKey = "show_value"
        val itemShowValue = panelView.findViewById<View>(R.id.itemShowValue)
        val checkShowValue = panelView.findViewById<ImageView>(R.id.checkShowValue)
        val isShowValue = (data.props[showValueKey] ?: "false").toBoolean()
        checkShowValue.visibility = if (isShowValue) View.VISIBLE else View.INVISIBLE
        itemShowValue.setOnClickListener {
            val newVal = !(data.props[showValueKey] ?: "false").toBoolean()
            checkShowValue.visibility = if (newVal) View.VISIBLE else View.INVISIBLE
            onUpdate(showValueKey, newVal.toString())
        }

        // Alarm Config
        val itemAlarmEnable = panelView.findViewById<View>(R.id.itemAlarmEnable)
        val checkAlarmEnable = panelView.findViewById<ImageView>(R.id.checkAlarmEnable)
        val containerAlarmSettings = panelView.findViewById<View>(R.id.containerAlarmSettings)
        
        val isAlarmEnabled = (data.props["alarm_enabled"] ?: "false").toBoolean()
        checkAlarmEnable.visibility = if (isAlarmEnabled) View.VISIBLE else View.INVISIBLE
        containerAlarmSettings.visibility = if (isAlarmEnabled) View.VISIBLE else View.GONE
        
        itemAlarmEnable.setOnClickListener {
            val newVal = !(data.props["alarm_enabled"] ?: "false").toBoolean()
            checkAlarmEnable.visibility = if (newVal) View.VISIBLE else View.INVISIBLE
            containerAlarmSettings.visibility = if (newVal) View.VISIBLE else View.GONE
            onUpdate("alarm_enabled", newVal.toString())
        }
        
        val toggleAlarmMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleAlarmMode)
        val alarmTypeStr = data.props["alarm_type"] ?: "LOW"
        toggleAlarmMode.check(if (alarmTypeStr == "HIGH") R.id.btnAlarmHigh else R.id.btnAlarmLow)
        
        toggleAlarmMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newType = if (checkedId == R.id.btnAlarmHigh) "HIGH" else "LOW"
                onUpdate("alarm_type", newType)
            }
        }
        
        CommonPropBinder.bindEditText(panelView, R.id.etAlarmDuration, "alarm_duration", data, onUpdate, "3")
        CommonPropBinder.bindEditText(panelView, R.id.etAlarmThreshold, "alarm_threshold", data, onUpdate, "4")

        // Color Mode
        val spinnerColorMode = panelView.findViewById<AutoCompleteTextView>(R.id.spinnerColorMode)
        val colorModes = arrayOf("純色", "階段", "漸變")
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, colorModes)
        spinnerColorMode.setAdapter(adapter)
        
        val cmStr = data.props["color_mode"] ?: "SOLID"
        val cmIndex = when(cmStr) { "STEP" -> 1; "GRADIENT" -> 2; else -> 0 }
        spinnerColorMode.setText(colorModes[cmIndex], false)
        
        val containerColorEnd = panelView.findViewById<View>(R.id.containerColorEnd)
        
        fun updateColorPickersVisibility(mode: String) {
            val isSolid = mode == "SOLID"
            if (isSolid) {
                containerColorEnd.visibility = View.GONE
            } else {
                containerColorEnd.visibility = View.VISIBLE
            }
            CommonPropBinder.bindColorPalette(panelView, R.id.containerColorStart, "theme_color", data, onUpdate, if (isSolid) "主體顏色" else "最小顏色", "#FF9800")
            if (!isSolid) {
                CommonPropBinder.bindColorPalette(panelView, R.id.containerColorEnd, "color_end", data, onUpdate, "最大顏色", "#F44336")
            }
        }
        updateColorPickersVisibility(cmStr)
        
        spinnerColorMode.setOnItemClickListener { _, _, position, _ ->
            val newMode = when(position) { 1 -> "STEP"; 2 -> "GRADIENT"; else -> "SOLID" }
            onUpdate("color_mode", newMode)
            updateColorPickersVisibility(newMode)
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
        updateProp: (key: String, value: String) -> Unit
    ) {
        val floatVal = payload.toFloatOrNull()
        if (floatVal != null) {
            updateProp("value", floatVal.toString())
        }
    }
}
