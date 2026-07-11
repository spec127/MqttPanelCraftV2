package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.util.Size
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

/**
 * 影像監控/顯示元件 (ImageSensorDefinition)
 *
 * Design Intent:
 * 作為 Sensor 感測器類別下的影像接收端，專門用於顯示 IoT 設備傳來的攝影機畫面、靜態圖片或 MQTT 傳輸的 Base64 影像數據。
 */
object ImageSensorDefinition : IComponentDefinition {

    override val type: String = "IMAGE_SENSOR"
    override val defaultSize: Size = Size(220, 160)
    override val labelPrefix: String = "cam"
    override val iconResId: Int = android.R.drawable.ic_menu_camera
    override val group: String = "SENSOR"

    override val propertiesLayoutId: Int = R.layout.layout_prop_generic_color

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "color" to "#FFEB3B",
        "theme_color" to "#FFEB3B",
        "scale_type" to "FIT_CENTER"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val frame = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        val imageView = ImageView(context).apply {
            tag = "target_image"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(android.R.drawable.ic_menu_camera)
            setColorFilter(Color.parseColor("#888888"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val placeholderText = TextView(context).apply {
            tag = "placeholder_text"
            text = "Camera / Image Stream"
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(8, 8, 8, 16)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        frame.addView(imageView)
        frame.addView(placeholderText)
        container.addView(frame, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val frame = (view as? FrameLayout)?.getChildAt(0) as? FrameLayout ?: return
        val imageView = frame.findViewWithTag<ImageView>("target_image") ?: return

        data.props["color"]?.let { colorHex ->
            try {
                val color = Color.parseColor(colorHex)
                if (imageView.drawable == null) {
                    imageView.setColorFilter(color)
                }
            } catch (_: Exception) {}
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        // 綁定標準顏色屬性面板
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        // 感測端主要被動接收 MQTT 訊息展示畫面
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val frame = (view as? FrameLayout)?.getChildAt(0) as? FrameLayout ?: return
        val imageView = frame.findViewWithTag<ImageView>("target_image") ?: return
        val placeholder = frame.findViewWithTag<TextView>("placeholder_text")

        try {
            val cleanPayload = if (payload.contains(",")) {
                payload.substringAfter(",")
            } else {
                payload
            }
            val bytes = Base64.decode(cleanPayload, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                imageView.clearColorFilter()
                imageView.setImageBitmap(bitmap)
                placeholder?.visibility = View.GONE
            }
        } catch (_: Exception) {
            placeholder?.text = payload
            placeholder?.visibility = View.VISIBLE
        }
    }
}
