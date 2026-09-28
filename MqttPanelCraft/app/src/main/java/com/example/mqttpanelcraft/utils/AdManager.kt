package com.example.mqttpanelcraft.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import com.example.mqttpanelcraft.BuildConfig
import com.example.mqttpanelcraft.DashboardActivity
import com.example.mqttpanelcraft.data.ProjectRepository
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.*
import com.google.android.gms.ads.rewarded.*
import java.lang.ref.WeakReference

object AdManager {
    private val bannerId = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/6300978111" else "ca-app-pub-4344043793626988/3938962153"
    private val interstitialId = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/1033173712" else "ca-app-pub-4344043793626988/5500182186"
    private val rewardedId = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/5224354917" else "ca-app-pub-4344043793626988/4187100512"
    private data class Banner(val owner: WeakReference<Activity>, val container: WeakReference<FrameLayout>,
        val fab: WeakReference<View>?, val ad: AdView)
    private val banners = mutableListOf<Banner>()
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private val session = AdvertisingSession()
    private val generation get() = session.generation
    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var showing = false
    private var naturalReturn = false
    private val cooldown = InterstitialCooldown(SystemClock.elapsedRealtime())

    fun isTutorial(context: Context): Boolean {
        var current = context
        while (current is ContextWrapper && current !is Activity) {
            val base = current.baseContext
            if (base === current) return false
            current = base
        }
        val activity = current as? Activity ?: return false
        if (activity.intent?.getBooleanExtra(com.example.mqttpanelcraft.ProjectViewActivity.EXTRA_SHOW_TUTORIAL, false) == true) return true
        val id = activity.intent?.getStringExtra("PROJECT_ID") ?: return false
        if (com.example.mqttpanelcraft.tutorial.TutorialSessionStore.contains(activity, id)) return true
        return ProjectRepository.getProjectById(id)?.let { DemoBroker.isLocal(it.broker) } == true
    }
    fun isSuppressed(context: Context) = session.tutorialActive || isTutorial(context)
    fun enterTutorial() {
        if (session.enterTutorial()) clearCachedAds()
        naturalReturn = false
    }
    fun onScreenResumed(activity: Activity) {
        if (isTutorial(activity)) enterTutorial() else session.leaveTutorial()
    }
    private fun allowed(context: Context) = !isSuppressed(context) && AdPrivacy.allowed(context) &&
        (context !is Activity || (!context.isFinishing && !context.isDestroyed))
    fun initialize(context: Context) { /* Initialization belongs exclusively to AdPrivacy. */ }
    fun setDisabled(disabled: Boolean, context: Context) { PremiumManager.setDevSkipAds(context, disabled) }
    private fun request(context: Context): AdRequest {
        val builder = AdRequest.Builder()
        if (AdPrivacy.minor(context)) builder.addNetworkExtrasBundle(AdMobAdapter::class.java,
            Bundle().apply { putString("npa", "1") })
        return builder.build()
    }

    fun loadBannerAd(activity: Activity, container: FrameLayout, fab: View? = null) {
        val old = banners.firstOrNull { it.container.get() === container }
        if (old != null) {
            if (allowed(activity)) return
            remove(old)
        }
        if (!allowed(activity)) { container.visibility = View.GONE; fab?.translationY = 0f; return }
        val ad = AdView(activity)
        ad.adUnitId = bannerId
        val width = (activity.resources.displayMetrics.widthPixels / activity.resources.displayMetrics.density).toInt()
        ad.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width))
        val slot = Banner(WeakReference(activity), WeakReference(container), fab?.let { WeakReference(it) }, ad)
        banners.add(slot)
        val expected = generation
        container.visibility = View.GONE
        ad.adListener = object : AdListener() {
            override fun onAdLoaded() {
                if (!session.accepts(expected) || slot !in banners || !allowed(activity)) { remove(slot); return }
                container.removeAllViews()
                container.addView(ad)
                container.visibility = View.VISIBLE
                fab?.translationY = -(ad.adSize?.getHeightInPixels(activity) ?: 0).toFloat()
            }
            override fun onAdFailedToLoad(error: LoadAdError) { remove(slot) }
        }
        ad.loadAd(request(activity))
    }

    private fun remove(slot: Banner) {
        if (!banners.remove(slot)) return
        slot.ad.destroy()
        slot.container.get()?.let { it.removeAllViews(); it.visibility = View.GONE }
        slot.fab?.get()?.translationY = 0f
    }
    fun release(activity: Activity) { banners.filter { it.owner.get() === activity }.toList().forEach { remove(it) } }
    fun invalidate() {
        session.invalidate()
        clearCachedAds()
    }
    private fun clearCachedAds() {
        banners.toList().forEach { remove(it) }
        interstitial = null; rewarded = null
        loadingInterstitial = false; loadingRewarded = false
    }
    fun refreshAdState(context: Context) { if (!allowed(context)) invalidate() }

    fun loadInterstitial(context: Context) {
        if (!allowed(context) || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        val expected = generation
        InterstitialAd.load(context, interstitialId, request(context), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                if (!session.accepts(expected)) return
                loadingInterstitial = false
                if (allowed(context)) interstitial = ad
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                if (session.accepts(expected)) { loadingInterstitial = false; interstitial = null }
            }
        })
    }
    fun markNaturalReturn() { if (!session.tutorialActive) naturalReturn = true }
    fun onDashboardVisible(activity: DashboardActivity) {
        val eligibleTransition = naturalReturn
        naturalReturn = false
        if (eligibleTransition && cooldown.eligible(SystemClock.elapsedRealtime())) {
            showInterstitialAtBreak(activity)
        }
        loadInterstitial(activity)
    }
    // Legacy call sites (export / former timers) must never interrupt an operation.
    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit = {}) { onAdClosed() }
    private fun showInterstitialAtBreak(activity: Activity) {
        if (!allowed(activity) || showing || !activity.hasWindowFocus()) return
        val ad = interstitial ?: return
        interstitial = null
        showing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                cooldown.shown(SystemClock.elapsedRealtime())
            }
            override fun onAdDismissedFullScreenContent() { showing = false }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { showing = false }
        }
        ad.show(activity)
    }
    fun loadRewarded(context: Context) {
        if (!allowed(context) || rewarded != null || loadingRewarded) return
        loadingRewarded = true
        val expected = generation
        RewardedAd.load(context, rewardedId, request(context), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                if (!session.accepts(expected)) return
                loadingRewarded = false
                if (allowed(context)) rewarded = ad
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                if (session.accepts(expected)) { loadingRewarded = false; rewarded = null }
            }
        })
    }
    fun isRewardedReady() = !session.tutorialActive && rewarded != null && !showing
    fun showRewarded(activity: Activity, onReward: () -> Unit, onClosed: () -> Unit) {
        if (isSuppressed(activity) || PremiumManager.isPremium(activity)) { onReward(); onClosed(); return }
        val ad = rewarded
        if (!allowed(activity) || showing || ad == null) { onClosed(); return }
        rewarded = null
        showing = true
        var closed = false
        fun closeOnce() { if (!closed) { closed = true; showing = false; onClosed() } }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { closeOnce() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { closeOnce() }
        }
        var granted = false
        ad.show(activity) { if (!granted) { granted = true; onReward() } }
    }
}
