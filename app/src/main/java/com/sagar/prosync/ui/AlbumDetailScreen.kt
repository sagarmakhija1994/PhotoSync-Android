package com.sagar.prosync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.data.api.AddPhotosRequest
import com.sagar.prosync.data.api.AlbumDetailResponse
import com.sagar.prosync.data.api.PhotoApi
import com.sagar.prosync.data.api.RemotePhoto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionStore = remember { SessionStore(context) }
    val api = remember { ApiClient.create(context).create(PhotoApi::class.java) }
    val token = sessionStore.getToken() ?: ""

    // Album State
    var albumDetails by remember { mutableStateOf<AlbumDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Viewing & Picker States
    var viewingPhotoId by remember { mutableStateOf<Int?>(null) }
    var showAddPhotosPicker by remember { mutableStateOf(false) }

    // Fetch Album Content
    LaunchedEffect(albumId, refreshTrigger) {
        try {
            isLoading = true
            albumDetails = api.getAlbumDetails(albumId)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(albumDetails?.name ?: "Loading Album...") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPhotosPicker = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Photos")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (albumDetails?.photos.isNullOrEmpty()) {
                Text(
                    "This album is empty. Tap + to add photos!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // --- THE ALBUM'S PHOTO GRID ---
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albumDetails!!.photos) { photo ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("http://192.168.0.181:8000/photos/file/${photo.id}?thumbnail=true")
                                .addHeader("Authorization", "Bearer $token")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .clickable { viewingPhotoId = photo.id }
                        )
                    }
                }
            }
        }
    }

    // --- OVERLAY 1: View Full Photo ---
    viewingPhotoId?.let { photoId ->
        PhotoViewerScreen(
            photoId = photoId,
            token = token,
            onClose = { viewingPhotoId = null }
        )
    }

    // --- OVERLAY 2: Route B "Add Photos" Picker ---
    if (showAddPhotosPicker) {
        AddPhotosPickerOverlay(
            albumId = albumId,
            api = api,
            token = token,
            onClose = { showAddPhotosPicker = false },
            onPhotosAdded = {
                showAddPhotosPicker = false
                refreshTrigger++ // Reload the album!
            }
        )
    }
}

// --- The Helper Picker Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPhotosPickerOverlay(
    albumId: Int,
    api: PhotoApi,
    token: String,
    onClose: () -> Unit,
    onPhotosAdded: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var allPhotos by remember { mutableStateOf<List<RemotePhoto>>(emptyList()) }
    val selectedPhotoIds = remember { mutableStateListOf<Int>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            allPhotos = api.getPhotos().photos
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedPhotoIds.isEmpty()) "Select Photos" else "${selectedPhotoIds.size} Selected") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Cancel") }
                },
                actions = {
                    if (selectedPhotoIds.isNotEmpty()) {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                try {
                                    api.addPhotosToAlbum(albumId, AddPhotosRequest(selectedPhotoIds.toList()))
                                    onPhotosAdded()
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }) {
                            Text("Add", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(allPhotos) { photo ->
                        val isSelected = selectedPhotoIds.contains(photo.id)

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .clickable {
                                    if (isSelected) selectedPhotoIds.remove(photo.id) else selectedPhotoIds.add(photo.id)
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("http://192.168.0.181:8000/photos/file/${photo.id}?thumbnail=true")
                                    .addHeader("Authorization", "Bearer $token")
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
                                    Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(4.dp).size(24.dp).align(Alignment.TopStart)
                                        .background(Color.White, CircleShape).border(1.dp, Color.White, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}