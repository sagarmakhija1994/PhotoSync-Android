package com.sagar.prosync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.sync.SyncWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val workManager = remember { WorkManager.getInstance(context) }

    // Observers to track the actual background job state
    val workInfos by workManager.getWorkInfosForUniqueWorkLiveData("ManualPhotoSyncJob").observeAsState()

    // Derived state: Is it currently running?
    val isSyncing = workInfos?.any {
        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
    } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PhotoSync") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (settingsStore.autoSyncEnabled) "Auto-Sync is ON" else "Auto-Sync is OFF",
                            fontWeight = FontWeight.Bold,
                            color = if (settingsStore.autoSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isSyncing) "Syncing now..." else "Ready",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // The Manual Sync Icon Button
                    IconButton(
                        enabled = !isSyncing,
                        onClick = {
                            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                                .setInputData(workDataOf(
                                    "SYNC_PHOTOS" to settingsStore.syncPhotos,
                                    "SYNC_VIDEOS" to settingsStore.syncVideos
                                ))
                                .build()

                            // Using enqueueUniqueWork allows us to observe it by name!
                            workManager.enqueueUniqueWork(
                                "ManualPhotoSyncJob",
                                ExistingWorkPolicy.REPLACE,
                                syncRequest
                            )
                        }
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Now")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Your devices and gallery will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}