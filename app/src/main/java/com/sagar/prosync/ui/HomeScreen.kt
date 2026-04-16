package com.sagar.prosync.ui

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.data.api.*
import com.sagar.prosync.sync.ManualUploadWorker
import com.sagar.prosync.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

enum class AppTab { PHOTOS, ALBUMS, SHARED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val sessionStore = remember { SessionStore(context) }
    val workManager = remember { WorkManager.getInstance(context) }
    val api = remember { ApiClient.create(context).create(PhotoApi::class.java) }

    var currentTab by remember { mutableStateOf(AppTab.PHOTOS) }
    var photos by remember { mutableStateOf<List<RemotePhoto>>(emptyList()) }
    var isLoadingGallery by remember { mutableStateOf(true) }
    var viewingPhotoIndex by remember { mutableStateOf<Int?>(null) }

    val selectedPhotoIds = remember { mutableStateListOf<Int>() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var showAddToAlbumDialog by remember { mutableStateOf(false) }
    var availableAlbums by remember { mutableStateOf<List<AlbumDto>>(emptyList()) }
    var isLoadingAlbums by remember { mutableStateOf(false) }

    var viewingAlbumId by remember { mutableStateOf<Int?>(null) }
    var viewingSharedAlbumId by remember { mutableStateOf<Int?>(null) }
    var albumsListRefreshTrigger by remember { mutableIntStateOf(0) }

    var showNetworkScreen by remember { mutableStateOf(false) }

    // --- WORK MANAGER STATES FOR MANUAL UPLOAD ---
    val manualUploadInfo by workManager.getWorkInfosForUniqueWorkLiveData("ManualUploadJob").observeAsState()
    val activeUploadJob = manualUploadInfo?.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    // We create an independent UI state so the overlay shows instantly!
    var forceShowUploadUI by remember { mutableStateOf(false) }

    val isManualUploading = forceShowUploadUI || activeUploadJob != null
    val manualUploadProgress = activeUploadJob?.progress?.getInt("PROGRESS", 0) ?: 0
    val manualUploadTotal = activeUploadJob?.progress?.getInt("TOTAL", 0) ?: 0

    LaunchedEffect(manualUploadInfo) {
        val finishedJob = manualUploadInfo?.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
        if (finishedJob != null) {
            val count = finishedJob.outputData.getInt("SUCCESS_COUNT", 0)
            if (count > 0) {
                refreshTrigger++
                Toast.makeText(context, "Successfully uploaded $count files!", Toast.LENGTH_SHORT).show()
            }
            forceShowUploadUI = false // Hide the UI
            workManager.pruneWork()
        }

        // Also hide UI if it fails or crashes
        if (manualUploadInfo?.any { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED } == true) {
            forceShowUploadUI = false
            Toast.makeText(context, "Upload Failed or Cancelled.", Toast.LENGTH_SHORT).show()
            workManager.pruneWork()
        }
    }

    // 1. Get the absolute maximum limit safely
    val maxSelectionLimit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Safe for Android 13+
        MediaStore.getPickImagesMaxLimit()
    } else {
        50
    }

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxSelectionLimit)
    ) { uris ->
        if (uris.isNotEmpty()) {
            // INSTANT UI FEEDBACK!
            forceShowUploadUI = true

            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    val queueFile = File(context.cacheDir, "upload_queue_${System.currentTimeMillis()}.txt")
                    queueFile.writeText(uris.joinToString("\n") { it.toString() })

                    val uploadRequest = OneTimeWorkRequestBuilder<ManualUploadWorker>()
                        .setInputData(workDataOf("QUEUE_FILE_PATH" to queueFile.absolutePath))
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()

                    workManager.enqueueUniqueWork(
                        "ManualUploadJob",
                        ExistingWorkPolicy.REPLACE,
                        uploadRequest
                    )
                }
            }
        }
    }

    // This creates a single, highly optimized ImageLoader that knows how to play GIFs
    val gifImageLoader = remember {
        ImageLoader.Builder(context)
            .memoryCache {
                coil.memory.MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("photosync_cache"))
                    .maxSizeBytes(1000L * 1024 * 1024) // 1000 MB local cache
                    .build()
            }
            .respectCacheHeaders(false)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        settingsStore.gridColumnsLandscape
    } else {
        settingsStore.gridColumnsPortrait
    }
    val safeColumns = columns.coerceAtLeast(1)

    val activeBaseUrl = remember(settingsStore.serverUrl, settingsStore.localServerUrl, settingsStore.useLocalServer) {
        var url = settingsStore.serverUrl.ifBlank { "http://127.0.0.1:8000/" }

        if (settingsStore.useLocalServer && settingsStore.localServerUrl.isNotBlank()) {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val isWifi = connManager.getNetworkCapabilities(connManager.activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (isWifi) {
                url = settingsStore.localServerUrl
            }
        }
        if (!url.endsWith("/")) "$url/" else url
    }

    val isLocalConnection = remember(activeBaseUrl, settingsStore.localServerUrl) {
        settingsStore.localServerUrl.isNotBlank() && activeBaseUrl.startsWith(settingsStore.localServerUrl)
    }

    val workInfos by workManager.getWorkInfosForUniqueWorkLiveData("ManualPhotoSyncJob").observeAsState()
    val isSyncing = workInfos?.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } == true

    LaunchedEffect(refreshTrigger) {
        try {
            if (photos.isEmpty()) isLoadingGallery = true
            photos = api.getPhotos().photos
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingGallery = false
        }
    }

    LaunchedEffect(isSyncing) {
        if (!isSyncing && photos.isNotEmpty()) {
            try {
                photos = api.getPhotos().photos
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            if (selectedPhotoIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedPhotoIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedPhotoIds.clear() }) { Icon(Icons.Default.Close, "Cancel") }
                    },
                    actions = {
                        IconButton(onClick = {
                            showAddToAlbumDialog = true
                            coroutineScope.launch {
                                isLoadingAlbums = true
                                try { availableAlbums = api.getAlbums().owned }
                                catch (e: Exception) { e.printStackTrace() }
                                finally { isLoadingAlbums = false }
                            }
                        }) { Icon(Icons.Default.LibraryAdd, "Add to Album") }

                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when(currentTab) {
                                        AppTab.PHOTOS -> "PhotoSync"
                                        AppTab.ALBUMS -> "My Albums"
                                        AppTab.SHARED -> "Shared Space"
                                    }
                                )

                                if (currentTab == AppTab.PHOTOS) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = if (isLocalConnection) Icons.Default.Wifi else Icons.Default.Cloud,
                                        contentDescription = if (isLocalConnection) "Local Network" else "Cloud Network",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isLocalConnection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (currentTab == AppTab.PHOTOS) {
                                val statusText = if (isSyncing) "Syncing now..." else if (settingsStore.autoSyncEnabled) "Auto-Sync: ON" else "Auto-Sync: OFF"
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentTab == AppTab.PHOTOS) {
                            IconButton(
                                enabled = !isSyncing,
                                onClick = {
                                    val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                                        .setInputData(workDataOf("SYNC_PHOTOS" to settingsStore.syncPhotos, "SYNC_VIDEOS" to settingsStore.syncVideos))
                                        .build()
                                    workManager.enqueueUniqueWork("ManualPhotoSyncJob", ExistingWorkPolicy.REPLACE, syncRequest)
                                }
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, "Sync Now", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        IconButton(onClick = { showNetworkScreen = true }) {
                            Icon(Icons.Default.People, "My Network")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Photo, "Photos") },
                    label = { Text("Photos") },
                    selected = currentTab == AppTab.PHOTOS,
                    onClick = { currentTab = AppTab.PHOTOS }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PhotoLibrary, "Albums") },
                    label = { Text("Albums") },
                    selected = currentTab == AppTab.ALBUMS,
                    onClick = { currentTab = AppTab.ALBUMS }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.People, "Shared") },
                    label = { Text("Shared") },
                    selected = currentTab == AppTab.SHARED,
                    onClick = { currentTab = AppTab.SHARED }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == AppTab.PHOTOS && selectedPhotoIds.isEmpty()) {
                FloatingActionButton(
                    onClick = {
                        multiplePhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Manual Upload")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentTab) {
                AppTab.PHOTOS -> {
                    if (isLoadingGallery) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (photos.isEmpty()) {
                        Text("No photos uploaded yet.", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(safeColumns),
                            contentPadding = PaddingValues(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(photos) { photo ->
                                val isSelected = selectedPhotoIds.contains(photo.id)
                                val token = sessionStore.getToken() ?: ""

                                val imageRequest = remember(photo.id, activeBaseUrl, token) {
                                    ImageRequest.Builder(context)
                                        .data("${activeBaseUrl}photos/file/${photo.id}?thumbnail=true")
                                        .addHeader("Authorization", "Bearer $token")
                                        .diskCacheKey("thumb_v1_${photo.id}")
                                        .memoryCacheKey("thumb_v1_${photo.id}")
                                        .crossfade(true)
                                        .build()
                                }

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .padding(1.dp)
                                        .combinedClickable(
                                            onClick = {
                                                if (selectedPhotoIds.isNotEmpty()) {
                                                    if (isSelected) selectedPhotoIds.remove(photo.id) else selectedPhotoIds.add(photo.id)
                                                } else {
                                                    viewingPhotoIndex = photos.indexOf(photo)
                                                }
                                            },
                                            onLongClick = { if (!isSelected) selectedPhotoIds.add(photo.id) }
                                        )
                                ) {
                                    AsyncImage(
                                        model = imageRequest,
                                        imageLoader = gifImageLoader,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(if (isSelected) 8.dp else 0.dp)
                                            .clip(if (isSelected) MaterialTheme.shapes.medium else MaterialTheme.shapes.extraSmall)
                                    )

                                    if (photo.media_type == "video") {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Video",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "Video",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(end = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .size(24.dp)
                                                .background(Color.White, CircleShape)
                                                .border(1.dp, Color.White, CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                AppTab.ALBUMS -> AlbumsTab(
                    refreshTrigger = albumsListRefreshTrigger,
                    onAlbumClick = { viewingAlbumId = it },
                    onRefreshRequested = { albumsListRefreshTrigger++ }
                )
                AppTab.SHARED -> SharedTab(onAlbumClick = { viewingSharedAlbumId = it })
            }

            // --- THE NEW NON-BLOCKING PROGRESS OVERLAY ---
            if (isManualUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .combinedClickable(onClick = {}, onLongClick = {}), // Prevents touches from passing through
                    contentAlignment = Alignment.Center
                ) {
                    Card(modifier = Modifier.padding(24.dp)) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Uploading $manualUploadProgress / $manualUploadTotal",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You can safely minimize the app.\nUpload will continue in the background.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // --- OVERLAYS ---
    viewingPhotoIndex?.let { initialIndex ->
        PhotoViewerScreen(
            photos = photos,
            initialIndex = initialIndex,
            activeBaseUrl = activeBaseUrl,
            token = sessionStore.getToken() ?: "",
            onClose = { viewingPhotoIndex = null }
        )
    }

    viewingAlbumId?.let { albumId ->
        AlbumDetailScreen(
            albumId = albumId,
            onClose = {
                viewingAlbumId = null
                albumsListRefreshTrigger++
            }
        )
    }

    viewingSharedAlbumId?.let { albumId ->
        SharedAlbumDetailScreen(
            albumId = albumId,
            onClose = { viewingSharedAlbumId = null }
        )
    }

    if (showNetworkScreen) {
        NetworkScreen(onNavigateBack = { showNetworkScreen = false })
    }

    if (showAddToAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showAddToAlbumDialog = false },
            title = { Text("Add to Album") },
            text = {
                if (isLoadingAlbums) {
                    CircularProgressIndicator()
                } else if (availableAlbums.isEmpty()) {
                    Text("You don't have any albums yet. Create one in the Albums tab first!")
                } else {
                    LazyColumn {
                        items(availableAlbums) { album ->
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            api.addPhotosToAlbum(album.id, AddPhotosRequest(selectedPhotoIds.toList()))
                                            selectedPhotoIds.clear()
                                            showAddToAlbumDialog = false
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(album.name, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = { },
            dismissButton = { TextButton(onClick = { showAddToAlbumDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Photos") },
            text = { Text("Are you sure you want to permanently delete these ${selectedPhotoIds.size} photos from the server?") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        try {
                            api.deletePhotos(DeletePhotosRequest(selectedPhotoIds.toList()))
                            selectedPhotoIds.clear()
                            showDeleteDialog = false
                            refreshTrigger++
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

// --- HELPER FUNCTION FOR MANUAL UPLOADS (Used by the Worker) ---
suspend fun processAndUploadUri(
    context: Context,
    uri: Uri,
    api: PhotoApi
): Boolean {
    return try {
        val contentResolver = context.contentResolver

        // 1. Get real file name and MIME type
        var fileName = "uploaded_file.jpg"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }

        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val mediaTypeString = if (mimeType.startsWith("video")) "video" else "photo"

        // 2. Copy the secure URI to a temporary cache file so we can hash and upload it
        val tempFile = File(context.cacheDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // 3. Calculate SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(tempFile).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

        // 4. Prepare Retrofit Multipart Data
        val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", fileName, requestFile)

        val sha256Body = sha256.toRequestBody("text/plain".toMediaTypeOrNull())

        // THIS automatically places it in the "Manual Uploads" folder on your backend!
        val pathBody = "Manual Uploads/$fileName".toRequestBody("text/plain".toMediaTypeOrNull())
        val typeBody = mediaTypeString.toRequestBody("text/plain".toMediaTypeOrNull())

        // 5. Upload!
        api.upload(file = body, sha256 = sha256Body, relativePath = pathBody, mediaType = typeBody)

        // 6. Cleanup temp file
        tempFile.delete()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}