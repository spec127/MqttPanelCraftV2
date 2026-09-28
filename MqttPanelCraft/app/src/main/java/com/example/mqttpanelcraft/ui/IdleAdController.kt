package com.example.mqttpanelcraft.ui

import android.app.Activity

/** Kept as a source-compatible facade. Ads are now shown only at natural Dashboard transitions. */
class IdleAdController(private val activity: Activity, private val onAdClosed: () -> Unit) {
    fun start() = Unit
    fun stop() = Unit
    fun onUserInteraction() = Unit
}
