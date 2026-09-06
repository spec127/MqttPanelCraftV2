package com.example.mqttpanelcraft.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mqttpanelcraft.MqttRepository
import com.example.mqttpanelcraft.ProjectViewActivity
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.WebViewActivity
import com.example.mqttpanelcraft.data.ProjectRepository
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.model.ProjectType
import com.example.mqttpanelcraft.mqtt.ClockAutomationEngine
import com.example.mqttpanelcraft.mqtt.ClockRuntimeRecord
import com.example.mqttpanelcraft.mqtt.MqttConnectionState
import com.example.mqttpanelcraft.mqtt.MqttSessionClient
import com.example.mqttpanelcraft.utils.TopicHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONArray
import org.json.JSONObject

class MqttSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val clockEngine = ClockAutomationEngine()
    private var client: MqttClient? = null
    private var project: Project? = null
    private var connectJob: Job? = null
    private var clockJob: Job? = null
    private var generation = 0L
    private var runtimeEnabled = true
    private val projectTopics = linkedSetOf<String>()
    private val dynamicTopics = linkedSetOf<String>()
    private var currentConfig: ConnectionConfig? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (project != null && client?.isConnected != true) startConnectLoop(generation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ProjectRepository.initialize(applicationContext)
        createNotificationChannel()
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        getSystemService(ConnectivityManager::class.java).registerNetworkCallback(request, networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return restoreBackgroundSession()
        when (intent.action) {
            MqttSessionClient.ACTION_ACTIVATE -> activateById(intent.getStringExtra(MqttSessionClient.EXTRA_PROJECT_ID))
            MqttSessionClient.ACTION_VISIBILITY -> handleVisibility(intent)
            MqttSessionClient.ACTION_RUNTIME -> handleRuntime(intent)
            MqttSessionClient.ACTION_REFRESH -> refresh(intent.getStringExtra(MqttSessionClient.EXTRA_PROJECT_ID))
            MqttSessionClient.ACTION_PUBLISH -> publish(intent.getStringExtra(MqttSessionClient.EXTRA_TOPIC), intent.getStringExtra(MqttSessionClient.EXTRA_PAYLOAD))
            MqttSessionClient.ACTION_SUBSCRIBE -> subscribeDynamic(intent.getStringExtra(MqttSessionClient.EXTRA_TOPIC))
            MqttSessionClient.ACTION_UNSUBSCRIBE -> unsubscribeDynamic(intent.getStringExtra(MqttSessionClient.EXTRA_TOPIC))
            MqttSessionClient.ACTION_STOP -> stopSession(MqttConnectionState.STOPPED)
        }
        if (project == null) stopSelf(startId)
        return if (project?.keepMqttInBackground == true) START_STICKY else START_NOT_STICKY
    }

    private fun restoreBackgroundSession(): Int {
        val restored = prefs.getString(KEY_ACTIVE_PROJECT, null)?.let(ProjectRepository::getProjectById)
        if (restored?.keepMqttInBackground != true) {
            stopSession(MqttConnectionState.STOPPED)
            return START_NOT_STICKY
        }
        showForeground(restored, MqttConnectionState.RECONNECTING)
        activate(restored, restoring = true)
        return START_STICKY
    }

    private fun activateById(projectId: String?) {
        val requested = projectId?.let(ProjectRepository::getProjectById)
        if (requested == null || requested.broker.isBlank()) {
            startForeground(NOTIFICATION_ID, buildStartingNotification())
            stopSession(MqttConnectionState.FAILED)
            return
        }
        showForeground(requested, MqttConnectionState.CONNECTING)
        activate(requested, restoring = false)
    }

    private fun activate(next: Project, restoring: Boolean) {
        val config = connectionConfig(next)
        val sameConnection = project?.id == next.id && currentConfig == config
        if (!sameConnection) {
            generation++
            connectJob?.cancel()
            disconnectClient()
            if (project?.id != next.id) {
                dynamicTopics.clear()
                MqttRepository.clearSessionState()
            }
        }
        project = next
        currentConfig = config
        MqttRepository.activeProjectId = next.id
        projectTopics.clear()
        projectTopics.addAll(TopicHelper.collectSubscriptionTopics(next))
        if (next.keepMqttInBackground) prefs.edit().putString(KEY_ACTIVE_PROJECT, next.id).apply()
        else prefs.edit().remove(KEY_ACTIVE_PROJECT).apply()
        if (restoring) restoreClockState(next.id)
        clockEngine.configure(next, runtimeEnabled)
        MqttRepository.updateClockDeadlines(clockEngine.deadlines())
        persistClockState()
        startClockLoop()
        if (!sameConnection || client?.isConnected != true) {
            startConnectLoop(generation)
        } else {
            updateState(next, MqttConnectionState.CONNECTED)
            subscribeAll()
        }
    }

    private fun handleVisibility(intent: Intent) {
        if (intent.getStringExtra(MqttSessionClient.EXTRA_PROJECT_ID) != project?.id) return
        if (!intent.getBooleanExtra(MqttSessionClient.EXTRA_VISIBLE, false) && project?.keepMqttInBackground != true) {
            stopSession(MqttConnectionState.STOPPED)
        }
    }

    private fun handleRuntime(intent: Intent) {
        if (intent.getStringExtra(MqttSessionClient.EXTRA_PROJECT_ID) != project?.id) return
        runtimeEnabled = intent.getBooleanExtra(MqttSessionClient.EXTRA_RUNTIME, true)
        project?.let { clockEngine.configure(it, runtimeEnabled) }
        MqttRepository.updateClockDeadlines(clockEngine.deadlines())
        persistClockState()
    }

    private fun refresh(projectId: String?) {
        if (projectId == null || projectId != project?.id) return
        ProjectRepository.getProjectById(projectId)?.let { activate(it, restoring = false) }
    }

    private fun startConnectLoop(expectedGeneration: Long) {
        connectJob?.cancel()
        connectJob = scope.launch {
            val backoff = longArrayOf(1, 2, 5, 10, 30, 60)
            var attempt = 0
            while (isActive && expectedGeneration == generation) {
                val active = project ?: return@launch
                updateState(active, if (attempt == 0) MqttConnectionState.CONNECTING else MqttConnectionState.RECONNECTING)
                try {
                    connectOnce(active, currentConfig ?: connectionConfig(active))
                    return@launch
                } catch (error: MqttException) {
                    log("Connect failed: ${error.message}")
                    if (error.reasonCode.toInt() in setOf(2, 4, 5)) {
                        stopSession(MqttConnectionState.FAILED)
                        return@launch
                    }
                } catch (error: Exception) {
                    log("Connect failed: ${error.message}")
                }
                delay(backoff[minOf(attempt++, backoff.lastIndex)] * 1000L)
            }
        }
    }

    private fun connectOnce(active: Project, config: ConnectionConfig) {
        disconnectClient()
        val nextClient = MqttClient(config.uri, config.clientId, MemoryPersistence())
        client = nextClient
        nextClient.setCallback(object : MqttCallbackExtended {
            override fun connectionLost(cause: Throwable?) {
                log("Connection lost: ${cause?.message}")
                if (project?.id == active.id) startConnectLoop(generation)
            }
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                MqttRepository.processMessage(topic, message?.toString().orEmpty(), time())
            }
            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            override fun connectComplete(reconnect: Boolean, serverURI: String?) = Unit
        })
        val options = MqttConnectOptions().apply {
            isCleanSession = false
            connectionTimeout = 30
            keepAliveInterval = 60
            isAutomaticReconnect = false
            if (config.username.isNotEmpty()) {
                userName = config.username
                password = config.password.toCharArray()
            }
        }
        nextClient.connect(options)
        MqttRepository.mqttClient = nextClient
        updateState(active, MqttConnectionState.CONNECTED)
        subscribeAll()
        log("Connected to ${config.uri}")
    }

    private fun subscribeAll() {
        val activeClient = client ?: return
        if (!activeClient.isConnected) return
        (projectTopics + dynamicTopics).filter(String::isNotBlank).distinct().forEach { topic ->
            try {
                activeClient.subscribe(topic)
                log("Subscribed to $topic")
            } catch (error: Exception) {
                log("Subscribe failed for $topic: ${error.message}")
            }
        }
    }

    private fun subscribeDynamic(topic: String?) {
        val clean = topic?.trim().orEmpty()
        if (clean.isEmpty() || project == null) return
        if (dynamicTopics.add(clean) && client?.isConnected == true) {
            try { client?.subscribe(clean) } catch (error: Exception) { log("Subscribe failed: ${error.message}") }
        }
    }

    private fun unsubscribeDynamic(topic: String?) {
        val clean = topic?.trim().orEmpty()
        if (!dynamicTopics.remove(clean) || client?.isConnected != true) return
        try { client?.unsubscribe(clean) } catch (error: Exception) { log("Unsubscribe failed: ${error.message}") }
    }

    private fun publish(topic: String?, payload: String?) {
        if (topic.isNullOrBlank() || payload == null || client?.isConnected != true || project == null) {
            log("Publish ignored: no active MQTT session")
            return
        }
        scope.launch {
            try {
                client?.publish(topic, MqttMessage(payload.toByteArray()))
                log("TX [$topic]: $payload")
            } catch (error: Exception) {
                log("Publish failed: ${error.message}")
            }
        }
    }

    private fun startClockLoop() {
        clockJob?.cancel()
        clockJob = scope.launch {
            while (isActive) {
                val active = project ?: return@launch
                val result = clockEngine.tick(active, client?.isConnected == true)
                MqttRepository.updateClockDeadlines(clockEngine.deadlines())
                result.events.forEach { publish(it.topic, it.payload) }
                if (result.changed) persistClockState()
                delay(1000L)
            }
        }
    }

    private fun stopSession(finalState: MqttConnectionState) {
        generation++
        connectJob?.cancel()
        clockJob?.cancel()
        persistClockState()
        disconnectClient()
        project = null
        currentConfig = null
        projectTopics.clear()
        dynamicTopics.clear()
        clockEngine.clear()
        prefs.edit().remove(KEY_ACTIVE_PROJECT).apply()
        MqttRepository.activeProjectId = null
        MqttRepository.clearSessionState()
        MqttRepository.setConnectionState(finalState)
        stopForeground(true)
        stopSelf()
    }

    private fun disconnectClient() {
        try { if (client?.isConnected == true) client?.disconnect() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        client = null
        MqttRepository.mqttClient = null
    }

    private fun connectionConfig(active: Project): ConnectionConfig {
        val uri = if (active.broker.startsWith("tcp://")) active.broker else "tcp://${active.broker}:${active.port}"
        val clientId = active.clientId.ifBlank {
            val key = "client_${active.id}"
            prefs.getString(key, null) ?: "MPC_${UUID.randomUUID()}".also { prefs.edit().putString(key, it).apply() }
        }
        return ConnectionConfig(uri, active.username, active.password, clientId)
    }

    private fun showForeground(active: Project, state: MqttConnectionState) {
        MqttRepository.setConnectionState(state)
        startForeground(NOTIFICATION_ID, buildNotification(active, state))
    }

    private fun updateState(active: Project, state: MqttConnectionState) {
        MqttRepository.setConnectionState(state)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(active, state))
    }

    private fun buildNotification(active: Project, state: MqttConnectionState): android.app.Notification {
        val destination = if (active.type == ProjectType.WEBVIEW) WebViewActivity::class.java else ProjectViewActivity::class.java
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val openIntent = Intent(this, destination).putExtra("PROJECT_ID", active.id).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val open = PendingIntent.getActivity(this, 20, openIntent, pendingFlags)
        val stop = PendingIntent.getService(
            this,
            21,
            Intent(this, MqttSessionService::class.java).setAction(MqttSessionClient.ACTION_STOP),
            pendingFlags
        )
        val text = when (state) {
            MqttConnectionState.CONNECTED -> getString(R.string.mqtt_notification_connected)
            MqttConnectionState.RECONNECTING -> getString(R.string.mqtt_notification_reconnecting)
            else -> getString(R.string.mqtt_notification_connecting)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_link)
            .setContentTitle("MqttPanelCraft · ${active.name}")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.mqtt_notification_stop), stop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.mqtt_notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildStartingNotification(): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_link)
            .setContentTitle("MqttPanelCraft")
            .setContentText(getString(R.string.mqtt_notification_connecting))
            .setOnlyAlertOnce(true)
            .build()

    private fun persistClockState() {
        val active = project ?: return
        val array = JSONArray()
        clockEngine.snapshot().forEach { (id, record) ->
            array.put(JSONObject().apply {
                put("id", id)
                put("signature", record.signature)
                put("deadlineAt", record.deadlineAt)
                put("countdownFired", record.countdownFired)
                put("lastScheduleDate", record.lastScheduleDate)
                put("activatedAt", record.activatedAt)
            })
        }
        prefs.edit().putString("clock_${active.id}", array.toString()).apply()
    }

    private fun restoreClockState(projectId: String) {
        val text = prefs.getString("clock_$projectId", null) ?: return
        try {
            val array = JSONArray(text)
            val restored = mutableMapOf<Int, ClockRuntimeRecord>()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                restored[item.getInt("id")] = ClockRuntimeRecord(
                    item.getString("signature"), item.optLong("deadlineAt"), item.optBoolean("countdownFired"),
                    item.optString("lastScheduleDate"), item.optLong("activatedAt")
                )
            }
            clockEngine.restore(projectId, restored)
        } catch (_: Exception) {
            prefs.edit().remove("clock_$projectId").apply()
        }
    }

    private fun log(message: String) = MqttRepository.addLog("Service: $message", time())
    private fun time() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        } catch (_: IllegalArgumentException) {
            // The callback was already removed by the system.
        }
        connectJob?.cancel()
        clockJob?.cancel()
        disconnectClient()
        scope.cancel()
        super.onDestroy()
    }

    private data class ConnectionConfig(val uri: String, val username: String, val password: String, val clientId: String)

    companion object {
        private const val CHANNEL_ID = "MqttServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val PREFS = "MqttSession"
        private const val KEY_ACTIVE_PROJECT = "active_background_project"
    }
}
