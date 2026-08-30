package com.example.mqttpanelcraft.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewOutlineProvider
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
class WebBoxView(context: Context) : FrameLayout(context) {

    private val webView: WebView = WebView(context)
    private val refreshHandler = Handler(Looper.getMainLooper())

    var isEditMode: Boolean = false
        set(value) {
            field = value
            updateInteraction()
            updateRefreshTimer()
        }
    
    // Properties
    var sourceType: String = "URL" // "URL" or "HTML"
        set(value) {
            field = value
            loadContent()
        }
        
    var urlContent: String = ""
        set(value) {
            field = value
            if (sourceType == "URL") loadContent()
        }
        
    var htmlContent: String = ""
        set(value) {
            field = value
            if (sourceType == "HTML") loadContent()
        }
        
    var enableInteraction: Boolean = true
        set(value) {
            field = value
            updateInteraction()
        }

    var showBorder: Boolean = false
        set(value) {
            field = value
            updateBorder()
        }
        
    var refreshIntervalSec: Int = 0
        set(value) {
            field = value
            updateRefreshTimer()
        }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isEditMode && refreshIntervalSec > 0) {
                webView.reload()
                refreshHandler.postDelayed(this, refreshIntervalSec * 1000L)
            }
        }
    }

    init {
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        
        // Setup WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        
        // Prevent opening in external browser
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // false means WebView handles it
            }
        }
        
        updateInteraction()
        updateBorder()
    }

    private fun loadContent() {
        if (sourceType == "URL") {
            if (urlContent.isNotBlank()) {
                val finalUrl = if (!urlContent.startsWith("http://") && !urlContent.startsWith("https://")) {
                    "https://$urlContent"
                } else {
                    urlContent
                }
                webView.loadUrl(finalUrl)
            } else {
                webView.loadDataWithBaseURL(null, "<html><body style='display:flex;align-items:center;justify-content:center;height:100%;font-family:sans-serif;'><h3>Please enter a URL in properties</h3></body></html>", "text/html", "utf-8", null)
            }
        } else {
            if (htmlContent.isNotBlank()) {
                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
            } else {
                webView.loadDataWithBaseURL(null, "<html><body style='display:flex;align-items:center;justify-content:center;height:100%;font-family:sans-serif;'><h3>Please enter HTML code in properties</h3></body></html>", "text/html", "utf-8", null)
            }
        }
    }
    private fun updateBorder() {
        if (showBorder) {
            val density = resources.displayMetrics.density
            val borderWidth = (3f * density).toInt().coerceAtLeast(1)
            val outerRadius = 12f * density
            background = GradientDrawable().apply {
                setStroke(borderWidth, Color.parseColor("#7B1FA2"))
                cornerRadius = outerRadius
                setColor(Color.WHITE)
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setPadding(borderWidth, borderWidth, borderWidth, borderWidth)

            webView.background = GradientDrawable().apply {
                cornerRadius = (outerRadius - borderWidth).coerceAtLeast(0f)
                setColor(Color.WHITE)
            }
            webView.outlineProvider = ViewOutlineProvider.BACKGROUND
            webView.clipToOutline = true
        } else {
            setPadding(0, 0, 0, 0)
            background = null
            outlineProvider = null
            clipToOutline = false
            webView.background = ColorDrawable(Color.WHITE)
            webView.outlineProvider = null
            webView.clipToOutline = false
        }
    }

    private fun updateInteraction() {
        webView.isEnabled = !isEditMode && enableInteraction
        if (isEditMode) {
            // In edit mode, intercept all touches so it can be dragged
            webView.setOnTouchListener { _, event -> if(event.action == MotionEvent.ACTION_DOWN) { parent.requestDisallowInterceptTouchEvent(false) }; true }
        } else {
            // In run mode, allow interaction if enabled, else intercept
            if (enableInteraction) {
                webView.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                                parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            } else {
                webView.setOnTouchListener { _, event -> if(event.action == MotionEvent.ACTION_DOWN) { parent.requestDisallowInterceptTouchEvent(false) }; true }
            }
        }
    }
    
    private fun updateRefreshTimer() {
        refreshHandler.removeCallbacks(refreshRunnable)
        if (!isEditMode && refreshIntervalSec > 0) {
            refreshHandler.postDelayed(refreshRunnable, refreshIntervalSec * 1000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateRefreshTimer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        refreshHandler.removeCallbacks(refreshRunnable)
    }
}
