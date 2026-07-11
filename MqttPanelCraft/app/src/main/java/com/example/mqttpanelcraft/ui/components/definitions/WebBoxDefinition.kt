package com.example.mqttpanelcraft.ui.components.definitions

import android.annotation.SuppressLint
import android.content.Context
import android.util.Size
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition

/**
 * 內嵌方塊網頁元件 (WebBoxDefinition)
 *
 * Design Intent:
 * 完全參考 WebViewActivity 網頁功能的寫法，在面板內部建立獨立的 WebView 方塊，啟用 JavaScript 與 DOM 存儲，
 * 支援載入自訂 HTML 或 HTTP/HTTPS 網址，並可透過 MQTT 接收 HTML/URL 動態更新。
 */
object WebBoxDefinition : IComponentDefinition {

    override val type: String = "WEB_BOX"
    override val defaultSize: Size = Size(300, 220)
    override val labelPrefix: String = "web"
    override val iconResId: Int = android.R.drawable.ic_menu_view
    override val group: String = "DISPLAY"

    override val propertiesLayoutId: Int = R.layout.layout_prop_generic_color

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "url" to "https://www.google.com",
        "html" to "<html><body style='background:#181818;color:white;text-align:center;'><h3>WebBox Embedded</h3></body></html>",
        "mode" to "HTML"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)

        val webView = WebView(context).apply {
            tag = "target_webview"
            webViewClient = WebViewClient()
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 預設展示內嵌 HTML
        val defaultHtml = "<html><body style='background:#181818;color:white;display:flex;align-items:center;justify-content:center;height:100%'><h3>Embedded Web Box</h3></body></html>"
        webView.loadDataWithBaseURL(null, defaultHtml, "text/html", "utf-8", null)

        container.addView(webView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val webView = container.findViewWithTag<WebView>("target_webview") ?: return

        val mode = data.props["mode"] ?: "HTML"
        if (mode == "URL") {
            val url = data.props["url"] ?: return
            if (url.startsWith("http://") || url.startsWith("https://")) {
                webView.loadUrl(url)
            }
        } else {
            val html = data.props["html"]
            if (!html.isNullOrEmpty()) {
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        }
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {}

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {}

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val container = view as? FrameLayout ?: return
        val webView = container.findViewWithTag<WebView>("target_webview") ?: return

        if (payload.startsWith("http://") || payload.startsWith("https://")) {
            webView.loadUrl(payload)
        } else {
            webView.loadDataWithBaseURL(null, payload, "text/html", "utf-8", null)
        }
    }
}
