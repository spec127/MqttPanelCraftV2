package com.example.mqttpanelcraft.ui.components.definitions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.ColorUtils
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.data.ColorHistoryManager
import com.example.mqttpanelcraft.model.ComponentData
import com.example.mqttpanelcraft.ui.ColorPickerDialog
import com.example.mqttpanelcraft.ui.components.ComponentContainer
import com.example.mqttpanelcraft.ui.components.IComponentDefinition
import com.google.android.material.button.MaterialButtonToggleGroup
import org.json.JSONArray
import org.json.JSONObject

/**
 * Switch Component Definition
 * Refactored for Robustness and Maintainability.
 * Strategy: "Re-inflate on State Change" to guarantees 100% clean state during Style/Mode switches.
 */
object SwitchDefinition : IComponentDefinition {

    // --- Constants ---
    object Mode {
        const val TWO_WAY = "switch_2"
        const val THREE_WAY = "switch_3"
        const val MULTI = "multi"
    }

    object Style {
        const val CLASSIC = "classic"
        const val LEVER = "lever"
        const val TRACK = "track"
        // Multi Styles
        const val ROUNDED = "rounded"
        const val CIRCLE = "circle"
    }

    // --- Identity ---
    override val type = "SWITCH"
    override val defaultSize = android.util.Size(200, 80)
    override val labelPrefix = "switch"
    override val iconResId = R.drawable.ic_grid_view
    override val group = "CONTROL"

    // --- Factory ---
    override fun createView(context: Context, isEditMode: Boolean): View {
        val container = ComponentContainer.createEndpoint(context, type, isEditMode)
        // Root content holder - always exists, manages re-inflation
        val contentRoot = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(contentRoot, 0)
        return container
    }

    // --- Update Logic (The Core Refactor) ---
    override fun onUpdateView(view: View, data: ComponentData) {
        // extract our content root
        val root = (view as? ViewGroup)?.getChildAt(0) as? FrameLayout ?: return

        // 1. Resolve State
        val mode = resolveMode(data)
        
        // Smart Default: 2-Way/3-Way default to LEVER (Neon), others default to CLASSIC
        val defaultStyle = if (mode == Mode.TWO_WAY || mode == Mode.THREE_WAY) Style.LEVER else Style.CLASSIC
        val style = data.props["style"] ?: defaultStyle
        
        val orient = data.props["orientation"] ?: "horz"
        val segmentsJson = if (mode == Mode.MULTI) data.props["segments"] ?: "" else "" // Segments affect Multi layout structure
        
        // 2. Calculate Signature (If this changes, we rebuild)
        val signature = "mode:$mode|style:$style|orient:$orient|segs:${parseSegments(segmentsJson).size}"

        // 3. Re-inflate if needed
        if (root.tag != signature) {
            // Persist State logic: Try to recover "current state" from existing view before wiping
            var savedState: Any? = null
            val oldView = root.getChildAt(0)
            if (oldView != null) savedState = oldView.tag
            
            root.removeAllViews()
            val inflater = LayoutInflater.from(root.context)

            if (mode == Mode.MULTI) {
                 val multiRoot = inflater.inflate(R.layout.layout_component_switch_multisegment, root, false) as LinearLayout
                 // multiRoot.tag = "MODE_MULTI" // No longer needed for ID
                 root.addView(multiRoot)
            } else {
                inflater.inflate(R.layout.layout_component_switch_tristate, root, true)
                val triRoot = root.getChildAt(0)
                // triRoot.tag = "MODE_TRISTATE" // No longer needed for ID
                
                // Restore state to new view tag if valid
                if (savedState is Int) {
                    val steps = if (mode == Mode.TWO_WAY) 2 else 3
                    val maxState = steps - 1
                    if (savedState <= maxState) triRoot.tag = savedState
                }
            }
            root.tag = signature
        }

        // 4. Apply Visuals
        val color = resolveColor(data)
        
        val child = root.getChildAt(0) ?: return
        
        if (mode == Mode.MULTI) {
             val multiContainer = child as? LinearLayout
             multiContainer?.let { 
                 updateMultiVisuals(it, data, color, orient) 
             }
        } else {
             val triContainer = child as? ConstraintLayout
             triContainer?.let { 
                 updateTriStateVisuals(it, data, color, mode, style)
             }
        }
    }

