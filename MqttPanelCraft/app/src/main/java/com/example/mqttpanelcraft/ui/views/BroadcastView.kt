package com.example.mqttpanelcraft.ui.views

import android.content.Context
import android.graphics.Color
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import java.util.Locale

/**
 * 語音廣播與警報視覺元件 (BroadcastView)
 * 支援單純 TTS 語音廣播、單純警報音效 (ToneGenerator)、以及警報前奏+TTS雙重廣播。
 */
class BroadcastView(context: Context) : FrameLayout(context), TextToSpeech.OnInitListener {

    private val contentText: TextView
    private val modeChip: TextView
    private val speakButton: ImageView
    private val iconView: ImageView
    private val contentRow: LinearLayout
    private val headerRow: LinearLayout
    
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastMessage: String = context.getString(R.string.broadcast_waiting_text)

    var isEditMode: Boolean = false

    var broadcastMode: String = "TTS_ONLY"
        set(value) {
            field = value
            modeChip.text = when (value) {
                "ALERT_ONLY" -> context.getString(R.string.broadcast_mode_pure_alert)
                "ALERT_AND_TTS" -> context.getString(R.string.broadcast_mode_alert_tts)
                else -> context.getString(R.string.broadcast_mode_pure_tts)
            }
            modeChip.setBackgroundColor(when (value) {
                "ALERT_ONLY" -> Color.parseColor("#33EF5350")
                "ALERT_AND_TTS" -> Color.parseColor("#33FFAB00")
                else -> Color.parseColor("#334CAF50")
            })
            modeChip.setTextColor(when (value) {
                "ALERT_ONLY" -> Color.parseColor("#EF5350")
                "ALERT_AND_TTS" -> Color.parseColor("#FFAB00")
                else -> Color.parseColor("#4CAF50")
            })
        }

    var alertType: String = "Chime"

    var speechRate: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var speechPitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    var voicePreset: String = "F1"

    var chartStyle: String = "Solid"
        set(value) {
            field = value
            invalidate()
        }

    var colorStr: String = "#00BCD4"
        set(value) {
            field = value
            try {
                val c = Color.parseColor(value)
                speakButton.backgroundTintList = android.content.res.ColorStateList.valueOf(c)
                if (chartStyle == "Solid") {
                    speakButton.setColorFilter(Color.WHITE)
                } else {
                    speakButton.setColorFilter(c)
                    speakButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A000000")) // faint dark
                }
            } catch (e: Exception) {}
            invalidate()
        }

    var showText: Boolean = true
        set(value) {
            field = value
            contentRow.visibility = if (value) View.VISIBLE else View.GONE
        }

    fun setVoiceSettings(preset: String, pitch: Float, rate: Float) {
        voicePreset = preset
        speechPitch = pitch
        speechRate = rate
        applyVoiceSettings()
    }

    private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 12f * resources.displayMetrics.density
        
