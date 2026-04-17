package com.sagar.prosync.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import com.sagar.prosync.data.ApiClient
import com.sagar.prosync.data.SessionStore
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.data.api.PhotoApi
import com.sagar.prosync.sync.FolderPicker
import com.sagar.prosync.sync.FolderStore
import com.sagar.prosync.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val sessionStore = remember { SessionStore(context) }
    val folderStore = remember { FolderStore(context) }
    val workManager = WorkManager.getInstance(context)
    val api = remember { ApiClient.create(context).create(PhotoApi::class.java) }

    var syncPhotos by remember { mutableStateOf(settingsStore.syncPhotos) }
    var syncVideos by remember { mutableStateOf(settingsStore.syncVideos) }
    var useCellular by remember { mutableStateOf(settingsStore.useCellular) }
    var autoSyncEnabled by remember { mutableStateOf(settingsStore.autoSyncEnabled) }

    // Grid States
    var columnsPortrait by remember { mutableIntStateOf(settingsStore.gridColumnsPortrait) }
    var columnsLandscape by remember { mutableIntStateOf(settingsStore.gridColumnsLandscape) }

    // Server Connection States
    var mainServerUrl by remember { mutableStateOf(settingsStore.serverUrl) }
    var localServerUrl by remember { mutableStateOf(settingsStore.localServerUrl) }
    var useLocalServer by remember { mutableStateOf(settingsStore.useLocalServer) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testConnectionResult by remember { mutableStateOf<String?>(null) }
    var testConnectionSuccess by remember { mutableStateOf(false) }

    // 💥 NEW: Bottom Version State
    var serverVersion by remember { mutableStateOf("Connecting...") }

    var selectedFolders by remember { mutableStateOf(folderStore.getAll().toList()) }
    var isBackfilling by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        FolderPicker.persistPermission(context, uri)
        folderStore.save(uri)
        selectedFolders = folderStore.getAll().toList()
    }

    // 💥 NEW: Silently fetch the version when the screen opens
    LaunchedEffect(Unit) {
        val urlToTest = if (useLocalServer && localServerUrl.isNotBlank()) localServerUrl else mainServerUrl
        if (urlToTest.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(3, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url(urlToTest.trimEnd('/') + "/server-info")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val json = JSONObject(response.body?.string() ?: "")
                            serverVersion = "v" + json.optString("version", "Unknown")
                        } else {
                            serverVersion = "Offline"
                        }
                    }
                } catch (e: Exception) {
                    serverVersion = "Offline"
                }
            }
        } else {
            serverVersion = "Not Configured"
        }
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
                .setInputData(workDataOf("SYNC_PHOTOS" to syncPhotos, "SYNC_VIDEOS" to syncVideos))
                .build()
            workManager.enqueueUniquePeriodicWork("DailyPhotoSyncJob", ExistingPeriodicWorkPolicy.UPDATE, syncRequest)
        } else {
            workManager.cancelUniqueWork("DailyPhotoSyncJob")
        }
    }

    val testServerConnection = {
        coroutineScope.launch {
            isTestingConnection = true
            testConnectionResult = null

            // Determine which URL to test based on toggle
            val urlToTest = if (useLocalServer && localServerUrl.isNotBlank()) localServerUrl else mainServerUrl

            if (urlToTest.isBlank()) {
                testConnectionSuccess = false
                testConnectionResult = "URL cannot be empty."
                serverVersion = "Not Configured"
                isTestingConnection = false
                return@launch
            }

            try {
                withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(3, TimeUnit.SECONDS)
                        .readTimeout(3, TimeUnit.SECONDS)
                        .build()

                    val request = Request.Builder()
                        .url(urlToTest.trimEnd('/') + "/server-info")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: ""
                            val json = JSONObject(responseBody)
                            val version = json.optString("version", "Unknown")
                            testConnectionSuccess = true
                            testConnectionResult = "Success! Connected to PhotoSync v$version"
                            serverVersion = "v$version" // Update the bottom text too!
                        } else {
                            testConnectionSuccess = false
                            testConnectionResult = "Failed: Server responded with HTTP ${response.code}"
                            serverVersion = "Offline"
                        }
                    }
                }
            } catch (e: Exception) {
                testConnectionSuccess = false
                testConnectionResult = "Failed: Could not reach server. Check IP and Port."
                serverVersion = "Offline"
            } finally {
                isTestingConnection = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Backup Folders", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { FolderPicker.launch(launcher) }) { Text("+ Add") }
                    }
                }

                items(selectedFolders) { uriString ->
                    val uri = Uri.parse(uriString)
                    val displayName = uri.lastPathSegment?.substringAfterLast(":") ?: "Unknown Folder"
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = {
                            folderStore.remove(uri)
                            selectedFolders = folderStore.getAll().toList()
                        }) { Icon(Icons.Default.Delete, "Remove", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                    }
                }

                item {
                    // --- UI PREFERENCES ---
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text("UI Preferences", style = MaterialTheme.typography.titleMedium)

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grid Columns (Portrait)")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (columnsPortrait > 1) { columnsPortrait--; settingsStore.gridColumnsPortrait = columnsPortrait } }) {
                                Icon(Icons.Default.RemoveCircleOutline, "Decrease")
                            }
                            Text("$columnsPortrait", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(24.dp))
                            IconButton(onClick = { if (columnsPortrait < 10) { columnsPortrait++; settingsStore.gridColumnsPortrait = columnsPortrait } }) {
                                Icon(Icons.Default.AddCircleOutline, "Increase")
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grid Columns (Landscape)")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (columnsLandscape > 1) { columnsLandscape--; settingsStore.gridColumnsLandscape = columnsLandscape } }) {
                                Icon(Icons.Default.RemoveCircleOutline, "Decrease")
                            }
                            Text("$columnsLandscape", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(24.dp))
                            IconButton(onClick = { if (columnsLandscape < 15) { columnsLandscape++; settingsStore.gridColumnsLandscape = columnsLandscape } }) {
                                Icon(Icons.Default.AddCircleOutline, "Increase")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text("Media Types", style = MaterialTheme.typography.titleMedium)

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Include Photos")
                        Switch(checked = syncPhotos, onCheckedChange = { syncPhotos = it; settingsStore.syncPhotos = it; if (autoSyncEnabled) toggleAutoSync(true) })
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Include Videos")
                        Switch(checked = syncVideos, onCheckedChange = { syncVideos = it; settingsStore.syncVideos = it; if (autoSyncEnabled) toggleAutoSync(true) })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text("Network & Automation", style = MaterialTheme.typography.titleMedium)

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Use Cellular Data"); Text("If off, syncs only on WiFi", style = MaterialTheme.typography.bodySmall) }
                        Switch(checked = useCellular, onCheckedChange = { useCellular = it; settingsStore.useCellular = it; if (autoSyncEnabled) toggleAutoSync(true) })
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Daily Auto-Sync"); Text("Syncs in the background", style = MaterialTheme.typography.bodySmall) }
                        Switch(checked = autoSyncEnabled, onCheckedChange = { toggleAutoSync(it) }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
                    }

                    // --- SERVER CONNECTION ---
                    // --- SERVER CONNECTION ---
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text("Server Connection", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = mainServerUrl,
                        onValueChange = { mainServerUrl = it; settingsStore.serverUrl = it; testConnectionResult = null },
                        label = { Text("Main Server URL (e.g., https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Moved the toggle ABOVE the local URL input for better UX
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Prioritize Local Network")
                            Text("Fast Wi-Fi sync when at home", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = useLocalServer, onCheckedChange = { useLocalServer = it; settingsStore.useLocalServer = it; testConnectionResult = null })
                    }

                    // 💥 NEW: Only show the Local URL input if the toggle is ON
                    if (useLocalServer) {
                        OutlinedTextField(
                            value = localServerUrl,
                            onValueChange = { localServerUrl = it; settingsStore.localServerUrl = it; testConnectionResult = null },
                            label = { Text("Local Server URL (e.g., http://192...:8000)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }

                    Button(
                        onClick = { testServerConnection() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isTestingConnection
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Test Connection")
                        }
                    }

                    if (testConnectionResult != null) {
                        Text(
                            text = testConnectionResult!!,
                            color = if (testConnectionSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    sessionStore.clear()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Log Out")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Running PhotoSync $serverVersion",
                style = MaterialTheme.typography.labelMedium,
                color = if (serverVersion == "Offline" || serverVersion == "Not Configured") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}