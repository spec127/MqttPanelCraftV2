package com.example.mqttpanelcraft.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.example.mqttpanelcraft.R
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Custom BottomSheetBehavior.
 * Rules:
 * 1. Dragging is ONLY allowed from the Header View.
 * 2. Touching content does NOT drag the sheet.
 * 3. Nested scrolling in content is handled naturally but doesn't affect sheet position unless at edges (standard behavior but we want to restrict it).
 */
class LockableBottomSheetBehavior<V : View> : BottomSheetBehavior<V> {
    
    // var headerViewId: Int = 0 (Removed)
    // private var headerView: View? = null (Removed)
    var isLocked: Boolean = true // Now always TRUE effectively based on user request
    private var dragStartedInHeader = false

    constructor() : super()
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    
    private fun isTouchInHeader(child: V, event: MotionEvent): Boolean {
        val header = child.findViewById<View>(R.id.bottomSheetHeader) ?: return false
        val location = IntArray(2)
        header.getLocationOnScreen(location)
        return event.rawX >= location[0] &&
                event.rawX <= location[0] + header.width &&
                event.rawY >= location[1] &&
                event.rawY <= location[1] + header.height
    }

    private fun beginGestureIfNeeded(child: V, event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            dragStartedInHeader = !isLocked || isTouchInHeader(child, event)
        }
    }

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        // Strict Lock: If hidden and locked (Edit Mode), do not allow ANY interaction to drag it out.
        // It must be opened programmatically.
        if (isLocked && state == STATE_HIDDEN) {
             return false
        }
    
        beginGestureIfNeeded(child, event)
        val allowed = !isLocked || dragStartedInHeader
        val handled = allowed && super.onInterceptTouchEvent(parent, child, event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            dragStartedInHeader = false
        }
        return handled
    }

    override fun onTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        beginGestureIfNeeded(child, event)
        val allowed = !isLocked || dragStartedInHeader
        val handled = allowed && super.onTouchEvent(parent, child, event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            dragStartedInHeader = false
        }
        return handled
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
        if (isLocked) return false
        return super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type)
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
         super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
    }
}