    // --- Private Visual Helpers ---

    private fun updateTriStateVisuals(
        container: ConstraintLayout,
        data: ComponentData,
        color: Int,
        mode: String,
        style: String
    ) {
        val density = container.resources.displayMetrics.density
        
        // --- Colors ---
        val slate100 = Color.parseColor("#F1F5F9")
        val slate200 = Color.parseColor("#E2E8F0")
        val slate300 = Color.parseColor("#CBD5E1")
        val slate400 = Color.parseColor("#94A3B8") // Darker border
        
        // --- Interaction Fix ---
        val zoneIds = listOf(R.id.zoneLeft, R.id.zoneCenter, R.id.zoneRight)
        zoneIds.forEach { zId ->
            container.findViewById<View>(zId).apply {
                elevation = 50f
                isClickable = true; isFocusable = true
            }
        }

        // --- Visual Elements ---
        val track = container.findViewById<View>(R.id.viewTrack)
        val cvThumb = container.findViewById<CardView>(R.id.cvThumb)
        track.isClickable = false
        cvThumb.isClickable = false
        cvThumb.isFocusable = false

        // Hide Legacy
        container.findViewById<View>(R.id.vLeverThumb)?.visibility = View.GONE
        container.findViewById<TextView>(R.id.tvLabelTop)?.visibility = View.GONE
        container.findViewById<TextView>(R.id.tvLabelBottom)?.visibility = View.GONE

        // 1. ConstraintSet
        val set = ConstraintSet()
        set.clone(container.context, R.layout.layout_component_switch_tristate)

        // Reset Zone Constraints
        zoneIds.forEach { zId ->
            set.clear(zId, ConstraintSet.TOP)
            set.clear(zId, ConstraintSet.BOTTOM)
            set.connect(zId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            set.connect(zId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }

        val cvThumbId = R.id.cvThumb
        val trackId = R.id.viewTrack
        
        // Clear previous thumb constraints to avoid conflicts
        set.clear(cvThumbId, ConstraintSet.TOP)
        set.clear(cvThumbId, ConstraintSet.BOTTOM)
        
        val trackBg = GradientDrawable()
        trackBg.shape = GradientDrawable.RECTANGLE

        // Inner Dot Config
        val innerDot = cvThumb.getChildAt(0)
        val dotParams = innerDot.layoutParams as FrameLayout.LayoutParams
        dotParams.width = (8 * density).toInt()
        dotParams.height = (8 * density).toInt() 
        dotParams.gravity = Gravity.CENTER
        dotParams.setMargins(0,0,0,0)

        when (style) {
            Style.LEVER -> { 
                 // Track: Large Rect Container
                 val h = (40 * density).toInt() // Taller for better border visibility
                 set.constrainHeight(trackId, h)
                 set.constrainWidth(trackId, 0)
                 set.connect(trackId, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT)
                 set.connect(trackId, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT)
                 
                 // Thumb: INSIDE Track (Top/Bottom to Track)
                 set.constrainWidth(cvThumbId, 0)
                 set.constrainHeight(cvThumbId, 0) // Match Constraint
                 set.setDimensionRatio(cvThumbId, "1.4:1") // Wide Block
                 
                 set.connect(cvThumbId, ConstraintSet.TOP, trackId, ConstraintSet.TOP)
                 set.connect(cvThumbId, ConstraintSet.BOTTOM, trackId, ConstraintSet.BOTTOM)
                 val m = (4 * density).toInt() // Inset Margin
                 set.setMargin(cvThumbId, ConstraintSet.TOP, m)
                 set.setMargin(cvThumbId, ConstraintSet.BOTTOM, m)
                 
                 cvThumb.radius = (6 * density)
                 trackBg.cornerRadius = (10 * density)
                 
                 // NEON Strip: Widen to 6dp
                 dotParams.width = (6 * density).toInt()
                 dotParams.height = FrameLayout.LayoutParams.MATCH_PARENT
                 dotParams.setMargins(0, (6 * density).toInt(), 0, (6 * density).toInt())
                 innerDot.visibility = View.VISIBLE
            }
            Style.TRACK -> { 
                 // Track: Slim Line
                 val h = (6 * density).toInt()
                 set.constrainHeight(trackId, h)
                 set.constrainWidth(trackId, 0)
                 set.connect(trackId, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT)
                 
                 // Thumb: Larger than Track (Centered)
                 val s = (26 * density).toInt()
                 set.constrainWidth(cvThumbId, s)
                 set.constrainHeight(cvThumbId, s)
                 set.setDimensionRatio(cvThumbId, null) 
                 
                 // Center Vertically on Track
                 set.connect(cvThumbId, ConstraintSet.TOP, trackId, ConstraintSet.TOP)
                 set.connect(cvThumbId, ConstraintSet.BOTTOM, trackId, ConstraintSet.BOTTOM)
                 
                 cvThumb.radius = (13 * density)
                 trackBg.cornerRadius = (3 * density)
                 innerDot.visibility = View.VISIBLE
            }
            else -> { // Classic
                // Track: Pill
                val h = (32 * density).toInt()
                set.constrainHeight(trackId, h)
                set.constrainWidth(trackId, 0)
                
                // Thumb: INSIDE Track
                set.constrainWidth(cvThumbId, 0)
                set.constrainHeight(cvThumbId, 0)
                set.setDimensionRatio(cvThumbId, "1:1")
                
                set.connect(cvThumbId, ConstraintSet.TOP, trackId, ConstraintSet.TOP)
                set.connect(cvThumbId, ConstraintSet.BOTTOM, trackId, ConstraintSet.BOTTOM)
                val m = (3 * density).toInt() // Classic Inset
                set.setMargin(cvThumbId, ConstraintSet.TOP, m)
                set.setMargin(cvThumbId, ConstraintSet.BOTTOM, m)
                
                cvThumb.radius = (100 * density)
                trackBg.cornerRadius = (100 * density)
                innerDot.visibility = View.GONE
            }
        }
        innerDot.layoutParams = dotParams

        val steps = if (mode == Mode.TWO_WAY) 2 else 3
        var state = (container.tag as? Int) ?: (if (steps == 2) 0 else 1)
        if (mode == Mode.TWO_WAY && state == 1) state = 0 
        container.tag = state 

        // Spacing: 5% Edge (Bias 0.05 / 0.95 relative to Track)
        set.connect(cvThumbId, ConstraintSet.LEFT, trackId, ConstraintSet.LEFT)
        set.connect(cvThumbId, ConstraintSet.RIGHT, trackId, ConstraintSet.RIGHT)
        val bias = when (state) { 0 -> 0.05f; 2 -> 0.95f; else -> 0.5f }
        set.setHorizontalBias(cvThumbId, bias)
        
        if (container.isAttachedToWindow) {
            val auto = AutoTransition()
            auto.duration = 200
            TransitionManager.beginDelayedTransition(container, auto)
        }
        set.applyTo(container)
        container.requestLayout()

        // 2. Styling
        cvThumb.visibility = View.VISIBLE
        cvThumb.setCardBackgroundColor(Color.WHITE)
        cvThumb.cardElevation = if (style == Style.TRACK) 6f * density else 4f * density
        
        val dotBg = (innerDot.background as? GradientDrawable) ?: GradientDrawable().also { innerDot.background = it }
        
        if (state == 0) {
            // OFF
            when(style) {
                 Style.TRACK -> {
                     trackBg.setColor(slate200)
                     trackBg.setStroke(0, 0)
                     dotBg.shape = GradientDrawable.OVAL
                     dotBg.setColor(Color.WHITE)
                     dotBg.setStroke((2 * density).toInt(), slate300)
                 }
                 Style.LEVER -> {
                     trackBg.setColor(slate100)
                     trackBg.setStroke((2 * density).toInt(), slate400) 
                     
                     dotBg.shape = GradientDrawable.RECTANGLE
                     dotBg.cornerRadius = (2 * density)
                     dotBg.setColor(slate300) 
                     dotBg.setStroke(0, 0)
                 }
                 else -> { // Classic
                     trackBg.setColor(slate200)
                     trackBg.setStroke((2 * density).toInt(), slate400)
                 }
            }
        } else {
            // ON
            when(style) {
                 Style.TRACK -> {
                     trackBg.setColor(ColorUtils.setAlphaComponent(color, 120))
                     trackBg.setStroke(0, 0)
                     dotBg.shape = GradientDrawable.OVAL
                     dotBg.setColor(color)
                     dotBg.setStroke(0, 0)
                 }
                 Style.LEVER -> {
                     trackBg.setColor(slate100) 
                     trackBg.setStroke((2 * density).toInt(), slate400) // Keep border visible!
                     
                     // Neon ON
                     dotBg.shape = GradientDrawable.RECTANGLE
                     dotBg.cornerRadius = (2 * density)
                     dotBg.setColor(color) 
                 }
                 else -> { // Classic
                     trackBg.setColor(color)
                     trackBg.setStroke((2 * density).toInt(), color)
                 }
            }
        }
        track.background = trackBg
        
        container.findViewById<View>(R.id.vDivider1)?.visibility = View.GONE
        container.findViewById<View>(R.id.vDivider2)?.visibility = View.GONE
    }

    private fun updateMultiVisuals(
        container: LinearLayout,
        data: ComponentData,
        color: Int,
        orientation: String
    ) {
        val density = container.resources.displayMetrics.density
        val isVertical = (orientation == "vert")
        container.orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        container.layoutTransition = android.animation.LayoutTransition()
        
        // Container Styling
        val bg = GradientDrawable()
        val style = data.props["style"] ?: Style.ROUNDED
        val isCircle = (style == Style.CIRCLE) // Check string value
        
        bg.setColor(Color.parseColor("#F8FAFC")) // Slate-50
        // Thicker Border: 2.0dp Slate-400
        bg.setStroke((2 * density).toInt(), Color.parseColor("#94A3B8")) 
        
        if (isCircle) {
             bg.shape = GradientDrawable.RECTANGLE
             bg.cornerRadius = (999 * density) // Capsule Shape
        } else {
             bg.shape = GradientDrawable.RECTANGLE
             bg.cornerRadius = (16 * density) // Rounded Rect Slot
        }
        
        container.background = bg
        val pad = (4 * density).toInt()
        container.setPadding(pad, pad, pad, pad)

        val render = {
            if (container.width > 0 && container.height > 0) {
                container.removeAllViews() 
                val segments = parseSegments(data.props["segments"] ?: "")
                if (segments.isEmpty()) segments.add(Segment("Err", "0"))
                
                val w = container.width - container.paddingLeft - container.paddingRight
                val h = container.height - container.paddingTop - container.paddingBottom
                val count = segments.size.coerceAtLeast(1)
                
                val maxItemDim = if (isVertical) h.toFloat() / count else w.toFloat() / count
                val crossDim = if (isVertical) w.toFloat() else h.toFloat()
                val itemSize = kotlin.math.min(maxItemDim, crossDim).toInt()

                if (itemSize > 0) {
                     val mainDim = if (isVertical) h else w
                     val totalItemSpace = itemSize * count
                     val remainingSpace = mainDim - totalItemSpace
                     val gap = if (count > 1) remainingSpace / (count + 1) else remainingSpace / 2
                     val gapSize = (4 * density).toInt()

                     segments.forEach { seg ->
                         val btn = TextView(container.context)
                         btn.text = seg.label
                         btn.tag = seg
                         btn.gravity = Gravity.CENTER
                         btn.textSize = 12f
                         btn.typeface = Typeface.DEFAULT_BOLD
                         btn.isAllCaps = true
                         
                         val lp = LinearLayout.LayoutParams(0, 0)
                         if (isCircle) {
                             lp.width = itemSize
                             lp.height = itemSize
                         } else {
                             lp.width = if (isVertical) LinearLayout.LayoutParams.MATCH_PARENT else 0
                             lp.height = if (isVertical) 0 else LinearLayout.LayoutParams.MATCH_PARENT
                             lp.weight = 1f
                         }
                         
                         if (gap > 0 && segments.indexOf(seg) > 0) {
                             if (isVertical) lp.topMargin = gapSize else lp.leftMargin = gapSize
                         }
                         
                         btn.layoutParams = lp
                         container.addView(btn)
                     }
                     updateMultiSegmentButtons(container, null, color, isCircle, itemSize)
                }
            }
        }
        
        if (container.width > 0 && container.height > 0 && !container.isLayoutRequested) {
            render()
        } else {
            container.post { if (container.isAttachedToWindow) render() }
        }
    }
    
    // Separate logic to update button styles in Multi without rebuilding
    private fun updateMultiSegmentButtons(container: LinearLayout, selectedValue: String?, color: Int, isCircle: Boolean, size: Int) {
         val density = container.resources.displayMetrics.density
         for (i in 0 until container.childCount) {
             val child = container.getChildAt(i) as? TextView ?: continue
             val seg = child.tag as? Segment
             
             val isSelected = (selectedValue != null && seg?.value == selectedValue) || (selectedValue == null && i == 0)
             
             val radius = if (isCircle) size / 2f else (12 * density)
             val bg = GradientDrawable()
             bg.shape = if (isCircle) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
             bg.cornerRadius = radius
             
             if (isSelected) {
                 bg.setColor(color) // Primary
                 bg.setStroke(0, 0)
                 child.setTextColor(Color.WHITE)
                 child.elevation = (4 * density)
                 child.translationZ = (2 * density)
                 child.animate().scaleX(0.95f).scaleY(0.95f).setDuration(120).start()
             } else {
                 if (isCircle) {
                      bg.setColor(Color.WHITE)
                      bg.setStroke((1 * density).toInt(), Color.parseColor("#E2E8F0"))
                      child.setTextColor(Color.parseColor("#94A3B8"))
                      child.elevation = (1 * density)
                  } else {
                      bg.setColor(Color.TRANSPARENT)
                      bg.setStroke(0, 0)
                      child.setTextColor(Color.parseColor("#64748B")) 
                      child.elevation = 0f
                  }
                 child.translationZ = 0f
                 child.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
             }
             child.background = bg
         }
    }

    // --- Properties ---
    override val propertiesLayoutId = R.layout.layout_prop_switch

    override fun bindPropertiesPanel(panelView: View, data: ComponentData, onUpdate: (String, String) -> Unit) {
         val context = panelView.context
         
         val toggleMode = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleMode)
         val containerTri = panelView.findViewById<View>(R.id.containerTriStateConfig)
         val containerMulti = panelView.findViewById<View>(R.id.containerMultiConfig)
         
         val mode = resolveMode(data)
         when(mode) {
             Mode.TWO_WAY -> toggleMode.check(R.id.btnMode2Way)
             Mode.THREE_WAY -> toggleMode.check(R.id.btnMode3Way)
             Mode.MULTI -> toggleMode.check(R.id.btnModeMulti)
         }
         
         // Helper to update visibility of sub-sections
         fun updateSubPanels(m: String) {
             containerTri.visibility = if (m.startsWith("switch")) View.VISIBLE else View.GONE
             containerMulti.visibility = if (m == Mode.MULTI) View.VISIBLE else View.GONE
             
             if (m.startsWith("switch")) {
                 val tilCenter = panelView.findViewById<View>(R.id.tilPayloadCenter)
                 val isThreeWay = (m == Mode.THREE_WAY)
                 tilCenter?.visibility = if (isThreeWay) View.VISIBLE else View.GONE
                 // Assuming spacePayloadCenter is there too, or managed by layout
                 panelView.findViewById<TextView>(R.id.tvPayloadLabel)?.text = 
                     if (isThreeWay) "Messages (L / C / R)" else "Messages (L / R)"
             }
         }
         updateSubPanels(mode)

         // Style Dropdown
         val spStyle = panelView.findViewById<AutoCompleteTextView>(R.id.spPropStyle)
         val adapterS = ArrayAdapter(context, R.layout.list_item_dropdown, mutableListOf<String>())
         spStyle.setAdapter(adapterS)
         
         fun refreshStyles(m: String) {
             val isMulti = (m == Mode.MULTI)
             val options = if (isMulti) 
                 listOf("Rounded (圓角)", "Circle (圓形)") 
             else 
                 listOf("Classic (經典)", "Track (滑軌)", "Lever (撥桿)")
             
             adapterS.clear()
             adapterS.addAll(options)
             adapterS.notifyDataSetChanged()
             
             val curKey = data.props["style"] ?: if(isMulti) Style.ROUNDED else Style.CLASSIC
             // Safety default if current style doesn't match new options
             val validKey = if(options.any { it.startsWith(curKey, true) }) curKey else if(isMulti) Style.ROUNDED else Style.CLASSIC
             
             val display = options.find { it.startsWith(validKey, ignoreCase = true) } ?: options[0]
             spStyle.setText(display, false)
         }
         refreshStyles(mode)
         
         toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
             if (isChecked) {
                 val m = when(checkedId) {
                     R.id.btnMode2Way -> Mode.TWO_WAY
                     R.id.btnMode3Way -> Mode.THREE_WAY
                     R.id.btnModeMulti -> Mode.MULTI
                     else -> Mode.THREE_WAY
                 }
                 updateSubPanels(m) // Update Visibility IMMEDIATELY
                 refreshStyles(m)    // Update Dropdown IMMEDIATELY
                 
                 onUpdate("mode", m)
                 onUpdate("style", if(m == Mode.MULTI) Style.ROUNDED else Style.CLASSIC)
             }
         }
         
         spStyle.setOnItemClickListener { _, _, position, _ ->
             val item = adapterS.getItem(position) ?: ""
             val key = item.split("(")[0].trim().lowercase()
             onUpdate("style", key)
         }
         
         // 3. Payloads (Standard)
         val payloads = listOf(
             R.id.etPayloadLeft to "payloadLeft",
             R.id.etPayloadCenter to "payloadCenter",
             R.id.etPayloadRight to "payloadRight"
         )
         payloads.forEach { (id, prop) ->
             val et = panelView.findViewById<EditText>(id)
             et.setText(data.props[prop] ?: if (prop == "payloadCenter") "1" else if (prop.endsWith("Left")) "0" else "2")
             et.setOnFocusChangeListener { _, hasFocus -> 
                 if (!hasFocus) onUpdate(prop, et.text.toString())
             }
         }
         
         // 4. Multi Logic (Segments & Orientation)
         // ... (Keep existing segment parsing/adding logic, just ensuring it updates props) ...
         val toggleOrient = panelView.findViewById<MaterialButtonToggleGroup>(R.id.toggleOrientation)
         toggleOrient.check(if (data.props["orientation"] == "vert") R.id.btnOrientVert else R.id.btnOrientHorz)
         toggleOrient.addOnButtonCheckedListener { _, checkedId, isChecked ->
             if (isChecked) {
                 val o = if (checkedId == R.id.btnOrientVert) "vert" else "horz"
                 onUpdate("orientation", o)
                 // Swap dimensions W/H for convenience
                 val w = data.width
                 val h = data.height
                 if ((o == "vert" && w > h) || (o == "horz" && h > w)) {
                     onUpdate("w", h.toString())
                     onUpdate("h", w.toString())
                 }
             }
         }
         
         setupSegmentEditor(panelView, context, data, onUpdate) // Extracted for clean code
         
         // 5. Color Selection (Unified)
         // 5. Color Selection (Unified)
         val colorViews = listOf(
             panelView.findViewById<View>(R.id.vColor1),
             panelView.findViewById<View>(R.id.vColor2),
             panelView.findViewById<View>(R.id.vColor3),
             panelView.findViewById<View>(R.id.vColor4),
             panelView.findViewById<View>(R.id.vColor5)
         )
         
         fun refreshColors() {
             val recentColors = com.example.mqttpanelcraft.data.ColorHistoryManager.load(context)
             colorViews.forEachIndexed { index, view ->
                 if (index < recentColors.size) {
                     val cHex = recentColors[index]
                     try {
                         val colorInt = android.graphics.Color.parseColor(cHex)
                         view?.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
                         view?.setOnClickListener { onUpdate("color", cHex) }
                     } catch(e: Exception) {}
                 }
             }
         }
         refreshColors()

         panelView.findViewById<View>(R.id.btnColorCustom)?.setOnClickListener { anchor ->
              val cur = data.props["color"] ?: "#a573bc"
              var tempColor = cur
              com.example.mqttpanelcraft.ui.ColorPickerDialog(
                  context = context,
                  initialColor = cur,
                  showAlpha = true,
                  onColorSelected = { 
                      tempColor = it
                      onUpdate("color", it) 
                  },
                  onDismiss = {
                      if (tempColor != cur) {
                          com.example.mqttpanelcraft.data.ColorHistoryManager.save(context, tempColor)
                          refreshColors()
                      }
                  }
              ).show(anchor)
         }
    }
    
