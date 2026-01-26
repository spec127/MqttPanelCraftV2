package com.example.mqttpanelcraft.utils

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.R

/**
 * Stub implementation of AdManager to allow compilation without GMS dependencies.
 * All ad logic is disabled.
 */
object AdManager {

    private const val TAG = "AdManager"

    // Real Ad Unit IDs (User Provided) - Kept for reference but unused
    const val BANNER_AD_ID = "ca-app-pub-4344043793626988/3938962153"
    const val INTERSTITIAL_AD_ID = "ca-app-pub-4344043793626988/5500182186"
    const val REWARDED_AD_ID = "ca-app-pub-4344043793626988/4187100512"

    fun initialize(context: android.content.Context) {
        Log.d(TAG, "AdManager Stub: Initialized (Ads Disabled)")
    }
    
    fun setDisabled(disabled: Boolean, context: android.content.Context) {
        PremiumManager.setPremium(context, disabled)
    }

    // --- Banner Ads ---
    
    fun loadBannerAd(activity: Activity, container: FrameLayout, fab: View? = null) {
        if (PremiumManager.isPremium(activity)) {
            container.visibility = View.GONE
            // Reset FAB
            fab?.animate()?.translationY(0f)?.setDuration(300)?.start()
        } else {
             container.visibility = View.VISIBLE
             container.removeAllViews()

             // Create Placeholder View
             val density = activity.resources.displayMetrics.density
             val heightPx = (50 * density).toInt()
             
             val placeholder = android.widget.TextView(activity).apply {
                 text = "Banner Ad Area (Test)"
                 gravity = android.view.Gravity.CENTER
                 setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
                 setTextColor(android.graphics.Color.DKGRAY)
                 layoutParams = FrameLayout.LayoutParams(
                     FrameLayout.LayoutParams.MATCH_PARENT, 
                     heightPx
                 )
             }
             container.addView(placeholder)

             // Move FAB Up
             fab?.animate()?.translationY(-heightPx.toFloat())?.setDuration(300)?.start()
        }
    }

    // --- Interstitial Ads ---

    fun loadInterstitial(context: android.content.Context) {
         // Stub
    }

    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit = {}) {
        Log.d(TAG, "AdManager Stub: showInterstitial called, passing through.")
        onAdClosed()
    }

    // --- Rewarded Ads ---

    fun loadRewarded(context: android.content.Context) {
        // Stub
    }

    fun isRewardedReady(): Boolean {
        return false
    }

    fun showRewarded(activity: Activity, onReward: () -> Unit, onClosed: () -> Unit) {
        Log.d(TAG, "AdManager Stub: showRewarded called. No reward granted.")
        onClosed()
    }
    
    fun refreshAdState(context: android.content.Context) {
         // Stub
    }
}
