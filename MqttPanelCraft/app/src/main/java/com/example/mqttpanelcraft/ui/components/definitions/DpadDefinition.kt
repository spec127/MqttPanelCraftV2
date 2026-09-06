package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Size
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.ComponentGroup
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.example.mqttpanelcraft.ui.components.prop.CommonPropBinder
import com.example.mqttpanelcraft.ui.components.prop.PropertyOption
import com.example.mqttpanelcraft.ui.views.JoystickView
import com.example.mqttpanelcraft.utils.TextWatcherAdapter
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * 方向鍵獨立元件 (DpadDefinition)
 *
 * Design Intent:
 * 自原本搖桿元件的 Buttons 模式抽離獨立出的方向鍵控制元件。
 * 支援 4-Way / 2-Way Horizontal / 2-Way Vertical 軸向選擇，
 * 提供圓形 (Beveled) 與銳利 (Neon) 外觀，並支援鬆手發送開關。
 */
object DpadDefinition : IComponentDefinition {

    override val type: String = "DPAD"
    override val defaultSize: Size = Size(180, 180)
    override val labelPrefix: String = "dpad"
    override val displayNameResId: Int = R.string.component_label_dpad
    override val iconResId: Int = R.drawable.ic_joystick
    override val group = ComponentGroup.CONTROL

    override val propertiesLayoutId: Int = R.layout.layout_prop_joystick

    override fun getDefaultProps(): Map<String, String> = mapOf(
        "color" to "#6366F1",
        "theme_color" to "#6366F1",
        "msg_up" to "up",
        "msg_down" to "down",
        "msg_left" to "left",
        "msg_right" to "right",
        "msg_release" to "stop",
        "send_on_release" to "true",
        "style" to "Beveled",
        "axisMode" to "4-Way"
    )

