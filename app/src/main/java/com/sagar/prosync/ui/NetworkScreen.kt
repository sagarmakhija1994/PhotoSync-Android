package com.sagar.prosync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.api.ConnectionDto
import com.sagar.prosync.data.api.PendingRequestDto
import com.sagar.prosync.data.api.PhotoApi
import com.sagar.prosync.data.api.UserDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val api = remember { ApiClient.create(context).create(PhotoApi::class.java) }

    var pendingRequests by remember { mutableStateOf<List<PendingRequestDto>>(emptyList()) }
    var sentRequests by remember { mutableStateOf<List<PendingRequestDto>>(emptyList()) }
    var connections by remember { mutableStateOf<List<ConnectionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Global Search States
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }

    // Dialog States
    var showUnfollowDialog by remember { mutableStateOf<ConnectionDto?>(null) }
    var showCancelDialog by remember { mutableStateOf<PendingRequestDto?>(null) }

    val refreshData = {
        coroutineScope.launch {
            try {
                pendingRequests = api.getPendingRequests()
                sentRequests = api.getSentRequests()
                connections = api.getConnections()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        refreshData()
        isLoading = false
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3) {
            kotlinx.coroutines.delay(500)
            isSearching = true
            try { searchResults = api.searchUsers(searchQuery) }
            catch (e: Exception) { e.printStackTrace() }
            finally { isSearching = false }
        } else { searchResults = emptyList() }
    }

    // --- DIALOGS ---
    if (showUnfollowDialog != null) {
        AlertDialog(
            onDismissRequest = { showUnfollowDialog = null },
            title = { Text("Remove Connection?") },
            text = { Text("Are you sure you want to disconnect from ${showUnfollowDialog?.username}? You will lose access to each other's shared albums.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = showUnfollowDialog
                        showUnfollowDialog = null
                        if (target != null) {
                            coroutineScope.launch {
                                try {
                                    api.removeConnection(target.user_id)
                                    refreshData()
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    }
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showUnfollowDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showCancelDialog != null) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = null },
            title = { Text("Cancel Request?") },
            text = { Text("Cancel your follow request to ${showCancelDialog?.username}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = showCancelDialog
                        showCancelDialog = null
                        if (target != null) {
                            coroutineScope.launch {
                                try {
                                    api.cancelSentRequest(target.request_id)
                                    refreshData()
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                    }
                ) { Text("Cancel Request", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = null }) { Text("Keep") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Network") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

                // --- 1. GLOBAL SEARCH BAR ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; searchMessage = null },
                    label = { Text("Search users to connect") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (searchMessage != null) {
                    Text(searchMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }

                if (searchQuery.isNotEmpty()) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            items(searchResults) { user ->
                                val isAlreadyConnected = connections.any { it.user_id == user.id }
                                val hasPendingSent = sentRequests.any { it.user_id == user.id }

                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Person, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(user.username, style = MaterialTheme.typography.titleMedium)
                                        }
                                        if (isAlreadyConnected) {
                                            Text("Connected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        } else if (hasPendingSent) {
                                            Text("Request Sent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        } else {
                                            IconButton(onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        val res = api.sendFollowRequest(user.username)
                                                        searchMessage = res.message
                                                        searchQuery = "" // Clear search on success
                                                        refreshData() // Refresh to update Sent Requests
                                                    } catch (e: Exception) { searchMessage = "Failed to send request." }
                                                }
                                            }) { Icon(Icons.Default.PersonAdd, "Connect", tint = MaterialTheme.colorScheme.primary) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // --- NORMAL VIEW (NO SEARCH) ---
                    LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {

                        // --- 2. PENDING RECEIVED REQUESTS ---
                        if (pendingRequests.isNotEmpty()) {
                            item {
                                Text("Pending Invites", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                            }
                            items(pendingRequests) { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                            Spacer(Modifier.width(8.dp))
                                            Text(req.username, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                        Row {
                                            IconButton(onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        api.resolveFollowRequest(req.request_id, "reject")
                                                        refreshData()
                                                    } catch (e: Exception) { e.printStackTrace() }
                                                }
                                            }) { Icon(Icons.Default.Close, "Reject", tint = MaterialTheme.colorScheme.error) }

                                            IconButton(onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        api.resolveFollowRequest(req.request_id, "accept")
                                                        refreshData()
                                                    } catch (e: Exception) { e.printStackTrace() }
                                                }
                                            }) { Icon(Icons.Default.Check, "Accept", tint = MaterialTheme.colorScheme.primary) }
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        // --- 3. SENT REQUESTS ---
                        if (sentRequests.isNotEmpty()) {
                            item {
                                Text("Sent Requests", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.height(8.dp))
                            }
                            items(sentRequests) { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.secondary)
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(req.username, style = MaterialTheme.typography.titleMedium)
                                                Text("Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        IconButton(onClick = { showCancelDialog = req }) {
                                            Icon(Icons.Default.Close, "Cancel Request", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        // --- 4. CONNECTIONS ---
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("My Connections", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            if (connections.isEmpty()) {
                                Text("Search above to add family to your network!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        items(connections) { conn ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text(conn.username, style = MaterialTheme.typography.titleMedium)
                                }
                                IconButton(onClick = { showUnfollowDialog = conn }) {
                                    Icon(Icons.Default.Delete, "Remove Connection", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}