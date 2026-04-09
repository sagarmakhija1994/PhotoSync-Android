package com.sagar.prosync.sync

import android.content.Context
import android.net.Uri

class FolderStore(context: Context) {

    private val prefs = context.getSharedPreferences("folder_store", Context.MODE_PRIVATE)

    fun save(uri: Uri) {
        val set = getAll().toMutableSet()
        set.add(uri.toString())
        prefs.edit().putStringSet("folders", set).apply()
    }

    fun remove(uri: Uri) {
        val set = getAll().toMutableSet()
        set.remove(uri.toString())
        prefs.edit().putStringSet("folders", set).apply()
    }

    fun getAll(): Set<String> =
        prefs.getStringSet("folders", emptySet()) ?: emptySet()
}
