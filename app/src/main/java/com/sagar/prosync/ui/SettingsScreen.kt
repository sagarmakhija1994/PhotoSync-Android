package com.sagar.prosync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.sync.SyncWorker
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val workManager = WorkManager.getInstance(context)

    // Local state for UI re-composition
    var syncPhotos by remember { mutableStateOf(settingsStore.syncPhotos) }
    var syncVideos by remember { mutableStateOf(settingsStore.syncVideos) }
    var useCellular by remember { mutableStateOf(settingsStore.useCellular) }
    var autoSyncEnabled by remember { mutableStateOf(settingsStore.autoSyncEnabled) }

    // Helper to toggle auto-sync in WorkManager
    val toggleAutoSync = { enabled: Boolean ->
        autoSyncEnabled = enabled
        settingsStore.autoSyncEnabled = enabled

        if (enabled) {
            // Schedule Daily Sync
            val constraints = Constraints.Builder().apply {
                setRequiresBatteryNotLow(true)
                // Determine network type based on user setting
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
            // CANCEL Auto Sync
            workManager.cancelUniqueWork("DailyPhotoSyncJob")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            Text("Media Types", style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Include Photos")
                Switch(checked = syncPhotos, onCheckedChange = {
                    syncPhotos = it
                    settingsStore.syncPhotos = it
                    if (autoSyncEnabled) toggleAutoSync(true) // Update existing worker data
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

            Divider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Network & Automation", style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Use Cellular Data")
                    Text("If off, syncs only on WiFi", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = useCellular, onCheckedChange = {
                    useCellular = it
                    settingsStore.useCellular = it
                    if (autoSyncEnabled) toggleAutoSync(true) // Rebuild worker with new network constraints
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

            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Dashboard")
            }
        }
    }
}