package com.shortsBlocker

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvSessionsRemaining: TextView
    private lateinit var tvTimerCountdown: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnEnterTemp: Button
    private lateinit var btnEnterPermanent: Button
    private lateinit var btnSettings: Button
    private lateinit var tvWarnings: TextView
    private lateinit var progressBar: ProgressBar

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.shortsBlocker.SESSION_TICK" -> {
                    val remaining = intent.getLongExtra("remaining_ms", 0L)
                    updateTimer(remaining)
                }
                "com.shortsBlocker.SESSION_ENDED" -> {
                    updateUI()
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            if (SessionManager.isSessionActive(this@MainActivity)) {
                updateTimer(SessionManager.getRemainingSessionTimeMs(this@MainActivity))
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // First-run check
        if (!PasswordManager.isSetupDone(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        tvSessionsRemaining = findViewById(R.id.tv_sessions_remaining)
        tvTimerCountdown = findViewById(R.id.tv_timer_countdown)
        tvStatus = findViewById(R.id.tv_status)
        btnEnterTemp = findViewById(R.id.btn_enter_temp)
        btnEnterPermanent = findViewById(R.id.btn_enter_permanent)
        btnSettings = findViewById(R.id.btn_settings)
        tvWarnings = findViewById(R.id.tv_warnings)
        progressBar = findViewById(R.id.progress_sessions)

        btnEnterTemp.setOnClickListener {
            showPasswordDialog(isTemp = true)
        }

        btnEnterPermanent.setOnClickListener {
            showPasswordDialog(isTemp = false)
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        updateUI()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        checkPermissions()
        val filter = IntentFilter().apply {
            addAction("com.shortsBlocker.SESSION_TICK")
            addAction("com.shortsBlocker.SESSION_ENDED")
        }
        registerReceiver(sessionReceiver, filter, RECEIVER_NOT_EXPORTED)
        handler.post(uiUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(sessionReceiver) } catch (e: Exception) {}
        handler.removeCallbacks(uiUpdateRunnable)
    }

    private fun updateUI() {
        val sessionsUsed = SessionManager.getSessionsUsedToday(this)
        val sessionsRemaining = SessionManager.getSessionsRemainingToday(this)
        val isActive = SessionManager.isSessionActive(this)
        val isDisabled = SessionManager.isBlockingPermanentlyDisabled(this)

        tvSessionsRemaining.text = "Sessions today: $sessionsUsed / ${SessionManager.MAX_SESSIONS_PER_DAY}"
        progressBar.max = SessionManager.MAX_SESSIONS_PER_DAY
        progressBar.progress = sessionsUsed

        tvStatus.text = when {
            isDisabled -> "Status: Blocking DISABLED (Permanent Access)"
            isActive -> "Status: Session Active — Shorts Unlocked"
            sessionsRemaining <= 0 -> "Status: Daily Limit Reached — Blocked"
            else -> "Status: Shorts BLOCKED"
        }

        tvTimerCountdown.visibility = if (isActive) View.VISIBLE else View.INVISIBLE

        if (!isActive) {
            tvTimerCountdown.text = "10:00"
        }

        // Check for warnings
        val warnings = StringBuilder()
        if (!isAccessibilityEnabled()) warnings.appendLine("⚠ Accessibility Service not enabled!")
        if (!hasOverlayPermission()) warnings.appendLine("⚠ Overlay permission not granted!")
        if (!isDeviceAdminEnabled()) warnings.appendLine("⚠ Device Admin not enabled (uninstall protection inactive)")
        tvWarnings.text = warnings.toString().trimEnd()
        tvWarnings.visibility = if (warnings.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateTimer(remainingMs: Long) {
        val minutes = (remainingMs / 1000 / 60).toInt()
        val seconds = (remainingMs / 1000 % 60).toInt()
        tvTimerCountdown.text = String.format("%02d:%02d remaining", minutes, seconds)
    }

    private fun showPasswordDialog(isTemp: Boolean) {
        val intent = Intent(this, PasswordDialogActivity::class.java).apply {
            putExtra("is_temp", isTemp)
        }
        startActivity(intent)
    }

    private fun showSettingsDialog() {
        val options = arrayOf(
            "Change Permanent Password",
            "Change Temporary Password",
            "Enable Device Admin (Uninstall Protection)",
            "Enable Accessibility Service",
            "Grant Overlay Permission",
            "Re-enable Blocking (if disabled)"
        )
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showChangePasswordDialog(isPermanent = true)
                    1 -> showChangePasswordDialog(isPermanent = false)
                    2 -> enableDeviceAdmin()
                    3 -> openAccessibilitySettings()
                    4 -> requestOverlayPermission()
                    5 -> reEnableBlocking()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showChangePasswordDialog(isPermanent: Boolean) {
        val label = if (isPermanent) "Permanent" else "Temporary"
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrent = view.findViewById<EditText>(R.id.et_current_password)
        val etNew = view.findViewById<EditText>(R.id.et_new_password)
        val etNewConfirm = view.findViewById<EditText>(R.id.et_new_confirm)
        val tvErr = view.findViewById<TextView>(R.id.tv_change_error)

        AlertDialog.Builder(this)
            .setTitle("Change $label Password")
            .setView(view)
            .setPositiveButton("Save") { dialog, _ ->
                val current = etCurrent.text.toString()
                val new1 = etNew.text.toString()
                val new2 = etNewConfirm.text.toString()

                val verified = if (isPermanent)
                    PasswordManager.checkPermanentPassword(this, current)
                else
                    PasswordManager.checkTempPassword(this, current)

                if (!verified) {
                    tvErr.text = "Current password is incorrect."
                    tvErr.visibility = View.VISIBLE
                    return@setPositiveButton
                }
                if (new1.length < 4) {
                    tvErr.text = "New password must be at least 4 characters."
                    tvErr.visibility = View.VISIBLE
                    return@setPositiveButton
                }
                if (new1 != new2) {
                    tvErr.text = "New passwords do not match."
                    tvErr.visibility = View.VISIBLE
                    return@setPositiveButton
                }
                if (isPermanent) PasswordManager.changePermanentPassword(this, new1)
                else PasswordManager.changeTempPassword(this, new1)
                Toast.makeText(this, "$label password changed.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reEnableBlocking() {
        val view = layoutInflater.inflate(R.layout.dialog_simple_password, null)
        val etPass = view.findViewById<EditText>(R.id.et_simple_password)
        val tvErr = view.findViewById<TextView>(R.id.tv_simple_error)

        AlertDialog.Builder(this)
            .setTitle("Re-enable Blocking")
            .setMessage("Enter Permanent Password to re-enable Shorts blocking:")
            .setView(view)
            .setPositiveButton("Confirm") { dialog, _ ->
                val input = etPass.text.toString()
                if (PasswordManager.checkPermanentPassword(this, input)) {
                    SessionManager.setBlockingPermanentlyDisabled(this, false)
                    Toast.makeText(this, "Blocking re-enabled.", Toast.LENGTH_SHORT).show()
                    updateUI()
                    dialog.dismiss()
                } else {
                    tvErr.text = "Incorrect password."
                    tvErr.visibility = View.VISIBLE
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkPermissions() {
        if (!isAccessibilityEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Accessibility Service Required")
                .setMessage("Shorts Blocker needs the Accessibility Service to detect and block YouTube Shorts. Please enable it.")
                .setPositiveButton("Open Settings") { _, _ -> openAccessibilitySettings() }
                .setNegativeButton("Later", null)
                .show()
        } else if (!hasOverlayPermission()) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("Shorts Blocker needs 'Display over other apps' permission to show the blocking screen.")
                .setPositiveButton("Grant") { _, _ -> requestOverlayPermission() }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Exception) { 0 }
        if (accessibilityEnabled != 1) return false

        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val serviceName = "${packageName}/${ShortsBlockerAccessibilityService::class.java.name}"
        return services?.contains(serviceName) == true
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun isDeviceAdminEnabled(): Boolean {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, AdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun enableDeviceAdmin() {
        val adminComponent = ComponentName(this, AdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Prevents Shorts Blocker from being uninstalled without the Permanent Password.")
        }
        startActivity(intent)
    }
}
