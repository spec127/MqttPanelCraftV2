package com.example.mqttpanelcraft.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
class WebBoxView(context: Context) : FrameLayout(context) {

    private val webView: WebView = WebView(context)
    private val refreshHandler = Handler(Looper.getMainLooper())

    var isEditMode: Boolean = false
    
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
        
    var transparentBg: Boolean = false
        set(value) {
            field = value
            updateBackground()
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
        updateBackground()
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
    
    private fun updateInteraction() {
        if (isEditMode) {
            // In edit mode, intercept all touches so it can be dragged
            webView.setOnTouchListener { _, _ -> true }
        } else {
            // In run mode, allow interaction if enabled, else intercept
            if (enableInteraction) {
                webView.setOnTouchListener(null)
            } else {
                webView.setOnTouchListener { _, _ -> true }
            }
        }
    }
    
    private fun updateBackground() {
        if (transparentBg) {
            webView.setBackgroundColor(Color.TRANSPARENT)
            setBackgroundColor(Color.TRANSPARENT)
        } else {
            webView.setBackgroundColor(Color.WHITE) // Default web view background
            setBackgroundColor(Color.TRANSPARENT) // Parent is transparent
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
