package com.example.mqttpanelcraft

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.mqttpanelcraft.utils.LocaleManager

/**
 * BaseActivity for strict Locale management.
 * All Activities should extend this to ensure language settings persist
 * across configuration changes and restarts.
 */
abstract class BaseActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && android.os.Build.VERSION.SDK_INT >= 33) {
            android.widget.Toast.makeText(
                this,
                R.string.mqtt_notification_permission_denied,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    protected fun ensureMqttNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // Enforce the selected language context wrapper
        super.attachBaseContext(LocaleManager.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
}
