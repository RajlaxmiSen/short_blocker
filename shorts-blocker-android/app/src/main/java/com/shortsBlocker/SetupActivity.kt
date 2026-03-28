package com.shortsBlocker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    private lateinit var etPermanentPassword: EditText
    private lateinit var etPermanentConfirm: EditText
    private lateinit var etTempPassword: EditText
    private lateinit var etTempConfirm: EditText
    private lateinit var btnSave: Button
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        etPermanentPassword = findViewById(R.id.et_permanent_password)
        etPermanentConfirm = findViewById(R.id.et_permanent_confirm)
        etTempPassword = findViewById(R.id.et_temp_password)
        etTempConfirm = findViewById(R.id.et_temp_confirm)
        btnSave = findViewById(R.id.btn_save_setup)
        tvError = findViewById(R.id.tv_setup_error)

        btnSave.setOnClickListener { validateAndSave() }
    }

    private fun validateAndSave() {
        val perm = etPermanentPassword.text.toString().trim()
        val permConfirm = etPermanentConfirm.text.toString().trim()
        val temp = etTempPassword.text.toString().trim()
        val tempConfirm = etTempConfirm.text.toString().trim()

        when {
            perm.length < 4 -> showError("Permanent password must be at least 4 characters.")
            perm != permConfirm -> showError("Permanent passwords do not match.")
            temp.length < 4 -> showError("Temporary password must be at least 4 characters.")
            temp != tempConfirm -> showError("Temporary passwords do not match.")
            perm == temp -> showError("Permanent and temporary passwords must be different.")
            else -> {
                PasswordManager.savePasswords(this, perm, temp)
                Toast.makeText(this, "Passwords saved! Shorts Blocker is active.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    override fun onBackPressed() {
        // Prevent skipping setup
        Toast.makeText(this, "You must complete setup to use Shorts Blocker.", Toast.LENGTH_SHORT).show()
    }
}
