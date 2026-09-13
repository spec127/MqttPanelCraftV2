package com.example.mqttpanelcraft.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log

class IdleAdController(
    private val activity: Activity,
    private val onAdClosed: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentIntervalIndex = 0
    private var isRunning = false

    private val idleRunnable = Runnable {
        showAdAndScheduleNext()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        currentIntervalIndex = 0 
        scheduleNext(IDLE_INTERVALS_SECONDS.first())
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(idleRunnable)
    }

    fun onUserInteraction() {
        if (isRunning) {
            // Reset timer for CURRENT interval
            handler.removeCallbacks(idleRunnable)
            scheduleNext(currentIntervalSeconds())
        }
    }

    private fun scheduleNext(delaySeconds: Long) {
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed(idleRunnable, delaySeconds * 1000)
        Log.d("IdleAd", "Scheduled ad in ${delaySeconds}s")
    }

    private fun showAdAndScheduleNext() {
        if (!isRunning) return

        com.example.mqttpanelcraft.utils.AdManager.showInterstitial(activity) {
            // On Ad Closed
            if (currentIntervalIndex < IDLE_INTERVALS_SECONDS.lastIndex) {
                currentIntervalIndex++
            }
            scheduleNext(currentIntervalSeconds())

            onAdClosed()
        }
    }

    private fun currentIntervalSeconds(): Long =
        IDLE_INTERVALS_SECONDS.getOrElse(currentIntervalIndex) { IDLE_INTERVALS_SECONDS.last() }

    companion object {
        // Isolated idle-ad policy: first wait 3 min, then 5 min, then stay at 10 min.
        private val IDLE_INTERVALS_SECONDS = listOf(180L, 300L, 600L)
    }
}
