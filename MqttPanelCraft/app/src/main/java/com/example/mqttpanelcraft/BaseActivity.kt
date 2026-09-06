package com.example.mqttpanelcraft

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * BaseActivity for strict Locale management.
 * All Activities should extend this to ensure language settings persist
 * across configuration changes and restarts.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable Edge-to-Edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
}
