package com.example.mqttpanelcraft_beta.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

object CrashLogger {

    fun logError(context: Context, errorTitle: String, e: Throwable) {
        // 1. Write to file
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val logContent = "Time: ${Date()}\nTitle: $errorTitle\nError: ${e.message}\nStack Trace:\n$stackTrace\n--------------------------------\n"
            
            val file = File(context.getExternalFilesDir(null), "crash_log.txt")
            file.appendText(logContent)
            
            // 2. Show Dialog
            showErrorDialog(context, errorTitle, "${e.message}\n\nLog saved to: ${file.absolutePath}")
            
        } catch (ioe: Exception) {
            ioe.printStackTrace()
            showErrorDialog(context, "Logging Failed", "Could not write to log file.\nOriginal Error: ${e.message}")
        }
    }

    private fun showErrorDialog(context: Context, title: String, message: String) {
        try {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        } catch (dialogEx: Exception) {
            dialogEx.printStackTrace()
        }
    }
}
