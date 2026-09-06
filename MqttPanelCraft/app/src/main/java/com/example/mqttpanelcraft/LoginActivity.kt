package com.example.mqttpanelcraft

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.File
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.example.mqttpanelcraft.utils.CrashLogger

class LoginActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val btnGuest = findViewById<MaterialButton>(R.id.btnGuest)
        val btnGoogle = findViewById<MaterialButton>(R.id.btnGoogle)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        val btnSettings = findViewById<MaterialButton>(R.id.btnSettings)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome) // Need to add ID to XML

        // Secret: Long click on "Welcome Back" to view logs
        tvWelcome?.setOnLongClickListener {
            showCrashLogs()
            true
        }

        // Login -> DashboardActivity (Mock flow)
        // Login -> DashboardActivity (Mock flow)
        btnLogin.setOnClickListener {
            try {
                // In a real app, perform validation here
                val intent = Intent(this, DashboardActivity::class.java)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                CrashLogger.logError(this, getString(R.string.crash_login_failed), e)
            }
        }

        // Guest -> DashboardActivity
        btnGuest.setOnClickListener {
            try {
                val intent = Intent(this, DashboardActivity::class.java)
                intent.putExtra("IS_GUEST", true)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                CrashLogger.logError(this, getString(R.string.crash_guest_login_failed), e)
            }
        }

        // Google -> Mock Toast
        btnGoogle.setOnClickListener {
            try {
                Toast.makeText(this, R.string.auth_google_demo, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                CrashLogger.logError(this, getString(R.string.crash_google_sign_in_failed), e)
            }
        }

        // Register -> RegisterActivity
        tvRegister.setOnClickListener {
            try {
                val intent = Intent(this, RegisterActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                CrashLogger.logError(this, getString(R.string.crash_register_navigation_failed), e)
            }
        }

        // Settings -> BottomSheet
        // Settings -> BottomSheet (Removed)
        btnSettings.visibility = View.GONE
        btnSettings.setOnClickListener {
            // No-op
        }
    }

    private fun showCrashLogs() {
        try {
            val file = File(getExternalFilesDir(null), "crash_log.txt")
            if (!file.exists()) {
                Toast.makeText(this, R.string.crash_no_logs, Toast.LENGTH_SHORT).show()
                return
            }
            val content = file.readText()
            
            AlertDialog.Builder(this)
                .setTitle(R.string.crash_logs_title)
                .setMessage(content.takeLast(2000)) // Show last 2000 chars
                .setPositiveButton(R.string.common_btn_close, null)
                .setNeutralButton(R.string.common_btn_copy) { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Crash Log", content)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, R.string.crash_logs_copied, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.common_btn_clear) { _, _ ->
                    file.delete()
                    Toast.makeText(this, R.string.crash_logs_cleared, Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(
                            this,
                            getString(R.string.crash_logs_error, e.message),
                            Toast.LENGTH_SHORT
                    )
                    .show()
        }
    }

}
