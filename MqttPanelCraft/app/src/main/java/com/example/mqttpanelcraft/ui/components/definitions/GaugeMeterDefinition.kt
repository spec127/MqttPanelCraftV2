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
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.findComponentTarget
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.GaugeMeterView
import com.google.android.material.button.MaterialButtonToggleGroup

object GaugeMeterDefinition : IComponentDefinition {
    override val type: String = "GAUGE_METER"
    override val defaultSize: Size = Size(200, 200)
    override val labelPrefix: String = "gauge"
    override val iconResId: Int = android.R.drawable.ic_menu_compass // 找個像圓形儀表的icon
    override val group: String = "SENSOR"
    override val propertiesLayoutId: Int = R.layout.layout_prop_gauge_meter

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val meter = GaugeMeterView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            tag = "target"
        }
        container.addView(meter)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val meter = view.findComponentTarget<GaugeMeterView>() ?: return

        meter.minValue = data.props["min"]?.toFloatOrNull() ?: 0f
        meter.maxValue = data.props["max"]?.toFloatOrNull() ?: 100f
        meter.value = data.props["value"]?.toFloatOrNull() ?: meter.minValue
        meter.unit = data.props["unit"] ?: ""

        val styleStr = data.props["style"] ?: "NEEDLE"
        meter.meterStyle = try { GaugeMeterView.Style.valueOf(styleStr) } catch (e: Exception) { GaugeMeterView.Style.NEEDLE }

        val angleStr = data.props["angle"] ?: "ARC_270"
        meter.trackAngle = try { GaugeMeterView.TrackAngle.valueOf(angleStr) } catch (e: Exception) { GaugeMeterView.TrackAngle.ARC_270 }

        data.props["theme_color"]?.let { c -> try { meter.themeColor = Color.parseColor(c) } catch (e: Exception) {} }

        // Threshold Logic
        meter.thresholdMode = (data.props["threshold_mode"] ?: "false").toBoolean()
        val effectStr = data.props["threshold_effect"] ?: "VALUE_CHANGE"
        meter.thresholdEffect = try { GaugeMeterView.ThresholdEffect.valueOf(effectStr) } catch (e: Exception) { GaugeMeterView.ThresholdEffect.VALUE_CHANGE }

        // Parse thresholds
        val thresholds = mutableListOf<Pair<Float, Int>>()
        data.props["rgb_states"]?.split(",")?.forEach {
            val parts = it.split("|")
            if (parts.size == 2) {
                val th = parts[0].toFloatOrNull()
                val color = try { Color.parseColor(parts[1]) } catch (e: Exception) { null }
                if (th != null && color != null) {
                    thresholds.add(Pair(th, color))
                }
            }
        }
        meter.thresholds = thresholds
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val ctx = panelView.context

        // Style Selector
        val acStyle = panelView.findViewById<AutoCompleteTextView>(R.id.acMeterStyle)
        val styleAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, listOf("指針 (Needle)", "陣列 (Segmented)"))
        acStyle.setAdapter(styleAdapter)
        val currentStyle = data.props["style"] ?: "NEEDLE"
        acStyle.setText(if (currentStyle == "NEEDLE") "指針 (Needle)" else "陣列 (Segmented)", false)
        acStyle.setOnItemClickListener { _, _, pos, _ ->
            val newValue = if (pos == 0) "NEEDLE" else "SEGMENTED"
            onUpdate("style", newValue)
        }

        // Angle Selector
        val acAngle = panelView.findViewById<AutoCompleteTextView>(R.id.acTrackAngle)
        val angleAdapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, listOf("120°", "180°", "270°"))
        acAngle.setAdapter(angleAdapter)
        val currentAngle = data.props["angle"] ?: "ARC_270"
        val angleStr = when (currentAngle) {
            "ARC_120" -> "120°"
            "ARC_180" -> "180°"
            else -> "270°"
        }
        acAngle.setText(angleStr, false)
        acAngle.setOnItemClickListener { _, _, pos, _ ->
            val newValue = when (pos) {
                0 -> "ARC_120"
                1 -> "ARC_180"
                else -> "ARC_270"
            }
            onUpdate("angle", newValue)
        }

        // Basic Bounds & Unit
        CommonPropBinder.bindEditText(panelView, R.id.etMin, "min", data, onUpdate, "0")
        CommonPropBinder.bindEditText(panelView, R.id.etMax, "max", data, onUpdate, "100")
        CommonPropBinder.bindEditText(panelView, R.id.etUnit, "unit", data, onUpdate, "")
        
        CommonPropBinder.bindColorPalette(panelView, R.id.containerThemeColor, "theme_color", data, onUpdate, "主體顏色", "#4CAF50")

        // Threshold Mode
        val isThresholdMode = (data.props["threshold_mode"] ?: "false").toBoolean()
        val checkThresholdMode = panelView.findViewById<ImageView>(R.id.checkThresholdMode)
        val containerThresholdOptions = panelView.findViewById<LinearLayout>(R.id.containerThresholdOptions)
        
        checkThresholdMode.setImageResource(R.drawable.ic_check_circle_premium)
        checkThresholdMode.visibility = if (isThresholdMode) View.VISIBLE else View.INVISIBLE
        containerThresholdOptions.visibility = if (isThresholdMode) View.VISIBLE else View.GONE
        
        panelView.findViewById<View>(R.id.itemThresholdMode).setOnClickListener {
            val newVal = !isThresholdMode
            onUpdate("threshold_mode", newVal.toString())
        }

        if (isThresholdMode) {
            val toggleEffect = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleThresholdEffect)
            val currentEffect = data.props["threshold_effect"] ?: "VALUE_CHANGE"
            if (currentEffect == "GRADIENT") toggleEffect.check(R.id.btnEffectGradient)
            else toggleEffect.check(R.id.btnEffectSolid)

            toggleEffect.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val effect = if (checkedId == R.id.btnEffectGradient) "GRADIENT" else "VALUE_CHANGE"
                    onUpdate("threshold_effect", effect)
                }
            }
            
            // Delegate RGB States rendering
            // 由於 ScaleMeter 的 rgb_states 面板寫在其內部，我們在此先以最簡陋的方式或暫時略過複雜 UI，
            // 為了不阻擋編譯，我們先將綁定註解，後續再實作動態行。
            // TODO: Implement standalone threshold row generator for GaugeMeter

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
        val meter = view.findComponentTarget<GaugeMeterView>() ?: return
        try {
            val v = payload.toFloat()
            meter.value = v
            onUpdateProp("value", v.toString())
        } catch (_: Exception) {}
    }
}
