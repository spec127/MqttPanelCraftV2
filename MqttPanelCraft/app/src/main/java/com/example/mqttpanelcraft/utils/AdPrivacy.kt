package com.example.mqttpanelcraft.utils

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.mqttpanelcraft.R
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.*

object AdPrivacy {
    private const val PREFS = "AdPrivacy"
    private var checked = false
    private var busy = false
    private var initialized = false
    private var ready = false
    fun age(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("age_band", -1)
    fun minor(context: Context) = age(context) in 0..1
    fun allowed(context: Context) = !AdManager.isSuppressed(context) && AdvertisingPolicy.canRequest(age(context) >= 0,
        ready && initialized && UserMessagingPlatform.getConsentInformation(context).canRequestAds(),
        PremiumManager.isPremium(context), AdManager.isTutorial(context))

    fun gather(activity: Activity, done: () -> Unit) {
        if (busy || activity.isFinishing || activity.isDestroyed || AdManager.isSuppressed(activity)) return
        if (PremiumManager.isPremium(activity)) { done(); return }
        if (checked) { done(); return }
        busy = true
        if (age(activity) < 0) {
            AlertDialog.Builder(activity).setTitle(R.string.ads_age_title)
                .setItems(arrayOf(activity.getString(R.string.ads_age_under13),
                    activity.getString(R.string.ads_age_teen), activity.getString(R.string.ads_age_adult))) { _, choice ->
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("age_band", choice).apply()
                    requestConsent(activity, done)
                }.setNegativeButton(R.string.common_btn_skip) { _, _ -> busy = false; checked = true; done() }
                .setOnCancelListener { busy = false; checked = true; done() }.show()
        } else requestConsent(activity, done)
    }

    private fun requestConsent(activity: Activity, done: () -> Unit) {
        if (!canContinue(activity)) { busy = false; return }
        val isMinor = minor(activity)
        val config = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(if (isMinor) RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE else RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED)
            .setTagForUnderAgeOfConsent(if (isMinor) RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE else RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
            .setMaxAdContentRating(if (isMinor) RequestConfiguration.MAX_AD_CONTENT_RATING_G else RequestConfiguration.MAX_AD_CONTENT_RATING_T)
        MobileAds.setRequestConfiguration(config.build())
        val info = UserMessagingPlatform.getConsentInformation(activity)
        fun finish() {
            busy = false
            if (!canContinue(activity)) { checked = false; return }
            checked = true
            ready = info.canRequestAds()
            if (!ready || PremiumManager.isPremium(activity)) { done(); return }
            if (!initialized) {
                MobileAds.initialize(activity.applicationContext) {
                    initialized = true
                    activity.runOnUiThread { if (canContinue(activity)) done() }
                }
            } else done()
        }
        info.requestConsentInfoUpdate(activity,
            ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(isMinor).build(),
            {
                if (!canContinue(activity)) { busy = false; return@requestConsentInfoUpdate }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { finish() }
            }, { finish() })
    }

    fun showOptions(activity: Activity) {
        if (AdManager.isSuppressed(activity)) return
        AdManager.invalidate()
        ready = false
        val info = UserMessagingPlatform.getConsentInformation(activity)
        if (info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                ready = info.canRequestAds()
                AdManager.refreshAdState(activity)
            }
        } else {
            checked = false
            // Allow users who skipped the neutral age question to answer it later.
            gather(activity) { AdManager.refreshAdState(activity) }
        }
    }

    private fun canContinue(activity: Activity): Boolean =
        !activity.isFinishing && !activity.isDestroyed && !AdManager.isSuppressed(activity) &&
            ((activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState
                ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) != false)
}
