package com.sagar.prosync.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

    var syncPhotos: Boolean
        get() = prefs.getBoolean("sync_photos", true)
        set(value) = prefs.edit().putBoolean("sync_photos", value).apply()

    var syncVideos: Boolean
        get() = prefs.getBoolean("sync_videos", false)
        set(value) = prefs.edit().putBoolean("sync_videos", value).apply()

    // If true, allow Cellular. If false, require WiFi.
    var useCellular: Boolean
        get() = prefs.getBoolean("use_cellular", false)
        set(value) = prefs.edit().putBoolean("use_cellular", value).apply()

    var autoSyncEnabled: Boolean
        get() = prefs.getBoolean("auto_sync_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_sync_enabled", value).apply()

    // --- NEW: Tracks if the user has completed the initial folder setup ---
    var isSetupComplete: Boolean
        get() = prefs.getBoolean("is_setup_complete", false) // Defaults to false for new installs
        set(value) = prefs.edit().putBoolean("is_setup_complete", value).apply()
}