package com.example.mqttpanelcraft

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // Fix: Force tint programmatically using setHomeAsUpIndicator
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        val colorOnSurface = if (typedValue.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
        
        val arrow = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_arrow_back)?.mutate()
        arrow?.setTint(colorOnSurface)
        supportActionBar?.setHomeAsUpIndicator(arrow)
        
        toolbar.setNavigationOnClickListener { onBackPressed() }

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        var version = "Unknown"
        var build: Long = 0
        
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = pInfo.versionName ?: "Unknown"
            build = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                pInfo.versionCode.toLong()
            }
            tvVersion.text = "Version $version (Build $build)"
        } catch (e: Exception) {
            e.printStackTrace()
            tvVersion.text = "Version Unknown"
        }

        findViewById<Button>(R.id.btnPrivacy).setOnClickListener {
            // Show Privacy Policy in-app instead of opening browser
            showPrivacyDialog()
        }

        findViewById<Button>(R.id.btnContact).setOnClickListener {
            // ... (keep existing email logic if needed, but for brevity I'll keep it as is if I don't touch it)
             val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:") 
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@example.com")) // Placeholder
                putExtra(Intent.EXTRA_SUBJECT, "MqttPanelCraft Support")
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                 // Fallback for emulator often
                 android.widget.Toast.makeText(this, "No email client found", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        findViewById<Button>(R.id.btnOssLicenses).setOnClickListener {
            // Feature disabled as dependencies were reverted
            android.widget.Toast.makeText(this, "Feature temporarily disabled", android.widget.Toast.LENGTH_SHORT).show()
            // startActivity(Intent(this, com.google.android.gms.oss.licenses.OssLicensesMenuActivity::class.java))
        }

        val tvContent = findViewById<TextView>(R.id.tvContent)
        tvContent.text = loadAboutContentFromAssets(version, build.toString())
    }
    
    // License Dialog replaced by Google OSS Activity

    private fun showPrivacyDialog() {
        val privacyText = try {
            assets.open("privacy_policy.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Error loading privacy policy."
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Privacy Policy")
            .setMessage(privacyText)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun loadAboutContentFromAssets(version: String, build: String): String {
        return try {
            val rawContent = assets.open("about_content.txt").bufferedReader().use { it.readText() }
            rawContent
                .replace("{VERSION_NAME}", version)
                .replace("{BUILD_NUMBER}", build)
                .replace("{Developer/Company}", "Spec127")
                .replace("{YYYY-MM-DD}", "2026-01-26") // Effective Date
                .replace("{SUPPORT_EMAIL}", "niceboat919@gmail.com")
                .replace("{WEBSITE_LINK}", "https://example.com")
                .replace("{PRIVACY_POLICY_LINK}", "https://www.google.com/policies/privacy")
        } catch (e: Exception) {
            e.printStackTrace()
            "Error loading about content. Please contact support."
        }
    }
}
