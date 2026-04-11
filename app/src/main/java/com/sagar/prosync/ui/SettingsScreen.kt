package com.sagar.prosync.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.sync.FolderPicker
import com.sagar.prosync.sync.FolderStore
import com.sagar.prosync.sync.SyncWorker
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val folderStore = remember { FolderStore(context) }
    val workManager = WorkManager.getInstance(context)

    var syncPhotos by remember { mutableStateOf(settingsStore.syncPhotos) }
    var syncVideos by remember { mutableStateOf(settingsStore.syncVideos) }
    var useCellular by remember { mutableStateOf(settingsStore.useCellular) }
    var autoSyncEnabled by remember { mutableStateOf(settingsStore.autoSyncEnabled) }

    // Initializing with your getAll()
    var selectedFolders by remember { mutableStateOf(folderStore.getAll().toList()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        FolderPicker.persistPermission(context, uri)

        folderStore.save(uri)
        selectedFolders = folderStore.getAll().toList()
    }

    val toggleAutoSync = { enabled: Boolean ->
        autoSyncEnabled = enabled
        settingsStore.autoSyncEnabled = enabled

        if (enabled) {
            val constraints = Constraints.Builder().apply {
                setRequiresBatteryNotLow(true)
                setRequiredNetworkType(if (useCellular) NetworkType.CONNECTED else NetworkType.UNMETERED)
            }.build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInputData(workDataOf(
                    "SYNC_PHOTOS" to syncPhotos,
                    "SYNC_VIDEOS" to syncVideos
                ))
                .build()

            workManager.enqueueUniquePeriodicWork(
                "DailyPhotoSyncJob",
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        } else {
            workManager.cancelUniqueWork("DailyPhotoSyncJob")
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Backup Folders", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { FolderPicker.launch(launcher) }) {
                    Text("+ Add")
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(selectedFolders) { uriString ->
                    val uri = Uri.parse(uriString)
                    val displayName = uri.lastPathSegment?.substringAfterLast(":") ?: "Unknown Folder"

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = {
                            folderStore.remove(uri)
                            selectedFolders = folderStore.getAll().toList()
                        }) {
                            Icon(Icons.Default.Delete, "Remove", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text("Media Types", style = MaterialTheme.typography.titleMedium)

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Include Photos")
                        Switch(checked = syncPhotos, onCheckedChange = {
                            syncPhotos = it
                            settingsStore.syncPhotos = it
                            if (autoSyncEnabled) toggleAutoSync(true)
                        })
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Include Videos")
                        Switch(checked = syncVideos, onCheckedChange = {
                            syncVideos = it
                            settingsStore.syncVideos = it
                            if (autoSyncEnabled) toggleAutoSync(true)
                        })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text("Network & Automation", style = MaterialTheme.typography.titleMedium)

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Use Cellular Data")
                            Text("If off, syncs only on WiFi", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = useCellular, onCheckedChange = {
                            useCellular = it
                            settingsStore.useCellular = it
                            if (autoSyncEnabled) toggleAutoSync(true)
                        })
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Daily Auto-Sync")
                            Text("Syncs in the background", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { toggleAutoSync(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Back to Dashboard")
            }
        }
    }
}