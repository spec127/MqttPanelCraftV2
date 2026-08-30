package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.util.Size
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.views.WebBoxView

object WebBoxDefinition : IComponentDefinition {
    override val type: String = "WEB_BOX"
    override val defaultSize: Size = Size(300, 200)
    override val labelPrefix: String = "web"
    override val iconResId: Int = android.R.drawable.ic_menu_mapmode
    override val group: String = "DISPLAY"
    override val propertiesLayoutId: Int = R.layout.layout_prop_web

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "source_type" to "URL",
        "url" to "https://www.google.com",
        "html" to "<h1>Hello World</h1>",
        "enable_interaction" to "true",
        "transparent_bg" to "false",
        "refresh_interval" to "0", "show_border" to "false"
    )

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val view = WebBoxView(context).apply {
            tag = "target_web"
            this.isEditMode = isEditMode
        }
        container.addView(view, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val container = view as? FrameLayout ?: return
        val webView = container.findViewWithTag<WebBoxView>("target_web") ?: return

        webView.isEditMode =
            (container as? com.example.mqttpanelcraft.ui.components.InterceptableFrameLayout)
                ?.isEditMode ?: false
        webView.sourceType = data.props["source_type"] ?: "URL"
        webView.urlContent = data.props["url"] ?: ""
        webView.htmlContent = data.props["html"] ?: ""
        webView.enableInteraction = (data.props["enable_interaction"] ?: "true").toBoolean()
        webView.transparentBg = (data.props["transparent_bg"] ?: "false").toBoolean()
        webView.refreshIntervalSec = (data.props["refresh_interval"] ?: "0").toIntOrNull() ?: 0
        webView.showBorder = (data.props["show_border"] ?: "false").toBoolean()
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val binder = CommonPropBinder
        
        // 1. Source Type Dropdown
        binder.bindDropdown(
            panelView, R.id.spSourceType, "source_type", data, onUpdate,
            listOf("外部網址 (URL)", "靜態代碼 (HTML)"),
            mapOf("外部網址 (URL)" to "URL", "靜態代碼 (HTML)" to "HTML"),
            defaultValue = "URL"
        )
        
        // Setup visibility logic based on Source Type
        val tilUrl = panelView.findViewById<View>(R.id.tilUrl)
        val tilHtml = panelView.findViewById<View>(R.id.tilHtml)
        
        fun updateVisibility(type: String) {
            tilUrl?.visibility = if (type == "URL") View.VISIBLE else View.GONE
            tilHtml?.visibility = if (type == "HTML") View.VISIBLE else View.GONE
        }
        
        updateVisibility(data.props["source_type"] ?: "URL")
        
        val spSourceType = panelView.findViewById<TextView>(R.id.spSourceType)
        spSourceType?.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val valStr = s?.toString() ?: ""
                val type = if (valStr.contains("URL")) "URL" else "HTML"
                updateVisibility(type)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 2. URL Input
        binder.bindEditText(panelView, R.id.etUrl, "url", data, onUpdate)

        // 3. HTML Input
        binder.bindEditText(panelView, R.id.etHtml, "html", data, onUpdate)

        // 4. Enable Interaction Switch
        binder.bindSwitch(panelView, R.id.swEnableInteraction, "enable_interaction", data, onUpdate, defaultChecked = true)
        
        // 5. Transparent Background Switch
        binder.bindSwitch(panelView, R.id.swTransparentBg, "transparent_bg", data, onUpdate, defaultChecked = false)

        // 6. Refresh Interval Input
        binder.bindEditText(panelView, R.id.etRefreshInterval, "refresh_interval", data, onUpdate)

        // 7. Show Border Toggle
        val itemShowBorder = panelView.findViewById<View>(R.id.itemShowBorder)
        val vShowBorderEnabled = panelView.findViewById<View>(R.id.vShowBorderEnabled)
        val updateBorderUI = {
            val showBorder = data.props["show_border"] == "true"
            vShowBorderEnabled?.visibility = if (showBorder) View.VISIBLE else View.INVISIBLE
        }
        updateBorderUI()
        itemShowBorder?.setOnClickListener {
            val showBorder = data.props["show_border"] == "true"
            val newVal = if (showBorder) "false" else "true"
            data.props["show_border"] = newVal
            updateBorderUI()
            onUpdate("show_border", newVal)
        }
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
    }
}
