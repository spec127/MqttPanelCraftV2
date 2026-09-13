package com.example.mqttpanelcraft.utils

import android.content.Context

object OnboardingCoordinator {
    private const val PREFS_NAME = "AppSettings"
    private const val KEY_LANGUAGE_GATE_DONE = "language_gate_done"
    private const val KEY_TUTORIAL_SEEDED = "tutorial_v1_done"

    fun migrateExistingInstall(context: Context, hadProjectsFile: Boolean) {
        val prefs = prefs(context)
        if (prefs.contains(KEY_LANGUAGE_GATE_DONE)) return
        if (!hadProjectsFile) return
        prefs.edit()
            .putBoolean(KEY_LANGUAGE_GATE_DONE, true)
            .putBoolean(KEY_TUTORIAL_SEEDED, true)
            .apply()
    }

    fun isLanguageGateDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LANGUAGE_GATE_DONE, false)

    fun markLanguageGateDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_LANGUAGE_GATE_DONE, true).apply()
    }

    fun isTutorialSeeded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TUTORIAL_SEEDED, false)

    fun markTutorialSeeded(context: Context) {
        prefs(context).edit().putBoolean(KEY_TUTORIAL_SEEDED, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
