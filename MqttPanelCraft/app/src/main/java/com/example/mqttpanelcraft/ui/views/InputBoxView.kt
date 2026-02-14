package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.mqttpanelcraft.R

class InputBoxView(context: Context) : FrameLayout(context) {

    var style: String = "Capsule" // Capsule, Modular, Infinity
        set(value) {
            field = value
            updateLayout()
        }

    var themeColor: Int = Color.BLUE
        set(value) {
            field = value
            updateColors()
        }

    var clearOnSend: Boolean = true
    var enterAsSend: Boolean = false

    var onSend: ((String) -> Unit)? = null

    private val inputField = EditText(context)
    private val sendButton = ImageView(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgRect = RectF()

    // Custom Cursor Logic
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cursorAlpha = 255
    private var cursorBlinkState = true
    private val cursorRunnable =
            object : Runnable {
                override fun run() {
                    if (attachedToWindow && style == "Infinity") {
                        cursorBlinkState = !cursorBlinkState
                        cursorAlpha = if (cursorBlinkState) 255 else 0
                        invalidate()
                        postDelayed(this, 500)
                    }
                }
            }
    private var attachedToWindow = false

    init {
        setWillNotDraw(false)

        // 1. Setup Input Field
        inputField.setBackgroundColor(Color.TRANSPARENT)
        inputField.setTextColor(Color.BLACK)
        inputField.textSize = 14f
        inputField.hint = "Enter text..."
        inputField.maxLines = 1
        inputField.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
        inputField.imeOptions = EditorInfo.IME_ACTION_NONE

        inputField.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                performSend()
                true
            } else if (enterAsSend && event != null && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    performSend()
                }
                true // Consume the event to prevent newline
            } else {
                false
            }
        }
        addView(inputField)

        // 2. Setup Send Button
        sendButton.setImageResource(R.drawable.ic_send) // Using standard send icon
        sendButton.scaleType = ImageView.ScaleType.FIT_CENTER
        sendButton.setOnClickListener { performSend() }

        // Initial params - standard WRAP_CONTENT
        val initialBtnParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        initialBtnParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        addView(sendButton, initialBtnParams)

        // Use post to ensure updateLayout runs after initial layout pass
        post { updateLayout() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedToWindow = true
        if (style == "Infinity") post(cursorRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        attachedToWindow = false
        removeCallbacks(cursorRunnable)
    }

    private fun performSend() {
        val text = inputField.text.toString()
        if (text.isNotEmpty()) {
            onSend?.invoke(text)
            if (clearOnSend) {
                inputField.setText("")
            }
            // Hide keyboard
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateLayout()
    }

    private fun updateLayout() {
        if (width == 0 || height == 0) return

        val density = resources.displayMetrics.density
        // Overall shrinking: Reduced global padding from 12dp to 8dp
        val padding = (8 * density).toInt()

        // Reset cursor visibility default
        inputField.isCursorVisible = true
        removeCallbacks(cursorRunnable)

        val w = width
        val h = height

        // V53.2: Scaling text size based on height
        // Base: 60dp height -> 14sp text
        // Scale factor roughly 0.233
        val calculatedSp = (h / density) * 0.24f
        inputField.textSize = calculatedSp.coerceAtLeast(10f).coerceAtMost(48f)

        when (style) {
            "Capsule" -> {
                // Input: Full width pill
                // Button scale with height (approx 70% of height)
                val btnSize = (h * 0.7f).toInt().coerceAtLeast((20 * density).toInt())

                val lpInput =
                        FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        )
                lpInput.setMargins(
                        padding,
                        0,
                        btnSize + (8 * density).toInt(),
                        0
                ) // Space for floating button
                inputField.layoutParams = lpInput
                inputField.gravity = Gravity.CENTER_VERTICAL
                inputField.setPadding(0, 0, 0, 0)

                // Button: Floating circle inside right
                val lpBtn = FrameLayout.LayoutParams(btnSize, btnSize)
                lpBtn.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                lpBtn.rightMargin = (4 * density).toInt()
                sendButton.layoutParams = lpBtn

                // Icon Padding: ~22%
                val iconPad = (btnSize * 0.22f).toInt()
                sendButton.setPadding(iconPad, iconPad, iconPad, iconPad)
            }
            "Modular" -> {
                // Input: Bracketed area
                // Button is full height square-ish
                val btnSize = h
                // V53: Spacing is 1/4 of button width
                val spacing = btnSize / 4

                val lpInput =
                        FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        )
                lpInput.setMargins(0, 0, btnSize + spacing, 0)
                inputField.layoutParams = lpInput
                inputField.gravity = Gravity.CENTER_VERTICAL
                inputField.setPadding(padding, 0, padding, 0)

                // Button: Separate Box
                val lpBtn = FrameLayout.LayoutParams(btnSize, FrameLayout.LayoutParams.MATCH_PARENT)
                lpBtn.gravity = Gravity.END
                sendButton.layoutParams = lpBtn

                // Icon Padding: ~22%
                val iconPad = (btnSize * 0.22f).toInt()
                sendButton.setPadding(iconPad, iconPad, iconPad, iconPad)
            }
            "Infinity" -> {
                // Input: Bottom line
                // Button scale: approx 60% of height (smaller than capsule)
                val btnSize = (h * 0.6f).toInt().coerceAtLeast((16 * density).toInt())

                val lpInput =
                        FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        )
                lpInput.setMargins(0, 0, btnSize + padding, 0)
                inputField.layoutParams = lpInput
                inputField.gravity = Gravity.BOTTOM or Gravity.START
                inputField.setPadding(0, 0, 0, (6 * density).toInt())

                // Button: Icon only
                val lpBtn = FrameLayout.LayoutParams(btnSize, btnSize)
                lpBtn.gravity = Gravity.END or Gravity.BOTTOM
                lpBtn.bottomMargin = (4 * density).toInt()
                sendButton.layoutParams = lpBtn

                // Icon Padding: ~22%
                val iconPad = (btnSize * 0.22f).toInt()
                sendButton.setPadding(iconPad, iconPad, iconPad, iconPad)

                // Custom Cursor Logic
                inputField.isCursorVisible = false
                post(cursorRunnable)
            }
        }
        updateColors()
        invalidate()
    }

    private fun updateColors() {
        val isDark =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val hintColor = if (isDark) Color.parseColor("#80FFFFFF") else Color.parseColor("#80000000")

        inputField.setTextColor(textColor)
        inputField.setHintTextColor(hintColor)

        // Update Cursor Paint for Infinity
        if (style == "Infinity") {
            cursorPaint.color = themeColor
            cursorPaint.style = Paint.Style.STROKE
            cursorPaint.strokeWidth = 2f * resources.displayMetrics.density
            // Neon Glow
            cursorPaint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.SOLID)
        } else {
            cursorPaint.maskFilter = null
        }

        when (style) {
            "Capsule" -> {
                // Button: Theme Color Circle, White Icon
                val bg = android.graphics.drawable.GradientDrawable()
                bg.setColor(themeColor)
                bg.shape = android.graphics.drawable.GradientDrawable.OVAL
                sendButton.background = bg
                sendButton.setColorFilter(Color.WHITE)
            }
            "Modular" -> {
                // Button Logic: Theme color outline
                val bg = android.graphics.drawable.GradientDrawable()
                bg.setColor(
                        if (isDark) Color.parseColor("#1AFFFFFF") else Color.parseColor("#1A000000")
                )
                bg.setStroke((1.5f * resources.displayMetrics.density).toInt(), themeColor)
                bg.cornerRadius = 4f * resources.displayMetrics.density
                sendButton.background = bg
                sendButton.setColorFilter(themeColor)
            }
            "Infinity" -> {
                sendButton.background = null
                sendButton.setColorFilter(themeColor)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas) // Draw children (EditText) first

        if (style == "Infinity") {
            // Draw custom cursor on top
            if (cursorAlpha > 0) {
                val layout = inputField.layout ?: return
                val density = resources.displayMetrics.density

                // Calculate cursor position
                val selectionEnd = inputField.selectionEnd.coerceAtLeast(0)
                val line = layout.getLineForOffset(selectionEnd)

                // Horizontal position
                // Use absolute relative position of inputField
                var x = layout.getPrimaryHorizontal(selectionEnd)
                x += inputField.x + inputField.paddingLeft - inputField.scrollX

                // Vertical position
                // Use totalPaddingTop which includes internal gravity offset
                val verticalOffset = inputField.y + inputField.totalPaddingTop - inputField.scrollY

                val top = layout.getLineTop(line)
                val bottom = layout.getLineBottom(line)

                var yTop = top.toFloat() + verticalOffset
                var yBottom = bottom.toFloat() + verticalOffset

                // Shrink slightly to match text height usually
                yTop += 2f * density
                yBottom -= 2f * density

                cursorPaint.alpha = cursorAlpha
                canvas.drawLine(x, yTop, x, yBottom, cursorPaint)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        val isDark =
                (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

        when (style) {
            "Capsule" -> {
                bgRect.set(0f, 0f, w, h)
                paint.style = Paint.Style.FILL
                // Background color: Clean white or dark grey
                paint.color = if (isDark) Color.parseColor("#2C2C2C") else Color.WHITE
                val r = h / 2f
                canvas.drawRoundRect(bgRect, r, r, paint)

                // Subtle Stroke
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * density
                paint.color =
                        if (isDark) Color.parseColor("#444444") else Color.parseColor("#E0E0E0")
                canvas.drawRoundRect(bgRect, r, r, paint)
            }
            "Modular" -> {
                // Input Area: Corner Brackets
                val btnWidth = sendButton.width.toFloat()
                val btnSize = h // Logic from updateLayout
                val spacing = btnSize / 4
                val inputW = w - btnSize - spacing

                // Fill (Optional: very subtle)
                bgRect.set(0f, 0f, inputW, h)
                paint.style = Paint.Style.FILL
                paint.color =
                        if (isDark) Color.parseColor("#11FFFFFF")
                        else Color.parseColor("#05000000") // Very faint fill
                canvas.drawRect(bgRect, paint)

                // Corner Brackets
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = themeColor
                paint.strokeCap = Paint.Cap.SQUARE

                val cornerLen = 8f * density

                // --- Input Brackets ---
                // Top-Left
                canvas.drawLine(0f, 0f, cornerLen, 0f, paint)
                canvas.drawLine(0f, 0f, 0f, cornerLen, paint)

                // Top-Right of input
                canvas.drawLine(inputW, 0f, inputW - cornerLen, 0f, paint)
                canvas.drawLine(inputW, 0f, inputW, cornerLen, paint)

                // Bottom-Left
                canvas.drawLine(0f, h, cornerLen, h, paint)
                canvas.drawLine(0f, h, 0f, h - cornerLen, paint)

                // Bottom-Right of input
                canvas.drawLine(inputW, h, inputW - cornerLen, h, paint)
                canvas.drawLine(inputW, h, inputW, h - cornerLen, paint)

                // --- V53: Send Button Brackets ---
                val btnX = w - btnSize
                // Top-Left of button
                canvas.drawLine(btnX, 0f, btnX + cornerLen, 0f, paint)
                canvas.drawLine(btnX, 0f, btnX, cornerLen, paint)

                // Top-Right of button
                canvas.drawLine(w, 0f, w - cornerLen, 0f, paint)
                canvas.drawLine(w, 0f, w, cornerLen, paint)

                // Bottom-Left of button
                canvas.drawLine(btnX, h, btnX + cornerLen, h, paint)
                canvas.drawLine(btnX, h, btnX, h - cornerLen, paint)

                // Bottom-Right of button
                canvas.drawLine(w, h, w - cornerLen, h, paint)
                canvas.drawLine(w, h, w, h - cornerLen, paint)
            }
            "Infinity" -> {
                // Bottom Line: Gradient or Solid? Solid theme color.
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = themeColor
                // Line with slight fade? No, solid is fine for "Neon Filament" look.
                val y = h - (1f * density)
                canvas.drawLine(0f, y, w, y, paint)
            }
        }
    }
}
