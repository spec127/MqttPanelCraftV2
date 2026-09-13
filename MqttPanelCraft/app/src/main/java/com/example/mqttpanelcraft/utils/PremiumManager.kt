package com.example.mqttpanelcraft.utils

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import com.example.mqttpanelcraft.R

object PremiumManager {
    private const val PREFS_NAME = "AppSettings"
    private const val BILLING_PREFS = "PlayBilling"
    private const val KEY_PREMIUM_STATUS = "play_premium_owned"
    private const val KEY_DEV_SKIP_ADS = "dev_skip_ads"

    fun isPremium(context: Context): Boolean =
        isEntitled(hasPlayEntitlement(context), isDevSkipAds(context))

    fun hasPlayEntitlement(context: Context): Boolean {
        val prefs = context.getSharedPreferences(BILLING_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREMIUM_STATUS, false)
    }

    internal fun isEntitled(playOwned: Boolean, debugSkip: Boolean): Boolean =
        playOwned || debugSkip

    fun applyPlayEntitlement(context: Context, owned: Boolean) {
        val prefs = context.getSharedPreferences(BILLING_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PREMIUM_STATUS, owned).apply()
        AdManager.refreshAdState(context)
    }

    fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun isDevSkipAds(context: Context): Boolean {
        if (!isDebuggable(context)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DEV_SKIP_ADS, false)
    }

    fun setDevSkipAds(context: Context, enabled: Boolean) {
        if (!isDebuggable(context)) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEV_SKIP_ADS, enabled).apply()
        AdManager.refreshAdState(context)
    }

    fun showPremiumDialog(context: Context, callback: (Boolean) -> Unit) {
        val activity = context as? Activity
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.premium_upgrade_title)
            .setMessage(R.string.premium_upgrade_message)
            .setPositiveButton(R.string.premium_buy) { _, _ ->
                if (activity != null) {
                    PlayBillingManager.launchPurchase(activity, callback)
                } else {
                    callback(false)
                }
            }
            .setNegativeButton(R.string.common_btn_cancel) { _, _ ->
                callback(false)
            }
            .setNeutralButton(R.string.premium_restore) { _, _ ->
                PlayBillingManager.restorePurchases(context, callback)
            }
            .show()
    }
}
