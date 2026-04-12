package com.sagar.prosync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.api.AlbumDto
import com.sagar.prosync.data.api.CreateAlbumRequest
import com.sagar.prosync.data.api.PhotoApi
import kotlinx.coroutines.launch

@Composable
fun AlbumsTab(
    refreshTrigger: Int,
    onAlbumClick: (Int) -> Unit,
    onRefreshRequested: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val api = remember { ApiClient.create(context).create(PhotoApi::class.java) }

    var myAlbums by remember { mutableStateOf<List<AlbumDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Dialog State
    var showCreateDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }

    // Fetch Albums
    LaunchedEffect(refreshTrigger) {
        try {
            isLoading = true
            val response = api.getAlbums()
            myAlbums = response.owned
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (myAlbums.isEmpty()) {
            Text(
                "No albums yet. Create one!",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(myAlbums) { album ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                onAlbumClick(album.id)
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(album.name, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }

        // Floating Action Button to Create Album
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Album")
        }

        // Create Album Dialog
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreateDialog = false
                    newAlbumName = ""
                },
                title = { Text("New Album") },
                text = {
                    OutlinedTextField(
                        value = newAlbumName,
                        onValueChange = { newAlbumName = it },
                        label = { Text("Album Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        enabled = newAlbumName.isNotBlank(),
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    api.createAlbum(CreateAlbumRequest(newAlbumName))
                                    showCreateDialog = false
                                    newAlbumName = ""
                                    onRefreshRequested() // Reload the list!
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCreateDialog = false
                        newAlbumName = ""
                    }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun SharedTab(onAlbumClick: (Int) -> Unit) {
    val context = LocalContext.current
    val api = remember { ApiClient.create(context).create(PhotoApi::class.java) }

    var sharedAlbums by remember { mutableStateOf<List<AlbumDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val response = api.getAlbums()
            sharedAlbums = response.shared_with_me
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (sharedAlbums.isEmpty()) {
            Text(
                "No one has shared any albums with you yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                items(sharedAlbums) { album ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onAlbumClick(album.id) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.People,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(album.name, style = MaterialTheme.typography.titleLarge)
                                Text("Shared by ${album.owner_username ?: "Unknown"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}