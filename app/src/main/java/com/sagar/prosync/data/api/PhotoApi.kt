package com.sagar.prosync.data.api

import android.util.Log
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

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

data class DeletePhotosRequest(val photo_ids: List<Int>)

data class AlbumDto(val id: Int, val name: String, val owner_id: Int? = null, val owner_username: String? = null)

data class AlbumsResponse(val owned: List<AlbumDto>, val shared_with_me: List<AlbumDto>)

data class CreateAlbumRequest(val name: String)

data class CreateAlbumResponse(val status: String, val album_id: Int, val name: String)

data class AddPhotosRequest(val photo_ids: List<Int>)

data class AlbumDetailResponse(val id: Int, val name: String, val owner_id: Int, val photos: List<RemotePhoto>)

data class ShareAlbumRequest(val target_username: String)

data class ShareAlbumResponse(val status: String, val message: String)

data class ImportPhotoRequest(val photo_id: Int)

data class ImportPhotoResponse(val status: String, val message: String?, val new_photo_id: Int? = null)

data class UserDto(val id: Int, val username: String)

data class RenameAlbumRequest(val name: String)

data class RenameAlbumResponse(val status: String, val new_name: String)

data class GenericMessageResponse(val status: String, val message: String)

data class RemovePhotosRequest(val photo_ids: List<Int>)

data class FollowResponse(val status: String, val message: String)

data class ConnectionDto(val user_id: Int, val username: String)

data class PendingRequestDto(val request_id: Int, val user_id: Int, val username: String)

interface PhotoApi {

    @POST("/photos/check-batch")
    suspend fun checkBatch(@Body body: PhotoBatchCheckRequest): PhotoBatchCheckResponse

    @POST("/photos/check")
    suspend fun check(
        @Body body: PhotoCheckRequest
    ): PhotoCheckResponse

    @Multipart
    @POST("/photos/upload")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("sha256") sha256: RequestBody,
        @Part("relative_path") relativePath: RequestBody,
        @Part("media_type") mediaType: RequestBody
    ): UploadResponse

    @GET("/photos/list")
    suspend fun getPhotos(): PhotoListResponse

    @POST("/photos/delete-batch")
    suspend fun deletePhotos(@Body request: DeletePhotosRequest)

    @GET("/albums/")
    suspend fun getAlbums(): AlbumsResponse

    @POST("/albums/create")
    suspend fun createAlbum(@Body request: CreateAlbumRequest): CreateAlbumResponse

    @POST("/albums/{album_id}/add-photos")
    suspend fun addPhotosToAlbum(
        @Path("album_id") albumId: Int,
        @Body request: AddPhotosRequest
    )

    @GET("/albums/{album_id}")
    suspend fun getAlbumDetails(@Path("album_id") albumId: Int): AlbumDetailResponse

    @POST("/albums/{album_id}/share")
    suspend fun shareAlbum(
        @Path("album_id") albumId: Int,
        @Body request: ShareAlbumRequest
    ): ShareAlbumResponse

    @POST("/albums/import-photo")
    suspend fun importSharedPhoto(
        @Body request: ImportPhotoRequest
    ): ImportPhotoResponse

    @POST("/albums/{album_id}/import-all")
    suspend fun importEntireAlbum(@Path("album_id") albumId: Int): Map<String, Any>

    @GET("/albums/available-users")
    suspend fun searchUsers(@Query("q") query: String): List<UserDto>

    @PUT("/albums/{album_id}/rename")
    suspend fun renameAlbum(
        @Path("album_id") albumId: Int,
        @Body request: RenameAlbumRequest
    ): RenameAlbumResponse

    @DELETE("/albums/{album_id}")
    suspend fun deleteAlbum(
        @Path("album_id") albumId: Int,
        @Query("delete_files") deleteFiles: Boolean
    ): GenericMessageResponse

    @GET("/albums/{album_id}/shares")
    suspend fun getAlbumShares(@Path("album_id") albumId: Int): List<UserDto>

    @DELETE("/albums/{album_id}/share/{target_user_id}")
    suspend fun unshareAlbum(
        @Path("album_id") albumId: Int,
        @Path("target_user_id") targetUserId: Int
    ): GenericMessageResponse

    @POST("/albums/{album_id}/remove-photos")
    suspend fun removePhotosFromAlbum(
        @Path("album_id") albumId: Int,
        @Body request: RemovePhotosRequest
    ): GenericMessageResponse

    // --- NETWORK & FOLLOW SYSTEM ---
    @POST("/network/follow/{target_username}")
    suspend fun sendFollowRequest(@Path("target_username") targetUsername: String): FollowResponse

    @GET("/network/requests/pending")
    suspend fun getPendingRequests(): List<PendingRequestDto>

    @POST("/network/requests/{request_id}/{action}")
    suspend fun resolveFollowRequest(
        @Path("request_id") requestId: Int,
        @Path("action") action: String // "accept" or "reject"
    ): FollowResponse

    @GET("/network/connections")
    suspend fun getConnections(): List<ConnectionDto>

    // --- ADMIN ENDPOINTS ---
    @POST("/photos/admin/backfill-gifs")
    suspend fun triggerGifBackfill(): GenericMessageResponse

    @GET("/network/requests/sent")
    suspend fun getSentRequests(): List<PendingRequestDto>

    @DELETE("/network/requests/{request_id}/cancel")
    suspend fun cancelSentRequest(@Path("request_id") requestId: Int): GenericMessageResponse

    @DELETE("/network/connections/{target_user_id}")
    suspend fun removeConnection(@Path("target_user_id") targetUserId: Int): GenericMessageResponse
}
