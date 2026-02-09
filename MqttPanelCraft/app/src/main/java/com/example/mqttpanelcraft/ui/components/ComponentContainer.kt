package com.example.mqttpanelcraft.ui.components

import android.content.Context
import android.content.res.Configuration
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
            group: String = "CONTROL"
    ): FrameLayout {
        val container = InterceptableFrameLayout(context)

        // Dynamic border color based on component group
        val isDark =
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
        val borderColor = getGroupColor(group, isDark)

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

    private fun getGroupColor(group: String, isDark: Boolean): Int {
        return when (group) {
            "CONTROL" -> if (isDark) Color.parseColor("#1976D2") else Color.parseColor("#2196F3")
            "SENSOR" -> if (isDark) Color.parseColor("#FBC02D") else Color.parseColor("#FFEB3B")
            "DISPLAY" -> if (isDark) Color.parseColor("#D32F2F") else Color.parseColor("#F44336")
            else -> if (isDark) Color.parseColor("#757575") else Color.parseColor("#9E9E9E")
        }
    }
}
