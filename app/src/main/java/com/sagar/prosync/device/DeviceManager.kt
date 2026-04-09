package com.sagar.prosync.device

import android.content.Context
import java.util.UUID

object DeviceManager {

    private const val PREFS = "device_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    fun getDeviceName(): String {
        return android.os.Build.MODEL ?: "AndroidDevice"
    }
}
