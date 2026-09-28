package com.example.mqttpanelcraft.tutorial

import android.content.Context
import org.json.JSONObject

object TutorialSessionStore {
    private fun prefs(context: Context) = context.getSharedPreferences("TutorialV2", Context.MODE_PRIVATE)
    fun currentProjectId(context: Context): String? = prefs(context).getString("project", null)
    fun contains(context: Context, projectId: String): Boolean = prefs(context).contains("session_$projectId")
    fun load(context: Context, projectId: String): TutorialSession {
        val raw = prefs(context).getString("session_$projectId", null)
        return try {
            val json = JSONObject(raw ?: "{}")
            val roles = mutableMapOf<String, Int>()
            val map = json.optJSONObject("roles") ?: JSONObject()
            map.keys().forEach { key -> map.optInt(key).takeIf { it > 0 }?.let { roles[key] = it } }
            TutorialSession(projectId,
                TutorialStep.entries.firstOrNull { it.name == json.optString("step") } ?: TutorialStep.WELCOME,
                roles, json.optBoolean("satisfied"), json.optBoolean("finished"))
        } catch (_: Exception) { TutorialSession(projectId) }
    }
    fun save(context: Context, session: TutorialSession) {
        val roles = JSONObject()
        session.roles.forEach { (key, id) -> roles.put(key, id) }
        val json = JSONObject().put("step", session.step.name).put("roles", roles)
            .put("satisfied", session.satisfied).put("finished", session.finished).toString()
        prefs(context).edit().putString("project", session.projectId)
            .putString("session_${session.projectId}", json).apply()
    }
}