    override fun isFixedAspectRatio(data: ComponentData): Boolean = true

    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode, group)
        val dpadView = JoystickView(context).apply {
            tag = "target"
            joystickMode = "Buttons" // 強制設定為按鍵式方向鍵
            axisMode = "4-Way"
            visualStyle = "Beveled"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(dpadView, 0)
        return container
    }

    override fun onUpdateView(view: View, data: ComponentData) {
        val dpad = view.findViewWithTag<JoystickView>("target") ?: return
        dpad.joystickMode = "Buttons" // 始終維持按鍵式方向鍵
        dpad.axisMode = data.props["axisMode"] ?: "4-Way"
        dpad.visualStyle = data.props["style"] ?: "Beveled"

        dpad.msgUp = data.props["msg_up"] ?: "up"
        dpad.msgDown = data.props["msg_down"] ?: "down"
        dpad.msgLeft = data.props["msg_left"] ?: "left"
        dpad.msgRight = data.props["msg_right"] ?: "right"

        val sendOnRelease = (data.props["send_on_release"] ?: "true") == "true"
        dpad.msgRelease = if (sendOnRelease) (data.props["msg_release"] ?: "stop") else ""

        val colorHex = data.props["color"] ?: "#6366F1"
        try {
            dpad.color = Color.parseColor(colorHex)
        } catch (_: Exception) {
            dpad.color = Color.parseColor("#6366F1")
        }
        dpad.invalidate()
    }

    override fun bindPropertiesPanel(
        panelView: View,
        data: ComponentData,
        onUpdate: (String, String) -> Unit
    ) {
        val context = panelView.context

        // 1. 隱藏純搖桿專用的無關區域（包含發送間隔與數值範圍區間）
        panelView.findViewById<View>(R.id.toggleJoystickMode)?.visibility = View.GONE
        panelView.findViewById<View>(R.id.containerJoystickInterval)?.visibility = View.GONE
        panelView.findViewById<View>(R.id.containerRangePrecision)?.visibility = View.GONE

        // 2. 顯示方向鍵按鍵指令專用區域與軸向選擇
        panelView.findViewById<View>(R.id.containerButtonMessages)?.visibility = View.VISIBLE

        val toggleAxisMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleAxisMode)
        toggleAxisMode?.visibility = View.VISIBLE

        val curAxisMode = data.props["axisMode"] ?: "4-Way"
        val containerMsgUpDown = panelView.findViewById<View>(R.id.containerMsgUpDown)
        val containerMsgLeftRight = panelView.findViewById<View>(R.id.containerMsgLeftRight)

        val updateMsgVisibility = { axes: String ->
            containerMsgUpDown?.visibility =
                if (axes == "2-Way Horizontal") View.GONE else View.VISIBLE
            containerMsgLeftRight?.visibility =
                if (axes == "2-Way Vertical") View.GONE else View.VISIBLE
        }
        updateMsgVisibility(curAxisMode)

        when (curAxisMode) {
            "2-Way Horizontal" -> toggleAxisMode?.check(R.id.btnAxis2WayH)
            "2-Way Vertical" -> toggleAxisMode?.check(R.id.btnAxis2WayV)
            else -> toggleAxisMode?.check(R.id.btnAxis4Way)
        }

        toggleAxisMode?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newAxisMode = when (checkedId) {
                    R.id.btnAxis2WayH -> "2-Way Horizontal"
                    R.id.btnAxis2WayV -> "2-Way Vertical"
                    else -> "4-Way"
                }
                onUpdate("axisMode", newAxisMode)
                updateMsgVisibility(newAxisMode)
            }
        }

        // 3. 風格設定面板 ("圓形" to "Beveled", "銳利" to "Neon")
        CommonPropBinder.bindLocalizedDropdown(
                panelView,
                R.id.tvJoystickStyle,
                "style",
                data,
                onUpdate,
                listOf(
                        PropertyOption("Beveled", R.string.val_joystick_style_smooth),
                        PropertyOption("Neon", R.string.val_joystick_style_sharp)
                ),
                "Beveled"
        )

        // 4. 方向指令輸入框設定
        val bindMsg = { id: Int, key: String, defVal: String ->
            CommonPropBinder.bindEditText(panelView, id, key, data, onUpdate, defVal)
        }
        bindMsg(R.id.etMsgUp, "msg_up", "up")
        bindMsg(R.id.etMsgDown, "msg_down", "down")
        bindMsg(R.id.etMsgLeft, "msg_left", "left")
        bindMsg(R.id.etMsgRight, "msg_right", "right")
        bindMsg(R.id.etMsgRelease, "msg_release", "stop")

        // 5. 鬆手發送開關設定與顯示/隱藏發送訊息框
        val toggleSendOnRelease = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleSendOnRelease)
        val containerMsgReleaseInput = panelView.findViewById<View>(R.id.containerMsgReleaseInput)
        val isSendOnRelease = (data.props["send_on_release"] ?: "true") == "true"

        val updateReleaseUI = { enabled: Boolean ->
            containerMsgReleaseInput?.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        updateReleaseUI(isSendOnRelease)

        if (isSendOnRelease) {
            toggleSendOnRelease?.check(R.id.btnSendOnReleaseTrue)
        } else {
            toggleSendOnRelease?.check(R.id.btnSendOnReleaseFalse)
        }

        toggleSendOnRelease?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val enabled = (checkedId == R.id.btnSendOnReleaseTrue)
                onUpdate("send_on_release", if (enabled) "true" else "false")
                updateReleaseUI(enabled)
            }
        }

        // 6. 顏色調色盤與自訂色彩設定
        val colorViews = listOf(
            R.id.vColor1, R.id.vColor2, R.id.vColor3, R.id.vColor4, R.id.vColor5
        ).map { panelView.findViewById<View>(it) }

        fun refreshColors() {
            val recent = ColorHistoryManager.load(context)
            colorViews.forEachIndexed { i, v ->
                if (v != null && i < recent.size) {
                    v.backgroundTintList = ColorStateList.valueOf(Color.parseColor(recent[i]))
                    v.setOnClickListener { onUpdate("color", recent[i]) }
                }
            }
        }
        refreshColors()

        panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener { anchor ->
            val cur = data.props["color"] ?: "#6366F1"
            var tempColor = cur
            ColorPickerDialog(
                context,
                cur,
                true,
                {
                    tempColor = it
                    onUpdate("color", it)
                },
                {
                    ColorHistoryManager.save(context, tempColor)
                    refreshColors()
                }
            ).show(anchor)
        }
    }

    override fun attachBehavior(
        view: View,
        data: ComponentData,
        sendMqtt: (topic: String, payload: String) -> Unit,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {
        val dpad = view.findViewWithTag<JoystickView>("target") ?: return
        dpad.onJoystickChange = { payload ->
            if (data.topicConfig.isNotEmpty() && payload.isNotEmpty()) {
                sendMqtt(data.topicConfig, payload)
            }
        }
    }

    override fun onMqttMessage(
        view: View,
        data: ComponentData,
        payload: String,
        onUpdateProp: (key: String, value: String) -> Unit
    ) {}
}