        try {
            val c = Color.parseColor(colorStr)
            val density = resources.displayMetrics.density
            when (chartStyle) {
                "Capsule" -> {
                    val r = h / 2f
                    bgPaint.style = android.graphics.Paint.Style.FILL
                    bgPaint.color = c
                    bgPaint.alpha = 30
                    canvas.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)
                    
                    bgPaint.style = android.graphics.Paint.Style.STROKE
                    bgPaint.strokeWidth = 2f * density
                    bgPaint.color = c
                    bgPaint.alpha = 200
                    canvas.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)
                }
                "Infinity" -> {
                    // Minimal background, no border
                    val r = 8f * density
                    bgPaint.style = android.graphics.Paint.Style.FILL
                    bgPaint.color = c
                    bgPaint.alpha = 15
                    canvas.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)
                }
                "Glass" -> {
                    val r = 16f * density
                    bgPaint.style = android.graphics.Paint.Style.FILL
                    bgPaint.color = c
                    bgPaint.alpha = 25
                    canvas.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)
                    
                    bgPaint.style = android.graphics.Paint.Style.STROKE
                    bgPaint.strokeWidth = 1.5f * density
                    bgPaint.color = Color.parseColor("#50FFFFFF")
                    canvas.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)
                }
                else -> { 
                    val r = 12f * density
                    bgPaint.style = android.graphics.Paint.Style.FILL
                    bgPaint.color = c
                    bgPaint.alpha = 40
                    canvas.drawRoundRect(0f, 0f, w, h, r, r, bgPaint)
                }
            }

            // Calculate luminance for contrast
            val luminance = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255
            val isDarkBackground = luminance < 0.5 && (chartStyle != "Infinity" || bgPaint.alpha > 0)
            
            // For Infinity style with low alpha, we just treat it as light background since canvas is usually light
            val isLightBg = if (chartStyle == "Infinity") true else !isDarkBackground
            
            val contrastColor = if (isLightBg) Color.parseColor("#424242") else Color.parseColor("#E0E0E0")
            val contrastTextColor = if (isLightBg) Color.parseColor("#616161") else Color.parseColor("#B0BEC5")
            
            iconView.setColorFilter(contrastColor)
            contentText.setTextColor(contrastTextColor)
            
        } catch(e: Exception) {}
        
        super.dispatchDraw(canvas)
    }

    init {
        val density = resources.displayMetrics.density
        val padH = (16 * density).toInt()
        val padV = (14 * density).toInt()
        setPadding(padH, padV, padH, padV)

        tts = TextToSpeech(context, this)

        val container = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // Header (Icon & Mode Chip)
        headerRow = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt()).apply {
                marginEnd = (8 * density).toInt()
            }
            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
        }
        headerRow.addView(iconView)

        val space = android.widget.Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        headerRow.addView(space)

        modeChip = TextView(context).apply {
            text = context.getString(R.string.broadcast_mode_pure_tts)
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            
            // Modern chip design: outlined
            setBackgroundResource(R.drawable.bg_input_outline) // we'll use stroke instead
            
            val padChipH = (8 * density).toInt()
            val padChipV = (2 * density).toInt()
            setPadding(padChipH, padChipV, padChipH, padChipV)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        headerRow.addView(modeChip)
        container.addView(headerRow)

        // Content Row (Text + Button)
        contentRow = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * density).toInt()
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        contentText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * density).toInt()
            }
            text = lastMessage
            textSize = 13f
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        contentRow.addView(contentText)

        speakButton = ImageView(context).apply {
            val btnSize = (32 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(Color.WHITE)
            setBackgroundResource(R.drawable.shape_circle_color)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            val padP = (6 * density).toInt()
            setPadding(padP, padP, padP, padP)
            elevation = 4f * density
            setOnClickListener {
                speak(lastMessage)
            }
        }
        contentRow.addView(speakButton)
        container.addView(contentRow)

        addView(container)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        // Base width is 150dp
        val baseWidth = 150f * density
        val scale = (w / baseWidth).coerceIn(0.5f, 2.0f)

        contentText.textSize = 13f * scale
        modeChip.textSize = 10f * scale
        val padChipH = (8 * density * scale).toInt()
        val padChipV = (2 * density * scale).toInt()
        modeChip.setPadding(padChipH, padChipV, padChipH, padChipV)
        
        val btnSize = (32 * density * scale).toInt()
        speakButton.layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
        val padP = (6 * density * scale).toInt()
        speakButton.setPadding(padP, padP, padP, padP)
        
        iconView.layoutParams = LinearLayout.LayoutParams((24 * density * scale).toInt(), (24 * density * scale).toInt()).apply {
            marginEnd = (8 * density * scale).toInt()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (isTtsReady) {
                applyVoiceSettings()
            }
        }
    }

    private fun applyVoiceSettings() {
        if (!isTtsReady || tts == null) return
        try {
            tts?.setSpeechRate(speechRate)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val allVoices = tts?.voices ?: emptySet()
                val curLocale = Locale.getDefault()
                val matchingVoices = allVoices.filter { 
                    it.locale.language == curLocale.language
                }.sortedByDescending { 
                    (if (it.locale.country == curLocale.country) 10 else 0) + 
                    (if (it.quality >= android.speech.tts.Voice.QUALITY_HIGH) 5 else 0) +
                    (if (it.isNetworkConnectionRequired) 3 else 0) // Prefer network voices for natural sound
                }

                if (matchingVoices.isNotEmpty()) {
                    val maleCandidates = matchingVoices.filter { 
                        it.name.contains("male", true) || it.name.contains("-a-", true) || it.name.contains("-c-", true)
                    }
                    val femaleCandidates = matchingVoices.filter { 
                        it.name.contains("female", true) || it.name.contains("-b-", true) || it.name.contains("-d-", true)
                    }

                    val selectedVoice = when (voicePreset) {
                        "LOW" -> maleCandidates.lastOrNull() ?: maleCandidates.firstOrNull() ?: matchingVoices.firstOrNull()
                        "BRISK" -> femaleCandidates.firstOrNull() ?: matchingVoices.firstOrNull()
                        else -> femaleCandidates.lastOrNull() ?: matchingVoices.firstOrNull()
                    }

                    if (selectedVoice != null) {
                        tts?.voice = selectedVoice
                    }
                }
            }
            val naturalPitch = when (voicePreset) {
                "M1" -> 0.82f
                "M2" -> 0.70f
                "F1" -> 1.18f
                "F2" -> 1.35f
                else -> speechPitch
            }
            tts?.setPitch(naturalPitch)
        } catch (_: Exception) {
            tts?.setPitch(speechPitch)
        }
    }

    private fun playAlertTone(onComplete: (() -> Unit)? = null) {
        try {
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
            val (toneType, durationMs) = when (alertType) {
                "Chime" -> Pair(android.media.ToneGenerator.TONE_PROP_PROMPT, 400)
                "Beep" -> Pair(android.media.ToneGenerator.TONE_PROP_ACK, 350)
                "Emergency" -> Pair(android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 800)
                "Siren" -> Pair(android.media.ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 700)
                "Buzzer" -> Pair(android.media.ToneGenerator.TONE_PROP_NACK, 500)
                else -> Pair(android.media.ToneGenerator.TONE_PROP_PROMPT, 400)
            }
            toneGen.startTone(toneType, durationMs)
            if (onComplete != null) {
                postDelayed({
                    try { toneGen.release() } catch (_: Exception) {}
                    onComplete()
                }, durationMs + 150L)
            } else {
                postDelayed({ try { toneGen.release() } catch (_: Exception) {} }, durationMs + 150L)
            }
        } catch (e: Exception) {
            onComplete?.invoke()
        }
    }

    fun speak(textMessage: String) {
        lastMessage = textMessage
        contentText.text = textMessage
        if (!isEditMode && textMessage.isNotBlank()) {
            when (broadcastMode) {
                "ALERT_ONLY" -> {
                    playAlertTone()
                }
                "ALERT_AND_TTS" -> {
                    playAlertTone {
                        if (isTtsReady) {
                            tts?.speak(textMessage, TextToSpeech.QUEUE_FLUSH, null, "BROADCAST_${System.currentTimeMillis()}")
                        }
                    }
                }
                else -> { // TTS_ONLY
                    if (isTtsReady) {
                        tts?.speak(textMessage, TextToSpeech.QUEUE_FLUSH, null, "BROADCAST_${System.currentTimeMillis()}")
                    }
                }
            }
        }
    }

    fun setMessageQuietly(textMessage: String) {
        lastMessage = textMessage
        contentText.text = textMessage
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tts?.stop()
        tts?.shutdown()
    }
}
