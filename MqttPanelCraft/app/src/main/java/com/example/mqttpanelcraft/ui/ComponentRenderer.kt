package com.example.mqttpanelcraft.ui

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.components.InterceptableFrameLayout

/**
 * Visual Renderer. Responsible for syncing the ViewModel state (List<ComponentData>) to the Android
 * View System. It strictly follows One-Way Data Flow: Data -> View.
 */
class ComponentRenderer(
        private val canvasCanvas: FrameLayout,
        private val context: android.content.Context
) {

    // Keep track of existing views to avoid recreating them (Diffing)
    private val viewCache = mutableMapOf<Int, View>()

    private var selectedComponentId: Int? = null

    fun render(components: List<ComponentData>, isEditMode: Boolean, selectedId: Int? = null) {
        this.selectedComponentId = selectedId
        val currentIds = components.map { it.id }.toSet()

        // 1. Remove stale views (components that were deleted)
        val iterator = viewCache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!currentIds.contains(entry.key)) {
                canvasCanvas.removeView(entry.value)
                // Also remove label
                val label = canvasCanvas.findViewWithTag<TextView>("LABEL_FOR_${entry.key}")
                if (label != null) canvasCanvas.removeView(label)
                iterator.remove()
            }
        }

        // 2. Add or Update views
        components.forEach { data ->
            val existingView = viewCache[data.id]
            val isSelected = (data.id == selectedId)

            if (existingView == null) {
                // CREATE NEW
                val def =
                        com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(
                                data.type
                        )
                val newView =
                        def?.createView(context, isEditMode)
                                ?: android.widget.TextView(context).apply {
                                    text = "Unknown: ${data.type}"
                                }

                newView.id = data.id // Important: View ID matches Data ID

                // Set Layout
                val params = FrameLayout.LayoutParams(data.width, data.height)
                newView.layoutParams = params
                newView.x = data.x
                newView.y = data.y

                // Add to Canvas
                canvasCanvas.addView(newView)
                viewCache[data.id] = newView

                // Create Label
                val label =
                        TextView(context).apply {
                            text = data.label
                            tag = "LABEL_FOR_${data.id}"

                            // Explicit LayoutParams for FrameLayout
                            layoutParams =
                                    FrameLayout.LayoutParams(
                                                    data.width,
                                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                                            )
                                            .apply {
                                                gravity =
                                                        android.view.Gravity.TOP or
                                                                android.view.Gravity.START
                                            }

                            x = data.x
                            y = data.y + data.height + 4
                            x = data.x
                            y = data.y + data.height + 4
                            gravity = android.view.Gravity.START
                            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                            val isHidden = data.props?.get("showLabel") == "false"
                            visibility = if (isHidden) View.GONE else View.VISIBLE
                        }
                canvasCanvas.addView(label)

                // Apply Initial State
                updateViewState(newView, data, isEditMode, isSelected)
            } else {
                // UPDATE EXISTING

                // Position & Size Update
                if (existingView.x != data.x || existingView.y != data.y) {
                    existingView.x = data.x
                    existingView.y = data.y
                }

                val params = existingView.layoutParams
                if (params.width != data.width || params.height != data.height) {
                    params.width = data.width
                    params.height = data.height
                    existingView.layoutParams = params
                }

                // Apply State (Border, Handle, etc)
                updateViewState(existingView, data, isEditMode, isSelected)

                // Label Update
                val label = canvasCanvas.findViewWithTag<TextView>("LABEL_FOR_${data.id}")
                if (label != null) {
                    if (label.text != data.label) label.text = data.label

                    // Update Params
                    val params = label.layoutParams
                    if (params.width != data.width) {
                        params.width = data.width
                        label.layoutParams = params
                    }

                    label.x = data.x
                    label.y = data.y + data.height + 4
                    label.x = data.x
                    label.y = data.y + data.height + 4
                    label.gravity = android.view.Gravity.START
                    label.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    val isHidden = data.props?.get("showLabel") == "false"
                    label.visibility = if (isHidden) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun updateViewState(
            view: View,
            data: ComponentData,
            isEditMode: Boolean,
            isSelected: Boolean
    ) {
        if (view is InterceptableFrameLayout) {
            view.isEditMode = isEditMode

            // Border Logic
            if (isEditMode && isSelected) {
                view.setBackgroundResource(R.drawable.component_border_selected)
            } else {
                view.background = null
            }

            // Handle Visibility
            val handle = view.findViewWithTag<View>("RESIZE_HANDLE")
            handle?.visibility = if (isEditMode && isSelected) View.VISIBLE else View.GONE
            // Update Handle Style: Use the new crosshair drawable
            handle?.setBackgroundResource(R.drawable.bg_resize_handle)

            // Ensure canvas doesn't clip children (so handle can bleed out)
            canvasCanvas.clipChildren = false
            canvasCanvas.clipToPadding = false

            val clear = view.findViewWithTag<View>("CLEAR_BTN")
            clear?.visibility = if (isEditMode && isSelected) View.VISIBLE else View.GONE

            // Dynamic Update via Definition
            val def =
                    com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(
                            data.type
                    )
            def?.onUpdateView(view, data)
        }
    }

    // Helper to get view for behavior binding
    fun getView(id: Int): View? = viewCache[id]
}
