package com.example.mqttpanelcraft

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.eclipse.paho.client.mqttv3.MqttClient
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object MqttRepository {
    var mqttClient: MqttClient? = null
    var account: String = ""
    var project: String = ""
    
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
    }

    fun addLog(message: String, timestamp: String) {
        val logEntry = "[$timestamp] $message"
        _logHistory.add(logEntry)
        _logs.postValue(ArrayList(_logHistory))
    }

    fun processMessage(topic: String?, payload: String, timestamp: String) {
        if (topic == null) return
        
        addLog("RX [$topic]: $payload", timestamp)

        try {
            val parts = topic.split("/")
            // Expected: account/project/type/id
            if (parts.size >= 4) {
                val type = parts[2]
                
                when (type) {
                    "text" -> {
                        _displayItem.postValue(LogItem.Text(payload, timestamp))
                    }
                    "led" -> {
                        val isOn = payload.contains("1") || payload.contains("true")
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
                                addLog("Image reassembled for $topic", timestamp)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            addLog("Error processing message: ${e.message}", timestamp)
        }
    }
    // Connection Status
    private val _connectionStatus = MutableLiveData<Int>(0) // 0:Connecting/Gray, 1:Connected/Green, 2:Failed/Red
    val connectionStatus: LiveData<Int> = _connectionStatus

    fun setStatus(status: Int) {
        _connectionStatus.postValue(status)
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
