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
    var connections by remember { mutableStateOf<List<ConnectionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // --- NEW: Global Search States ---
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            isLoading = true
            pendingRequests = api.getPendingRequests()
            connections = api.getConnections()
        } catch (e: Exception) { e.printStackTrace() }
        finally { isLoading = false }
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
                    label = { Text("Search users to follow") },
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
                                            Text("Following", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            IconButton(onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        val res = api.sendFollowRequest(user.username)
                                                        searchMessage = res.message
                                                        searchQuery = "" // Clear search on success
                                                    } catch (e: Exception) { searchMessage = "Failed to send request." }
                                                }
                                            }) { Icon(Icons.Default.PersonAdd, "Follow", tint = MaterialTheme.colorScheme.primary) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // --- 2. PENDING REQUESTS ---
                    LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
                        item {
                            Text("Pending Requests", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            if (pendingRequests.isEmpty()) {
                                Text("No pending requests.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(16.dp))
                            }
                        }

                        items(pendingRequests) { req ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(req.username, style = MaterialTheme.typography.titleMedium)
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    api.resolveFollowRequest(req.request_id, "reject")
                                                    pendingRequests = pendingRequests.filter { it.request_id != req.request_id }
                                                } catch (e: Exception) { e.printStackTrace() }
                                            }
                                        }) { Icon(Icons.Default.Close, "Reject", tint = MaterialTheme.colorScheme.error) }

                                        IconButton(onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    api.resolveFollowRequest(req.request_id, "accept")
                                                    pendingRequests = pendingRequests.filter { it.request_id != req.request_id }
                                                    connections = connections + ConnectionDto(req.user_id, req.username)
                                                } catch (e: Exception) { e.printStackTrace() }
                                            }
                                        }) { Icon(Icons.Default.Check, "Accept", tint = MaterialTheme.colorScheme.primary) }
                                    }
                                }
                            }
                        }

                        // --- 3. CONNECTIONS ---
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                            Text("My Connections", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            if (connections.isEmpty()) {
                                Text("Search above to add family to your network!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        items(connections) { conn ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(conn.username, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}