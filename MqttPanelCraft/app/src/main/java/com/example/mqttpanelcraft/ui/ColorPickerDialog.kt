package com.example.mqttpanelcraft.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.content.res.ColorStateList
import com.example.mqttpanelcraft.R

/**
 * 獨立的調色盤對話框
 * 可在任何地方呼叫使用，提供完整的顏色選擇功能
 * 
 * @param context Context
 * @param initialColor 初始顏色（支援 #RRGGBB 或 #AARRGGBB 格式）
 * @param showAlpha 是否顯示 Alpha 透明度滑桿（預設 true）
 * @param onColorSelected 顏色選擇完成的回呼函式
 */
class ColorPickerDialog(
    private val context: Context,
    private val initialColor: String = "#FFFFFFFF",
    private val showAlpha: Boolean = true,
    private val onColorSelected: (String) -> Unit,
    private val onDismiss: (() -> Unit)? = null
) {
    
    private var popupWindow: PopupWindow? = null
    private val density = context.resources.displayMetrics.density
    
    /**
     * 顯示調色盤 Popup
     * @param anchorView 錨點 View（Popup 會顯示在此 View 上方）
     * @param gravity 顯示位置（預設為上方）
     */
    fun show(anchorView: View, gravity: Int = Gravity.TOP) {
        val popupRoot = buildColorPickerUI()
        
        popupWindow = PopupWindow(
            popupRoot,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 20f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener { onDismiss?.invoke() }
        }
        
        // 測量並定位
        popupRoot.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        
        val popupHeight = popupRoot.measuredHeight
        val anchorHeight = anchorView.height
        val yOffset = -popupHeight - anchorHeight - (10 * density).toInt()
        
        popupWindow?.showAsDropDown(anchorView, 0, yOffset)
    }
    
    /**
     * 關閉調色盤
     */
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
    
    /**
     * 建立調色盤 UI
     */
    private fun buildColorPickerUI(): View {
        // Helper: 建立邊框 Drawable
        fun createBorderDrawable(): GradientDrawable {
            return GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((1 * density).toInt(), Color.LTGRAY)
                cornerRadius = 4 * density
            }
        }
        
        // Root Container
        val popupRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 6 * density
                setStroke((1 * density).toInt(), Color.parseColor("#E0E0E0"))
            }
            elevation = 12f
        }
        
        // Logic: Color state management
        var currentAlpha = 255
        var currentHue = 0f
        var currentSat = 1f
        var currentVal = 1f
        
        // Shared thumbDrawable for sliders
        val thumbDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setSize((2 * density).toInt(), (14 * density).toInt())
            setColor(Color.WHITE)
            setStroke(1, Color.GRAY)
        }

        // Top: Spectrum + Alpha
        val topContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        
        // 1. Spectrum Box
        val spectrumW = (130 * density).toInt()
        val spectrumH = (130 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(spectrumW, spectrumH, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Draw Spectrum will be handled dynamically
        fun updateSpectrum(sat: Float) {
            // 1. Draw Hue background (Horizontal)
            val hueColors = IntArray(7)
            for (i in 0..6) hueColors[i] = Color.HSVToColor(floatArrayOf(i * 60f, 1f, 1f))
            val huePaint = android.graphics.Paint()
            huePaint.shader = LinearGradient(0f, 0f, spectrumW.toFloat(), 0f, hueColors, null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, spectrumW.toFloat(), spectrumH.toFloat(), huePaint)

            // 2. White Overlay (Top 50%)
            val whitePaint = android.graphics.Paint()
            whitePaint.shader = LinearGradient(0f, 0f, 0f, spectrumH / 2f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, spectrumW.toFloat(), spectrumH / 2f, whitePaint)

            // 3. Black Overlay (Bottom 50%)
            val blackPaint = android.graphics.Paint()
            blackPaint.shader = LinearGradient(0f, spectrumH / 2f, 0f, spectrumH.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, spectrumH / 2f, spectrumW.toFloat(), spectrumH.toFloat(), blackPaint)
            
            // 4. Dashed line at Saturation=1 (Middle)
            val dashPaint = android.graphics.Paint().apply {
                color = Color.argb(100, 255, 255, 255)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f)
            }
            canvas.drawLine(0f, spectrumH / 2f, spectrumW.toFloat(), spectrumH / 2f, dashPaint)
        }
        
        val spectrumContainer = FrameLayout(context).apply {
            background = createBorderDrawable()
            setPadding((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = (6 * density).toInt()
                leftMargin = (6 * density).toInt()
            }
        }
        
        val imgSpectrum = ImageView(context).apply {
            setImageBitmap(bitmap)
            layoutParams = FrameLayout.LayoutParams(spectrumW, spectrumH)
        }
        spectrumContainer.addView(imgSpectrum)
        
        // 2. Grayscale Strip (Vertical, Left)
        val satWidth = (14 * density).toInt()
        val sliderWidth = (22 * density).toInt()
        val satContainer = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(satWidth, spectrumH + (4 * density).toInt())
            scaleType = ImageView.ScaleType.FIT_XY
            background = createBorderDrawable()
            
            // Draw a high-precision vertical gradient bitmap
            val gradBmp = Bitmap.createBitmap(1, 256, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(gradBmp)
            val paint = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, 256f, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, 1f, 256f, paint)
            setImageBitmap(gradBmp)
        }

        // 3. Alpha Slider (Vertical, Right)
        val alphaContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(sliderWidth, spectrumH + (4 * density).toInt())
            background = createBorderDrawable()
            visibility = if (showAlpha) View.VISIBLE else View.GONE
        }

        val alphaSeekBar = SeekBar(context).apply {
            max = 255
            progress = 255
            rotation = 270f
            thumb = thumbDrawable
            splitTrack = false
            layoutParams = FrameLayout.LayoutParams(spectrumH, sliderWidth).apply {
                gravity = Gravity.CENTER
            }
        }
        
        // Removed icAlpha icon as requested
        alphaContainer.addView(alphaSeekBar)

        // Add Saturation to Left, Spectrum in Middle, Alpha on Right
        topContainer.addView(satContainer)
        topContainer.addView(spectrumContainer)
        topContainer.addView(alphaContainer)
        popupRoot.addView(topContainer)
        
        // Bottom: Hex Input
        val etHex = EditText(context).apply {
            hint = "#RRGGBB"
            textSize = 14f
            background = createBorderDrawable()
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
            }
            setText(initialColor)
            setSingleLine(true)
        }
        popupRoot.addView(etHex)
        
        // Helper: Update backgrounds
        fun updateSliderBackgrounds() {
            // Saturation slider is now grayscale presets, keep its border background
            satContainer.background = createBorderDrawable()
            
            // Revert Alpha background to original border style as requested
            alphaContainer.background = createBorderDrawable()
        }
        
        // Helper: Emit color update
        fun emitColor(fromInput: Boolean = false) {
            updateSliderBackgrounds()
            val rgb = Color.HSVToColor(currentAlpha, floatArrayOf(currentHue, currentSat, currentVal))
            val hex = String.format("#%08X", rgb)
            
            if (!fromInput && etHex.text.toString() != hex) {
                etHex.tag = "programmatic"
                etHex.setText(hex)
                etHex.tag = null
            }
            
            onColorSelected(hex)
        }
        
        // Parse initial color
        try {
            val c = Color.parseColor(initialColor)
            currentAlpha = Color.alpha(c)
            val hsv = FloatArray(3)
            Color.colorToHSV(c, hsv)
            currentHue = hsv[0]
            currentSat = hsv[1]
            currentVal = hsv[2]
            
            alphaSeekBar.progress = currentAlpha
            updateSpectrum(currentSat)
            updateSliderBackgrounds()
            imgSpectrum.invalidate()
            emitColor()
        } catch (e: Exception) {}
        
        // Bind local variables for presets (deprecated for presets but useful for potential expansion)
        
        // Grayscale Strip Touch Listener
        satContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val h = satContainer.height.toFloat()
                if (h > 0) {
                    val y = event.y.coerceIn(0f, h)
                    currentHue = 0f
                    currentSat = 0f
                    currentVal = 1f - (y / h)
                    updateSpectrum(currentSat)
                    imgSpectrum.invalidate()
                    emitColor()
                }
            }
            true
        }
        
        // Spectrum Touch Listener
        imgSpectrum.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val x = event.x.coerceIn(0f, spectrumW.toFloat())
                val y = event.y.coerceIn(0f, spectrumH.toFloat())
                
                currentHue = (x / spectrumW) * 360f
                val ratioY = y / spectrumH
                
                // Vertical Logic: 0(White)->0.5(Full)->1(Black)
                if (ratioY <= 0.5f) {
                    currentSat = ratioY * 2f // 0 to 1
                    currentVal = 1f
                } else {
                    currentSat = 1f
                    currentVal = 1f - (ratioY - 0.5f) * 2f // 1 to 0
                }

                emitColor()
            }
            true
        }
        
        // Saturation SeekBar Listener REMOVED - Replaced by Gray Presets
        
        // Alpha SeekBar Listener
        alphaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentAlpha = progress
                    emitColor()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Hex Input Listener
        etHex.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (etHex.tag == "programmatic") return
                
                if (etHex.hasFocus()) {
                    val hex = s.toString()
                    if (hex.length >= 7) {
                        try {
                            val c = Color.parseColor(hex)
                            currentAlpha = Color.alpha(c)
                            val hsv = FloatArray(3)
                            Color.colorToHSV(c, hsv)
                            currentHue = hsv[0]
                            currentSat = hsv[1]
                            currentVal = hsv[2]
                            
                            alphaSeekBar.progress = currentAlpha
                            updateSpectrum(currentSat)
                            imgSpectrum.invalidate()
                            
                            onColorSelected(hex)
                        } catch (e: Exception) {}
                    }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        return popupRoot
    }
}