    private fun setupSegmentEditor(panelView: View, context: Context, data: ComponentData, onUpdate: (String, String) -> Unit) {
         val llSegs = panelView.findViewById<LinearLayout>(R.id.llSegmentsContainer)
         val tvCount = panelView.findViewById<TextView>(R.id.tvSegCount)
         val segmentList = parseSegments(data.props["segments"] ?: "")
         tvCount.text = "${segmentList.size}"
         
         fun save() {
             val json = JSONArray()
             segmentList.forEach { json.put(JSONObject().put("label", it.label).put("value", it.value)) }
             onUpdate("segments", json.toString())
             tvCount.text = "${segmentList.size}"
         }
         
         fun renderRows() {
             llSegs.removeAllViews()
             segmentList.forEachIndexed { index, seg ->
                 val row = LayoutInflater.from(context).inflate(R.layout.layout_switch_segment_row, llSegs, false)
                 val etLabel = row.findViewById<EditText>(R.id.etSegLabel)
                 val etValue = row.findViewById<EditText>(R.id.etSegValue)
                 etLabel.setText(seg.label)
                 etValue.setText(seg.value)
                 
                 val watcher = object: android.text.TextWatcher {
                     override fun afterTextChanged(s: android.text.Editable?) {
                         segmentList[index] = Segment(etLabel.text.toString(), etValue.text.toString())
                         save() // Note: Auto-save on text change might be too heavy? Maybe debounce? 
                         // Original code did it. Keeping for consistency.
                     }
                     override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                     override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                 }
                 etLabel.addTextChangedListener(watcher)
                 etValue.addTextChangedListener(watcher)
                 llSegs.addView(row)
             }
         }
         renderRows()
         
         panelView.findViewById<View>(R.id.btnSegAdd)?.setOnClickListener {
             if (segmentList.size < 8) {
                 segmentList.add(Segment("N", "${segmentList.size+1}"))
                 save(); renderRows()
             }
         }
         panelView.findViewById<View>(R.id.btnSegRemove)?.setOnClickListener {
             if (segmentList.size > 2) {
                 segmentList.removeAt(segmentList.lastIndex)
                 save(); renderRows()
             }
         }
    }

