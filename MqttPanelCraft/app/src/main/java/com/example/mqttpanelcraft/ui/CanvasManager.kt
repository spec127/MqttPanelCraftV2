package com.example.mqttpanelcraft.ui

import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

class CanvasManager(
    private val canvasCanvas: FrameLayout,
    private val guideOverlay: AlignmentOverlayView,
    private val dropDeleteZone: View,
    private val onComponentClicked: (View) -> Unit, // Replaced Dropped with Clicked
    private val onComponentMoved: (View) -> Unit,
    private val onComponentDeleted: (View) -> Unit,
    private val onCreateNewComponent: (String, Float, Float) -> Unit,
    private val onComponentResized: (View) -> Unit 
) {

    private val density = canvasCanvas.context.resources.displayMetrics.density

    // --- GRID CONSTANTS ---
    companion object {
        const val GRID_UNIT_DP = 20
        const val DRAG_THRESHOLD = 10f // Pixels to detect drag vs click
    }

    private val gridUnitPx: Int by lazy {
        (GRID_UNIT_DP * density).toInt()
    }

    // State Machine
    private enum class Mode {
        IDLE, DRAGGING, RESIZING
    }
    private var currentMode = Mode.IDLE

    // Interaction State
    private var activeView: View? = null
    private var downX = 0f
    private var downY = 0f
    private var initViewX = 0f
    private var initViewY = 0f
    private var initViewW = 0
    private var initViewH = 0
    private var isDragDetected = false // To detect click vs drag

    // Cache
    private val cachedCanvasLoc = IntArray(2)

    init {
        // Initial Calculation
        canvasCanvas.post {
            canvasCanvas.getLocationOnScreen(cachedCanvasLoc)
        }
    }

    /**
     * Main Entry Point: Set up the centralized Touch Listener
     */
    fun setupDragListener(isEditMode: () -> Boolean) {
        // [RESTORED] Drag Listener for Sidebar Drops (System Drag)
        canvasCanvas.setOnDragListener { _, event ->
            if (!isEditMode()) return@setOnDragListener false
            when (event.action) {
                android.view.DragEvent.ACTION_DROP -> {
                     // Handle New Component Drop
                     if (event.clipData != null && event.clipData.itemCount > 0) {
                         val item = event.clipData.getItemAt(0)
                         val tag = item.text.toString()
                         // Center Logic: x, y are drop coords. User expects component center there.
                         // onCreateNewComponent logic handles this call.
                         onCreateNewComponent(tag, event.x, event.y)
                     }
                }
                android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                     dropDeleteZone.visibility = View.VISIBLE
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                     dropDeleteZone.visibility = View.GONE
                }
            }
            true
        }

        // Centralized Touch Handler (Existing Components Move/Resize)
        canvasCanvas.setOnTouchListener { _, event ->
            if (!isEditMode()) return@setOnTouchListener false
            handleTouchEvent(event)
        }
    }

    fun attachResizeBehavior(view: View, handle: View, isEditMode: () -> Boolean) {
        // No-Op: Resize logic is now centralized in handleTouchEvent
        // We only verify the handle exists by tag "RESIZE_HANDLE"
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val rawX = event.rawX
        val rawY = event.rawY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = rawX
                downY = rawY
                isDragDetected = false
                
                // 1. Check for Resize Handle Hit First (High Priority)
                val handleInfo = findResizeHandleAt(x, y)
                if (handleInfo != null) {
                    currentMode = Mode.RESIZING
                    activeView = handleInfo // The Parent Component View
                    initViewW = activeView!!.width
                    initViewH = activeView!!.height
                    // Stop any parent scrolling interception
                    canvasCanvas.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                // 2. Check for Component Body Hit
                val component = findComponentAt(x, y)
                if (component != null) {
                    currentMode = Mode.DRAGGING
                    activeView = component
                    initViewX = component.x
                    initViewY = component.y
                    
                    // Show Delete Zone
                    dropDeleteZone.visibility = View.VISIBLE
                    canvasCanvas.requestDisallowInterceptTouchEvent(true) // Steal events
                    return true
                }
                
                currentMode = Mode.IDLE
                activeView = null
                return false // Pass through if touching empty space
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = rawX - downX
                val dy = rawY - downY

                if (!isDragDetected && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                    isDragDetected = true
                }

                if (currentMode == Mode.DRAGGING && activeView != null) {
                    handleDrag(dx, dy)
                    
                    // Check Delete Zone
                    if (event.action != MotionEvent.ACTION_UP) { // Small optimization
                         checkDeleteZoneHover(event.rawX, event.rawY, activeView)
                    }
                    return true
                } else if (currentMode == Mode.RESIZING && activeView != null) {
                    handleResize(dx, dy)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                guideOverlay.clear()

                if (currentMode == Mode.DRAGGING && activeView != null) {
                    
                    // Check Delete (Must be done BEFORE hiding the zone)
                    if (isDeleteZoneHovered(event.rawX, event.rawY)) {
                        onComponentDeleted(activeView!!)
                        // Also remove labels manually for immediate feedback
                        canvasCanvas.removeView(activeView)
                        findLabelView(activeView)?.let { canvasCanvas.removeView(it) }
                    } else {
                        // Normal Drop
                        if (isDragDetected) {
                            onComponentMoved(activeView!!)
                        } else {
                            // It was a click!
                            // Force IDLE mode to ensure clean state
                            onComponentClicked(activeView!!)
                        }
                    }
                } else if (currentMode == Mode.RESIZING && activeView != null) {
                    if (isDragDetected) {
                        onComponentResized(activeView!!)
                    }
                }

                // Reset & Hide
                dropDeleteZone.visibility = View.GONE
                currentMode = Mode.IDLE
                activeView = null
                canvasCanvas.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun handleDrag(dx: Float, dy: Float) {
        val view = activeView ?: return
        
        // Raw Layout Position
        val rawX = initViewX + dx
        val rawY = initViewY + dy

        // Snap Layer
        // We pass W/H to centering logic if needed, here we use Top-Left logic
        var finalX = snapToGrid(rawX)
        var finalY = snapToGrid(rawY)

        // Alignment Assist (Optional, using center)
        // checkAlignment(finalX + view.width/2f, finalY + view.height/2f, view)

        // Boundary Check (Inside Canvas)
        finalX = finalX.coerceIn(0f, (canvasCanvas.width - view.width).toFloat())
        finalY = finalY.coerceIn(0f, (canvasCanvas.height - view.height).toFloat())

        // Apply
        view.x = finalX
        view.y = finalY
        
        // Move Label
        findLabelView(view)?.let { 
            it.x = finalX
            it.y = finalY + view.height + 4
        }
    }

    private fun handleResize(dx: Float, dy: Float) {
        val view = activeView ?: return

        val rawW = initViewW + dx
        val rawH = initViewH + dy

        // Snap to Grid (Round to nearest)
        var newW = (kotlin.math.round(rawW / gridUnitPx) * gridUnitPx).toInt()
        var newH = (kotlin.math.round(rawH / gridUnitPx) * gridUnitPx).toInt()
        
        // Min Size (1 unit)
        newW = newW.coerceAtLeast(gridUnitPx)
        newH = newH.coerceAtLeast(gridUnitPx)

        // Apply
        val params = view.layoutParams
        if (params.width != newW || params.height != newH) {
            params.width = newW
            params.height = newH
            view.layoutParams = params
            view.requestLayout()
            
            // Label pos update needed if height changes
            findLabelView(view)?.let { 
                it.y = view.y + newH + 4
            }
        }
    }

    // --- HIT TESTING ---

    /**
     * Finds if (x,y) hits a resize handle of any component.
     * Returns the PARENT component view if hit, null otherwise.
     */
    private fun findResizeHandleAt(x: Float, y: Float): View? {
        // Handle assumed to be at Bottom-Right, size 40x40px, or check specific view tag
        // Iterate children in reverse (top Z-order first)
        for (i in canvasCanvas.childCount - 1 downTo 0) {
            val child = canvasCanvas.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            // Ignore Labels & Overlays
            if (child.tag?.toString()?.startsWith("LABEL_FOR_") == true) continue
            if (child is AlignmentOverlayView) continue
            
            // Check if this child has a resize handle
            val handle = child.findViewWithTag<View>("RESIZE_HANDLE")
            if (handle != null && handle.visibility == View.VISIBLE) {
                // Determine handle absolute bounds relative to Canvas
                // Handle is inside Child (FrameLayout). child.x + handle.left
                val handleLeft = child.x + handle.left
                val handleTop = child.y + handle.top
                val handleRight = handleLeft + handle.width
                val handleBottom = handleTop + handle.height
                
                // Allow some slop/touch area expansion?
                // The handle view is already 40px in factory.
                if (x >= handleLeft && x <= handleRight && y >= handleTop && y <= handleBottom) {
                    return child // Return the component
                }
            }
        }
        return null
    }

    private fun findComponentAt(x: Float, y: Float): View? {
        for (i in canvasCanvas.childCount - 1 downTo 0) {
            val child = canvasCanvas.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child.tag?.toString()?.startsWith("LABEL_FOR_") == true) continue
            if (child is AlignmentOverlayView) continue
            
            // Check bounds
            if (x >= child.x && x <= child.x + child.width &&
                y >= child.y && y <= child.y + child.height) {
                return child
            }
        }
        return null
    }

    // --- UTILS ---

    private fun snapToGrid(value: Float): Float {
        return kotlin.math.round(value / gridUnitPx) * gridUnitPx.toFloat()
    }

    private fun findLabelView(view: View?): TextView? {
        if (view == null) return null
        val expectedTag = "LABEL_FOR_${view.id}"
        return canvasCanvas.findViewWithTag<TextView>(expectedTag)
    }

    // Reuse existing visual logic
    private fun checkDeleteZoneHover(rawX: Float, rawY: Float, view: View?) {
        val locations = IntArray(2)
        dropDeleteZone.getLocationOnScreen(locations)
        val zoneCenterX = locations[0] + dropDeleteZone.width / 2f
        val zoneCenterY = locations[1] + dropDeleteZone.height / 2f
        
        val dx = rawX - zoneCenterX
        val dy = rawY - zoneCenterY
        val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val threshold = 200f * density
        
        if (dist < threshold) {
            val factor = (1f - (dist / threshold)).coerceIn(0f, 1f)
            val scale = 1.0f + (0.5f * factor)
            dropDeleteZone.animate().scaleX(scale).scaleY(scale).alpha(0.5f + 0.5f * factor).setDuration(0).start()
            
            if (dist < 80f * density) { 
                 view?.alpha = 0.3f
                 dropDeleteZone.setBackgroundResource(com.example.mqttpanelcraft.R.drawable.shape_circle_red)
            } else {
                  view?.alpha = 0.8f
                  dropDeleteZone.setBackgroundResource(com.example.mqttpanelcraft.R.drawable.shape_circle_grey) 
             }
        } else {
            dropDeleteZone.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(100).start()
            view?.alpha = 1.0f
            dropDeleteZone.setBackgroundResource(com.example.mqttpanelcraft.R.drawable.shape_circle_grey)
        }
    }

    private fun isDeleteZoneHovered(rawX: Float, rawY: Float): Boolean {
        // Removed visibility check as we check this right before hiding
        val locations = IntArray(2)
        dropDeleteZone.getLocationOnScreen(locations)
        val zoneCenterX = locations[0] + dropDeleteZone.width / 2f
        val zoneCenterY = locations[1] + dropDeleteZone.height / 2f
        
        val dx = rawX - zoneCenterX
        val dy = rawY - zoneCenterY
        val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        return dist < 80f * density
    }
    
    // Kept to satisfy imports if any, but method is replaced
    fun getSnappedPosition(rawX: Float, rawY: Float, w: Int, h: Int, viewToExclude: View?): Point {
         val sx = snapToGrid(rawX).toInt()
         val sy = snapToGrid(rawY).toInt()
         return Point(sx, sy)
    }
}
