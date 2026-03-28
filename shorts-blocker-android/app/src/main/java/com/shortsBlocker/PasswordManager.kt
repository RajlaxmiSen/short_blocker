package com.shortsBlocker

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object PasswordManager {

    private const val PREFS_FILE = "shorts_blocker_secure_prefs"
    private const val KEY_PERMANENT_PASSWORD = "permanent_password"
    private const val KEY_TEMP_PASSWORD = "temp_password"
    private const val KEY_SETUP_DONE = "setup_done"

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        PREFS_FILE,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isSetupDone(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SETUP_DONE, false)
    }

    fun savePasswords(context: Context, permanent: String, temp: String) {
        getPrefs(context).edit()
            .putString(KEY_PERMANENT_PASSWORD, permanent)
            .putString(KEY_TEMP_PASSWORD, temp)
            .putBoolean(KEY_SETUP_DONE, true)
            .apply()
    }

    fun checkPermanentPassword(context: Context, input: String): Boolean {
        val stored = getPrefs(context).getString(KEY_PERMANENT_PASSWORD, null) ?: return false
        return stored == input
    }

    fun checkTempPassword(context: Context, input: String): Boolean {
        val stored = getPrefs(context).getString(KEY_TEMP_PASSWORD, null) ?: return false
        return stored == input
    }

    fun changePermanentPassword(context: Context, newPassword: String) {
        getPrefs(context).edit().putString(KEY_PERMANENT_PASSWORD, newPassword).apply()
    }

    fun changeTempPassword(context: Context, newPassword: String) {
        getPrefs(context).edit().putString(KEY_TEMP_PASSWORD, newPassword).apply()
    }
}
