package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Base64
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.example.mqttpanelcraft.R

class ImageCropperView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onMatrixChanged: ((String) -> Unit)? = null
    var onModeCustomTriggered: (() -> Unit)? = null

    private var bitmap: Bitmap? = null
    
    // Format: "MODE;scale,txPct,tyPct"
    private var mode: String = "FIT"
    private var scaleFactor = 1f
    private var transX = 0f
    private var transY = 0f

    private val transformMatrix = Matrix()

    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.props_primary)
    }

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f))
            switchToCustomIfNeeded()
            updateMatrixAndInvalidate()
            return true
        }
    }

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            transX -= distanceX
            transY -= distanceY
            switchToCustomIfNeeded()
            updateMatrixAndInvalidate()
            return true
        }
    }

    private val scaleDetector = ScaleGestureDetector(context, scaleListener)
    private val gestureDetector = GestureDetector(context, gestureListener)

    fun setImageSrc(src: String?, existingMatrixStr: String? = null) {
        if (src.isNullOrEmpty()) {
            bitmap = null
            invalidate()
            return
        }

        try {
            val base64 = if (src.contains(",")) src.substringAfter(",") else src
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            bitmap = null
        }

        if (!existingMatrixStr.isNullOrEmpty() && existingMatrixStr.contains(";")) {
            val parts = existingMatrixStr.split(";")
            mode = parts[0]
            if (parts.size > 1) {
                val coords = parts[1].split(",")
                if (coords.size >= 3) {
                    scaleFactor = coords[0].toFloatOrNull() ?: 1f
                    transX = (coords[1].toFloatOrNull() ?: 0f) * resources.displayMetrics.widthPixels // will be fixed in onSizeChanged
                    transY = (coords[2].toFloatOrNull() ?: 0f) * resources.displayMetrics.heightPixels // will be fixed in onSizeChanged
                }
            }
        } else {
            mode = "FIT"
        }

        updateMatrixAndInvalidate(notify = false)
    }

    fun setCropMode(newMode: String) {
        if (mode == newMode) return
        mode = newMode
        if (mode == "FIT" || mode == "FILL") {
            scaleFactor = 1f
            transX = 0f
            transY = 0f
        }
        updateMatrixAndInvalidate(notify = true)
    }

    private fun switchToCustomIfNeeded() {
        if (mode != "CUSTOM") {
            mode = "CUSTOM"
            onModeCustomTriggered?.invoke()
        }
    }

    private fun updateMatrixAndInvalidate(notify: Boolean = true) {
        transformMatrix.reset()
        if (bitmap != null && width > 0 && height > 0) {
            val bw = bitmap!!.width.toFloat()
            val bh = bitmap!!.height.toFloat()
            
            val initialScale = if (mode == "FILL") {
                Math.max(width / bw, height / bh)
            } else {
                Math.min(width / bw, height / bh)
            }
            
            val dx = (width - bw * initialScale) / 2f
            val dy = (height - bh * initialScale) / 2f

            transformMatrix.postScale(initialScale, initialScale)
            transformMatrix.postTranslate(dx, dy)

            if (mode == "CUSTOM") {
                transformMatrix.postScale(scaleFactor, scaleFactor, width / 2f, height / 2f)
                transformMatrix.postTranslate(transX, transY)
            }
        }
        invalidate()
        if (notify && width > 0 && height > 0) {
            val txPct = transX / width
            val tyPct = transY / height
            onMatrixChanged?.invoke("$mode;$scaleFactor,$txPct,$tyPct")
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrixAndInvalidate(notify = false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        
        canvas.drawBitmap(bmp, transformMatrix, null)

        val src = floatArrayOf(
            0f, 0f,
            bmp.width.toFloat(), 0f,
            bmp.width.toFloat(), bmp.height.toFloat(),
            0f, bmp.height.toFloat()
        )
        val dst = FloatArray(8)
        transformMatrix.mapPoints(dst, src)

        val path = Path()
        path.moveTo(dst[0], dst[1])
        path.lineTo(dst[2], dst[3])
        path.lineTo(dst[4], dst[5])
        path.lineTo(dst[6], dst[7])
        path.close()
        canvas.drawPath(path, pathPaint)

        val r = 8f * resources.displayMetrics.density
        for (i in 0..3) {
            canvas.drawCircle(dst[i*2], dst[i*2+1], r, nodePaint)
        }
    }

    private var draggingCornerIndex = -1
    private var isDraggingImage = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        
        val x = event.x
        val y = event.y
        
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return false

        val bmp = bitmap!!
        val src = floatArrayOf(
            0f, 0f,
            bmp.width.toFloat(), 0f,
            bmp.width.toFloat(), bmp.height.toFloat(),
            0f, bmp.height.toFloat()
        )
        val dst = FloatArray(8)
        transformMatrix.mapPoints(dst, src)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastTouchX = x
                lastTouchY = y
                
                var nearestIdx = -1
                var minDist = Float.MAX_VALUE
                val touchRadius = 40f * resources.displayMetrics.density

                for (i in 0..3) {
                    val px = dst[i * 2]
                    val py = dst[i * 2 + 1]
                    val dist = Math.hypot((x - px).toDouble(), (y - py).toDouble()).toFloat()
                    if (dist < touchRadius && dist < minDist) {
                        minDist = dist
                        nearestIdx = i
                    }
                }

                if (nearestIdx != -1) {
                    draggingCornerIndex = nearestIdx
                    switchToCustomIfNeeded()
                    return true
                }

                val minX = minOf(dst[0], dst[2], dst[4], dst[6])
                val maxX = maxOf(dst[0], dst[2], dst[4], dst[6])
                val minY = minOf(dst[1], dst[3], dst[5], dst[7])
                val maxY = maxOf(dst[1], dst[3], dst[5], dst[7])

                if (x in minX..maxX && y in minY..maxY) {
                    isDraggingImage = true
                    switchToCustomIfNeeded()
                    return true
                }
                
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingCornerIndex != -1) {
                    val oppIndex = (draggingCornerIndex + 2) % 4
                    val oppX = dst[oppIndex * 2]
                    val oppY = dst[oppIndex * 2 + 1]
                    
                    val oldDist = Math.hypot((lastTouchX - oppX).toDouble(), (lastTouchY - oppY).toDouble()).toFloat()
                    val newDist = Math.hypot((x - oppX).toDouble(), (y - oppY).toDouble()).toFloat()
                    
                    if (oldDist > 0f) {
                        val deltaScale = newDist / oldDist
                        scaleFactor *= deltaScale
                        scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f))
                        
                        transX = oppX - w/2f - (oppX - w/2f - transX) * deltaScale
                        transY = oppY - h/2f - (oppY - h/2f - transY) * deltaScale
                        
                        updateMatrixAndInvalidate()
                    }
                    lastTouchX = x
                    lastTouchY = y
                    return true
                } else if (isDraggingImage) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    transX += dx
                    transY += dy
                    updateMatrixAndInvalidate()
                    lastTouchX = x
                    lastTouchY = y
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (draggingCornerIndex != -1 || isDraggingImage) {
                    draggingCornerIndex = -1
                    isDraggingImage = false
                    if (w > 0 && h > 0) {
                        val txPct = transX / w
                        val tyPct = transY / h
                        onMatrixChanged?.invoke("$mode;$scaleFactor,$txPct,$tyPct")
                    }
                }
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }
}
