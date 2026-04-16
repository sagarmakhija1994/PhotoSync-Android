package com.sagar.prosync.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

    var syncPhotos: Boolean
        get() = prefs.getBoolean("sync_photos", true)
        set(value) = prefs.edit().putBoolean("sync_photos", value).apply()

    var syncVideos: Boolean
        get() = prefs.getBoolean("sync_videos", true)
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

    // --- NEW: Dynamic Grid Columns ---
    var gridColumnsPortrait: Int
        get() = prefs.getInt("grid_columns_portrait", 5)
        set(value) = prefs.edit().putInt("grid_columns_portrait", value).apply()

    var gridColumnsLandscape: Int
        get() = prefs.getInt("grid_columns_landscape", 9)
        set(value) = prefs.edit().putInt("grid_columns_landscape", value).apply()

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) = prefs.edit().putString("server_url", value).apply()

    var localServerUrl: String
        get() = prefs.getString("local_server_url", "") ?: ""
        set(value) = prefs.edit().putString("local_server_url", value).apply()

    var useLocalServer: Boolean
        get() = prefs.getBoolean("use_local_server", false)
        set(value) = prefs.edit().putBoolean("use_local_server", value).apply()
}