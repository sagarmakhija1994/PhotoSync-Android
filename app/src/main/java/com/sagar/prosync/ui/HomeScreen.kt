package com.sagar.prosync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.data.api.PhotoApi
import com.sagar.prosync.data.api.RemotePhoto
import com.sagar.prosync.sync.SyncWorker
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val sessionStore = remember { SessionStore(context) }
    val workManager = remember { WorkManager.getInstance(context) }

    // API setup
    val api = remember { ApiClient.create(context).create(PhotoApi::class.java) }

    // State for the gallery
    var photos by remember { mutableStateOf<List<RemotePhoto>>(emptyList()) }
    var isLoadingGallery by remember { mutableStateOf(true) }

    // Observers to track the actual background job state
    val workInfos by workManager.getWorkInfosForUniqueWorkLiveData("ManualPhotoSyncJob").observeAsState()

    // Derived state: Is it currently running?
    val isSyncing = workInfos?.any {
        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
    } == true

    var viewingPhotoId by remember { mutableStateOf<Int?>(null) }

    // Fetch photos when the screen loads, or when a sync finishes!
    LaunchedEffect(isSyncing) {
        if (!isSyncing) {
            try {
                isLoadingGallery = true
                val response = api.getPhotos()
                photos = response.photos
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "Failed to fetch gallery", e)
            } finally {
                isLoadingGallery = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PhotoSync") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (settingsStore.autoSyncEnabled) "Auto-Sync is ON" else "Auto-Sync is OFF",
                            fontWeight = FontWeight.Bold,
                            color = if (settingsStore.autoSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isSyncing) "Syncing now..." else "Ready",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // The Manual Sync Icon Button
                    IconButton(
                        enabled = !isSyncing,
                        onClick = {
                            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                                .setInputData(workDataOf(
                                    "SYNC_PHOTOS" to settingsStore.syncPhotos,
                                    "SYNC_VIDEOS" to settingsStore.syncVideos
                                ))
                                .build()

                            // Using enqueueUniqueWork allows us to observe it by name!
                            workManager.enqueueUniqueWork(
                                "ManualPhotoSyncJob",
                                ExistingWorkPolicy.REPLACE,
                                syncRequest
                            )
                        }
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Now")
                        }
                    }
                }
            }
        }
    ) { padding ->
        // --- THE NEW NATIVE GALLERY UI ---
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoadingGallery) {
                CircularProgressIndicator()
            } else if (photos.isEmpty()) {
                Text(
                    text = "No photos uploaded yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(photos) { photo ->
                        val token = sessionStore.getToken() ?: ""
                        val imageUrl = "http://192.168.0.181:8000/photos/file/${photo.id}?thumbnail=true"

                        // Coil 2.6.0 Image Request with your JWT Token
                        val imageRequest = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .addHeader("Authorization", "Bearer $token")
                            .crossfade(true)
                            .build()

                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "Photo from ${photo.device_name}",
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .clickable { viewingPhotoId = photo.id },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
    viewingPhotoId?.let { photoId ->
        PhotoViewerScreen(
            photoId = photoId,
            token = sessionStore.getToken() ?: "",
            onClose = { viewingPhotoId = null }
        )
    }
}