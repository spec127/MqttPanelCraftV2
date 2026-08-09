package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButtonToggleGroup
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.ImageDisplayView

/**
 * 影像(圖片與串流)感測器定義元件 (ImageSensorDefinition)
 * 支援 MQTT 影像接收、單次照片 / FPS 串流控制、快照定時器與手動點擊更新。
 */
object ImageSensorDefinition : IComponentDefinition {
    override val type: String = "IMAGE_SENSOR"
    override val defaultSize: Size = Size(200, 150)
    override val labelPrefix: String = "cam"
    override val iconResId: Int = android.R.drawable.ic_menu_camera
    override val group: String = "SENSOR"
    override val propertiesLayoutId: Int = R.layout.layout_prop_image

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "title" to "影像",
        "topic" to "home/cam/stream",
        "gesture_zoom" to "true",
        "quick_save" to "true",
        "rotation" to "0",
        "scale_type" to "FIT_CENTER",
        "show_info" to "true",
        "stream_mode" to "SINGLE",
        "stream_fps" to "2",
        "trigger_type" to "TIMER",
        "timer_interval" to "3000",
        "color" to "#FF9800"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val imageDisplayView = ImageDisplayView(context).apply {
            tag = "target_img_view"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            this.isEditMode = isEditMode
            placeholderIconResId = R.drawable.ic_camera_placeholder
        }
        container.addView(imageDisplayView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val imgView = view.findViewWithTag<ImageDisplayView>("target_img_view") ?: return
        imgView.gestureZoomEnabled = (data.props["gesture_zoom"] ?: "true") == "true"
        imgView.showQuickSave = (data.props["quick_save"] ?: "true") == "true"
        imgView.rotationAngle = (data.props["rotation"] ?: "0").toIntOrNull() ?: 0
        imgView.setScaleMode(data.props["scale_type"] ?: "FIT_CENTER")
        imgView.showInfo = (data.props["show_info"] ?: "true") == "true"
        imgView.streamMode = data.props["stream_mode"] ?: "SINGLE"
        imgView.fps = data.props["stream_fps"] ?: "2"

        // Restore cached image from properties
        imgView.placeholderIconResId = R.drawable.ic_camera_placeholder
        val savedTime = data.props["image_time"] ?: ""
        imgView.lastReceivedTime = savedTime
        val cachedImage = data.props["value"]
        if (!cachedImage.isNullOrEmpty()) {
            imgView.updatePayload(cachedImage, isNewArrival = false)
        } else {
            imgView.clearCurrentBitmap()
        }

        // Apply theme color
        val colorHex = data.props["color"] ?: "#FF9800"
        try {
            imgView.setThemeColor(android.graphics.Color.parseColor(colorHex))
        } catch (_: Exception) {}
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // Color Palette binding
        CommonPropBinder.bindColorPalette(
            panelView,
            R.id.containerColorPalette,
            "color",
            data,
            onUpdate,
            label = "整體顏色",
            defaultColor = "#FF9800"
        )

        // Premium Check Rows: Gesture Zoom, Quick Save, Show Info
        var hasGesture = (data.props["gesture_zoom"] ?: "true") == "true"
        val checkGesture = panelView.findViewById<ImageView>(R.id.checkGestureZoom)
        checkGesture?.visibility = if (hasGesture) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemGestureZoom)?.setOnClickListener {
            hasGesture = !hasGesture
            checkGesture?.visibility = if (hasGesture) View.VISIBLE else View.INVISIBLE
            onUpdate("gesture_zoom", hasGesture.toString())
        }

        var hasSave = (data.props["quick_save"] ?: "true") == "true"
        val checkSave = panelView.findViewById<ImageView>(R.id.checkQuickSave)
        checkSave?.visibility = if (hasSave) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemQuickSave)?.setOnClickListener {
            hasSave = !hasSave
            checkSave?.visibility = if (hasSave) View.VISIBLE else View.INVISIBLE
            onUpdate("quick_save", hasSave.toString())
        }

        var hasInfo = (data.props["show_info"] ?: "true") == "true"
        val checkInfo = panelView.findViewById<ImageView>(R.id.checkShowInfo)
        checkInfo?.visibility = if (hasInfo) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemShowInfo)?.setOnClickListener {
            hasInfo = !hasInfo
            checkInfo?.visibility = if (hasInfo) View.VISIBLE else View.INVISIBLE
            onUpdate("show_info", hasInfo.toString())
        }

        // Rotation
        val spRot = panelView.findViewById<AutoCompleteTextView>(R.id.spImageRot)
        if (spRot != null) {
            val rotOptions = listOf(
                context.getString(R.string.val_rot_0),
                context.getString(R.string.val_rot_90),
                context.getString(R.string.val_rot_180),
                context.getString(R.string.val_rot_270)
            )
            val rotValues = listOf("0", "90", "180", "270")
            spRot.setAdapter(ArrayAdapter(context, R.layout.list_item_dropdown, rotOptions))
            val curRot = data.props["rotation"] ?: "0"
            val idx = rotValues.indexOf(curRot).coerceAtLeast(0)
            spRot.setText(rotOptions[idx], false)
            spRot.setOnItemClickListener { _, _, pos, _ ->
                onUpdate("rotation", rotValues[pos])
            }
        }

        // Scale Mode (Left-Right Cycler)
        val tvScale = panelView.findViewById<android.widget.TextView>(R.id.tvScaleValue)
        val btnScalePrev = panelView.findViewById<android.view.View>(R.id.btnScalePrev)
        val btnScaleNext = panelView.findViewById<android.view.View>(R.id.btnScaleNext)
        if (tvScale != null && btnScalePrev != null && btnScaleNext != null) {
            val scaleLabels = listOf(
                context.getString(R.string.val_image_scale_fit),
                context.getString(R.string.val_image_scale_crop),
                context.getString(R.string.val_image_scale_xy)
            )
            val scaleValues = listOf("FIT_CENTER", "CENTER_CROP", "FIT_XY")
            
            fun updateScaleUI() {
                val curScale = data.props["scale_type"] ?: "FIT_CENTER"
                val idx = scaleValues.indexOf(curScale).coerceAtLeast(0)
                tvScale.text = scaleLabels[idx]
            }
            updateScaleUI()

            btnScalePrev.setOnClickListener {
                val curScale = data.props["scale_type"] ?: "FIT_CENTER"
                var idx = scaleValues.indexOf(curScale)
                idx = if (idx - 1 < 0) scaleValues.size - 1 else idx - 1
                onUpdate("scale_type", scaleValues[idx])
                updateScaleUI()
            }
            btnScaleNext.setOnClickListener {
                val curScale = data.props["scale_type"] ?: "FIT_CENTER"
                var idx = scaleValues.indexOf(curScale)
                idx = (idx + 1) % scaleValues.size
                onUpdate("scale_type", scaleValues[idx])
                updateScaleUI()
            }
        }
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val imageDisplayView = view.findViewWithTag<ImageDisplayView>("target_img_view") ?: return
        imageDisplayView.isEditMode = false
        // Persist reassembled image string and timestamp when a full frame is decoded
        imageDisplayView.onImageReassembled = { fullB64, timeStr ->
            onUpdateProp("value", fullB64)
            onUpdateProp("image_time", timeStr)
        }
        imageDisplayView.onImageCleared = {
            onUpdateProp("value", "")
            onUpdateProp("image_time", "")
        }
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val imageDisplayView = view.findViewWithTag<ImageDisplayView>("target_img_view") ?: return
        imageDisplayView.updatePayload(payload, isNewArrival = true)
    }
}
