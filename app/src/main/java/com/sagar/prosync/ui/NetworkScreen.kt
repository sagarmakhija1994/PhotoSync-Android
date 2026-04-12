package com.sagar.prosync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.data.api.ConnectionDto
import com.sagar.prosync.data.api.PendingRequestDto
import com.sagar.prosync.data.api.PhotoApi
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

    // Fetch data on load
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            pendingRequests = api.getPendingRequests()
            connections = api.getConnections()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Network") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // --- PENDING REQUESTS SECTION ---
                item {
                    Text("Pending Requests", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    if (pendingRequests.isEmpty()) {
                        Text("No pending requests.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                    }
                }

                items(pendingRequests) { req ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null)
                                Spacer(Modifier.width(8.dp))
                                Text(req.username, style = MaterialTheme.typography.titleMedium)
                            }
                            Row {
                                // REJECT BUTTON
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        try {
                                            api.resolveFollowRequest(req.request_id, "reject")
                                            pendingRequests = pendingRequests.filter { it.request_id != req.request_id }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }) {
                                    Icon(Icons.Default.Close, "Reject", tint = MaterialTheme.colorScheme.error)
                                }

                                // ACCEPT BUTTON
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        try {
                                            api.resolveFollowRequest(req.request_id, "accept")
                                            // Instantly remove from pending and add to connections!
                                            pendingRequests = pendingRequests.filter { it.request_id != req.request_id }
                                            connections = connections + ConnectionDto(req.user_id, req.username)
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                }) {
                                    Icon(Icons.Default.Check, "Accept", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // --- MY CONNECTIONS SECTION ---
                item {
                    if (pendingRequests.isNotEmpty()) Spacer(Modifier.height(24.dp))
                    HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
                    Text("My Connections", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))

                    if (connections.isEmpty()) {
                        Text("You aren't connected to anyone yet. Share an album to search for family!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(connections) { conn ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(conn.username, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}