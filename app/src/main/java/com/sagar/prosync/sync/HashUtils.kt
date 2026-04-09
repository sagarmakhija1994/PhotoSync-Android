package com.sagar.prosync.sync

import android.content.Context
import java.security.MessageDigest

object HashUtils {

    fun sha256(context: Context, uri: android.net.Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")

        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(1024 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
