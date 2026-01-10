package com.example.mqttpanelcraft.ui.properties

import android.view.View
import com.example.mqttpanelcraft.model.ComponentData

/**
 * Interface for component-specific property binding.
 * Each component type (BUTTON, SWITCH, etc.) can have its own binder.
 */
interface IComponentPropertyBinder {
    /**
     * Return the layout resource ID for the specific properties.
     * e.g. R.layout.props_button
     */
    fun getLayoutId(): Int

    /**
     * Bind the specific properties UI to the component data.
     * @param rootView The root of the inflated layout (getLayoutId).
     * @param data The current component data.
     * @param onUpdate Callback when a specific property changes. Key should match the key in `data.props`.
     */
    fun bind(rootView: View, data: ComponentData, onUpdate: (key: String, value: String) -> Unit)
}
