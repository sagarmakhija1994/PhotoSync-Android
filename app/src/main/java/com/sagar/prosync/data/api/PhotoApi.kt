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

    @retrofit2.http.POST("/photos/delete-batch")
    suspend fun deletePhotos(@retrofit2.http.Body request: DeletePhotosRequest)

    @retrofit2.http.GET("/albums/")
    suspend fun getAlbums(): AlbumsResponse

    @retrofit2.http.POST("/albums/create")
    suspend fun createAlbum(@retrofit2.http.Body request: CreateAlbumRequest): CreateAlbumResponse

    @retrofit2.http.POST("/albums/{album_id}/add-photos")
    suspend fun addPhotosToAlbum(
        @retrofit2.http.Path("album_id") albumId: Int,
        @retrofit2.http.Body request: AddPhotosRequest
    )

    @retrofit2.http.GET("/albums/{album_id}")
    suspend fun getAlbumDetails(@retrofit2.http.Path("album_id") albumId: Int): AlbumDetailResponse

    @retrofit2.http.POST("/albums/{album_id}/share")
    suspend fun shareAlbum(
        @retrofit2.http.Path("album_id") albumId: Int,
        @retrofit2.http.Body request: ShareAlbumRequest
    ): ShareAlbumResponse

    @retrofit2.http.POST("/albums/import-photo")
    suspend fun importSharedPhoto(
        @retrofit2.http.Body request: ImportPhotoRequest
    ): ImportPhotoResponse

    @retrofit2.http.POST("/albums/{album_id}/import-all")
    suspend fun importEntireAlbum(@retrofit2.http.Path("album_id") albumId: Int): Map<String, Any>

    @retrofit2.http.GET("/albums/available-users")
    suspend fun searchUsers(@retrofit2.http.Query("q") query: String): List<UserDto>

    @retrofit2.http.PUT("/albums/{album_id}/rename")
    suspend fun renameAlbum(
        @retrofit2.http.Path("album_id") albumId: Int,
        @retrofit2.http.Body request: RenameAlbumRequest
    ): RenameAlbumResponse

    @retrofit2.http.DELETE("/albums/{album_id}")
    suspend fun deleteAlbum(
        @retrofit2.http.Path("album_id") albumId: Int,
        @retrofit2.http.Query("delete_files") deleteFiles: Boolean
    ): GenericMessageResponse

    @retrofit2.http.GET("/albums/{album_id}/shares")
    suspend fun getAlbumShares(@retrofit2.http.Path("album_id") albumId: Int): List<UserDto>

    @retrofit2.http.DELETE("/albums/{album_id}/share/{target_user_id}")
    suspend fun unshareAlbum(
        @retrofit2.http.Path("album_id") albumId: Int,
        @retrofit2.http.Path("target_user_id") targetUserId: Int
    ): GenericMessageResponse

    @retrofit2.http.POST("/albums/{album_id}/remove-photos")
    suspend fun removePhotosFromAlbum(
        @retrofit2.http.Path("album_id") albumId: Int,
        @retrofit2.http.Body request: RemovePhotosRequest
    ): GenericMessageResponse

    // --- NETWORK & FOLLOW SYSTEM ---
    @retrofit2.http.POST("/network/follow/{target_username}")
    suspend fun sendFollowRequest(@retrofit2.http.Path("target_username") targetUsername: String): FollowResponse

    @retrofit2.http.GET("/network/requests/pending")
    suspend fun getPendingRequests(): List<PendingRequestDto>

    @retrofit2.http.POST("/network/requests/{request_id}/{action}")
    suspend fun resolveFollowRequest(
        @retrofit2.http.Path("request_id") requestId: Int,
        @retrofit2.http.Path("action") action: String // "accept" or "reject"
    ): FollowResponse

    @retrofit2.http.GET("/network/connections")
    suspend fun getConnections(): List<ConnectionDto>
}
