package com.example.mqttpanelcraft.ui

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R

class CanvasInteractionManager(
        private val canvasCanvas: FrameLayout,
        private val guideOverlay: AlignmentOverlayView,
        private val bottomSheetPeekHeight: Int,
        val callbacks: InteractionCallbacks
) {

    private var isEditMode: () -> Boolean = { false }
    private var isGridSnapEnabled: () -> Boolean = { true }
    private var isBottomSheetExpanded: () -> Boolean = { false }

    fun setup(
            isEditMode: () -> Boolean,
            isGridEnabled: () -> Boolean,
            isBottomSheetExpanded: () -> Boolean
    ) {
        this.isEditMode = isEditMode
        this.isGridSnapEnabled = isGridEnabled
        this.isBottomSheetExpanded = isBottomSheetExpanded
    }

    private enum class Mode {
        IDLE,
        DRAGGING,
        RESIZING
    }

    private var currentMode = Mode.IDLE
    private var activeView: View? = null

    // Drag/Resize State
    private var downX = 0f
    private var downY = 0f
    private var isDragDetected = false
    private var hasTriggeredDragSelection = false
    private var isDeleteHovered = false

    private var initX = 0f
    private var initY = 0f
    private var initW = 0
    private var initH = 0

    // Configuration
    private val density = canvasCanvas.context.resources.displayMetrics.density
    private val DRAG_THRESHOLD = 8 * density
    private val gridUnitPx = (10 * density).toInt()
    private val RESIZE_STEP_DP = 10
    private var bottomInset = bottomSheetPeekHeight

    fun updateBottomInset(inset: Int) {
        bottomInset = inset
    }

    // Dynamic Offset State
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var initialCanvasY = 0f

    enum class DeleteState {
        NONE,
        WARNING,
        ACTIVE
    }

    interface InteractionCallbacks {
        fun onComponentClicked(id: Int)
        fun onComponentSelected(id: Int)
        fun onComponentMoved(id: Int, newX: Float, newY: Float)
        fun onComponentResized(id: Int, newW: Int, newH: Int)
        fun onComponentResizing(id: Int, newW: Int, newH: Int)
        fun onComponentDeleted(id: Int)
        fun onNewComponent(type: String, x: Float, y: Float)
        fun onDeleteZoneState(state: DeleteState) // UPDATED
        fun onInteractionStarted()
    }

    // ... (Keep existing properties) ...

    // State Tracking
    private var currentDeleteState = DeleteState.NONE

    fun handleTouch(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val rawX = event.rawX
        val rawY = event.rawY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = rawX
                downY = rawY
                isDragDetected = false
                hasTriggeredDragSelection = false
                currentDeleteState = DeleteState.NONE

                // Only allow Drag/Resize in Edit Mode
                if (isEditMode()) {
                    val handle = findResizeHandleAt(x, y)
                    if (handle != null) {
                        currentMode = Mode.RESIZING
                        activeView = handle
                        initW = activeView!!.width
                        initH = activeView!!.height
                        resizeStartX = rawX
                        resizeStartY = rawY

                        // Notify Interaction Start IMMEDIATELY (Collapses Sheet)
                        callbacks.onInteractionStarted()

                        canvasCanvas.requestDisallowInterceptTouchEvent(true)
                        return true
                    }

                    val component = findComponentAt(x, y)
                    if (component != null) {
                        currentMode = Mode.DRAGGING
                        activeView = component

                        // Capture Initial State
                        initX = component.x
                        initY = component.y

                        // Dynamic Offset Calculation (Screen Space)
                        val canvasLoc = IntArray(2)
                        canvasCanvas.getLocationOnScreen(canvasLoc)
                        initialCanvasY = canvasLoc[1].toFloat()

                        // Offset = Finger - (CanvasScreen + ComponentLocal)
                        // Result: Where is the finger relative to the component's Top-Left?
                        val compScreenX = canvasLoc[0] + initX
                        val compScreenY = canvasLoc[1] + initY

                        touchOffsetX = rawX - compScreenX
                        touchOffsetY = rawY - compScreenY

                        canvasCanvas.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }

                callbacks.onComponentClicked(-1)
                currentMode = Mode.IDLE
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = rawX - downX
                val dy = rawY - downY
                if (!isDragDetected &&
                                (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)
                ) {
                    isDragDetected = true
                    // Trigger selection immediately on drag start
                    if (!hasTriggeredDragSelection && activeView != null) {
                        callbacks.onComponentSelected(activeView!!.id)
                        hasTriggeredDragSelection = true

                        // Notify Interaction Start (Late Trigger for Drag)
                        if (currentMode == Mode.DRAGGING) {
                            callbacks.onInteractionStarted()
                        }
                    }
                }

                if (currentMode == Mode.DRAGGING && activeView != null) {
                    // DYNAMIC MAPPING LOGIC
                    val canvasLoc = IntArray(2)
                    canvasCanvas.getLocationOnScreen(canvasLoc)
                    val currentCanvasScreenX = canvasLoc[0].toFloat()
                    val currentCanvasScreenY = canvasLoc[1].toFloat()

                    val targetX = rawX - currentCanvasScreenX - touchOffsetX
                    val targetY = rawY - currentCanvasScreenY - touchOffsetY

                    var newX = snap(targetX)
                    val nominalY = snap(targetY)

                    // Resistance & Delete Logic
                    val canvasH = canvasCanvas.height
                    val canvasW = canvasCanvas.width
                    val compH = activeView!!.height
                    val compW = activeView!!.width
                    val safeLimitY = (canvasH - bottomInset - compH).toFloat()

                    var newY = nominalY
                    var nextState = DeleteState.NONE

                    val screenH = canvasCanvas.context.resources.displayMetrics.heightPixels
                    val fingerInDeleteZone = rawY > (screenH - bottomInset)

                    if (nominalY > safeLimitY) {
                        // OVERSHOOT Logic
                        if (isBottomSheetExpanded()) {
                            newY = safeLimitY
                        } else {
                            // Apply Heavy Resistance
                            val excess = nominalY - safeLimitY
                            val resistedExcess = excess * 0.15f // Heavy resistance
                            newY = safeLimitY + resistedExcess
                        }
                    }

                    // Delete State Logic
                    if (isBottomSheetExpanded()) {
                        nextState = DeleteState.NONE
                    } else {
                        if (fingerInDeleteZone) {
                            nextState = DeleteState.ACTIVE // Red Zone
                            activeView?.alpha = 0.5f // Fade out
                        } else {
                            nextState = DeleteState.WARNING // Gray Zone
                            activeView?.alpha = 1.0f
                        }
                    }

                    // State Change
                    if (currentDeleteState != nextState) {
                        currentDeleteState = nextState
                        callbacks.onDeleteZoneState(nextState)
                    }

                    // Clamp X
                    if (newX < 0f) newX = 0f
                    if (newX > canvasW - compW) newX = (canvasW - compW).toFloat()

                    // Clamp Y Top only
                    if (newY < 0f) newY = 0f

                    activeView!!.x = newX
                    activeView!!.y = newY

                    findLabelFor(activeView!!)?.let {
                        it.x = newX
                        it.y = newY + activeView!!.height + 4
                    }

                    if (currentDeleteState == DeleteState.NONE) {
                        checkAlignment(activeView!!, compW, compH)
                    } else {
                        guideOverlay.clear()
                    }
                    return true
                }

                if (currentMode == Mode.RESIZING && activeView != null) {
                    // ... (Resize logic remains same) ...
                    val dxResize = rawX - resizeStartX
                    val dyResize = rawY - resizeStartY

                    val targetWDp = (initW + dxResize) / density
                    val targetHDp = (initH + dyResize) / density

                    val stepDp = RESIZE_STEP_DP.toFloat()

                    val newWDp =
                            if (isGridSnapEnabled()) kotlin.math.round(targetWDp / stepDp) * stepDp
                            else kotlin.math.round(targetWDp)
                    val newHDp =
                            if (isGridSnapEnabled()) kotlin.math.round(targetHDp / stepDp) * stepDp
                            else kotlin.math.round(targetHDp)

                    var newW = kotlin.math.round(newWDp * density).toInt().coerceAtLeast(gridUnitPx)
                    var newH = kotlin.math.round(newHDp * density).toInt().coerceAtLeast(gridUnitPx)

                    // Aspect Ratio Locking
                    val tag = activeView!!.tag?.toString() ?: ""

                    val finalView = activeView!!.findViewWithTag<View>("target")
                    if (finalView is com.example.mqttpanelcraft.ui.views.JoystickView) {
                        if (finalView.axes == "4-Way") {
                            if (dxResize > dyResize) newH = newW else newW = newH
                        }
                    }

                    val params = activeView!!.layoutParams
                    params.width = newW
                    params.height = newH
                    activeView!!.layoutParams = params
                    activeView!!.requestLayout()

                    val label = findLabelFor(activeView!!)
                    label?.let { it.y = activeView!!.y + newH + 4 }

                    checkAlignment(activeView!!, newW, newH)
                    callbacks.onComponentResizing(activeView!!.id, newW, newH)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                guideOverlay.clear()

                if (currentMode == Mode.DRAGGING && activeView != null) {
                    val canvasH = canvasCanvas.height
                    val compH = activeView!!.height
                    val safeLimitY = (canvasH - bottomInset - compH).toFloat()

                    if (currentDeleteState == DeleteState.ACTIVE) {
                        callbacks.onComponentDeleted(activeView!!.id)
                        currentDeleteState = DeleteState.NONE
                        callbacks.onDeleteZoneState(DeleteState.NONE)
                    } else if (isDragDetected) {
                        // Ensure state is reset if we didn't delete
                        if (currentDeleteState != DeleteState.NONE) {
                            currentDeleteState = DeleteState.NONE
                            callbacks.onDeleteZoneState(DeleteState.NONE)
                        }

                        if (activeView!!.y > safeLimitY && !isBottomSheetExpanded()) {
                            // Snap Back
                            activeView!!
                                    .animate()
                                    .y(safeLimitY)
                                    .setDuration(300)
                                    .setInterpolator(
                                            android.view.animation.OvershootInterpolator(0.8f)
                                    )
                                    .start()

                            findLabelFor(activeView!!)?.let {
                                it.animate().y(safeLimitY + compH + 4).setDuration(300).start()
                            }
                            callbacks.onComponentMoved(activeView!!.id, activeView!!.x, safeLimitY)
                            callbacks.onDeleteZoneState(DeleteState.NONE) // Ensure reset
                        } else {
                            callbacks.onComponentMoved(
                                    activeView!!.id,
                                    activeView!!.x,
                                    activeView!!.y
                            )
                        }
                    } else {
                        callbacks.onComponentClicked(activeView!!.id)
                    }
                    activeView?.alpha = 1.0f
                } else if (currentMode == Mode.RESIZING && activeView != null) {
                    if (isDragDetected) {
                        callbacks.onComponentResized(
                                activeView!!.id,
                                activeView!!.width,
                                activeView!!.height
                        )
                    }
                } else if (activeView != null) {
                    callbacks.onComponentClicked(activeView!!.id)
                }

                currentMode = Mode.IDLE
                activeView = null
                isDeleteHovered = false
                return true
            }
        }
        return false
    }

    private fun checkAlignment(currentView: View, currentW: Int, currentH: Int) {
        if (isGridSnapEnabled()) {
            guideOverlay.clear()
            return
        }

        var newX = currentView.x
        var newY = currentView.y

        // Use PASSED width/height (essential for predictive resize alignment)
        val w = currentW
        val h = currentH

        val l = newX
        val t = newY
        val r = l + w
        val b = t + h
        val cx = l + w / 2f
        val cy = t + h / 2f

        val lines = mutableListOf<Float>()
        val threshold = 10 * density

        var snappedX = false
        var snappedY = false

        for (i in 0 until canvasCanvas.childCount) {
            val other = canvasCanvas.getChildAt(i)
            if (other == currentView ||
                            other.visibility != View.VISIBLE ||
                            other.tag?.toString()?.startsWith("LABEL") == true ||
                            other is AlignmentOverlayView
            )
                    continue

            val ol = other.x
            val ot = other.y
            val ow = other.width
            val oh = other.height
            val or_ = ol + ow
            val ob = ot + oh
            val ocx = ol + ow / 2f
            val ocy = ot + oh / 2f

            // Vertical Alignment (X axis)
            if (!snappedX) {
                if (checkSnap(l, ol, threshold)) {
                    newX = ol
                    snappedX = true
                    addVLine(lines, ol, t, b, ot, ob)
                } else if (checkSnap(l, or_, threshold)) {
                    newX = or_
                    snappedX = true
                    addVLine(lines, or_, t, b, ot, ob)
                } else if (checkSnap(l, ocx, threshold)) {
                    newX = ocx
                    snappedX = true
                    addVLine(lines, ocx, t, b, ot, ob)
                } else if (checkSnap(r, ol, threshold)) {
                    newX = ol - w
                    snappedX = true
                    addVLine(lines, ol, t, b, ot, ob)
                } else if (checkSnap(r, or_, threshold)) {
                    newX = or_ - w
                    snappedX = true
                    addVLine(lines, or_, t, b, ot, ob)
                } else if (checkSnap(r, ocx, threshold)) {
                    newX = ocx - w
                    snappedX = true
                    addVLine(lines, ocx, t, b, ot, ob)
                } else if (checkSnap(cx, ol, threshold)) {
                    newX = ol - w / 2f
                    snappedX = true
                    addVLine(lines, ol, t, b, ot, ob)
                } else if (checkSnap(cx, or_, threshold)) {
                    newX = or_ - w / 2f
                    snappedX = true
                    addVLine(lines, or_, t, b, ot, ob)
                } else if (checkSnap(cx, ocx, threshold)) {
                    newX = ocx - w / 2f
                    snappedX = true
                    addVLine(lines, ocx, t, b, ot, ob)
                }
            }

            // Horizontal Alignment (Y axis)
            if (!snappedY) {
                if (checkSnap(t, ot, threshold)) {
                    newY = ot
                    snappedY = true
                    addHLine(lines, ot, l, r, ol, or_)
                } else if (checkSnap(t, ob, threshold)) {
                    newY = ob
                    snappedY = true
                    addHLine(lines, ob, l, r, ol, or_)
                } else if (checkSnap(t, ocy, threshold)) {
                    newY = ocy
                    snappedY = true
                    addHLine(lines, ocy, l, r, ol, or_)
                } else if (checkSnap(b, ot, threshold)) {
                    newY = ot - h
                    snappedY = true
                    addHLine(lines, ot, l, r, ol, or_)
                } else if (checkSnap(b, ob, threshold)) {
                    newY = ob - h
                    snappedY = true
                    addHLine(lines, ob, l, r, ol, or_)
                } else if (checkSnap(b, ocy, threshold)) {
                    newY = ocy - h
                    snappedY = true
                    addHLine(lines, ocy, l, r, ol, or_)
                } else if (checkSnap(cy, ot, threshold)) {
                    newY = ot - h / 2f
                    snappedY = true
                    addHLine(lines, ot, l, r, ol, or_)
                } else if (checkSnap(cy, ob, threshold)) {
                    newY = ob - h / 2f
                    snappedY = true
                    addHLine(lines, ob, l, r, ol, or_)
                } else if (checkSnap(cy, ocy, threshold)) {
                    newY = ocy - h / 2f
                    snappedY = true
                    addHLine(lines, ocy, l, r, ol, or_)
                }
            }
        }

        // If Resizing, we ONLY update params, we can't move X/Y via layout params in the same way?
        // Wait, resizing usually only changes W/H. If we support Left/Top resize, X/Y changes too.
        // Current implementation is simple Bottom-Right resize (see MotionEvent).
        // Actually, current resize is usually only W/H increase?
        // Let's look at RESIZING logic again:
        // params.width = newW, params.height = newH.
        // X/Y are not modified during resize in current logic.
        // So aligning L, T, R, B...
        // If we only resize Bottom-Right, Top-Left (X/Y) is fixed.
        // We should only snap Width/Height then?
        // But if we snap Right edge to something, we adjust Width.
        // My `checkAlignment` updates `newX`/`newY`. This works for DRAGGING.
        // For RESIZING, we are NOT updating X/Y.

        // WARNING: Re-using `checkAlignment` for Resize is tricky if it modifies X/Y.
        // If simple interaction (Bottom-Right resize handle), we only care about R and B alignment.
        // AND we achieve it by changing Width/Height, not X/Y.

        // For now, I will disable alignment guides during RESIZE to avoid jumpiness,
        // OR better: Only show lines, but don't snap X/Y.
        // But user asked for "Alignment line mismatch".
        // The issue is simply visual?
        // If I just draw lines based on `currentW/currentH`, it should be correct.

        // Let's modify: `checkAlignment` will Draw lines. It will also RETURN snapped bounds if
        // needed?
        // Currently it modifies `currentView.x`. That's bad for Resize if we don't want to move
        // Top-Left.

        if (currentMode == Mode.DRAGGING) {
            if (snappedX) currentView.x = newX
            if (snappedY) currentView.y = newY
            if (snappedX || snappedY) {
                findLabelFor(currentView)?.let {
                    it.x = currentView.x
                    it.y = currentView.y + currentView.height + 4
                }
            }
        }

        // Draw lines regardless
        guideOverlay.drawLines(lines.toFloatArray())
    }

    private fun checkSnap(val1: Float, val2: Float, threshold: Float): Boolean {
        return Math.abs(val1 - val2) < threshold
    }

    private fun addVLine(
            lines: MutableList<Float>,
            x: Float,
            y1: Float,
            y2: Float,
            oy1: Float,
            oy2: Float
    ) {
        val min = Math.min(y1, oy1)
        val max = Math.max(y2, oy2)
        lines.add(x)
        lines.add(min)
        lines.add(x)
        lines.add(max)
    }
    private fun addHLine(
            lines: MutableList<Float>,
            y: Float,
            x1: Float,
            x2: Float,
            ox1: Float,
            ox2: Float
    ) {
        val min = Math.min(x1, ox1)
        val max = Math.max(x2, ox2)
        lines.add(min)
        lines.add(y)
        lines.add(max)
        lines.add(y)
    }

    private fun snap(v: Float, step: Int = gridUnitPx): Float =
            if (isGridSnapEnabled()) (kotlin.math.round(v / step) * step).toFloat()
            else kotlin.math.round(v).toFloat()
    private fun snap(v: Int, step: Int = gridUnitPx): Float = snap(v.toFloat(), step)

    private fun findComponentAt(x: Float, y: Float): View? {
        for (i in canvasCanvas.childCount - 1 downTo 0) {
            val child = canvasCanvas.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child.id == R.id.backgroundGrid) continue
            if (child.tag?.toString()?.startsWith("LABEL") == true) continue
            if (child is AlignmentOverlayView) continue
            if (x >= child.x &&
                            x <= child.x + child.width &&
                            y >= child.y &&
                            y <= child.y + child.height
            )
                    return child
        }
        return null
    }

    private fun findResizeHandleAt(x: Float, y: Float): View? {
        for (i in canvasCanvas.childCount - 1 downTo 0) {
            val child = canvasCanvas.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child.id == R.id.backgroundGrid) continue
            if (child.tag?.toString()?.startsWith("LABEL") == true) continue
            val handle = child.findViewWithTag<View>("RESIZE_HANDLE")
            if (handle != null && handle.visibility == View.VISIBLE) {
                // The handle's layout position is adjusted by negative margins.
                // handle.x and handle.y are relative to parent 'child'.
                // Handle size is 48px. Visual center is at (24, 24) in handle-space.
                val centerX = child.x + handle.x + (handle.width / 2)
                val centerY = child.y + handle.y + (handle.height / 2)

                // Expand touch area towards bottom-right (outside the component) as requested
                val toleranceInside = 24f // Towards top-left (inside component)
                val toleranceOutside = 72f // Towards bottom-right (outside component)

                if (x >= centerX - toleranceInside &&
                                x <= centerX + toleranceOutside &&
                                y >= centerY - toleranceInside &&
                                y <= centerY + toleranceOutside
                ) {
                    return child
                }
            }
        }
        return null
    }

    private fun findLabelFor(view: View): View? =
            canvasCanvas.findViewWithTag("LABEL_FOR_${view.id}")
}
