package com.example.mqttpanelcraft

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class RegisterActivity : BaseActivity() {

    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val btnSendCode = findViewById<MaterialButton>(R.id.btnSendCode)
        val btnRegisterAction = findViewById<MaterialButton>(R.id.btnRegisterAction)
        val etVerifyCode = findViewById<TextInputEditText>(R.id.etVerifyCode)

        // Send Code -> Mock Countdown
        btnSendCode.setOnClickListener {
            startCountdown(btnSendCode)
            Toast.makeText(this, R.string.auth_code_sent, Toast.LENGTH_SHORT).show()
        }

        // Register -> Return to Login (Mock)
        btnRegisterAction.setOnClickListener {
            // Validation logic would go here
            Toast.makeText(this, R.string.auth_registration_success, Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun startCountdown(button: MaterialButton) {
        button.isEnabled = false
        countDownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                button.text = getString(R.string.auth_resend_countdown, secondsRemaining)
            }

            override fun onFinish() {
                button.isEnabled = true
                button.setText(R.string.auth_send)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
