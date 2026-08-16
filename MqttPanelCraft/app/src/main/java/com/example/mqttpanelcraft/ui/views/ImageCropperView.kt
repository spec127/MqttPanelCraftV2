package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Base64
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class ImageCropperView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Format: "scale,transX_pct,transY_pct" (transX_pct is relative to view width)
    var onMatrixChanged: ((String) -> Unit)? = null

    private var bitmap: Bitmap? = null
    
    // Transformations
    private var scaleFactor = 1f
    private var transX = 0f
    private var transY = 0f

    private val transformMatrix = Matrix()

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 10.0f))
            updateMatrixAndInvalidate()
            return true
        }
    }

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            transX -= distanceX
            transY -= distanceY
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

        // Try decoding base64
        try {
            val base64 = if (src.contains(",")) src.substringAfter(",") else src
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap = null
        }

        // Parse existing matrix if present
        if (!existingMatrixStr.isNullOrEmpty()) {
            try {
                val parts = existingMatrixStr.split(",")
                if (parts.size >= 3) {
                    scaleFactor = parts[0].toFloat()
                    transX = parts[1].toFloat() * width
                    transY = parts[2].toFloat() * height
                }
            } catch (e: Exception) {
                // Ignore, use default
            }
        } else {
            // Reset to fit center
            scaleFactor = 1f
            transX = 0f
            transY = 0f
        }

        updateMatrixAndInvalidate(notify = false)
    }

    private fun updateMatrixAndInvalidate(notify: Boolean = true) {
        transformMatrix.reset()
        if (bitmap != null && width > 0 && height > 0) {
            // First, scale the bitmap to fit inside the view initially
            val bw = bitmap!!.width.toFloat()
            val bh = bitmap!!.height.toFloat()
            val initialScale = Math.min(width / bw, height / bh)
            
            // Move to center
            val dx = (width - bw * initialScale) / 2f
            val dy = (height - bh * initialScale) / 2f

            // Apply base fit-center
            transformMatrix.postScale(initialScale, initialScale)
            transformMatrix.postTranslate(dx, dy)

            // Apply user transform (scale from center, then translate)
            transformMatrix.postScale(scaleFactor, scaleFactor, width / 2f, height / 2f)
            transformMatrix.postTranslate(transX, transY)
        }
        invalidate()
        if (notify && width > 0 && height > 0) {
            val txPct = transX / width
            val tyPct = transY / height
            onMatrixChanged?.invoke("$scaleFactor,$txPct,$tyPct")
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrixAndInvalidate(notify = false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let {
            canvas.drawBitmap(it, transformMatrix, null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            handled = gestureDetector.onTouchEvent(event) || handled
        }
        
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            // Notify end of gesture
            if (width > 0 && height > 0) {
                val txPct = transX / width
                val tyPct = transY / height
                onMatrixChanged?.invoke("$scaleFactor,$txPct,$tyPct")
            }
        }
        return handled || super.onTouchEvent(event)
    }
}
