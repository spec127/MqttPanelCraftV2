package com.example.mqttpanelcraft.ui.components.prop

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.google.android.material.button.MaterialButtonToggleGroup

/** A localized label paired with the stable value stored in project JSON. */
data class PropertyOption(val value: String, @StringRes val labelResId: Int)

/**
 * A utility class to reduce boilerplate code in
 * [com.example.mqttpanelcraft.ui.components.IComponentDefinition.bindPropertiesPanel].
 */
object CommonPropBinder {

    private val activePalettes = java.util.WeakHashMap<View, (String?) -> Unit>()

    fun registerPalette(container: View, refresher: (String?) -> Unit) {
        activePalettes[container] = refresher
    }

    fun notifyHistoryChanged() {
        activePalettes.values.forEach { it.invoke(null) }
    }

    /** Binds a standard color palette + custom color picker to a FrameLayout container. */
    fun bindColorPalette(
            panelView: View,
            containerId: Int,
            propKey: String,
            data: ComponentData,
            onUpdate: (String, String) -> Unit,
            label: String? = null,
            defaultColor: String = "#2196F3"
    ) {
        val container = panelView.findViewById<FrameLayout>(containerId) ?: return
        val context = panelView.context
        val currentColor = data.props[propKey] ?: defaultColor

        if (container.childCount == 0) {
            LayoutInflater.from(context)
                    .inflate(R.layout.layout_prop_generic_color, container, true)
        }

        // Apply Native Label
        val inputLayout = container.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.containerColorInputLayout)
        if (label != null) {
            inputLayout?.hint = label
            inputLayout?.isHintEnabled = true
        } else {
            inputLayout?.hint = null
            inputLayout?.isHintEnabled = false
        }

        // Palette colors
        val colorViews =
                listOf(R.id.vColor1, R.id.vColor2, R.id.vColor3, R.id.vColor4, R.id.vColor5).map {
                    container.findViewById<View>(it)
                }
        val btnSelect = container.findViewById<View>(R.id.btnColorCustom)

        fun refreshPalette(currentSelectedColor: String?) {
            val selected = currentSelectedColor ?: data.props[propKey] ?: defaultColor
            val recent = ColorHistoryManager.load(context)
            colorViews.forEachIndexed { i, v ->
                if (v != null && i < recent.size) {
                    val colorStr = recent[i]
                    v.backgroundTintList = ColorStateList.valueOf(Color.parseColor(colorStr))
                    v.setOnClickListener { 
                        onUpdate(propKey, colorStr) 
                        refreshPalette(colorStr)
                        notifyHistoryChanged()
                    }
                }
            }
            // Update custom picker icon to match currently selected color
            try {
                (btnSelect as? android.widget.ImageView)?.imageTintList = ColorStateList.valueOf(Color.parseColor(selected))
            } catch (e: Exception) {}
        }
        
        activePalettes[container] = { refreshPalette(null) }
        refreshPalette(currentColor)

