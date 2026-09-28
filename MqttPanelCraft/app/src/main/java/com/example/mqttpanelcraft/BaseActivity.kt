package com.example.mqttpanelcraft

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * BaseActivity for strict Locale management.
 * All Activities should extend this to ensure language settings persist
 * across configuration changes and restarts.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        com.example.mqttpanelcraft.utils.AdManager.onScreenResumed(this)
        com.example.mqttpanelcraft.utils.PlayBillingManager.refreshPurchases(this, null)
    }

    override fun onStop() {
        if (isFinishing && (this is ProjectViewActivity || this is WebViewActivity) &&
            !com.example.mqttpanelcraft.utils.AdManager.isTutorial(this)) {
            com.example.mqttpanelcraft.utils.AdManager.markNaturalReturn()
        }
        super.onStop()
    }

    override fun onDestroy() {
        com.example.mqttpanelcraft.utils.AdManager.release(this)
        super.onDestroy()
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.example.mqttpanelcraft.utils.AdManager.isTutorial(this)) {
            com.example.mqttpanelcraft.utils.AdManager.enterTutorial()
        }
        
        // Enable Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
}
