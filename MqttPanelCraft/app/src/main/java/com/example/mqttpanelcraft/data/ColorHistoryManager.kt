package com.example.mqttpanelcraft.data

import android.content.Context

object ColorHistoryManager {
    private const val PREF_NAME = "global_colors"
    private const val KEY_HISTORY = "history"
    private val DEFAULT_COLORS = listOf("#a573bc", "#2196F3", "#4CAF50", "#FFC107", "#FF5722")

    fun load(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_HISTORY, null)
        return saved?.split(",")?.filter { it.isNotEmpty() } ?: DEFAULT_COLORS
    }

    fun save(context: Context, newColor: String) {
        val current = load(context).toMutableList()
        // Remove if exists to move to top
        current.remove(newColor)
        current.add(0, newColor)
        // Keep max 5
        if (current.size > 5) {
            current.subList(5, current.size).clear()
        }
        
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, current.joinToString(","))
            .apply()
    }
}
