package com.example.mqttpanelcraft.utils

import android.content.Context

object PremiumManager {
    private const val PREFS_NAME = "AppSettings"
    // We map premium status to the legacy "ads_disabled" key to maintain current behavior,
    // as the user mentioned they are currently using SP to simulate it.
    private const val KEY_PREMIUM_STATUS = "ads_disabled"

    /**
     * Checks if the user has Premium status.
     * This is the single source of truth for ad display logic.
     */
    fun isPremium(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREMIUM_STATUS, false)
    }

    /**
     * Sets the Premium status.
     * To be called when a purchase is successful or for debugging/simulation.
     */
    fun setPremium(context: Context, isPremium: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PREMIUM_STATUS, isPremium).apply()
        
        // Notify AdManager to update its state if necessary (e.g. hide existing banners)
        AdManager.refreshAdState(context)
    }

    /**
     * Shows a dialog to simulate Premium purchase.
     */
    fun showPremiumDialog(context: Context, callback: (Boolean) -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Upgrade to Premium")
            .setMessage("Unlock advanced features like File Import and remove all ads!\n\n(Simulation: Click 'Buy' to enable)")
            .setPositiveButton("Buy ($1.99)") { _, _ ->
                setPremium(context, true)
                android.widget.Toast.makeText(context, "Premium Unlocked!", android.widget.Toast.LENGTH_SHORT).show()
                callback(true)
            }
            .setNegativeButton("Cancel") { _, _ ->
                callback(false)
            }
            .setNeutralButton("Restore") { _, _ ->
                 // Simulation: Check if already true? Or just re-enable
                 if (isPremium(context)) {
                     android.widget.Toast.makeText(context, "Already Premium", android.widget.Toast.LENGTH_SHORT).show()
                     callback(true)
                 } else {
                     android.widget.Toast.makeText(context, "No purchase found", android.widget.Toast.LENGTH_SHORT).show()
                 }
            }
            .show()
    }
}
