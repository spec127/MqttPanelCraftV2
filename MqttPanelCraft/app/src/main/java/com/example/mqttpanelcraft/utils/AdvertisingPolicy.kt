package com.example.mqttpanelcraft.utils

object AdvertisingPolicy {
    fun canRequest(ageKnown: Boolean, consentReady: Boolean, premium: Boolean, tutorial: Boolean): Boolean =
        ageKnown && consentReady && !premium && !tutorial
}

/** Invalidates requests from the previous screen, including callbacks arriving after the tutorial. */
class AdvertisingSession {
    var tutorialActive = false
        private set
    var generation = 0
        private set

    fun enterTutorial(): Boolean {
        if (tutorialActive) return false
        tutorialActive = true
        invalidate()
        return true
    }
    fun leaveTutorial() { tutorialActive = false }
    fun invalidate() { generation++ }
    fun accepts(requestGeneration: Int) = !tutorialActive && generation == requestGeneration
}
class InterstitialCooldown(private var anchor: Long) {
    private var interval = 0
    private val intervals = longArrayOf(180_000, 300_000, 600_000)
    fun eligible(now: Long) = now - anchor >= intervals[interval]
    fun shown(now: Long) { anchor = now; interval = minOf(interval + 1, intervals.lastIndex) }
}
