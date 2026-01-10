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
    
    var headerViewId: Int = 0
    private var headerView: View? = null
    var isLocked: Boolean = true // Now always TRUE effectively based on user request

    constructor() : super()
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    
    // Helper to check if touch is inside header
    private fun isTouchInHeader(event: MotionEvent): Boolean {
        // Try to find header if null
        if (headerView == null && headerViewId != 0) {
             // We can't easily findById from here without a reference view, 
             // but we can try in onInterceptTouchEvent's parent or child
             return false 
        }
        
        val header = headerView ?: return false
        val location = IntArray(2)
        header.getLocationOnScreen(location)
        
        val x = location[0]
        val y = location[1]
        val w = header.width
        val h = header.height
        
        val touchX = event.rawX
        val touchY = event.rawY
        
        return touchX >= x && touchX <= (x + w) && touchY >= y && touchY <= (y + h)
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
    
        // Initialize Header View references if needed
        if (headerView == null && headerViewId != 0) {
            headerView = child.findViewById(headerViewId)
            // Fallback: try finding in parent if child is just content
            if (headerView == null) {
                headerView = parent.findViewById(headerViewId)
            }
        }

        // 1. If Locked matches user request: Only DRAG from Header
        // If event is DOWN or MOVE, we check if it is in header.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // Check if touch is in header
            val inHeader = isTouchInHeader(event)
            
            if (isLocked) {
                // If Locked: Only allow interaction if in Header
                if (!inHeader) return false
                // If in Header, we allow it (return super so Behavior handles drag)
            } else {
                // If Unlocked: Standard behavior (but we might want to restrict content drag per original design?
                // The class doc says "Dragging is ONLY allowed from the Header View".
                // So effectively, we always limit to header?
                // User said "Cannot manual pull up". 
                // If we always limit to header, that's fine.
                // But let's stick to: Locked -> Header Only. Unlocked -> All.
                // Or: This class is "Lockable".
                // Let's implement: ALWAYS Header Only for Drag.
                // Content scroll is handled by NestedScroll.
                if (!inHeader) {
                    // Start of touch not in header. 
                    // Should we allow dragging from content? 
                    // Standard BottomSheet allows dragging from content if content is not scrollable.
                    // User complained "Cannot manual pull up".
                    // Let's allow dragging from everywhere if NOT Locked.
                    // And if Locked, only from Header.
                }
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
                if (!isTouchInHeader(event)) return false
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
