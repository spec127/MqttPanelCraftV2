package com.example.mqttpanelcraft.ui

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.ui.LockableBottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.textfield.TextInputEditText

class PropertiesSheetManager(
    private val propertyContainer: View, // Expecting the scrollview or container holding inputs
    private val onExpandRequest: () -> Unit, // Callback to expand bottom sheet
    private val onPropertyUpdated: (viewId: Int, name: String, w: Int, h: Int, color: String, topicConfig: String) -> Unit
) {

    private var selectedViewId: Int = View.NO_ID
    
    // UI Elements - find directly from container
    private val etPropName: EditText? = propertyContainer.findViewById(R.id.etPropName)
    private val etPropWidth: EditText? = propertyContainer.findViewById(R.id.etPropWidth)
    private val etPropHeight: EditText? = propertyContainer.findViewById(R.id.etPropHeight)
    private val etPropColor: EditText? = propertyContainer.findViewById(R.id.etPropColor)
    private val etPropTopicConfig: TextInputEditText? = propertyContainer.findViewById(R.id.etPropTopicConfig)
    private val btnSaveProps: Button? = propertyContainer.findViewById(R.id.btnSaveProps)
    
    init {
        setupListeners()
    }

    private fun setupListeners() {
        val autoSaveWatcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                saveCurrentProps()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etPropName?.addTextChangedListener(autoSaveWatcher)
        etPropWidth?.addTextChangedListener(autoSaveWatcher)
        etPropHeight?.addTextChangedListener(autoSaveWatcher)
        etPropTopicConfig?.addTextChangedListener(autoSaveWatcher)

        btnSaveProps?.setOnClickListener {
             saveCurrentProps()
             Toast.makeText(propertyContainer.context, "Properties Apply", Toast.LENGTH_SHORT).show()
        }
        
        etPropColor?.isFocusable = false
        etPropColor?.setOnClickListener { showColorPicker() }
    }

    private fun saveCurrentProps() {
        if (selectedViewId != View.NO_ID) {
            try {
                val w = etPropWidth?.text.toString().toIntOrNull() ?: 100
                val h = etPropHeight?.text.toString().toIntOrNull() ?: 100
                val name = etPropName?.text.toString() ?: ""
                val color = etPropColor?.text.toString() ?: ""
                val topicConfig = etPropTopicConfig?.text.toString() ?: ""
                
                onPropertyUpdated(selectedViewId, name, w, h, color, topicConfig)
            } catch (e: Exception) {
                // Ignore parsing errors during typing
            }
        }
    }

    fun showProperties(view: View, label: String, topicConfig: String) {
        android.util.Log.d("PropsManager", "showProperties called for ${view.id}, Label: $label")
        selectedViewId = view.id
        
        try {
            if (etPropName == null) android.util.Log.e("PropsManager", "etPropName is NULL!")
            
            etPropName?.setText(label)
            
            // Fix: Convert Pixels to DP for display
            val density = propertyContainer.resources.displayMetrics.density
            val wDp = (view.width / density).toInt()
            val hDp = (view.height / density).toInt()
            
            etPropWidth?.setText(wDp.toString())
            etPropHeight?.setText(hDp.toString())
            
            etPropTopicConfig?.setText(topicConfig)
        } catch (e: Exception) {
            android.util.Log.e("PropsManager", "Error setting properties: ${e.message}")
            e.printStackTrace()
        }
        
        // Ensure container is visible
        propertyContainer.visibility = View.VISIBLE
        
        // Find behavior and EXPAND
        var currentParent = propertyContainer.parent
        while (currentParent is View) {
            try {
                val params = currentParent.layoutParams
                if (params is androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
                    if (params.behavior is BottomSheetBehavior) {
                        val behavior = params.behavior as BottomSheetBehavior
                        behavior.isHideable = true // Allow user to drag it down to hide
                        behavior.skipCollapsed = true // Skip collapsed state, go directly to Hidden
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                        break
                    }
                }
            } catch(e: Exception) {}
            currentParent = currentParent.parent
        }
        
        // Request expansion from callback as backup
        onExpandRequest()
    }

    fun hide() {
        propertyContainer.visibility = View.GONE
        // Find behavior and HIDE
        var currentParent = propertyContainer.parent
        while (currentParent is View) {
            try {
                val params = currentParent.layoutParams
                if (params is androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
                    if (params.behavior is BottomSheetBehavior) {
                        val behavior = params.behavior as BottomSheetBehavior
                        behavior.state = BottomSheetBehavior.STATE_HIDDEN
                        break
                    }
                }
            } catch(e: Exception) {}
            currentParent = currentParent.parent
        }
    }


    private fun showColorPicker() {
        // Simple Color Picker Logic
        val context = propertyContainer.context
        val width = 600
        val height = 400
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val gradient = LinearGradient(0f, 0f, width.toFloat(), 0f,
            intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
            null, Shader.TileMode.CLAMP)
        val paint = android.graphics.Paint()
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val darkGradient = LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            null, Shader.TileMode.CLAMP)
        val darkPaint = android.graphics.Paint()
        darkPaint.shader = darkGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), darkPaint)

        val imageView = ImageView(context)
        imageView.setImageBitmap(bitmap)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Select Color")
            .setView(imageView)
            .setPositiveButton("Close", null)
            .create()

        imageView.setOnTouchListener { _, event ->
             if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                 val x = event.x.toInt().coerceIn(0, width - 1)
                 val y = event.y.toInt().coerceIn(0, height - 1)
                 val pixel = bitmap.getPixel(x, y)
                 val hex = String.format("#%06X", (0xFFFFFF and pixel))
                 etPropColor?.setText(hex)
                 dialog.setTitle(hex)
             }
             true
        }
        dialog.show()
    }
}