    // --- Runtime Behavior ---
    override fun attachBehavior(view: View, data: ComponentData, sendMqtt: (topic: String, payload: String) -> Unit) {
        val root = (view as? ViewGroup)?.getChildAt(0) as? FrameLayout ?: return
        val mode = resolveMode(data)
        
        val child = root.getChildAt(0) ?: return
        
        if (mode == Mode.MULTI) {
            val multi = child as? LinearLayout ?: return
            
            // Re-bind listeners. 
            // If No (because of post), we can post strict `attachBehavior` logic to the queue too.
             view.post {
                 // Re-find view inside post just in case? Or use captured 'child'? Checked safe if not rebuilt.
                 // Better use root.getChildAt(0) to be robust against updates.
                 val m = (root.getChildAt(0) as? LinearLayout) ?: return@post
                 for (i in 0 until m.childCount) {
                     val btn = m.getChildAt(i)
                     val seg = btn.tag as? Segment
                     btn.setOnClickListener {
                         if (seg != null) {
                             sendMqtt(data.topicConfig, seg.value)
                             // Optimistic UI update
                             val color = resolveColor(data)
                             val style = data.props["style"] ?: Style.ROUNDED
                             updateMultiSegmentButtons(m, seg.value, color, style == Style.CIRCLE, btn.width)
                         }
                     }
                 }
             }
        } else {
             val tri = child as? ConstraintLayout ?: return
             val color = resolveColor(data)
             val style = data.props["style"] ?: Style.CLASSIC
             
             // Define update function
             fun updateState(newState: Int) {
                 tri.tag = newState
                 updateTriStateVisuals(tri, data, color, mode, style) // This will re-apply constraints (no rebuild)
                 // Map state to payload
                 val payload = when(newState) {
                     0 -> data.props["payloadLeft"] ?: "0"
                     2 -> data.props["payloadRight"] ?: "2"
                     else -> data.props["payloadCenter"] ?: "1"
                 }
                 sendMqtt(data.topicConfig, payload)
             }
             
             tri.findViewById<View>(R.id.zoneLeft).setOnClickListener { updateState(0) }
             tri.findViewById<View>(R.id.zoneRight).setOnClickListener { updateState(2) }
             tri.findViewById<View>(R.id.zoneCenter).setOnClickListener { 
                 if (mode == Mode.TWO_WAY) {
                     val cur = tri.tag as? Int ?: 0
                     updateState(if (cur == 0) 2 else 0)
                 } else {
                     updateState(1) 
                 }
             }
        }
    }

