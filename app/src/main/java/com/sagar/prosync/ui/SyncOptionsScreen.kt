package com.sagar.prosync.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState // Required for observation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.sagar.prosync.sync.FolderPicker
import com.sagar.prosync.sync.FolderStore
import com.sagar.prosync.sync.SyncWorker
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncOptionsScreen() {
    val context = LocalContext.current
    val folderStore = remember { FolderStore(context) }
    val workManager = remember { WorkManager.getInstance(context) }

    // 1. State for UI selections
    var syncPhotos by remember { mutableStateOf(true) }
    var syncVideos by remember { mutableStateOf(false) }

    // 2. Observe the Background Sync Work
    val syncWorkInfo by workManager.getWorkInfosForUniqueWorkLiveData("PhotoSyncJob")
        .observeAsState()

    // 3. Extract Progress Data
    val activeWork = syncWorkInfo?.firstOrNull()
    val isRunning = activeWork?.state == WorkInfo.State.RUNNING
    val progress = activeWork?.progress?.getInt("PROGRESS", 0) ?: 0
    val total = activeWork?.progress?.getInt("TOTAL", 0) ?: 0

    // 1. Define the permissions needed based on Android version
    val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    // 2. Wrap your flawless WorkManager logic into a reusable lambda
    val enqueueSyncWork = {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                "SYNC_PHOTOS" to syncPhotos,
                "SYNC_VIDEOS" to syncVideos
            ))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            "PhotoSyncJob",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    // 3. Create the Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        // Check if all requested permissions were granted
        val allGranted = permissionsMap.values.all { it == true }

        if (allGranted) {
            // PERMISSION GRANTED! Fire your WorkManager logic
            enqueueSyncWork()
        } else {
            android.util.Log.e("ProSync", "User denied media permissions! Cannot sync.")
            // Optional: Trigger a Toast or Snackbar here to tell the user why it didn't start
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        FolderPicker.persistPermission(context, uri)
        folderStore.save(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PhotoSync Options") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text("Storage Configuration", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { FolderPicker.launch(launcher) }
            ) {
                Text("Select Folders to Sync")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            Text("Sync Preferences", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = syncPhotos, onCheckedChange = { syncPhotos = it })
                Text("Include Photos")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = syncVideos, onCheckedChange = { syncVideos = it })
                Text("Include Videos")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Progress Section: Only visible when syncing
            if (isRunning) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Text(
                        text = "Syncing in Progress...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = if (total > 0) progress.toFloat() / total else 0f,
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Text(
                        text = "Uploaded $progress of $total items",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    )
                }
            }

            // 5. Start Sync Button
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning && (syncPhotos || syncVideos),
                onClick = {
                    permissionLauncher.launch(mediaPermissions)
                }
            ) {
                Text(if (isRunning) "Syncing..." else "Start Background Sync")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Note: Syncing is optimized for Wi-Fi to save mobile data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}