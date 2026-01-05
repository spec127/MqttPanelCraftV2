package com.example.mqttpanelcraft.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private val logBuffer = StringBuffer()
    
    fun log(tag: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val line = "$timestamp [$tag] $message"
        Log.d(tag, message)
        synchronized(logBuffer) {
            logBuffer.append(line).append("\n")
        }
    }
    
    fun getLogs(): String = synchronized(logBuffer) { logBuffer.toString() }
    
    fun clear() = synchronized(logBuffer) { logBuffer.setLength(0) }
}
