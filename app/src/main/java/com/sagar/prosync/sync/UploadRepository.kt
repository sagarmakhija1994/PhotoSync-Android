package com.sagar.prosync.sync

import android.content.Context
import android.util.Log
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.data.api.PhotoApi
import com.sagar.prosync.data.api.PhotoBatchCheckRequest
import com.sagar.prosync.data.api.PhotoCheckItem
import com.sagar.prosync.data.api.PhotoCheckRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.FileInputStream

class UploadRepository(private val context: Context) {

    private val api: PhotoApi = ApiClient.create(context).create(PhotoApi::class.java)
    private val sessionStore = SessionStore(context)

    suspend fun processSync(items: List<MediaItem>, onProgress: suspend (Int, Int) -> Unit) {
        // 1. Prepare the batch check request
        val checkItems = items.map {
            PhotoCheckItem(
                HashUtils.sha256(context, it.uri),
                it.size,
                if (it.isVideo) "video" else "photo"
            )
        }

        // 2. Ask server which files it ALREADY has
        val response = try {
            api.checkBatch(PhotoBatchCheckRequest(checkItems))
        } catch (e: Exception) {
            Log.e("UploadRepo", "Batch check failed", e)
            return
        }

        val existingHashes = response.existing_hashes.toSet()

        // 3. Filter only files that DON'T exist on server
        val filesToUpload = items.filterIndexed { index, _ ->
            !existingHashes.contains(checkItems[index].sha256)
        }

        // If nothing to upload, notify progress 0/0 and exit early
        if (filesToUpload.isEmpty()) {
            onProgress(0, 0)
            return
        }

        // 4. Upload only the missing files
        filesToUpload.forEachIndexed { index, item ->
            val sha = checkItems[items.indexOf(item)].sha256

            // ACTUALLY execute the upload API call!
            executeUpload(item, sha)

            // Notify WorkManager of progress
            onProgress(index + 1, filesToUpload.size)
        }
    }

    suspend fun upload(item: MediaItem) {
        // Fallback for individual uploads (if needed later)
        val sha = HashUtils.sha256(context, item.uri)
        try {
            val exists = api.check(
                PhotoCheckRequest(
                    sha256 = sha,
                    file_size = item.size,
                    media_type = if (item.isVideo) "video" else "photo"
                )
            ).exists

            if (exists) return
            executeUpload(item, sha)
        } catch (e: Exception) {
            Log.e("UploadRepo", "Single check failed", e)
        }
    }

    // --- NEW: Reusable core upload engine ---
    private suspend fun executeUpload(item: MediaItem, sha: String) {
        try {
            val filePart = createMultipartBody(item, sha)

            api.upload(
                file = filePart,
                sha256 = sha.toRequestBody("text/plain".toMediaType()),
                relativePath = item.relativePath.toRequestBody("text/plain".toMediaType()),
                mediaType = (if (item.isVideo) "video" else "photo").toRequestBody("text/plain".toMediaType())
            )
            Log.d("UploadRepo", "Successfully uploaded: ${item.relativePath}")

        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) {
                Log.e("UploadRepo", "Token expired! Clearing session.")
                sessionStore.clear()
            } else {
                Log.e("UploadRepo", "Server error during upload: ${e.code()}")
            }
        } catch (e: Exception) {
            Log.e("UploadRepo", "Failed to upload ${item.relativePath}", e)
        }
    }

    private fun createMultipartBody(item: MediaItem, sha: String): MultipartBody.Part {
        val requestBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength(): Long = item.size
            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openFileDescriptor(item.uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { inputStream ->
                        inputStream.source().use { source ->
                            sink.writeAll(source)
                        }
                    }
                }
            }
        }
        return MultipartBody.Part.createFormData(
            name = "file",
            filename = item.relativePath.substringAfterLast("/"),
            body = requestBody
        )
    }
}