package com.shortsBlocker

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PasswordDialogActivity : AppCompatActivity() {

    private lateinit var etPassword: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnCancel: Button
    private lateinit var tvTitle: TextView
    private lateinit var tvError: TextView
    private lateinit var rgPasswordType: RadioGroup
    private lateinit var rbTemp: RadioButton
    private lateinit var rbPermanent: RadioButton
    private lateinit var tvSessionInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_dialog)

        etPassword = findViewById(R.id.et_dialog_password)
        btnSubmit = findViewById(R.id.btn_dialog_submit)
        btnCancel = findViewById(R.id.btn_dialog_cancel)
        tvTitle = findViewById(R.id.tv_dialog_title)
        tvError = findViewById(R.id.tv_dialog_error)
        rgPasswordType = findViewById(R.id.rg_password_type)
        rbTemp = findViewById(R.id.rb_temp_password)
        rbPermanent = findViewById(R.id.rb_permanent_password)
        tvSessionInfo = findViewById(R.id.tv_session_info)

        updateSessionInfo()

        btnSubmit.setOnClickListener { handleSubmit() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun updateSessionInfo() {
        val remaining = SessionManager.getSessionsRemainingToday(this)
        tvSessionInfo.text = "Sessions remaining today: $remaining / ${SessionManager.MAX_SESSIONS_PER_DAY}"
        if (remaining <= 0) {
            rbTemp.isEnabled = false
            rbTemp.text = "Temporary Password (daily limit reached)"
            rbPermanent.isChecked = true
        }
    }

    private fun handleSubmit() {
        val input = etPassword.text.toString()
        if (input.isEmpty()) {
            showError("Please enter a password.")
            return
        }

        val isTemp = rbTemp.isChecked

        if (isTemp) {
            // Check daily limit
            if (!SessionManager.canStartSession(this)) {
                showError("Daily limit reached. You have used all 5 sessions today.")
                return
            }
            if (PasswordManager.checkTempPassword(this, input)) {
                // Start session
                ShortsBlockerAccessibilityService.instance?.startSession()
                Toast.makeText(this, "Access granted! 10 minutes of Shorts.", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                showError("Incorrect temporary password. Try again.")
                etPassword.text?.clear()
            }
        } else {
            if (PasswordManager.checkPermanentPassword(this, input)) {
                Toast.makeText(this, "Permanent access granted.", Toast.LENGTH_SHORT).show()
                SessionManager.setBlockingPermanentlyDisabled(this, true)
                ShortsBlockerAccessibilityService.instance?.removeOverlay()
                finish()
            } else {
                showError("Incorrect permanent password. Try again.")
                etPassword.text?.clear()
            }
        }
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }
}
