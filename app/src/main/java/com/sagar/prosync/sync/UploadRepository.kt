package com.sagar.prosync.sync

import android.content.Context
import android.net.Uri // Make sure this is imported!
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
    private val hashCache = HashCache(context)

    // Changed the callback to pass: (Message String, Current Progress, Total Target)
    suspend fun processSync(items: List<MediaItem>, onProgress: suspend (String, Int, Int) -> Unit) {
        val totalItems = items.size

        // ==========================================
        // PHASE 1: PRE-FLIGHT CHECK (Find missing files)
        // ==========================================
        val missingFiles = mutableListOf<MediaItem>()
        val precalculatedHashes = mutableMapOf<Uri, String>() // Store hashes so we don't calculate them twice

        var checkedCount = 0
        // Check in large chunks of 500 (Network is fast when just sending text hashes)
        val checkChunks = items.chunked(500)

        for (chunk in checkChunks) {
            val checkItems = chunk.map { item ->
                // Check local SQLite cache first (Instant)
                var sha = hashCache.getHash(item.relativePath, item.dateModified)

                if (sha == null) {
                    // Calculate and cache (Only happens for brand new or edited files)
                    sha = HashUtils.sha256(context, item.uri)
                    hashCache.putHash(item.relativePath, item.dateModified, sha)
                }

                // Save it to memory so Phase 2 doesn't have to re-calculate it
                precalculatedHashes[item.uri] = sha

                PhotoCheckItem(sha, item.size, if (item.isVideo) "video" else "photo")
            }

            // Ask server what it already has
            val response = try {
                api.checkBatch(PhotoBatchCheckRequest(checkItems))
            } catch (e: Exception) {
                Log.e("UploadRepo", "Batch check failed", e)
                continue // Skip to next chunk on network error
            }

            val existingHashes = response.existing_hashes.toSet()

            // Build our master list of missing files
            chunk.forEachIndexed { index, item ->
                val sha = checkItems[index].sha256
                if (!existingHashes.contains(sha)) {
                    missingFiles.add(item)
                }
            }

            checkedCount += chunk.size
            onProgress("Analyzing library...($checkedCount / $totalItems)", checkedCount, totalItems)
        }

        // ==========================================
        // PHASE 2: EXECUTE UPLOADS
        // ==========================================
        val totalToUpload = missingFiles.size

        if (totalToUpload == 0) {
            onProgress("Everything is up to date!", totalItems, totalItems)
            return
        }

        var uploadedCount = 0

        // Now we know exactly how many files are missing, upload them sequentially.
        // Because we process them 1-by-1 with OkHttp's isOneShot streaming, memory stays perfectly flat!
        for (item in missingFiles) {
            val sha = precalculatedHashes[item.uri] ?: continue

            // Update UI BEFORE the upload starts
            onProgress("Uploading new files...($uploadedCount / $totalToUpload)", uploadedCount, totalToUpload)

            // Execute the actual network upload
            executeUpload(item, sha)

            // Update UI AFTER the upload succeeds
            uploadedCount++
            onProgress("Uploading new files...($uploadedCount / $totalToUpload)", uploadedCount, totalToUpload)
        }
    }

    suspend fun upload(item: MediaItem) {
        // Fallback for individual uploads
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
            override fun isOneShot(): Boolean = true

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(item.uri)?.use { inputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
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