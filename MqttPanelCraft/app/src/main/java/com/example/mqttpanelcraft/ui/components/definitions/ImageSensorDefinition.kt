package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.views.ImageDisplayView

/**
 * 影像監控/顯示元件 (ImageSensorDefinition / cam)
 *
 * Design Intent:
 * 作為 Sensor 感測器類別下的影像接收端，專門用於顯示 IoT 設備（例如 ESP32-CAM）傳來的 Base64 JPEG/PNG 連續幀影像或單張圖片。
 * 支援手勢縮放、旋轉校正、快照相簿儲存、與圖片資訊橫幅（時間與解析度）。
 */
object ImageSensorDefinition : IComponentDefinition {

    override val type: String = "IMAGE_SENSOR"
    override val defaultSize: Size = Size(220, 160)
    override val labelPrefix: String = "cam"
    override val iconResId: Int = android.R.drawable.ic_menu_camera
    override val group: String = "SENSOR"

    override val propertiesLayoutId: Int = R.layout.layout_prop_image

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "gesture_zoom" to "true",
        "quick_save" to "true",
        "show_info" to "true",
        "rotation" to "0",
        "scale_type" to "FIT_CENTER"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val imageDisplayView = ImageDisplayView(context).apply {
            tag = "target_image_display"
            this.isEditMode = isEditMode
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(imageDisplayView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val imageDisplayView = view.findViewWithTag<ImageDisplayView>("target_image_display") ?: return

        imageDisplayView.gestureZoomEnabled = (data.props["gesture_zoom"] ?: "true") == "true"
        imageDisplayView.showQuickSave = (data.props["quick_save"] ?: "true") == "true"
        imageDisplayView.showInfo = (data.props["show_info"] ?: "true") == "true"
        imageDisplayView.rotationAngle = (data.props["rotation"] ?: "0").toIntOrNull() ?: 0
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val cbGestureZoom = panelView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbGestureZoom)
        val cbQuickSave = panelView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbQuickSave)
        val cbShowInfo = panelView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbShowInfo)

        cbGestureZoom?.isChecked = (data.props["gesture_zoom"] ?: "true") == "true"
        cbGestureZoom?.setOnCheckedChangeListener { _, isChecked ->
            onUpdate("gesture_zoom", isChecked.toString())
        }

        cbQuickSave?.isChecked = (data.props["quick_save"] ?: "true") == "true"
        cbQuickSave?.setOnCheckedChangeListener { _, isChecked ->
            onUpdate("quick_save", isChecked.toString())
        }

        cbShowInfo?.isChecked = (data.props["show_info"] ?: "true") == "true"
        cbShowInfo?.setOnCheckedChangeListener { _, isChecked ->
            onUpdate("show_info", isChecked.toString())
        }

        val context = panelView.context
        val tvRotation = panelView.findViewById<AutoCompleteTextView>(R.id.tvImageRotation)
        if (tvRotation != null) {
            val rotOptions = listOf("0°", "90°", "180°", "270°")
            val rotValues = listOf("0", "90", "180", "270")
            tvRotation.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, rotOptions))
            val curRot = data.props["rotation"] ?: "0"
            val idx = rotValues.indexOf(curRot).coerceAtLeast(0)
            tvRotation.setText(rotOptions[idx], false)
            tvRotation.setOnItemClickListener { _, _, position, _ ->
                onUpdate("rotation", rotValues[position])
            }
        }

        val tvScale = panelView.findViewById<AutoCompleteTextView>(R.id.tvImageScaleMode)
        if (tvScale != null) {
            val scaleOptions = listOf("FIT_CENTER", "CENTER_CROP", "FIT_XY")
            tvScale.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, scaleOptions))
            val curScale = data.props["scale_type"] ?: "FIT_CENTER"
            val idx = scaleOptions.indexOf(curScale).coerceAtLeast(0)
            tvScale.setText(scaleOptions[idx], false)
            tvScale.setOnItemClickListener { _, _, position, _ ->
                onUpdate("scale_type", scaleOptions[position])
            }
        }
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val imageDisplayView = view.findViewWithTag<ImageDisplayView>("target_image_display") ?: return
        imageDisplayView.isEditMode = false
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val imageDisplayView = view.findViewWithTag<ImageDisplayView>("target_image_display") ?: return
        imageDisplayView.updatePayload(payload)
    }
}
