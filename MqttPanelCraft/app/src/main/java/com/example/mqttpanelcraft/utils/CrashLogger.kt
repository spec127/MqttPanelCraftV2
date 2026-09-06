package com.example.mqttpanelcraft.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.mqttpanelcraft.R
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
            showErrorDialog(
                context,
                errorTitle,
                context.getString(R.string.crash_log_saved_to, e.message.orEmpty(), file.absolutePath)
            )
            
        } catch (ioe: Exception) {
            ioe.printStackTrace()
            showErrorDialog(
                context,
                context.getString(R.string.crash_logging_failed),
                context.getString(R.string.crash_log_write_failed, e.message.orEmpty())
            )
        }
    }

    private fun showErrorDialog(context: Context, title: String, message: String) {
        try {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.common_btn_ok, null)
                .show()
        } catch (dialogEx: Exception) {
            dialogEx.printStackTrace()
        }
    }
}
