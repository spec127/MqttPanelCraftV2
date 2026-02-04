package com.example.mqttpanelcraft.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData

import com.google.android.material.bottomsheet.BottomSheetBehavior

class PropertiesSheetManager(
    private val propertyContainer: View, 
    private val onExpandRequest: () -> Unit, 
    private val onPropertyUpdated: (ComponentData) -> Unit,
    private val onClone: (Int) -> Unit,
    private val onResetTopic: (Int) -> Unit
) {

    private var isBinding = false
    private var selectedViewId: Int = View.NO_ID
    private var currentData: ComponentData? = null
    // Auto-restore logic
    private var lastComponentData: ComponentData? = null
    private var lastViewId: Int = View.NO_ID
    
    // Registry (Legacy binders removed)
    // private val binders = mapOf<String, IComponentPropertyBinder>(...)
    
    // UI Elements
    private val etPropName: EditText? = propertyContainer.findViewById(R.id.etPropName)
    private val etPropWidth: EditText? = propertyContainer.findViewById(R.id.etPropWidth)
    private val etPropHeight: EditText? = propertyContainer.findViewById(R.id.etPropHeight)
    private val btnPropLabelVisibility: ImageView? = propertyContainer.findViewById(R.id.btnPropLabelVisibility)
    
    // Color Picker UI
    // private val vPropColorPreview: TextView? = propertyContainer.findViewById(R.id.vPropColorPreview)
    // private val tvColorHex: TextView? = propertyContainer.findViewById(R.id.tvColorHex)
    
    // Composite Topic
    private val tvTopicPrefix: TextView? = propertyContainer.findViewById(R.id.tvTopicPrefix)
    private val etTopicName: EditText? = propertyContainer.findViewById(R.id.etTopicName)
    private val tvTopicSuffix: TextView? = propertyContainer.findViewById(R.id.tvTopicSuffix)
    private val btnTopicReset: View? = propertyContainer.findViewById(R.id.btnTopicReset)
    private val btnTopicCopy: View? = propertyContainer.findViewById(R.id.btnTopicCopy)

    // Generic Payload (New)
    private val tilGenericPayload: View? = propertyContainer.findViewById(R.id.tilGenericPayload)
    private val etPropGenericPayload: EditText? = propertyContainer.findViewById(R.id.etPropGenericPayload)
    
    private val btnSaveProps: Button? = propertyContainer.findViewById(R.id.btnSaveProps)
    
    // Specific Props
    private val containerSpecificProps: LinearLayout? = propertyContainer.findViewById(R.id.containerSpecificProps)
    
    // NEW: Button Specific Props - REMOVED (Handled in Definition)
    // private val containerButtonProps: View? = propertyContainer.findViewById(R.id.containerButtonProps)
    // private val etPropPayload: EditText? ...

    init {
        setupListeners()
    }

    private fun setupListeners() {
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isBinding) saveCurrentProps()
            }
        }
        
        etPropName?.addTextChangedListener(textWatcher)
        etPropWidth?.addTextChangedListener(textWatcher)
        etPropHeight?.addTextChangedListener(textWatcher)
        // etPropColor is hidden, driven by Popup
        etTopicName?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Validation: Only allow Alphanumeric + Underscore
                val input = s.toString()
                val valid = input.matches(Regex("^[a-zA-Z0-9_]*$"))
                
                if (!valid) {
                    etTopicName.error = "Invalid characters! Use (a-z, 0-9, _)"
                } else {
                    etTopicName.error = null
                }

                // Auto-shrink text
                val len = s?.length ?: 0
                val newSize = when {
                    len > 25 -> 12f
                    len > 20 -> 14f
                    len > 15 -> 16f
                    else -> 18f
                }
                etTopicName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, newSize)
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                 // Only save if valid
                 val valid = s.toString().matches(Regex("^[a-zA-Z0-9_]*$"))
                 if (!isBinding && valid) saveCurrentProps()
            }
        })
        
        // Generic Payload Binding
        etPropGenericPayload?.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isBinding && selectedViewId != View.NO_ID && currentData != null) {
                    currentData?.props?.put("payload", s.toString())
                    onPropertyUpdated(currentData!!)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        btnSaveProps?.visibility = View.GONE
        
        propertyContainer.findViewById<View>(R.id.btnClone)?.setOnClickListener {
            if (selectedViewId != View.NO_ID) onClone(selectedViewId)
        }
        
        btnTopicReset?.setOnClickListener {
            if (selectedViewId != View.NO_ID) onResetTopic(selectedViewId)
        }
        
        btnTopicCopy?.setOnClickListener {
             val full = getFullTopic()
             val clipboard = propertyContainer.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
             val clip = ClipData.newPlainText("Topic", full)
             clipboard.setPrimaryClip(clip)
             Toast.makeText(propertyContainer.context, "Topic Copied", Toast.LENGTH_SHORT).show()
        }
        
        btnTopicCopy?.setOnClickListener {
             val full = getFullTopic()
             val clipboard = propertyContainer.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
             val clip = ClipData.newPlainText("Topic", full)
             clipboard.setPrimaryClip(clip)
             Toast.makeText(propertyContainer.context, "Topic Copied", Toast.LENGTH_SHORT).show()
        }
        
        // vPropColorPreview Listener - REMOVED
        
        // Button Props Listeners - REMOVED
        // Button Props Listeners - REMOVED
        btnPropLabelVisibility?.setOnClickListener {
            if (selectedViewId != View.NO_ID && currentData != null) {
                // Logic: Default is VISIBLE (null or "true"). Hidden is "false".
                val currentShow = currentData?.props?.get("showLabel")
                val isCurrentlyHidden = currentShow == "false"
                
                // Toggle
                val checkNewHidden = !isCurrentlyHidden
                
                if (checkNewHidden) {
                    // Hide it
                    currentData?.props?.put("showLabel", "false")
                    btnPropLabelVisibility.setImageResource(R.drawable.ic_visibility_off)
                } else {
                    // Show it (Remove prop to revert to default)
                    currentData?.props?.remove("showLabel")
                    btnPropLabelVisibility.setImageResource(R.drawable.ic_visibility_on)
                }
                
                onPropertyUpdated(currentData!!)
            }
        }
    }
    
    private fun getFullTopic(): String {
         val prefix = tvTopicPrefix?.text.toString()
         val name = etTopicName?.text.toString()
         val suffix = tvTopicSuffix?.text.toString()
         return "$prefix$name$suffix"
    }

    // ... (Existing init) ...
    // Removed legacy Color & Button bindings
    
    // ...

    fun updateDimensions(wPx: Int, hPx: Int) {
        if (isBinding) return
        isBinding = true
        val density = propertyContainer.resources.displayMetrics.density
        val wDp = kotlin.math.round(wPx / density).toInt()
        val hDp = kotlin.math.round(hPx / density).toInt()
        
        etPropWidth?.setText(wDp.toString())
        etPropHeight?.setText(hDp.toString())
        isBinding = false
    }

    private fun saveCurrentProps() {
        if (selectedViewId != View.NO_ID && currentData != null) {
            try {
                val density = propertyContainer.resources.displayMetrics.density
                
                val wDp = etPropWidth?.text.toString().toFloatOrNull() ?: 100f
                val hDp = etPropHeight?.text.toString().toFloatOrNull() ?: 100f
                
                val wPx = (wDp * density).toInt()
                val hPx = (hDp * density).toInt()
                
                val name = etPropName?.text.toString()
                val topicConfig = getFullTopic()
                
                // Construct updated data
                val updated = currentData!!.copy(
                    label = name,
                    width = wPx,
                    height = hPx,
                    topicConfig = topicConfig
                )
                // Color & Button Props are now handled via callbacks from Definitions
                
                onPropertyUpdated(updated)
            } catch (e: Exception) {
            }
        }
    }
    
    fun clear() {
        isBinding = true
        selectedViewId = View.NO_ID
        currentData = null
        etPropName?.setText("")
        etPropWidth?.setText("")
        etPropHeight?.setText("")
        etTopicName?.setText("")
        tvTopicPrefix?.text = ""
        tvTopicSuffix?.text = ""
        etPropGenericPayload?.setText("")
        tilGenericPayload?.visibility = View.GONE
        containerSpecificProps?.removeAllViews()
        isBinding = false
    }

    fun showProperties(view: View, data: ComponentData, autoExpand: Boolean = true) {
        selectedViewId = view.id
        currentData = data
        lastViewId = view.id
        lastComponentData = data
        isBinding = true
        
        try {
            // Specific Props Architecture
            containerSpecificProps?.removeAllViews()
            
            // PRIORITY 1: Definition Architecture
            val def = com.example.mqttpanelcraft.ui.components.ComponentDefinitionRegistry.get(data.type)
            if (def != null && def.propertiesLayoutId != 0) {
                 val inflater = LayoutInflater.from(propertyContainer.context)
                 val root = inflater.inflate(def.propertiesLayoutId, containerSpecificProps, true)
                 def.bindPropertiesPanel(root, data) { key: String, value: String ->
                      // Immediate Update from Definition
                      if (currentData != null) {
                          if (value.isEmpty()) currentData?.props?.remove(key)
                          else currentData?.props?.put(key, value)
                          onPropertyUpdated(currentData!!)
                      }
                 }
            } 
            
            // Generic Payload Logic
            if (def != null && def.group == "CONTROL") {
                tilGenericPayload?.visibility = View.VISIBLE
                etPropGenericPayload?.setText(data.props["payload"] ?: "")
            } else {
                tilGenericPayload?.visibility = View.GONE
            } 

            etPropName?.setText(data.label)
            
            // Initial Visibility Icon State
            val isLabelHidden = data.props["showLabel"] == "false"
            if (isLabelHidden) {
                btnPropLabelVisibility?.setImageResource(R.drawable.ic_visibility_off)
            } else {
                btnPropLabelVisibility?.setImageResource(R.drawable.ic_visibility_on)
            }
            
            // Dimensions
            val density = propertyContainer.resources.displayMetrics.density
            val wDp = kotlin.math.round(data.width / density).toInt()
            val hDp = kotlin.math.round(data.height / density).toInt()
            etPropWidth?.setText(wDp.toString())
            etPropHeight?.setText(hDp.toString())
            
            // Topic Parsing
            val topicConfig = data.topicConfig
            val parts = topicConfig.split("/")
            
            if (parts.size >= 2) {
                 // Reconstruct prefix (everything before the last part)
                 val prefixParts = parts.dropLast(1)
                 val nameStr = parts.last()
                 
                 var prefixStr = prefixParts.joinToString("/") + "/"
                 
                 // "Topic Prefix Fixed Size... Max 15 chars... Priority Hide ID"
                 // ID is usually the random string at index 1 (p27/RANDOM/name)
                 if (prefixStr.length > 15) {
                     if (parts.size >= 3) {
                         // Try hiding the middle ID part
                         // Ex: p27/czr0r8jw0z/ -> p27/.../
                         val first = parts[0]
                         prefixStr = "$first/.../"
                     }
                     // If still too long, hard truncate
                     if (prefixStr.length > 15) {
                         prefixStr = prefixStr.take(12) + "..."
                     }
                 }
                 
                 tvTopicPrefix?.text = prefixStr
                 etTopicName?.setText(nameStr)
                 tvTopicSuffix?.text = "" 
            } else {
                tvTopicPrefix?.text = ""
                etTopicName?.setText(topicConfig)
                tvTopicSuffix?.text = ""
            }
            
            // Payload Preset Spinner
            val spPresets = propertyContainer.findViewById<android.widget.Spinner>(R.id.spPayloadPresets)
            if (spPresets != null) {
                val items = listOf("1", "0", "TRUE", "FALSE", "ON", "OFF")
                val adapter = android.widget.ArrayAdapter(propertyContainer.context, android.R.layout.simple_spinner_dropdown_item, items)
                spPresets.adapter = adapter
                
                spPresets.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (isBinding) return
                        val sel = items[position]
                        etPropGenericPayload?.setText(sel)
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isBinding = false
        }
        
        propertyContainer.visibility = View.VISIBLE
        propertyContainer.findViewById<View>(R.id.svPropertiesContent)?.visibility = View.VISIBLE
        
        if (autoExpand) {
            forceExpandBottomSheet()
            onExpandRequest()
        }
    }
    
    // ... Helper functions (hide, showTitleOnly, restoreLastState) keep existing ...

    fun hide() {
        propertyContainer.visibility = View.GONE
        findBottomSheetBehavior()?.state = BottomSheetBehavior.STATE_HIDDEN
        currentData = null
    }

    fun showTitleOnly() {
        propertyContainer.visibility = View.VISIBLE
        val content = propertyContainer.findViewById<View>(R.id.svPropertiesContent)
        content?.visibility = View.GONE
    }
    
    fun getLastSelectedId(): Int? = if (lastComponentData != null && lastViewId != View.NO_ID) lastViewId else null
    
    fun restoreLastState(): Boolean {
        if (lastComponentData != null && lastViewId != View.NO_ID) {
            val view = propertyContainer.rootView.findViewById<View>(lastViewId)
            if (view != null) {
                showProperties(view, lastComponentData!!)
                return true
            }
        }
        return false
    }
    
    fun hasLastState(): Boolean = lastComponentData != null
    
    private fun forceExpandBottomSheet() {
        val behavior = findBottomSheetBehavior() ?: return
        behavior.isHideable = false 
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
    
    private fun findBottomSheetBehavior(): BottomSheetBehavior<View>? {
        var currentParent = propertyContainer.parent
        while (currentParent is View) {
            try {
                val params = currentParent.layoutParams
                if (params is androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
                     if (params.behavior is BottomSheetBehavior) {
                         return params.behavior as BottomSheetBehavior<View>
                     }
                }
            } catch(e: Exception) {}
            currentParent = currentParent.parent
        }
        return null
    }
}