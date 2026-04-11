package com.sagar.prosync.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class PhotoCheckItem(
    val sha256: String,
    val file_size: Long,
    val media_type: String
)
data class PhotoBatchCheckRequest(val items: List<PhotoCheckItem>)
data class PhotoBatchCheckResponse(val existing_hashes: List<String>)
data class PhotoCheckRequest(
    val sha256: String,
    val file_size: Long,
    val media_type: String
)
data class PhotoCheckResponse(val exists: Boolean)
data class UploadResponse(val status: String)

data class RemotePhoto(
    val id: Int,
    val filename: String,
    val device_name: String,
    val media_type: String,
    val file_size: Long,
    val created_at: String
)

data class PhotoListResponse(val photos: List<RemotePhoto>)

interface PhotoApi {

    @POST("/photos/check-batch")
    suspend fun checkBatch(@Body body: PhotoBatchCheckRequest): PhotoBatchCheckResponse

    @POST("/photos/check")
    suspend fun check(
        @retrofit2.http.Body body: PhotoCheckRequest
    ): PhotoCheckResponse

    @Multipart
    @POST("/photos/upload")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("sha256") sha256: RequestBody,
        @Part("relative_path") relativePath: RequestBody,
        @Part("media_type") mediaType: RequestBody
    ): UploadResponse

    @retrofit2.http.GET("/photos/list")
    suspend fun getPhotos(): PhotoListResponse
}
