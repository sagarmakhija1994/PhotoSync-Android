package com.sagar.prosync.ui

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.sagar.prosync.data.api.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    var albumDetails by remember { mutableStateOf<AlbumDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) } // Global Loading Overlay!
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Viewing & Selection States
    var viewingPhotoId by remember { mutableStateOf<Int?>(null) }
    var showAddPhotosPicker by remember { mutableStateOf(false) }
    val selectedPhotoIds = remember { mutableStateListOf<Int>() }

    // Dialog States
    var showMenu by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var shareMessage by remember { mutableStateOf<String?>(null) }
    var availableUsers by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var myConnections by remember { mutableStateOf<List<ConnectionDto>>(emptyList()) }
    var isLoadingConnections by remember { mutableStateOf(false) }
    var isLoadingSearch by remember { mutableStateOf(false) }
    var showManageSharesDialog by remember { mutableStateOf(false) }
    var sharedWithUsers by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var isLoadingShares by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteFilesPermanently by remember { mutableStateOf(false) }

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

    LaunchedEffect(albumId, refreshTrigger) {
        try {
            isLoading = true
            albumDetails = api.getAlbumDetails(albumId)
        } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3) {
            kotlinx.coroutines.delay(500)
            isLoadingSearch = true
            try { availableUsers = api.searchUsers(searchQuery) }
            catch (e: Exception) { e.printStackTrace() } finally { isLoadingSearch = false }
        } else { availableUsers = emptyList() }
    }

    Scaffold(
        topBar = {
            if (selectedPhotoIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedPhotoIds.size} Selected") },
                    navigationIcon = { IconButton(onClick = { selectedPhotoIds.clear() }) { Icon(Icons.Default.Close, "Cancel") } },
                    actions = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                try {
                                    api.removePhotosFromAlbum(albumId, RemovePhotosRequest(selectedPhotoIds.toList()))
                                    selectedPhotoIds.clear()
                                    refreshTrigger++
                                } catch (e: Exception) { e.printStackTrace() }
                                finally { isProcessing = false }
                            }
                        }) { Icon(Icons.Default.RemoveCircleOutline, "Remove from Album", tint = MaterialTheme.colorScheme.error) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                TopAppBar(
                    title = { Text(albumDetails?.name ?: "Loading Album...") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { showShareDialog = true
                            searchQuery = ""
                            availableUsers = emptyList()
                            shareMessage = null
                            coroutineScope.launch {
                                isLoadingConnections = true
                                try { myConnections = api.getConnections() }
                                catch (e: Exception) { e.printStackTrace() }
                                finally { isLoadingConnections = false }
                            }}) {
                            Icon(Icons.Default.PersonAdd, "Share Album")
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename Album") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { showMenu = false; newAlbumName = albumDetails?.name ?: ""; showRenameDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Manage Access") },
                                    leadingIcon = { Icon(Icons.Default.People, null) },
                                    onClick = {
                                        showMenu = false; showManageSharesDialog = true
                                        coroutineScope.launch {
                                            isLoadingShares = true
                                            try { sharedWithUsers = api.getAlbumShares(albumId) }
                                            catch (e: Exception) { e.printStackTrace() }
                                            finally { isLoadingShares = false }
                                        }
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete Album", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; showDeleteDialog = true }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (selectedPhotoIds.isEmpty()) {
                FloatingActionButton(onClick = { showAddPhotosPicker = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "Add Photos")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (albumDetails?.photos.isNullOrEmpty()) {
                Text("This album is empty. Tap + to add photos!", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albumDetails!!.photos) { photo ->
                        val isSelected = selectedPhotoIds.contains(photo.id)
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
                                    .data("${activeBaseUrl}photos/file/${photo.id}?thumbnail=true") // UPDATED HERE
                                    .addHeader("Authorization", "Bearer $token")
                                    .crossfade(true).build(),
                                contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().padding(if (isSelected) 8.dp else 0.dp).clip(if (isSelected) MaterialTheme.shapes.medium else MaterialTheme.shapes.extraSmall)
                            )
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(4.dp).size(24.dp).align(Alignment.TopStart).background(Color.White, CircleShape).border(1.dp, Color.White, CircleShape))
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    Card(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(16.dp))
                            Text("Processing...", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    viewingPhotoId?.let { photoId -> PhotoViewerScreen(photoId = photoId, token = token, onClose = { viewingPhotoId = null }) }
    if (showAddPhotosPicker) { AddPhotosPickerOverlay(albumId = albumId, api = api, token = token, onClose = { showAddPhotosPicker = false }, onPhotosAdded = { showAddPhotosPicker = false; refreshTrigger++ }) }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false; shareMessage = null },
            title = { Text("Share Album") },
            text = {
                Column {
                    if (shareMessage != null) {
                        Text(shareMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))

                    if (searchQuery.isEmpty()) {
                        Text("My Network", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))

                        if (isLoadingConnections) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else if (myConnections.isEmpty()) {
                            Text("You aren't following anyone yet. Search above to find family!", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(myConnections) { conn ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                            coroutineScope.launch {
                                                try {
                                                    api.shareAlbum(albumId, ShareAlbumRequest(conn.username))
                                                    shareMessage = "Shared with ${conn.username}!"
                                                    kotlinx.coroutines.delay(1500)
                                                    showShareDialog = false; shareMessage = null
                                                } catch (e: Exception) { shareMessage = "Error sharing album." }
                                            }
                                        },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(12.dp))
                                                Text(conn.username, style = MaterialTheme.typography.titleMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (isLoadingSearch) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else if (searchQuery.length < 3) {
                            Text("Type 3 chars to search entire server.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(availableUsers) { serverUser ->
                                    val isKnown = myConnections.any { it.user_id == serverUser.id }

                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                            coroutineScope.launch {
                                                try {
                                                    api.shareAlbum(albumId, ShareAlbumRequest(serverUser.username))
                                                    shareMessage = "Shared with ${serverUser.username}!"
                                                    kotlinx.coroutines.delay(1500)
                                                    showShareDialog = false; shareMessage = null
                                                } catch (e: Exception) { shareMessage = "Error sharing album." }
                                            }
                                        },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Person, null, tint = if (isKnown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(Modifier.width(12.dp))
                                                Text(serverUser.username, style = MaterialTheme.typography.titleMedium)
                                            }
                                            if (isKnown) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text("Following", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { }, dismissButton = { TextButton(onClick = { showShareDialog = false }) { Text("Cancel") } }
        )
    }

    if (showManageSharesDialog) {
        AlertDialog(
            onDismissRequest = { showManageSharesDialog = false },
            title = { Text("Manage Access") },
            text = {
                if (isLoadingShares) { CircularProgressIndicator() }
                else if (sharedWithUsers.isEmpty()) { Text("This album isn't shared with anyone yet.") }
                else {
                    LazyColumn {
                        items(sharedWithUsers) { user ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, null); Spacer(Modifier.width(8.dp)); Text(user.username, style = MaterialTheme.typography.bodyLarge)
                                }
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        try {
                                            api.unshareAlbum(albumId, user.id)
                                            sharedWithUsers = sharedWithUsers.filter { it.id != user.id }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }) { Icon(Icons.Default.PersonRemove, "Revoke Access", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showManageSharesDialog = false }) { Text("Done") } }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false }, title = { Text("Rename Album") },
            text = { OutlinedTextField(value = newAlbumName, onValueChange = { newAlbumName = it }, label = { Text("New Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(
                    enabled = newAlbumName.isNotBlank(),
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val response = api.renameAlbum(albumId, RenameAlbumRequest(newAlbumName))
                                albumDetails = albumDetails?.copy(name = response.new_name)
                                showRenameDialog = false
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false }, title = { Text("Delete Album") },
            text = {
                Column {
                    Text("Are you sure you want to delete this album? This will remove the folder structure.")
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteFilesPermanently, onCheckedChange = { deleteFilesPermanently = it })
                        Text("Permanently delete physical files", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        isProcessing = true
                        showDeleteDialog = false
                        try {
                            api.deleteAlbum(albumId, deleteFilesPermanently)
                            onClose()
                        } catch (e: Exception) { e.printStackTrace(); isProcessing = false }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

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
                    columns = GridCells.Fixed(columns),
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