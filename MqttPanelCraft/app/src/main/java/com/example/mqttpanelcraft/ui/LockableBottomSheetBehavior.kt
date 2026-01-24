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

    constructor() : super()
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    
    // Helper removed (isTouchInHeader)

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
    
        // 1. If Locked matches user request: Only DRAG from Header Area (e.g. top 80dp)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (isLocked) {
                // Check if touch is within the top draggable area of the sheet
                // instead of a specific view ID, which might be hard to sync layout-wise.
                val location = IntArray(2)
                child.getLocationOnScreen(location)
                val sheetTop = location[1]
                val touchY = event.rawY
                
                // Allow dragging if touch is within top 85dp (matching peek height or handle area)
                val density = child.resources.displayMetrics.density
                val dragLimit = sheetTop + (85 * density) 
                
                if (touchY > dragLimit) {
                    return false // Touch is below the header area, consume or ignore based on behavior
                }
                // If in Header area, allow!
            }
        }

        return super.onInterceptTouchEvent(parent, child, event)
    }

    override fun onTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (isLocked) {
                 // Check if touch is within the top draggable area of the sheet
                val location = IntArray(2)
                child.getLocationOnScreen(location)
                val sheetTop = location[1]
                val touchY = event.rawY
                
                val density = child.resources.displayMetrics.density
                val dragLimit = sheetTop + (85 * density) 
                
                if (touchY > dragLimit) return false
            }
        }
        return super.onTouchEvent(parent, child, event)
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ): Boolean {
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
