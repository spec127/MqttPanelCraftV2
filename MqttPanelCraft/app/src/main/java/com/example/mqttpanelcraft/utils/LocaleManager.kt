package com.example.mqttpanelcraft.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Manages App-wide Locale Settings.
 * Supports: "en" (English), "zh" (Traditional Chinese), "auto" (System Default).
 */
object LocaleManager {

    private const val PREFS_NAME = "AppSettings"
    private const val KEY_LANGUAGE = "language_code"
    
    // Constant codes
    const val CODE_AUTO = "auto"
    const val CODE_EN = "en"
    const val CODE_ZH = "zh"
    const val CODE_CN = "zh-CN"

    /**
     * Set the language and save to preferences.
     * Returns the new Context (though for Activities, recreation is usually needed).
     */
    fun setLocale(context: Context, languageCode: String): Context {
        persistLanguage(context, languageCode)
        return updateResources(context, languageCode)
    }

    /**
     * Called from BaseActivity.attachBaseContext to apply the persisted language.
     */
    fun onAttach(context: Context): Context {
        val lang = getPersistedLanguage(context)
        return updateResources(context, lang)
    }

    /**
     * Get the currently selected language code (e.g. "en", "zh", "auto").
     */
    fun getLanguageCode(context: Context): String {
        return getPersistedLanguage(context)
    }

    private fun getPersistedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, CODE_AUTO) ?: CODE_AUTO
    }

    private fun persistLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = getLocaleForCode(language)
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)
        
        // Apply Locale to Configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        // For API 24+, we must use createConfigurationContext
        return context.createConfigurationContext(config)
    }
    
    /**
     * Resolves the String code to a real Locale object.
     * Handles "auto" by returning the System's default locale.
     */
    private fun getLocaleForCode(code: String): Locale {
        return when (code) {
            CODE_ZH -> Locale.TRADITIONAL_CHINESE
            CODE_CN -> Locale.SIMPLIFIED_CHINESE
            CODE_EN -> Locale.ENGLISH
            CODE_AUTO -> getSystemLocale()
            else -> Locale.ENGLISH // Fallback
        }
    }

    private fun getSystemLocale(): Locale {
        val localeList = LocaleList.getDefault()
        return localeList[0]
    }
}
