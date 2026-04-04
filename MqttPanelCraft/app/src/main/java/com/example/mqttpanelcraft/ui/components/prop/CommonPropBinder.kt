package com.example.mqttpanelcraft.ui.components.prop

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * A utility class to reduce boilerplate code in
 * [com.example.mqttpanelcraft.ui.components.IComponentDefinition.bindPropertiesPanel].
 */
object CommonPropBinder {

    /** Binds a standard color palette + custom color picker to a FrameLayout container. */
    fun bindColorPalette(
            panelView: View,
            containerId: Int,
            propKey: String,
            data: ComponentData,
            onUpdate: (String, String) -> Unit,
            label: String? = null
    ) {
        val container = panelView.findViewById<FrameLayout>(containerId) ?: return
        val context = panelView.context
        val currentColor = data.props[propKey] ?: "#2196F3"

        if (container.childCount == 0) {
            LayoutInflater.from(context)
                    .inflate(R.layout.layout_prop_generic_color, container, true)
        }

        // Palette colors
        val colorViews =
                listOf(R.id.vColor1, R.id.vColor2, R.id.vColor3, R.id.vColor4, R.id.vColor5).map {
                    container.findViewById<View>(it)
                }
        val btnSelect = container.findViewById<View>(R.id.btnColorCustom)

        fun refreshPalette() {
            val recent = ColorHistoryManager.load(context)
            colorViews.forEachIndexed { i, v ->
                if (v != null && i < recent.size) {
                    val colorStr = recent[i]
                    v.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorStr))
                    v.setOnClickListener { onUpdate(propKey, colorStr) }
                }
            }
        }
        refreshPalette()

        btnSelect?.setOnClickListener {
            ColorPickerDialog(
                            context,
                            currentColor,
                            true,
                            { selectedColor ->
                                onUpdate(propKey, selectedColor)
                                ColorHistoryManager.save(context, selectedColor)
                                refreshPalette()
                            },
                            {}
                    )
                    .show(btnSelect ?: container)
        }
    }

    /** Binds a MaterialButtonToggleGroup to a property. */
    fun bindToggleGroup(
            panelView: View,
            toggleGroupId: Int,
            propKey: String,
            data: ComponentData,
            onUpdate: (String, String) -> Unit,
            map: Map<Int, String>
    ) {
        val toggleGroup = panelView.findViewById<MaterialButtonToggleGroup>(toggleGroupId) ?: return
        val currentValue = data.props[propKey]

        // Find button ID by value
        val initialBtnId = map.entries.find { it.value == currentValue }?.key ?: map.keys.first()
        toggleGroup.check(initialBtnId)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                map[checkedId]?.let { onUpdate(propKey, it) }
            }
        }
    }

    /** Binds an EditText to a property. */
    fun bindEditText(
            panelView: View,
            editTextId: Int,
            propKey: String,
            data: ComponentData,
            onUpdate: (String, String) -> Unit,
            defaultValue: String = ""
    ) {
        val editText = panelView.findViewById<EditText>(editTextId) ?: return
        editText.setText(data.props[propKey] ?: defaultValue)
        editText.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        onUpdate(propKey, s?.toString() ?: "")
                    }
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}
                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}
                }
        )
    }

    /** Binds an AutoCompleteTextView (Dropdown) to a property. */
    fun bindDropdown(
            panelView: View,
            autoCompleteId: Int,
            propKey: String,
            data: ComponentData,
            onUpdate: (String, String) -> Unit,
            options: List<String>,
            valueMap: Map<String, String>? = null, // Label to Value
            defaultValue: String = ""
    ) {
        val autoComplete = panelView.findViewById<AutoCompleteTextView>(autoCompleteId) ?: return
        val context = panelView.context

        val currentValue = data.props[propKey] ?: defaultValue
        val currentLabel =
                if (valueMap != null) {
                    valueMap.entries.find { it.value == currentValue }?.key ?: options[0]
                } else {
                    currentValue
                }

        autoComplete.setText(currentLabel, false)
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, options)
        autoComplete.setAdapter(adapter)

        autoComplete.setOnItemClickListener { _, _, position, _ ->
            val selectedLabel = options[position]
            val selectedValue = valueMap?.get(selectedLabel) ?: selectedLabel
            onUpdate(propKey, selectedValue)
        }
    }
}
