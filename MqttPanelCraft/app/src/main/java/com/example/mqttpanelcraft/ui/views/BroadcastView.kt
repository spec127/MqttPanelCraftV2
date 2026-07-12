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
 * 語音廣播畫布專屬視覺元件 (BroadcastView)
 * 接收文字訊息時利用 Android 系統內建 TTS 引擎進行語音播送，同時顯示近期廣播訊息。
 */
class BroadcastView(context: Context) : FrameLayout(context), TextToSpeech.OnInitListener {

    private val titleText: TextView
    private val contentText: TextView
    private val speakButton: ImageView
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastMessage: String = "等待語音廣播文字..."

    var isEditMode: Boolean = false

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

    init {
        setBackgroundResource(R.drawable.bg_card_unselected)
        setPadding(24, 20, 24, 20)

        tts = TextToSpeech(context, this)

        val container = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            orientation = LinearLayout.VERTICAL
        }

        // Header
        val headerRow = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply {
                marginEnd = 12
            }
            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            setColorFilter(Color.parseColor("#4CAF50"))
        }
        headerRow.addView(icon)

        titleText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "TTS 語音廣播"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E0E0E0"))
        }
        headerRow.addView(titleText)

        speakButton = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(56, 56)
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(Color.WHITE)
            setBackgroundResource(R.drawable.shape_circle_color)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2E3846"))
            setPadding(12, 12, 12, 12)
            setOnClickListener {
                speak(lastMessage)
            }
        }
        headerRow.addView(speakButton)

        container.addView(headerRow)

        // Content
        contentText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
            }
            text = lastMessage
            textSize = 14f
            setTextColor(Color.parseColor("#B0B8C4"))
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        container.addView(contentText)

        addView(container)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (isTtsReady) {
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(speechPitch)
            }
        }
    }

    fun speak(textMessage: String) {
        lastMessage = textMessage
        contentText.text = textMessage
        if (!isEditMode && isTtsReady && textMessage.isNotBlank()) {
            tts?.speak(textMessage, TextToSpeech.QUEUE_FLUSH, null, "BROADCAST_${System.currentTimeMillis()}")
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tts?.stop()
        tts?.shutdown()
    }
}
