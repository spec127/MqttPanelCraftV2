package com.example.mqttpanelcraft.mqtt

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.mqttpanelcraft.MqttRepository
import com.example.mqttpanelcraft.service.MqttSessionService

object MqttSessionClient {
    const val ACTION_ACTIVATE = "com.example.mqttpanelcraft.mqtt.ACTIVATE"
    const val ACTION_VISIBILITY = "com.example.mqttpanelcraft.mqtt.VISIBILITY"
    const val ACTION_RUNTIME = "com.example.mqttpanelcraft.mqtt.RUNTIME"
    const val ACTION_REFRESH = "com.example.mqttpanelcraft.mqtt.REFRESH"
    const val ACTION_PUBLISH = "com.example.mqttpanelcraft.mqtt.PUBLISH"
    const val ACTION_SUBSCRIBE = "com.example.mqttpanelcraft.mqtt.SUBSCRIBE"
    const val ACTION_UNSUBSCRIBE = "com.example.mqttpanelcraft.mqtt.UNSUBSCRIBE"
    const val ACTION_STOP = "com.example.mqttpanelcraft.mqtt.STOP"
    const val EXTRA_PROJECT_ID = "PROJECT_ID"
    const val EXTRA_VISIBLE = "VISIBLE"
    const val EXTRA_RUNTIME = "RUNTIME"
    const val EXTRA_TOPIC = "TOPIC"
    const val EXTRA_PAYLOAD = "PAYLOAD"

    fun activate(context: Context, projectId: String) {
        val intent = serviceIntent(context, ACTION_ACTIVATE).putExtra(EXTRA_PROJECT_ID, projectId)
        ContextCompat.startForegroundService(context, intent)
    }

    fun setVisible(context: Context, projectId: String, visible: Boolean) =
        send(context, ACTION_VISIBILITY) {
            putExtra(EXTRA_PROJECT_ID, projectId)
            putExtra(EXTRA_VISIBLE, visible)
        }

    fun setRuntime(context: Context, projectId: String, runtime: Boolean) =
        send(context, ACTION_RUNTIME) {
            putExtra(EXTRA_PROJECT_ID, projectId)
            putExtra(EXTRA_RUNTIME, runtime)
        }

    fun refresh(context: Context, projectId: String) =
        send(context, ACTION_REFRESH) { putExtra(EXTRA_PROJECT_ID, projectId) }

    fun publish(context: Context, topic: String, payload: String) =
        send(context, ACTION_PUBLISH) {
            putExtra(EXTRA_TOPIC, topic)
            putExtra(EXTRA_PAYLOAD, payload)
        }

    fun subscribe(context: Context, topic: String) =
        send(context, ACTION_SUBSCRIBE) { putExtra(EXTRA_TOPIC, topic) }

    fun unsubscribe(context: Context, topic: String) =
        send(context, ACTION_UNSUBSCRIBE) { putExtra(EXTRA_TOPIC, topic) }

    fun stop(context: Context) = send(context, ACTION_STOP)

    private fun send(context: Context, action: String, extras: Intent.() -> Unit = {}) {
        if (MqttRepository.activeProjectId == null) return
        context.startService(serviceIntent(context, action).apply(extras))
    }

    private fun serviceIntent(context: Context, action: String) =
        Intent(context, MqttSessionService::class.java).setAction(action)
}
