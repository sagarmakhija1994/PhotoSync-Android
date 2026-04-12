package com.sagar.prosync.ui

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    var viewingPhotoId by remember { mutableStateOf<Int?>(null) }

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

    val configuration = LocalConfiguration.current
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        settingsStore.gridColumnsLandscape
    } else {
        settingsStore.gridColumnsPortrait
    }
    val safeColumns = columns.coerceAtLeast(1)

    // --- FIX: Dynamic URL with your 192.168.0.181 as the bulletproof default ---
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
                        // --- UI FIX: Title and Status Stacked! ---
                        Column {
                            Text(when(currentTab) {
                                AppTab.PHOTOS -> "PhotoSync"
                                AppTab.ALBUMS -> "My Albums"
                                AppTab.SHARED -> "Shared Space"
                            })
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
                        // --- UI FIX: Sync Button moved next to Network! ---
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

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .padding(1.dp)
                                        .combinedClickable(
                                            onClick = {
                                                if (selectedPhotoIds.isNotEmpty()) {
                                                    if (isSelected) selectedPhotoIds.remove(photo.id) else selectedPhotoIds.add(photo.id)
                                                } else { viewingPhotoId = photo.id }
                                            },
                                            onLongClick = { if (!isSelected) selectedPhotoIds.add(photo.id) }
                                        )
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("${activeBaseUrl}photos/file/${photo.id}?thumbnail=true")
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
                AppTab.ALBUMS -> AlbumsTab(
                    refreshTrigger = albumsListRefreshTrigger,
                    onAlbumClick = { viewingAlbumId = it },
                    onRefreshRequested = { albumsListRefreshTrigger++ }
                )
                AppTab.SHARED -> SharedTab(onAlbumClick = { viewingSharedAlbumId = it })
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