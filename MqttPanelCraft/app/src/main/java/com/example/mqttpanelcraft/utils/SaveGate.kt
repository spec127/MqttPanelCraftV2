package com.example.mqttpanelcraft.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.CountDownTimer
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.mqttpanelcraft.R

object SaveGate {
    fun show(activity: Activity, save: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (AdManager.isSuppressed(activity) || PremiumManager.isPremium(activity)) { save(); return }
        var completed = false
        var earned = false
        val prefs = activity.getSharedPreferences("PlayBilling", Context.MODE_PRIVATE)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.setup_saving_project).setMessage(R.string.save_choice)
            .setPositiveButton(activity.getString(R.string.setup_continue_countdown, 10), null)
            .setNegativeButton(R.string.common_btn_cancel, null)
            .setNeutralButton(R.string.save_watch_ad, null).create()
        var timer: CountDownTimer? = null
        var observer: DefaultLifecycleObserver? = null
        lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener
        fun cleanup() {
            timer?.cancel()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            observer?.let { (activity as? LifecycleOwner)?.lifecycle?.removeObserver(it) }
        }
        fun finish() {
            if (completed || activity.isFinishing || activity.isDestroyed) return
            completed = true
            cleanup()
            dialog.dismiss()
            save()
        }
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (PremiumManager.isPremium(activity)) activity.runOnUiThread { finish() }
        }
        observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { cleanup(); dialog.dismiss() }
        }
        (activity as? LifecycleOwner)?.lifecycle?.addObserver(observer!!)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        dialog.setOnDismissListener { cleanup() }
        dialog.show()
        val proceed = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        proceed.isEnabled = false
        proceed.setOnClickListener { finish() }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            if (!AdManager.isRewardedReady()) { AdManager.loadRewarded(activity); return@setOnClickListener }
            dialog.hide()
            AdManager.showRewarded(activity, { earned = true }, {
                if (earned || PremiumManager.isPremium(activity)) finish()
                else if (!activity.isFinishing && !activity.isDestroyed) dialog.show()
            })
        }
        timer = object : CountDownTimer(10_000, 1000) {
            override fun onTick(remaining: Long) {
                proceed.text = activity.getString(R.string.setup_continue_countdown, (remaining + 999) / 1000)
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = AdManager.isRewardedReady()
            }
            override fun onFinish() { proceed.setText(R.string.setup_continue); proceed.isEnabled = true }
        }.start()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = AdManager.isRewardedReady()
        AdManager.loadRewarded(activity)
    }
}
