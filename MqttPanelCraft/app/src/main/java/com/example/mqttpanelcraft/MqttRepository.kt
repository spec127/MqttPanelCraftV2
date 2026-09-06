package com.example.mqttpanelcraft

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import org.eclipse.paho.client.mqttv3.MqttClient
import com.example.mqttpanelcraft.mqtt.MqttConnectionState
import com.example.mqttpanelcraft.mqtt.MqttSnapshot
import com.example.mqttpanelcraft.mqtt.MqttSnapshotCache

object MqttRepository {
    private var applicationContext: Context? = null
    var mqttClient: MqttClient? = null
    var account: String = ""
    var project: String = ""

    fun initializeContext(context: Context) {
        applicationContext = context.applicationContext
    }

    // Log history (Text only)
    private val _logHistory = Collections.synchronizedList(mutableListOf<String>())
    private val _logs = MutableLiveData<List<String>>()
    val logs: LiveData<List<String>> = _logs

    // Latest Display Item (Rich Content)
    private val _displayItem = MutableLiveData<LogItem>()
    val displayItem: LiveData<LogItem> = _displayItem

    // Image Buffering: Topic -> (Index -> Data)
    private val imageBuffer = ConcurrentHashMap<String, MutableMap<Int, String>>()
    private val imageTotals = ConcurrentHashMap<String, Int>()

    fun initialize(client: MqttClient, acc: String, proj: String) {
        mqttClient = client
        account = acc
        project = proj
        clear()
    }

    fun clear() {
        _logHistory.clear()
        _logs.postValue(emptyList())
        imageBuffer.clear()
        imageTotals.clear()
        clearSessionState()
    }

    fun addLog(message: String, timestamp: String) {
        val logEntry = "[$timestamp] $message"
        _logHistory.add(logEntry)
        _logs.postValue(ArrayList(_logHistory))
    }

    data class RawMessage(val topic: String, val payload: String)
    // private val _messageReceived = SingleLiveEvent<RawMessage>() // Removed unused/unresolved
    // Let's stick to MutableLiveData for simplicity, but beware of multiple observers if not
    // handled carefully.
    // Ideally use a SharedFlow but sticking to LiveData as per existing code style.
    private val _latestMessage = MutableLiveData<RawMessage>()
    val latestMessage: LiveData<RawMessage> = _latestMessage

    // Multi-Project Support
    var activeProjectId: String? = null
    private val cachedStates = ConcurrentHashMap<String, String>() // Topic -> Payload
    private val snapshotCache = MqttSnapshotCache()
    private val uiDetachedAt = ConcurrentHashMap<String, Long>()
    private val clockDeadlines = ConcurrentHashMap<Int, Long>()

    fun getTopicState(topic: String): String? {
        return cachedStates[topic]
    }

    fun markUiDetached(projectId: String) {
        uiDetachedAt[projectId] = snapshotCache.currentSequence()
    }

    fun consumeBackgroundSnapshots(projectId: String): List<MqttSnapshot> {
        val after = uiDetachedAt[projectId] ?: snapshotCache.currentSequence()
        val result = snapshotCache.since(after)
        uiDetachedAt[projectId] = snapshotCache.currentSequence()
        return result
    }

    fun clearSessionState() {
        cachedStates.clear()
        snapshotCache.clear()
        uiDetachedAt.clear()
        clockDeadlines.clear()
    }

    fun updateClockDeadlines(values: Map<Int, Long>) {
        clockDeadlines.clear()
        clockDeadlines.putAll(values)
    }

    fun getClockDeadline(componentId: Int): Long? = clockDeadlines[componentId]

    // Listener Interface for Zero-Loss Message Handling
    interface MessageListener {
        fun onMessageReceived(topic: String, payload: String)
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<MessageListener>()