        btnSelect?.setOnClickListener {
            var latestSelectedColor = data.props[propKey] ?: defaultColor
            ColorPickerDialog(
                            context,
                            latestSelectedColor,
                            true,
                            { selectedColor ->
                                latestSelectedColor = selectedColor
                                onUpdate(propKey, selectedColor)
                                try {
                                    (btnSelect as? android.widget.ImageView)?.imageTintList = ColorStateList.valueOf(Color.parseColor(selectedColor))
                                } catch (e: Exception) {}
                            },
                            {
                                ColorHistoryManager.save(context, latestSelectedColor)
                                refreshPalette(latestSelectedColor)
                                notifyHistoryChanged()
                            }
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
        val adapter = ArrayAdapter(context, R.layout.list_item_dropdown, options)
        autoComplete.setAdapter(adapter)

        autoComplete.setOnItemClickListener { _, _, position, _ ->
            val selectedLabel = options[position]
            val selectedValue = valueMap?.get(selectedLabel) ?: selectedLabel
            onUpdate(propKey, selectedValue)
        }
    }

    /** Binds a fixed-value dropdown without deriving JSON values from translated labels. */
    fun bindLocalizedDropdown(
            panelView: View,
            autoCompleteId: Int,
            propKey: String,
            data: ComponentData,
            onUpdate: (String, String) -> Unit,
            options: List<PropertyOption>,
            defaultValue: String = options.firstOrNull()?.value.orEmpty()
    ) {
        if (options.isEmpty()) return
        val autoComplete = panelView.findViewById<AutoCompleteTextView>(autoCompleteId) ?: return
        val labels = options.map { panelView.context.getString(it.labelResId) }
        val currentValue = data.props[propKey] ?: defaultValue
        val currentIndex = options.indexOfFirst { it.value == currentValue }.coerceAtLeast(0)

        autoComplete.setAdapter(
                ArrayAdapter(panelView.context, R.layout.list_item_dropdown, labels)
        )
        autoComplete.setText(labels[currentIndex], false)
        autoComplete.setOnItemClickListener { _, _, position, _ ->
            options.getOrNull(position)?.let { onUpdate(propKey, it.value) }
        }
    }

    /** Binds the large card-style checks shared by multiple property panels. */
    fun bindCheckCard(
            panelView: View,
            rowId: Int,
            checkId: Int,
            propKey: String,
            data: ComponentData,
            onUpdate: (String, String) -> Unit,
            defaultChecked: Boolean = false
    ) {
        val row = panelView.findViewById<View>(rowId) ?: return
        val check = panelView.findViewById<ImageView>(checkId) ?: return
        var checked = data.props[propKey]?.toBooleanStrictOrNull() ?: defaultChecked
        fun render() {
            check.visibility = if (checked) View.VISIBLE else View.INVISIBLE
            row.isSelected = checked
        }
        render()
        row.setOnClickListener {
            checked = !checked
            render()
            onUpdate(propKey, checked.toString())
        }
    }

    /** Applies one conditional visibility rule during both initial and later binding. */
    fun setVisibleWhen(value: String?, expectedValue: String, vararg views: View?) {
        val visibility = if (value == expectedValue) View.VISIBLE else View.GONE
        views.forEach { it?.visibility = visibility }
    }

    /**
     * Binds the linked-component checklist used by receiver and local-trigger components.
     * Component ids remain the only values persisted in JSON; labels and topics are presentation.
     */
    fun bindLinkedComponents(
        panelView: View,
        containerId: Int,
        data: ComponentData,
        candidates: List<ComponentData>,
        onUpdate: (String, String) -> Unit,
        includeOwner: Boolean = true,
        @StringRes emptyTextResId: Int = 0,
        itemLabel: (ComponentData) -> String = { "${it.label} (${it.topicConfig})" },
        ownerLabel: (ComponentData) -> String = itemLabel
    ) {
        val container = panelView.findViewById<LinearLayout>(containerId) ?: return
        val context = panelView.context
        val linked = data.props["linked_components"]
            .orEmpty()
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toMutableSet()
        val targets = candidates.filter { it.id != data.id }

        container.removeAllViews()
        if (includeOwner) {
            container.addView((LayoutInflater.from(context)
                .inflate(R.layout.item_prop_linked_component, container, false) as CheckBox).apply {
                text = ownerLabel(data)
                isChecked = true
                isEnabled = false
                alpha = 0.5f
            })
        }
        targets.forEach { component ->
            container.addView((LayoutInflater.from(context)
                .inflate(R.layout.item_prop_linked_component, container, false) as CheckBox).apply {
                text = itemLabel(component)
                isChecked = component.id.toString() in linked
                setOnCheckedChangeListener { _, checked ->
                    if (checked) linked.add(component.id.toString())
                    else linked.remove(component.id.toString())
                    onUpdate("linked_components", linked.joinToString(","))
                }
            })
        }
        if (targets.isEmpty() && emptyTextResId != 0) {
            container.addView((LayoutInflater.from(context)
                .inflate(R.layout.item_prop_linked_empty, container, false) as TextView).apply {
                setText(emptyTextResId)
            })
        }
    }
    
    fun bindSwitch(
        panelView: View,
        switchId: Int,
        propKey: String,
        data: ComponentData,
        onUpdate: (String, String) -> Unit,
        defaultChecked: Boolean = false
    ) {
        val switchView = panelView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(switchId) ?: return
        val currentStr = data.props[propKey]
        val isChecked = if (currentStr != null) currentStr.toBoolean() else defaultChecked
        
        switchView.isChecked = isChecked
        switchView.setOnCheckedChangeListener { _, isCheckedNow ->
            onUpdate(propKey, isCheckedNow.toString())
        }
    }
}
