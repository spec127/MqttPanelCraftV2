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
        "show_info" to "true"
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

        // Hide stream sensor controls for standard ImageDisplay
        panelView.findViewById<View>(R.id.toggleImageMode)?.visibility = View.GONE
        panelView.findViewById<View>(R.id.containerStreamConfig)?.visibility = View.GONE

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

        // 4. Scale Mode Dropdown (視覺外觀)
        val scaleAuto = panelView.findViewById<AutoCompleteTextView>(R.id.spImageScale)
        if (scaleAuto != null) {
            val scaleItems = listOf(
                context.getString(R.string.val_image_scale_fit) to "FIT_CENTER",
                context.getString(R.string.val_image_scale_crop) to "CENTER_CROP",
                context.getString(R.string.val_image_scale_xy) to "FIT_XY"
            )
            val scaleAdapter = ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                scaleItems.map { it.first }
            )
            scaleAuto.setAdapter(scaleAdapter)

            val currentScaleVal = data.props["scale_mode"] ?: "FIT_CENTER"
            val matchScaleLabel = scaleItems.find { it.second == currentScaleVal }?.first ?: context.getString(R.string.val_image_scale_fit)
            scaleAuto.setText(matchScaleLabel, false)

            scaleAuto.setOnItemClickListener { _, _, position, _ ->
                onUpdate("scale_mode", scaleItems[position].second)
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
    ) {}

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val imgView = view.findViewWithTag<ImageDisplayView>("target_img_view") ?: return
        imgView.updatePayload(payload)
    }
}
