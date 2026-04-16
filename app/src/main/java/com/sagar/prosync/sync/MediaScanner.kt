package com.sagar.prosync.sync

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

data class MediaItem(
    val uri: Uri,
    val relativePath: String,
    val size: Long,
    val dateModified: Long,
    val isVideo: Boolean
)

object MediaScanner {

    /**
     * Entry point
     */
    fun scan(
        context: Context,
        uploadPhotos: Boolean,
        uploadVideos: Boolean
    ): List<MediaItem> {

        val selectedFolders = FolderStore(context)
            .getAll()
            .map { extractPathFromUri(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        Log.d("ProSyncDebug", "Tripwire 1 -> Extracted Selected Folders: $selectedFolders")

        val results = mutableListOf<MediaItem>()

        if (uploadPhotos) {
            Log.d("ProSyncDebug", "Tripwire 2 -> Scanning Photos...")
            results += scanImages(context, selectedFolders)
        }

        if (uploadVideos) {
            results += scanVideos(context, selectedFolders)
        }
        Log.d("ProSyncDebug", "Tripwire 5 -> Total Items Found to Upload: ${results.size}")
        return results
    }

    // ---------- Photos ----------

    private fun scanImages(
        context: Context,
        selectedFolders: Set<String>
    ): List<MediaItem> {
        return scanCommon(
            context = context,
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            isVideo = false,
            selectedFolders = selectedFolders
        )
    }

    // ---------- Videos ----------

    private fun scanVideos(
        context: Context,
        selectedFolders: Set<String>
    ): List<MediaItem> {
        return scanCommon(
            context = context,
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            isVideo = true,
            selectedFolders = selectedFolders
        )
    }

    // ---------- Core scanner ----------

    private fun scanCommon(
        context: Context,
        collection: Uri,
        isVideo: Boolean,
        selectedFolders: Set<String>
    ): List<MediaItem> {

        val items = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            Log.d("ProSyncDebug", "Tripwire 3 -> Cursor found ${cursor.count} files in MediaStore.")
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val size = cursor.getLong(sizeCol)
                val dateModified = cursor.getLong(dateCol)
                val relativeDir = cursor.getString(pathCol) ?: continue
                val name = cursor.getString(nameCol) ?: continue

                val normalizedPath = normalizeFolder(relativeDir)
                Log.d("ProSyncDebug", "Tripwire 4 -> Inspecting File: [$normalizedPath$name]")

                // 🔒 SAF folder filter
                if (!isUnderSelectedFolders(normalizedPath, selectedFolders)) {
                    Log.d("ProSyncDebug", "          -> SKIPPED (Not in selected folder)")
                    continue
                }
                Log.d("ProSyncDebug", "          -> ACCEPTED! Adding to upload queue.")
                val contentUri = ContentUris.withAppendedId(collection, id)

                items += MediaItem(
                    uri = contentUri,
                    relativePath = "$relativeDir$name", // EXACT backend match
                    size = size,
                    dateModified = dateModified,
                    isVideo = isVideo
                )
            }
        }

        return items
    }

    // ---------- Helpers ----------

    /**
     * Normalizes MediaStore folder paths:
     * "DCIM/Camera/" → "dcim/camera/"
     */
    private fun normalizeFolder(path: String): String =
        path.trim().lowercase().removePrefix("/")

    /**
     * Checks whether file path is under one of the user-selected folders
     */
    private fun isUnderSelectedFolders(
        mediaPath: String,
        selectedFolders: Set<String>
    ): Boolean {
        // If no folders are selected, allow everything to upload
        if (selectedFolders.isEmpty()) return true

        val cleanMedia = mediaPath.lowercase().trim('/')

        /*return selectedFolders.any { folder ->
            val cleanFolder = folder.lowercase().trim('/')
            cleanMedia.contains(cleanFolder) || cleanFolder.contains(cleanMedia)
        }*/
        return selectedFolders.any { cleanFolder ->
            cleanMedia.startsWith(cleanFolder)
        }
    }

    /**
     * Extracts the real folder name from an ugly SAF URI string.
     * "content://.../tree/primary%3ADCIM" -> "dcim"
     */
    private fun extractPathFromUri(uriString: String): String {
        return try {
            // 1. Decode %3A back into a colon (:)
            val decoded = android.net.Uri.decode(uriString)
            // 2. Take everything after the last colon
            val path = decoded.substringAfterLast(":", "")
            // 3. Clean it up
            path.lowercase().trim('/')
        } catch (e: Exception) {
            ""
        }
    }
}

