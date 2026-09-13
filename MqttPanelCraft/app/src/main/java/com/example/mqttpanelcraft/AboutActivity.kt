package com.example.mqttpanelcraft

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.mqttpanelcraft.utils.PremiumManager
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import java.io.File

class AboutActivity : BaseActivity() {

    private var versionTapCount = 0
    private var lastVersionTapAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
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

        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        var version = getString(R.string.version_unknown_value)
        var build: Long = 0

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = pInfo.versionName ?: getString(R.string.version_unknown_value)
            build = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
            tvVersion.text = getString(R.string.version_format, version, build.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            tvVersion.text = getString(R.string.version_unknown)
        }
        tvVersion.setOnClickListener {
            onVersionTapped()
        }
        tvVersion.setOnLongClickListener {
            showCrashLogs()
            true
        }

        findViewById<Button>(R.id.btnPrivacy).setOnClickListener {
            showPrivacyDialog()
        }

        findViewById<Button>(R.id.btnContact).setOnClickListener {
            openSupportEmail()
        }

        findViewById<Button>(R.id.btnOssLicenses).setOnClickListener {
            startActivity(Intent(this, OssLicensesMenuActivity::class.java))
        }

        val tvContent = findViewById<TextView>(R.id.tvContent)
        tvContent.text = loadAboutContent(version, build.toString())
    }

    private fun openSupportEmail() {
        val email = getString(R.string.support_email_address)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_email_subject))
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.contact_support)))
        } catch (_: ActivityNotFoundException) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("support email", email))
            Toast.makeText(this, getString(R.string.error_no_email_client_copied, email), Toast.LENGTH_LONG).show()
        }
    }

    private fun onVersionTapped() {
        if (!PremiumManager.isDebuggable(this)) {
            Toast.makeText(this, R.string.developer_mode_release_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastVersionTapAt > 1500L) versionTapCount = 0
        lastVersionTapAt = now
        versionTapCount++
        if (versionTapCount >= 7) {
            versionTapCount = 0
            showDeveloperModeDialog()
        }
    }

    private fun showDeveloperModeDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.developer_mode_title)
            .setMessage(R.string.developer_mode_message)
            .setPositiveButton(R.string.developer_mode_enable_skip) { _, _ ->
                PremiumManager.setDevSkipAds(this, true)
                Toast.makeText(this, R.string.developer_mode_enabled, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.developer_mode_disable_skip) { _, _ ->
                PremiumManager.setDevSkipAds(this, false)
                Toast.makeText(this, R.string.developer_mode_disabled, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.common_btn_close, null)
            .show()
    }

    private fun showPrivacyDialog() {
        val privacyText = try {
            resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            getString(R.string.error_privacy_load)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_privacy_policy))
            .setMessage(privacyText)
            .setPositiveButton(getString(R.string.common_btn_close), null)
            .show()
    }

    private fun loadAboutContent(version: String, build: String): String {
        return try {
            val rawContent = resources.openRawResource(R.raw.about_content).bufferedReader().use { it.readText() }
            rawContent
                .replace("{VERSION_NAME}", version)
                .replace("{BUILD_NUMBER}", build)
                .replace("{COPYRIGHT_HOLDER}", "Spec127")
                .replace("{EFFECTIVE_DATE}", "2026-09-13")
                .replace("{SUPPORT_EMAIL}", getString(R.string.support_email_address))
        } catch (e: Exception) {
            e.printStackTrace()
            getString(R.string.error_about_load)
        }
    }

    private fun showCrashLogs() {
        try {
            val file = File(getExternalFilesDir(null), "crash_log.txt")
            if (!file.exists()) {
                Toast.makeText(this, R.string.crash_no_logs, Toast.LENGTH_SHORT).show()
                return
            }
            val content = java.io.RandomAccessFile(file, "r").use { log ->
                val count = minOf(log.length(), 64L * 1024).toInt()
                log.seek(log.length() - count)
                val bytes = ByteArray(count)
                log.readFully(bytes)
                bytes.toString(Charsets.UTF_8)
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.crash_logs_title)
                .setMessage(content.takeLast(2000))
                .setPositiveButton(R.string.common_btn_close, null)
                .setNeutralButton(R.string.common_btn_copy) { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", content))
                    Toast.makeText(this, R.string.crash_logs_copied, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.common_btn_clear) { _, _ ->
                    file.delete()
                    Toast.makeText(this, R.string.crash_logs_cleared, Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.crash_logs_error, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}
