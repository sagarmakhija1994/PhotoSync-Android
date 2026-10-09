package com.sagar.prosync.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "session_store",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        // Backwards-compatible: saving single token still sets access token
        prefs.edit().putString("access_token", token).apply()
    }

    fun saveAccessToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)

    fun saveRefreshToken(token: String) {
        prefs.edit().putString("refresh_token", token).apply()
    }

    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun clear() {
        prefs.edit().remove("access_token").apply()
        prefs.edit().remove("refresh_token").apply()
        prefs.edit().clear().apply()
    }
}