    override fun onMqttMessage(view: View, data: ComponentData, payload: String) {
        val root = (view as? ViewGroup)?.getChildAt(0) as? FrameLayout ?: return
        val mode = resolveMode(data)
        val color = resolveColor(data)
        
        val child = root.getChildAt(0) ?: return
        
        if (mode == Mode.MULTI) {
            val multi = child as? LinearLayout ?: return
            // Visual Update Only
             val style = data.props["style"] ?: Style.ROUNDED
             // Need size... find first child?
             val firstChild = multi.getChildAt(0)
             if (firstChild != null) {
                 updateMultiSegmentButtons(multi, payload, color, style == Style.CIRCLE, firstChild.width)
             }
        } else {
            val tri = child as? ConstraintLayout ?: return
            val pL = data.props["payloadLeft"] ?: "0"
            val pR = data.props["payloadRight"] ?: "2"
            // pC is default
            val newState = when(payload) { pL -> 0; pR -> 2; else -> 1 }
            
            tri.tag = newState
            val style = data.props["style"] ?: Style.CLASSIC
            // Ensure UI thread just in case
            view.post {
                updateTriStateVisuals(tri, data, color, mode, style)
            }
        }
    }

    // --- Helpers ---
    private fun resolveMode(data: ComponentData): String {
        var m = data.props["mode"] ?: Mode.TWO_WAY
        if (m == "switch") {
            m = if (data.props["steps"] == "2") Mode.TWO_WAY else Mode.THREE_WAY
        }
        return m
    }
    
    private fun resolveColor(data: ComponentData): Int {
         return try {
             Color.parseColor(data.props["color"] ?: "#a573bc")
         } catch(e: Exception) { Color.MAGENTA }
    }

    data class Segment(val label: String, val value: String)
    private fun parseSegments(json: String): MutableList<Segment> {
        val list = mutableListOf<Segment>()
        if (json.isEmpty()) {
            return mutableListOf(Segment("LOW", "1"), Segment("MED", "2"), Segment("HIGH", "3"))
        }
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Segment(obj.getString("label"), obj.getString("value")))
            }
        } catch(e: Exception) { return mutableListOf(Segment("Error", "0")) }
        return list
    }
}
