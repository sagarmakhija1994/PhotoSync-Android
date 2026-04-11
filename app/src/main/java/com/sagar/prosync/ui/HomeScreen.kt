package com.sagar.prosync.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.data.api.*
import com.sagar.prosync.sync.SyncWorker
import kotlinx.coroutines.launch

enum class AppTab { PHOTOS, ALBUMS, SHARED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val sessionStore = remember { SessionStore(context) }
    val workManager = remember { WorkManager.getInstance(context) }
    val api = remember { ApiClient.create(context).create(PhotoApi::class.java) }

    var currentTab by remember { mutableStateOf(AppTab.PHOTOS) }
    var photos by remember { mutableStateOf<List<RemotePhoto>>(emptyList()) }
    var isLoadingGallery by remember { mutableStateOf(true) }
    var viewingPhotoId by remember { mutableStateOf<Int?>(null) }

    val selectedPhotoIds = remember { mutableStateListOf<Int>() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // --- NEW: Add to Album Dialog State ---
    var showAddToAlbumDialog by remember { mutableStateOf(false) }
    var availableAlbums by remember { mutableStateOf<List<AlbumDto>>(emptyList()) }
    var isLoadingAlbums by remember { mutableStateOf(false) }

    var viewingAlbumId by remember { mutableStateOf<Int?>(null) }

    val workInfos by workManager.getWorkInfosForUniqueWorkLiveData("ManualPhotoSyncJob").observeAsState()
    val isSyncing = workInfos?.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } == true

    LaunchedEffect(isSyncing, refreshTrigger) {
        if (!isSyncing) {
            try {
                isLoadingGallery = true
                photos = api.getPhotos().photos
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingGallery = false
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
                        // THE NEW ADD TO ALBUM BUTTON
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
                        Text(when(currentTab) {
                            AppTab.PHOTOS -> "PhotoSync"
                            AppTab.ALBUMS -> "My Albums"
                            AppTab.SHARED -> "Shared Space"
                        })
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Settings") }
                    }
                )
            }
        },
        bottomBar = {
            Column {
                if (currentTab == AppTab.PHOTOS) {
                    Surface(tonalElevation = 8.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (settingsStore.autoSyncEnabled) "Auto-Sync is ON" else "Auto-Sync is OFF",
                                    fontWeight = FontWeight.Bold,
                                    color = if (settingsStore.autoSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(text = if (isSyncing) "Syncing now..." else "Ready", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(
                                enabled = !isSyncing,
                                onClick = {
                                    val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                                        .setInputData(workDataOf("SYNC_PHOTOS" to settingsStore.syncPhotos, "SYNC_VIDEOS" to settingsStore.syncVideos))
                                        .build()
                                    workManager.enqueueUniqueWork("ManualPhotoSyncJob", ExistingWorkPolicy.REPLACE, syncRequest)
                                }
                            ) {
                                if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Refresh, "Sync Now")
                            }
                        }
                    }
                }

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
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(photos) { photo ->
                                val isSelected = selectedPhotoIds.contains(photo.id)
                                val token = sessionStore.getToken() ?: ""

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .padding(1.dp)
                                        .combinedClickable(
                                            onClick = {
                                                if (selectedPhotoIds.isNotEmpty()) {
                                                    if (isSelected) selectedPhotoIds.remove(photo.id) else selectedPhotoIds.add(photo.id)
                                                } else {
                                                    viewingPhotoId = photo.id
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelected) selectedPhotoIds.add(photo.id)
                                            }
                                        )
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("http://192.168.0.181:8000/photos/file/${photo.id}?thumbnail=true")
                                            .addHeader("Authorization", "Bearer $token")
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(if (isSelected) 8.dp else 0.dp)
                                            .clip(if (isSelected) MaterialTheme.shapes.medium else MaterialTheme.shapes.extraSmall)
                                    )

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(4.dp).size(24.dp).align(Alignment.TopStart)
                                                .background(Color.White, CircleShape).border(1.dp, Color.White, CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                AppTab.ALBUMS -> AlbumsTab(onAlbumClick = { viewingAlbumId = it })
                AppTab.SHARED -> SharedTab()
            }
        }
    }

    // --- OVERLAYS ---
    viewingPhotoId?.let { photoId ->
        PhotoViewerScreen(
            photoId = photoId,
            token = sessionStore.getToken() ?: "",
            onClose = { viewingPhotoId = null }
        )
    }

    // --- ALBUM DETAIL OVERLAY ---
    viewingAlbumId?.let { albumId ->
        AlbumDetailScreen(
            albumId = albumId,
            onClose = { viewingAlbumId = null }
        )
    }

    // 1. Add to Album Dialog Overlay
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

    // 2. Delete Dialog Overlay
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