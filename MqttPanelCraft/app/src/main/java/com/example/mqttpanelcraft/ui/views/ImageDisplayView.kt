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
import android.widget.LinearLayout
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
 * 支援單幅與分塊切片 Base64 影像解碼顯示、雙指捏合縮放、角度旋轉校正、
 * 畫布上方圓角外框、與畫布底部外框控制橫列 (左資訊欄 / 中刪除重置 / 右快照保存)。
 */
class ImageDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val canvasContainer: FrameLayout
    private val imageView: ImageView
    private val placeholderView: LinearLayout
    private val bottomBar: LinearLayout
    private val infoTextView: TextView
    private val deleteButton: ImageView
    private val saveButton: ImageView

    private var currentBitmap: Bitmap? = null
    var isEditMode: Boolean = false

    var streamMode: String = "SINGLE"
        set(value) {
            field = value
            updatePlaceholderText()
        }

    var fps: String = "2"
        set(value) {
            field = value
            updatePlaceholderText()
        }

    var gestureZoomEnabled: Boolean = true
    var showQuickSave: Boolean = true
        set(value) {
            field = value
            saveButton.visibility = if (value && !isEditMode) View.VISIBLE else View.INVISIBLE
        }

    var showInfo: Boolean = true
        set(value) {
            field = value
            infoTextView.visibility = if (value) View.VISIBLE else View.INVISIBLE
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

    private val chunkBuffer = StringBuilder()
    private var expectedChunks = 0
    private var receivedChunks = 0

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_card_unselected)
        setPadding(2, 2, 2, 2)

        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        // 1. Canvas Container (霧面半透明底圖，支援深淺色背景)
        canvasContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f)
            setBackgroundColor(if (isDarkMode) Color.parseColor("#18FFFFFF") else Color.parseColor("#0B000000"))
        }
        addView(canvasContainer)

        imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        canvasContainer.addView(imageView)

        // 未收到相片前：顯示大的灰色 image icon
        placeholderView = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            orientation = VERTICAL
            gravity = Gravity.CENTER

            val bigImageIcon = ImageView(context).apply {
                layoutParams = LayoutParams(76, 76)
                setImageResource(android.R.drawable.ic_menu_gallery)
                setColorFilter(if (isDarkMode) Color.parseColor("#78869B") else Color.parseColor("#94A3B8"))
            }
            addView(bigImageIcon)
        }
        canvasContainer.addView(placeholderView)

        // 2. Bottom Bar (適配日夜模式的高質感半透明磨砂控制條)
        bottomBar = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (isDarkMode) Color.parseColor("#331E293B") else Color.parseColor("#4DE2E8F0"))
            setPadding(14, 8, 14, 8)
        }
        addView(bottomBar)

        // 左側：資訊文字 (接收時間與像素)
        infoTextView = TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)
            textSize = 10f
            setTextColor(if (isDarkMode) Color.parseColor("#E2E8F0") else Color.parseColor("#334155"))
            text = "無圖片 | 800x600px"
            visibility = View.VISIBLE
        }
        bottomBar.addView(infoTextView)

        // 中間：刪除/清空按鈕 (寬度 88dp、高度 50dp)
        deleteButton = ImageView(context).apply {
            layoutParams = LayoutParams(88, 50).apply {
                marginEnd = 8
            }
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(Color.parseColor("#EF5350"))
            setBackgroundResource(R.drawable.bg_card_unselected)
            setPadding(22, 10, 22, 10)
            setOnClickListener { clearCurrentBitmap() }
        }
        bottomBar.addView(deleteButton)

        // 右側：保存按鈕 (寬度 88dp、高度 50dp)
        saveButton = ImageView(context).apply {
            layoutParams = LayoutParams(88, 50)
            setImageResource(android.R.drawable.ic_menu_save)
            setColorFilter(Color.parseColor("#26A69A"))
            setBackgroundResource(R.drawable.bg_card_unselected)
            setPadding(22, 10, 22, 10)
            setOnClickListener { saveCurrentSnapshot() }
            visibility = View.VISIBLE
        }
        bottomBar.addView(saveButton)
    }

    fun setScaleMode(modeStr: String) {
        imageView.scaleType = when (modeStr.uppercase(Locale.ROOT)) {
            "CENTER_CROP" -> ImageView.ScaleType.CENTER_CROP
            "FIT_XY" -> ImageView.ScaleType.FIT_XY
            else -> ImageView.ScaleType.FIT_CENTER
        }
    }

    private fun updatePlaceholderText() {
        val label = placeholderView.findViewById<TextView>(R.id.tvPayloadLabel)
        label?.text = if (streamMode == "SINGLE") {
            "單次快照 • 等待圖片傳入"
        } else {
            "連續串流 ($fps FPS) • 等待推流"
        }
    }

    private fun clearCurrentBitmap() {
        currentBitmap = null
        imageView.setImageDrawable(null)
        placeholderView.visibility = View.VISIBLE
        infoTextView.text = "無圖片 | 等待傳送"
    }

    fun updatePayload(base64OrData: String) {
        val payload = base64OrData.trim()
        if (payload.startsWith("CHUNK:")) {
            try {
                val rest = payload.substringAfter("CHUNK:")
                val header = rest.substringBefore(":")
                val dataPart = rest.substringAfter(":")
                val parts = header.split("/")
                val idx = parts[0].toIntOrNull() ?: 1
                val total = parts.getOrNull(1)?.toIntOrNull() ?: 1
                if (idx == 1) {
                    chunkBuffer.clear()
                    expectedChunks = total
                    receivedChunks = 0
                }
                chunkBuffer.append(dataPart)
                receivedChunks++
                if (receivedChunks >= expectedChunks) {
                    decodeAndShowImage(chunkBuffer.toString())
                    chunkBuffer.clear()
                }
                return
            } catch (_: Exception) {}
        }
        decodeAndShowImage(payload)
    }

    private fun decodeAndShowImage(rawPayload: String) {
        try {
            val cleanStr = if (rawPayload.contains(",")) {
                rawPayload.substringAfter(",")
            } else {
                rawPayload
            }
            val bytes = Base64.decode(cleanStr, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                currentBitmap = bitmap
                placeholderView.visibility = View.GONE
                imageView.colorFilter = null
                imageView.setImageBitmap(bitmap)
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                infoTextView.text = "$timeStr | ${bitmap.width}x${bitmap.height}px"
            }
        } catch (_: Exception) {}
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
                val outStream: OutputStream? = context.contentResolver.openOutputStream(uri)
                outStream?.use {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)
                }
                Toast.makeText(context, "已保存快照至相簿 (${bmp.width}x${bmp.height})", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "快照保存失敗: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!gestureZoomEnabled || isEditMode) return false
        when (event.actionMasked) {
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
                if (mode == ZOOM) {
                    val newDist = spacing(event)
                    if (newDist > 10f) {
                        val scale = newDist / startDist
                        scaleFactor = (scaleFactor * scale).coerceIn(1.0f, 4.0f)
                        imageView.scaleX = scaleFactor
                        imageView.scaleY = scaleFactor
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
        }
        return true
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 1f
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }
}
