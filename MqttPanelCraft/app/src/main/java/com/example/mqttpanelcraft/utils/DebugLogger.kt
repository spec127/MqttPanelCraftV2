package com.example.mqttpanelcraft.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val TAG = "MqttPanel"
    private val logBuffer = StringBuffer()
    private val listeners = mutableListOf<(String) -> Unit>()
    
    fun log(tag: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val fullMsg = "$timestamp [$tag] $message"
        Log.d(TAG, fullMsg) // Changed to use TAG and fullMsg
        synchronized(logBuffer) {
            logBuffer.append(fullMsg).append("\n") // Changed to use fullMsg
        }
        notifyListeners(fullMsg) // Trigger listeners
    }
    
    fun getLogs(): String = synchronized(logBuffer) { logBuffer.toString() }
    

    fun clear() = synchronized(logBuffer) { logBuffer.setLength(0) }

    fun observe(callback: (String) -> Unit) {
        listeners.add(callback)
    }

    private fun notifyListeners(msg: String) {
        listeners.forEach { it(msg) }
    }
}
