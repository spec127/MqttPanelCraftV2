package com.example.mqttpanelcraft.ui

import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/**
 * Handles all User Interactions (Touch, Drag, Click).
 * Decoupled from Rendering. It only emits "Intentions" (Callbacks).
 */
class CanvasInteractionManager(
    private val canvasCanvas: FrameLayout,
    private val guideOverlay: AlignmentOverlayView,
    private val dropDeleteZone: View,
    private val callbacks: InteractionCallbacks
) {

    interface InteractionCallbacks {
        fun onComponentClicked(id: Int)
        fun onComponentMoved(id: Int, newX: Float, newY: Float)
        fun onComponentResized(id: Int, newW: Int, newH: Int)
        fun onComponentDeleted(id: Int)
        fun onNewComponent(type: String, x: Float, y: Float)
    }

    private val density = canvasCanvas.context.resources.displayMetrics.density

    // Grid
    companion object {
        const val GRID_UNIT_DP = 20
        const val DRAG_THRESHOLD = 10f
    }
    private val gridUnitPx = (GRID_UNIT_DP * density).toInt()

    // State
    private enum class Mode { IDLE, DRAGGING, RESIZING }
    private var currentMode = Mode.IDLE

    // Interaction vars
    private var activeView: View? = null
    private var downX = 0f
    private var downY = 0f
    private var initX = 0f
    private var initY = 0f
    private var initW = 0
    private var initH = 0
    private var isDragDetected = false

    fun setup(isEditMode: () -> Boolean) {
        // 1. Touch Listener (Move/Resize)
        canvasCanvas.setOnTouchListener { _, event ->
            if (!isEditMode()) return@setOnTouchListener false
            handleTouch(event)
        }

        // 2. Drag Listener (Sidebar Drops)
        canvasCanvas.setOnDragListener { _, event ->
             if (!isEditMode()) return@setOnDragListener false
             when (event.action) {
                 android.view.DragEvent.ACTION_DRAG_ENTERED -> dropDeleteZone.visibility = View.VISIBLE
                 android.view.DragEvent.ACTION_DRAG_ENDED -> dropDeleteZone.visibility = View.GONE
                 android.view.DragEvent.ACTION_DROP -> {
                     if (event.clipData != null && event.clipData.itemCount > 0) {
                         val type = event.clipData.getItemAt(0).text.toString()
                         callbacks.onNewComponent(type, event.x, event.y)
                     }
                 }
             }
             true
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val rawX = event.rawX
        val rawY = event.rawY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = rawX
                downY = rawY
                isDragDetected = false
                
                // Hit Test Priority: Resize Handle -> Body
                val handle = findResizeHandleAt(x, y)
                if (handle != null) {
                    currentMode = Mode.RESIZING
                    activeView = handle // Logic: handle is part of view structure, we need parent
                    // Ideally findResizeHandleAt returns the PARENT component
                    initW = handle.width
                    initH = handle.height
                    // wait, findResizeHandleAt logic in previous code returned "child" (the parent container)
                    activeView = handle // Confirmed: findResizeHandleAt returns the Component View (parent)
                    initW = activeView!!.width
                    initH = activeView!!.height
                    canvasCanvas.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                val component = findComponentAt(x, y)
                if (component != null) {
                    currentMode = Mode.DRAGGING
                    activeView = component
                    initX = component.x
                    initY = component.y
                    dropDeleteZone.visibility = View.VISIBLE
                    canvasCanvas.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                // Background Tap -> Deselect
                callbacks.onComponentClicked(-1) 
                currentMode = Mode.IDLE
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = rawX - downX
                val dy = rawY - downY
                if (!isDragDetected && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                    isDragDetected = true
                }

                if (currentMode == Mode.DRAGGING && activeView != null) {
                    // Preview Move (Visual only)
                    val newX = snap(initX + dx)
                    val newY = snap(initY + dy)
                    activeView!!.x = newX
                    activeView!!.y = newY
                    
                    // Move Label
                    findLabelFor(activeView!!)?.let {
                        it.x = newX
                        it.y = newY + activeView!!.height + 4
                    }

                    checkDeleteZone(rawX, rawY, activeView)
                    checkAlignment(activeView!!)
                    return true
                }
// ... (rest of function)



                if (currentMode == Mode.RESIZING && activeView != null) {
                    // Preview Resize
                    var newW = snap(initW + dx.toInt()).toInt().coerceAtLeast(gridUnitPx)
                    var newH = snap(initH + dy.toInt()).toInt().coerceAtLeast(gridUnitPx)
                    
                    val params = activeView!!.layoutParams
                    params.width = newW
                    params.height = newH
                    activeView!!.layoutParams = params
                    activeView!!.requestLayout()
                    
                    findLabelFor(activeView!!)?.let {
                        it.y = activeView!!.y + newH + 4
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dropDeleteZone.visibility = View.GONE
                guideOverlay.clear()

                if (currentMode == Mode.DRAGGING && activeView != null) {
                    if (isDeleteZoneHovered(rawX, rawY)) {
                        callbacks.onComponentDeleted(activeView!!.id)
                    } else if (isDragDetected) {
                        callbacks.onComponentMoved(activeView!!.id, activeView!!.x, activeView!!.y)
                    } else {
                        callbacks.onComponentClicked(activeView!!.id)
                    }
                } else if (currentMode == Mode.RESIZING && activeView != null) {
                     if (isDragDetected) {
                         callbacks.onComponentResized(activeView!!.id, activeView!!.width, activeView!!.height)
                     }
                }

                currentMode = Mode.IDLE
                activeView = null
                return true
            }
        }
        return false
    }
    
    private fun checkAlignment(currentView: View) {
        val cx = currentView.x + currentView.width / 2f
        val cy = currentView.y + currentView.height / 2f
        
        val lines = mutableListOf<Float>() // Format: [x1, y1, x2, y2]
        val threshold = 10f 

        for (i in 0 until canvasCanvas.childCount) {
            val other = canvasCanvas.getChildAt(i)
            if (other == currentView || other.visibility != View.VISIBLE || other.tag?.toString()?.startsWith("LABEL") == true || other is AlignmentOverlayView) continue

            val ox = other.x + other.width / 2f
            val oy = other.y + other.height / 2f

            if (Math.abs(cx - ox) < threshold) {
                lines.add(ox)
                lines.add(Math.min(currentView.y, other.y))
                lines.add(ox)
                lines.add(Math.max(currentView.y + currentView.height, other.y + other.height))
            }
            
            if (Math.abs(cy - oy) < threshold) {
                lines.add(Math.min(currentView.x, other.x))
                lines.add(oy)
                lines.add(Math.max(currentView.x + currentView.width, other.x + other.width))
                lines.add(oy)
            }
        }
        
        guideOverlay.drawLines(lines.toFloatArray())
    }

    // --- Helpers ---
    private fun snap(v: Float): Float = (kotlin.math.round(v / gridUnitPx) * gridUnitPx).toFloat()
    private fun snap(v: Int): Float = snap(v.toFloat())

    private fun findComponentAt(x: Float, y: Float): View? {
        for (i in canvasCanvas.childCount - 1 downTo 0) {
            val child = canvasCanvas.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child.tag?.toString()?.startsWith("LABEL") == true) continue
            if (child is AlignmentOverlayView) continue
            if (x >= child.x && x <= child.x + child.width && y >= child.y && y <= child.y + child.height) return child
        }
        return null
    }

    private fun findResizeHandleAt(x: Float, y: Float): View? {
        for (i in canvasCanvas.childCount - 1 downTo 0) {
            val child = canvasCanvas.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child.tag?.toString()?.startsWith("LABEL") == true) continue
            val handle = child.findViewWithTag<View>("RESIZE_HANDLE")
            if (handle != null && handle.visibility == View.VISIBLE) {
                // Handle coords relative to screen/canvas need calculation often
                // handle.left is relative to child
                val hLeft = child.x + handle.left
                val hTop = child.y + handle.top
                if (x >= hLeft && x <= hLeft + handle.width && y >= hTop && y <= hTop + handle.height) return child
            }
        }
        return null
    }

    private fun findLabelFor(view: View): View? = canvasCanvas.findViewWithTag("LABEL_FOR_${view.id}")

    private fun checkDeleteZone(rawX: Float, rawY: Float, view: View?) {
        if (isDeleteZoneHovered(rawX, rawY)) {
            dropDeleteZone.setBackgroundResource(com.example.mqttpanelcraft.R.drawable.shape_circle_red)
            view?.alpha = 0.5f
        } else {
            dropDeleteZone.setBackgroundResource(com.example.mqttpanelcraft.R.drawable.shape_circle_grey)
            view?.alpha = 1.0f
        }
    }

    private fun isDeleteZoneHovered(rawX: Float, rawY: Float): Boolean {
         val loc = IntArray(2)
         dropDeleteZone.getLocationOnScreen(loc)
         val cx = loc[0] + dropDeleteZone.width / 2f
         val cy = loc[1] + dropDeleteZone.height / 2f
         val dist = kotlin.math.hypot((rawX - cx).toDouble(), (rawY - cy).toDouble())
         return dist < (80 * density)
    }
}
