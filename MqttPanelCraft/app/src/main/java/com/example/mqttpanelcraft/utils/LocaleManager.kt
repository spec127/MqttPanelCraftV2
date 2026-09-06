package com.example.mqttpanelcraft.utils

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Manages App-wide Locale Settings.
 * Uses AppCompat application locales so Android 13's per-app language setting and the in-app
 * selector share one source of truth.
 */
object LocaleManager {

    private const val PREFS_NAME = "AppSettings"
    private const val KEY_LANGUAGE = "language_code"
    private const val KEY_PLATFORM_MIGRATED = "platform_locale_migrated"
    
    // Constant codes
    const val CODE_AUTO = "auto"
    const val CODE_EN = "en"
    const val CODE_ZH = "zh-TW"
    const val CODE_CN = "zh-CN"
    private const val LEGACY_CODE_ZH = "zh"

    /**
     * Set the language and save to preferences.
     * Returns the new Context (though for Activities, recreation is usually needed).
     */
    fun setLocale(context: Context, languageCode: String) {
        val normalizedCode = normalizeCode(languageCode)
        persistLanguage(context, normalizedCode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_PLATFORM_MIGRATED, true).apply()
        }
        AppCompatDelegate.setApplicationLocales(toLocaleList(normalizedCode))
    }

    /**
     * Called from BaseActivity.attachBaseContext to apply the persisted language.
     */
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_LANGUAGE, CODE_AUTO) ?: CODE_AUTO
        val normalized = normalizeCode(stored)
        if (stored != normalized) persistLanguage(context, normalized)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val platformCode = codeFromLocaleList(AppCompatDelegate.getApplicationLocales())
            val migrated = prefs.getBoolean(KEY_PLATFORM_MIGRATED, false)
            if (!migrated && platformCode == CODE_AUTO && normalized != CODE_AUTO) {
                AppCompatDelegate.setApplicationLocales(toLocaleList(normalized))
            } else {
                persistLanguage(context, platformCode)
            }
            prefs.edit().putBoolean(KEY_PLATFORM_MIGRATED, true).apply()
        } else {
            AppCompatDelegate.setApplicationLocales(toLocaleList(normalized))
        }
    }

    /**
     * Get the currently selected language code (e.g. "en", "zh", "auto").
     */
    fun getLanguageCode(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return codeFromLocaleList(AppCompatDelegate.getApplicationLocales())
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return normalizeCode(prefs.getString(KEY_LANGUAGE, CODE_AUTO) ?: CODE_AUTO)
    }

    private fun persistLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    private fun normalizeCode(code: String): String {
        return when (code) {
            CODE_AUTO -> CODE_AUTO
            CODE_EN -> CODE_EN
            LEGACY_CODE_ZH, CODE_ZH -> CODE_ZH
            CODE_CN -> CODE_CN
            else -> CODE_AUTO
        }
    }

    private fun toLocaleList(code: String): LocaleListCompat =
            if (code == CODE_AUTO) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(code)

    private fun codeFromLocaleList(locales: LocaleListCompat): String {
        val locale = locales.get(0) ?: return CODE_AUTO
        return when (locale.language) {
            "en" -> CODE_EN
            "zh" -> if (locale.country.equals("CN", true) || locale.script.equals("Hans", true)) CODE_CN else CODE_ZH
            else -> CODE_AUTO
        }
    }
}
