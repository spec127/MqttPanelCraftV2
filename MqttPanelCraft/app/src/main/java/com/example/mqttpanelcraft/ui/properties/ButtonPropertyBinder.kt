package com.example.mqttpanelcraft.ui.properties

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData

class ButtonPropertyBinder : IComponentPropertyBinder {
    override fun getLayoutId(): Int = R.layout.layout_props_button

    override fun bind(rootView: View, data: ComponentData, onUpdate: (key: String, value: String) -> Unit) {
        val etPayload = rootView.findViewById<EditText>(R.id.etPayload)
        
        // Initial Value
        val currentPayload = data.props["payload"] ?: "1" // Default payload
        if (etPayload.text.toString() != currentPayload) {
            etPayload.setText(currentPayload)
        }

        // Listener
        etPayload.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onUpdate("payload", s.toString())
            }
        })
    }
}
