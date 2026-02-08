package com.example.mqttpanelcraft.ui.components

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import com.example.mqttpanelcraft.R

object ComponentContainer {

    fun createEndpoint(context: Context, tag: String, isEditMode: Boolean): FrameLayout {
        val container = InterceptableFrameLayout(context)
        container.setBackgroundResource(R.drawable.component_border)
        container.setPadding(8, 8, 8, 8)
        container.tag = tag // Important for Renderer
        container.isEditMode = isEditMode

        // Add Resize Handle (Default for all components)
        val handle =
                View(context).apply {
                    this.tag = "RESIZE_HANDLE"
                    // Enlarged view (48dp) for better touch targets.
                    // But centered at the corner: Half of 48dp is 24dp.
                    // Using 48px/dp depends on density, here we use fixed px for relative offset
                    // logic if needed
                    // or better, rely on Gravity and negative Margins to center the "Visual Center"
                    // on the corner.
                    val handleSize = 48
                    layoutParams =
                            FrameLayout.LayoutParams(handleSize, handleSize).apply {
                                gravity = Gravity.BOTTOM or Gravity.END
                                // Pivot the center (24, 24) onto the corner (0, 0) relative to
                                // bottom-end
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
}
