package com.example.mqttpanelcraft.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import com.example.mqttpanelcraft.R

object ComponentContainer {

    fun createEndpoint(
            context: Context,
            tag: String,
            isEditMode: Boolean,
            group: ComponentGroup = ComponentGroup.CONTROL
    ): FrameLayout {
        val container = InterceptableFrameLayout(context)

        val borderColor = getGroupColor(context, group)

        val borderDrawable =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setStroke(2 * context.resources.displayMetrics.density.toInt(), borderColor)
                    cornerRadius = 8 * context.resources.displayMetrics.density
                    setColor(Color.TRANSPARENT)
                }
        container.background = borderDrawable

        container.setPadding(8, 8, 8, 8)
        container.tag = tag // Important for Renderer
        container.isEditMode = isEditMode

        // Add Resize Handle (Default for all components)
        val handle =
                View(context).apply {
                    this.tag = "RESIZE_HANDLE"
                    val handleSize = 48
                    layoutParams =
                            FrameLayout.LayoutParams(handleSize, handleSize).apply {
                                gravity = Gravity.BOTTOM or Gravity.END
                                setMargins(0, 0, -handleSize / 2, -handleSize / 2)
                            }
                    setBackgroundResource(R.drawable.bg_resize_handle)
                    elevation = 100f
                    visibility = View.GONE
                }
        container.addView(handle)
        container.clipChildren = false
        container.clipToPadding = false

        // Close/Clear Button (Special case for Image, or maybe generic later)
        if (tag == "IMAGE") {
            val closeBtn =
                    ImageButton(context).apply {
                        setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                        background = null
                        val params = FrameLayout.LayoutParams(64, 64)
                        params.gravity = Gravity.TOP or Gravity.END
                        layoutParams = params
                        this.tag = "CLEAR_BTN"
                        visibility = if (isEditMode) View.VISIBLE else View.GONE
                        elevation = 10f
                    }
            container.addView(closeBtn)
        }

        return container
    }

    private fun getGroupColor(context: Context, group: ComponentGroup): Int {
        return when (group) {
            ComponentGroup.CONTROL -> context.getColor(R.color.component_control_accent)
            ComponentGroup.SENSOR -> context.getColor(R.color.component_sensor_accent)
            ComponentGroup.DISPLAY -> context.getColor(R.color.component_display_accent)
        }
    }
}