    fun registerListener(listener: MessageListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun unregisterListener(listener: MessageListener) {
        listeners.remove(listener)
    }

    fun processMessage(topic: String?, payload: String, timestamp: String) {
        if (topic == null) return

        // Notify Listeners (Direct Call - Background Thread)
        for (listener in listeners) {
            try {
                listener.onMessageReceived(topic, payload)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _latestMessage.postValue(RawMessage(topic, payload))

        // v29: Update Cache
        cachedStates[topic] = payload
        snapshotCache.put(topic, payload)

        // v29: Log Filtering & v36: Relative Path Formatting
        var shouldLog = true
        var displayTopic = topic

        if (activeProjectId != null) {
            val parts = topic.split("/")
            // Topic format: name/id/type/index/direction
            // length check: project/id/type/index/dir >= 5
            if (parts.size >= 5) {
                val msgProjectId = parts[1]
                if (msgProjectId != activeProjectId) {
                    shouldLog = false
                } else {
                    // It IS our project, format as relative path: [type/index/dir]
                    // parts[0]=name, [1]=id, [2]=type, [3]=index, [4]=dir
                    displayTopic = "${parts[2]}/${parts[3]}/${parts[4]}"
                }
            } else if (parts.size >= 2) {
                // Fallback for partial topics
                val msgProjectId = parts[1]
                if (msgProjectId != activeProjectId) shouldLog = false
            }
        }

        if (shouldLog) {
            addLog("RX [$displayTopic]: $payload", timestamp)
        }

        try {
            val parts = topic.split("/")
            // Expected: name/id/type/index/dir
            if (parts.size >= 5) {
                // Determine if this message belongs to active project for Rich UI (DisplayItem)
                val msgProjectId = parts[1]
                if (activeProjectId != null && msgProjectId != activeProjectId) {
                    return // Do not update Rich UI for background projects
                }

                val type = parts[2]

                when (type) {
                    "text" -> {
                        _displayItem.postValue(LogItem.Text(payload, timestamp))
                    }
                    "led" -> {
                        val isOn =
                                payload.contains("1") ||
                                        payload.contains("true") ||
                                        payload.equals("ON", true)
                        _displayItem.postValue(LogItem.Led(isOn, timestamp))
                    }
                    "image" -> {
                        // Protocol: index|total|data
                        val dataParts = payload.split("|", limit = 3)
                        if (dataParts.size == 3) {
                            val index = dataParts[0].toInt()
                            val total = dataParts[1].toInt()
                            val data = dataParts[2]

                            val buffer = imageBuffer.getOrPut(topic) { ConcurrentHashMap() }
                            buffer[index] = data
                            imageTotals[topic] = total

                            if (buffer.size == total) {
                                // Reassemble
                                val sb = StringBuilder()
                                for (i in 1..total) {
                                    sb.append(buffer[i] ?: "")
                                }
                                val fullBase64 = sb.toString()
                                _displayItem.postValue(LogItem.Image(fullBase64, timestamp))

                                // Clean up
                                imageBuffer.remove(topic)
                                imageTotals.remove(topic)
                                if (shouldLog) {
                                    addLog(
                                        applicationContext?.getString(R.string.mqtt_log_image_reassembled, topic)
                                            ?: topic,
                                        timestamp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Only log errors for active project or general errors
            if (shouldLog) {
                addLog(
                    applicationContext?.getString(R.string.mqtt_log_message_processing_error, e.message.orEmpty())
                        ?: e.message.orEmpty(),
                    timestamp
                )
            }
        }
    }
    // Connection Status
    private val _connectionStatus =
            MutableLiveData<Int>(0) // 0:Connecting/Gray, 1:Connected/Green, 2:Failed/Red
    val connectionStatus: LiveData<Int> = _connectionStatus
    private val _connectionState = MutableLiveData(MqttConnectionState.IDLE)
    val connectionState: LiveData<MqttConnectionState> = _connectionState

    fun setConnectionState(state: MqttConnectionState) {
        _connectionState.postValue(state)
        _connectionStatus.postValue(
            when (state) {
                MqttConnectionState.CONNECTED -> MqttStatus.CONNECTED
                MqttConnectionState.FAILED -> MqttStatus.FAILED
                else -> MqttStatus.CONNECTING
            }
        )
    }

    fun setStatus(status: Int) {
        setConnectionState(
            when (status) {
                MqttStatus.CONNECTED -> MqttConnectionState.CONNECTED
                MqttStatus.FAILED -> MqttConnectionState.FAILED
                else -> MqttConnectionState.CONNECTING
            }
        )
    }
}

sealed class LogItem {
    data class Text(val content: String, val timestamp: String) : LogItem()
    data class Image(val base64: String, val timestamp: String) : LogItem()
    data class Led(val isOn: Boolean, val timestamp: String) : LogItem()
}

// Status Constants
object MqttStatus {
    const val CONNECTING = 0
    const val CONNECTED = 1
    const val FAILED = 2
}
