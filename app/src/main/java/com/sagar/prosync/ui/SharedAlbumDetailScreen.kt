package com.sagar.prosync.ui

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.data.api.AlbumDetailResponse
import com.sagar.prosync.data.api.ImportPhotoRequest
import com.sagar.prosync.data.api.PhotoApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SharedAlbumDetailScreen(
    albumId: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionStore = remember { SessionStore(context) }
    val api = remember { ApiClient.create(context).create(PhotoApi::class.java) }
    val token = sessionStore.getToken() ?: ""

    var albumDetails by remember { mutableStateOf<AlbumDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var viewingPhotoIndex by remember { mutableStateOf<Int?>(null) }

    // Selection State for Importing
    val selectedPhotoIds = remember { mutableStateListOf<Int>() }
    var isImporting by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val settingsStore = remember { SettingsStore(context) }
    val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        settingsStore.gridColumnsLandscape
    } else {
        settingsStore.gridColumnsPortrait
    }

    // --- DYNAMIC URL RESOLUTION ---
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

    LaunchedEffect(albumId) {
        try {
            albumDetails = api.getAlbumDetails(albumId)
        } catch (e: Exception) { e.printStackTrace() }
        finally { isLoading = false }
    }

    Scaffold(
        topBar = {
            if (selectedPhotoIds.isNotEmpty()) {
                // --- MULTI-SELECTION MODE ---
                TopAppBar(
                    title = { Text("${selectedPhotoIds.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedPhotoIds.clear() }) { Icon(Icons.Default.Close, "Cancel") }
                    },
                    actions = {
                        // Select All Button
                        IconButton(onClick = {
                            albumDetails?.photos?.let { photos ->
                                selectedPhotoIds.clear()
                                selectedPhotoIds.addAll(photos.map { it.id })
                            }
                        }) {
                            Icon(Icons.Default.DoneAll, "Select All")
                        }

                        // Import Selected Photos Button
                        IconButton(onClick = {
                            coroutineScope.launch {
                                isImporting = true
                                var successCount = 0
                                for (photoId in selectedPhotoIds) {
                                    try {
                                        api.importSharedPhoto(ImportPhotoRequest(photoId))
                                        successCount++
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                                isImporting = false
                                android.widget.Toast.makeText(context, "Imported $successCount photos!", android.widget.Toast.LENGTH_LONG).show()
                                selectedPhotoIds.clear()
                            }
                        }) {
                            Icon(Icons.Default.CloudDownload, "Import to My Space", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                // --- NORMAL MODE (Nothing Selected) ---
                TopAppBar(
                    title = { Text(albumDetails?.name ?: "Shared Album") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back") } },
                    actions = {
                        // CLONE ENTIRE ALBUM BUTTON
                        IconButton(onClick = {
                            coroutineScope.launch {
                                isImporting = true // This locks the screen!
                                try {
                                    api.importEntireAlbum(albumId)
                                    android.widget.Toast.makeText(context, "Album & photos cloned to your space!", android.widget.Toast.LENGTH_LONG).show()
                                    onClose()
                                } catch (e: Exception) { e.printStackTrace() }
                                finally { isImporting = false }
                            }
                        }) {
                            Icon(Icons.Default.DriveFileMove, "Clone Album to My Space", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albumDetails?.photos ?: emptyList()) { photo ->
                        val isSelected = selectedPhotoIds.contains(photo.id)

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (selectedPhotoIds.isNotEmpty()) {
                                            if (isSelected) selectedPhotoIds.remove(photo.id) else selectedPhotoIds.add(photo.id)
                                        } else { viewingPhotoIndex = albumDetails?.photos?.indexOf(photo) }
                                    },
                                    onLongClick = {
                                        if (!isSelected) selectedPhotoIds.add(photo.id)
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("${activeBaseUrl}photos/file/${photo.id}?thumbnail=true") // UPDATED HERE
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
                                Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(4.dp).size(24.dp).align(Alignment.TopStart)
                                        .background(Color.White, CircleShape).border(1.dp, Color.White, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }

    // Photo Viewer Overlay
    viewingPhotoIndex?.let { initialIndex ->
        PhotoViewerScreen(
            photos = albumDetails?.photos ?: emptyList(),
            initialIndex = initialIndex,
            activeBaseUrl = activeBaseUrl,
            token = token,
            onClose = { viewingPhotoIndex = null }
        )
    }

    // PROCESSING OVERLAY (Blocks UI interactions!)
    if (isImporting) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
            Card(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(16.dp))
                    Text("Importing files...", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}