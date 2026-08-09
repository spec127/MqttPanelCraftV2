package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.views.ImageDisplayView
import android.widget.ImageView

/**
 * 影像顯示元件 (ImageDisplayDefinition)
 *
 * Design Intent:
 * 針對新手外網控制設計的高效純粹 MQTT 影像監控元件，支援接收 Base64/二進制影像即時顯示、
 * 雙指或雙擊放大、視角旋轉校正、一鍵快照存入相簿與圖片時間像素資訊切換顯示。
 */
object ImageDisplayDefinition : IComponentDefinition {

    override val type: String = "IMAGE"
    override val defaultSize: Size = Size(160, 120)
    override val labelPrefix: String = "img"
    override val iconResId: Int = android.R.drawable.ic_menu_gallery
    override val group: String = "DISPLAY"

    override val propertiesLayoutId: Int = R.layout.layout_prop_image

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "title" to "影像",
        "topic" to "home/cam/image",
        "gesture_zoom" to "true",
        "quick_save" to "true",
        "rotation" to "0",
        "scale_mode" to "FIT_CENTER",
        "show_info" to "true",
        "color" to "#FF9800"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val imageDisplayView = ImageDisplayView(context).apply {
            tag = "target_img_view"
            this.isEditMode = isEditMode
        }
        container.addView(imageDisplayView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val imgView = view.findViewWithTag<ImageDisplayView>("target_img_view") ?: return

        imgView.gestureZoomEnabled = (data.props["gesture_zoom"] ?: "true") == "true"
        imgView.showQuickSave = (data.props["quick_save"] ?: "true") == "true"
        imgView.rotationAngle = (data.props["rotation"] ?: "0").toIntOrNull() ?: 0
        imgView.setScaleMode(data.props["scale_mode"] ?: "FIT_CENTER")
        imgView.showInfo = (data.props["show_info"] ?: "true") == "true"

        // Restore cached image from properties
        imgView.placeholderIconResId = R.drawable.ic_image_placeholder
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

        // 1. Gesture Zoom Premium Check Row
        var hasGesture = (data.props["gesture_zoom"] ?: "true") == "true"
        val checkGesture = panelView.findViewById<ImageView>(R.id.checkGestureZoom)
        checkGesture?.visibility = if (hasGesture) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemGestureZoom)?.setOnClickListener {
            hasGesture = !hasGesture
            checkGesture?.visibility = if (hasGesture) View.VISIBLE else View.INVISIBLE
            onUpdate("gesture_zoom", hasGesture.toString())
        }

        // 2. Quick Save Premium Check Row
        var hasSave = (data.props["quick_save"] ?: "true") == "true"
        val checkSave = panelView.findViewById<ImageView>(R.id.checkQuickSave)
        checkSave?.visibility = if (hasSave) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemQuickSave)?.setOnClickListener {
            hasSave = !hasSave
            checkSave?.visibility = if (hasSave) View.VISIBLE else View.INVISIBLE
            onUpdate("quick_save", hasSave.toString())
        }

        // Color Palette binding
        com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder.bindColorPalette(
            panelView,
            R.id.containerColorPalette,
            "color",
            data,
            onUpdate,
            defaultColor = "#FF9800"
        )

        // 3. Rotation Angle Dropdown (視覺外觀)
        val rotAuto = panelView.findViewById<AutoCompleteTextView>(R.id.spImageRot)
        if (rotAuto != null) {
            val rotItems = listOf(
                context.getString(R.string.val_image_rot_0) to "0",
                context.getString(R.string.val_image_rot_90) to "90",
                context.getString(R.string.val_image_rot_180) to "180",
                context.getString(R.string.val_image_rot_270) to "270"
            )
            val rotAdapter = ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                rotItems.map { it.first }
            )
            rotAuto.setAdapter(rotAdapter)

            val currentRotVal = data.props["rotation"] ?: "0"
            val matchRotLabel = rotItems.find { it.second == currentRotVal }?.first ?: context.getString(R.string.val_image_rot_0)
            rotAuto.setText(matchRotLabel, false)

            rotAuto.setOnItemClickListener { _, _, position, _ ->
                onUpdate("rotation", rotItems[position].second)
            }
        }

        // 4. Scale Mode (Left-Right Cycler)
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
                val curScale = data.props["scale_mode"] ?: "FIT_CENTER"
                val idx = scaleValues.indexOf(curScale).coerceAtLeast(0)
                tvScale.text = scaleLabels[idx]
            }
            updateScaleUI()

            btnScalePrev.setOnClickListener {
                val curScale = data.props["scale_mode"] ?: "FIT_CENTER"
                var idx = scaleValues.indexOf(curScale)
                idx = if (idx - 1 < 0) scaleValues.size - 1 else idx - 1
                onUpdate("scale_mode", scaleValues[idx])
                updateScaleUI()
            }
            btnScaleNext.setOnClickListener {
                val curScale = data.props["scale_mode"] ?: "FIT_CENTER"
                var idx = scaleValues.indexOf(curScale)
                idx = (idx + 1) % scaleValues.size
                onUpdate("scale_mode", scaleValues[idx])
                updateScaleUI()
            }
        }

        // 5. Show Info Premium Check Row
        var hasInfo = (data.props["show_info"] ?: "true") == "true"
        val checkInfo = panelView.findViewById<ImageView>(R.id.checkShowInfo)
        checkInfo?.visibility = if (hasInfo) View.VISIBLE else View.INVISIBLE
        panelView.findViewById<View>(R.id.itemShowInfo)?.setOnClickListener {
            hasInfo = !hasInfo
            checkInfo?.visibility = if (hasInfo) View.VISIBLE else View.INVISIBLE
            onUpdate("show_info", hasInfo.toString())
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
        val imgView = view.findViewWithTag<ImageDisplayView>("target_img_view") ?: return
        imgView.updatePayload(payload, isNewArrival = true)
    }
}
