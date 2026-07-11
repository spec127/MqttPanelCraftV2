package com.example.mqttpanelcraft.ui.views

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Build
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.mqttpanelcraft.R
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * MQTT 即時影像顯示元件視圖 (ImageDisplayView)
 * 支援 Base64 解碼顯示、雙擊與手勢捏合縮放、角度旋轉校正、快照保存至手機相簿、以及即時資訊橫幅。
 */
class ImageDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val imageView: ImageView
    private val infoTextView: TextView
    private val saveButton: ImageView

    private var currentBitmap: Bitmap? = null
    var isEditMode: Boolean = false

    var gestureZoomEnabled: Boolean = true
    var showQuickSave: Boolean = true
        set(value) {
            field = value
            saveButton.visibility = if (value && !isEditMode) View.VISIBLE else View.GONE
        }

    var showInfo: Boolean = true
        set(value) {
            field = value
            infoTextView.visibility = if (value) View.VISIBLE else View.GONE
        }

    var rotationAngle: Int = 0
        set(value) {
            field = value
            imageView.rotation = value.toFloat()
        }

    // Touch Zoom & Pan State
    private var scaleFactor = 1.0f
    private var mode = NONE
    private val startPoint = PointF()
    private var startDist = 1f

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

    init {
        imageView = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(android.R.drawable.ic_menu_gallery)
            setColorFilter(Color.parseColor("#888888"))
        }
        addView(imageView)

        // 資訊提示橫幅 (底部右側)
        infoTextView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(12, 8, 12, 8)
            }
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(12, 4, 12, 4)
            text = "等待接收影像..."
            visibility = View.VISIBLE
        }
        addView(infoTextView)

        // 快照保存按鈕 (右上角)
        saveButton = ImageView(context).apply {
            layoutParams = LayoutParams(64, 64).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(12, 12, 12, 12)
            }
            setImageResource(android.R.drawable.ic_menu_save)
            setColorFilter(Color.WHITE)
            setBackgroundResource(R.drawable.shape_circle_color)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#99000000"))
            setPadding(12, 12, 12, 12)
            setOnClickListener { saveCurrentSnapshot() }
            visibility = View.GONE
        }
        addView(saveButton)
    }

    fun setScaleMode(modeStr: String) {
        imageView.scaleType = when (modeStr.uppercase(Locale.ROOT)) {
            "CENTER_CROP" -> ImageView.ScaleType.CENTER_CROP
            else -> ImageView.ScaleType.FIT_CENTER
        }
    }

    fun updatePayload(base64OrData: String) {
        try {
            val cleanStr = if (base64OrData.contains(",")) {
                base64OrData.substringAfter(",")
            } else {
                base64OrData
            }
            val bytes = Base64.decode(cleanStr, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                currentBitmap = bitmap
                imageView.colorFilter = null
                imageView.setImageBitmap(bitmap)
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                infoTextView.text = "$timeStr | ${bitmap.width}x${bitmap.height}px"
            }
        } catch (e: Exception) {
            // 解析失敗時保留原狀或忽略
        }
    }

    private fun saveCurrentSnapshot() {
        val bmp = currentBitmap ?: return
        try {
            val filename = "MQTT_IMG_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/MqttPanelCraft")
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                val stream: OutputStream? = context.contentResolver.openOutputStream(uri)
                stream?.use {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
                }
                Toast.makeText(context, "影像已保存至手機相簿", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "保存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gestureZoomEnabled || isEditMode) return super.onTouchEvent(event)
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mode = DRAG
                startPoint.set(event.x, event.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                startDist = spacing(event)
                if (startDist > 10f) {
                    mode = ZOOM
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == ZOOM && event.pointerCount >= 2) {
                    val newDist = spacing(event)
                    if (newDist > 10f) {
                        val scale = newDist / startDist
                        scaleFactor = (scaleFactor * scale).coerceIn(1.0f, 4.0f)
                        imageView.scaleX = scaleFactor
                        imageView.scaleY = scaleFactor
                        startDist = newDist
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
                if (scaleFactor <= 1.05f) {
                    scaleFactor = 1.0f
                    imageView.scaleX = 1.0f
                    imageView.scaleY = 1.0f
                }
            }
        }
        return true
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }
}
