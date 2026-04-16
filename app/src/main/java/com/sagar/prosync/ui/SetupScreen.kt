package com.sagar.prosync.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.sync.FolderPicker
import com.sagar.prosync.sync.FolderStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onFinishSetup: () -> Unit
) {
    val context = LocalContext.current
    val folderStore = remember { FolderStore(context) }
    val settingsStore = remember { SettingsStore(context) }

    var selectedFolders by remember { mutableStateOf(folderStore.getAll().toList()) }

    // --- PERMISSION DEFINITIONS ---
    val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else null

    // --- STATE TRACKERS ---
    var hasStoragePermission by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(false) }

    // Helper to check current status
    val checkPermissions = {
        hasStoragePermission = storagePermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        hasNotificationPermission = if (notificationPermission != null) {
            ContextCompat.checkSelfPermission(context, notificationPermission) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 12 and below don't need explicit notification prompts
        }
    }

    // Check immediately on launch
    LaunchedEffect(Unit) {
        checkPermissions()
    }

    // --- LAUNCHERS ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkPermissions()
        if (!hasStoragePermission || !hasNotificationPermission) {
            Toast.makeText(context, "Both permissions are required to use PhotoSync securely.", Toast.LENGTH_LONG).show()
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        FolderPicker.persistPermission(context, uri)
        folderStore.save(uri)
        selectedFolders = folderStore.getAll().toList()
    }

    val allPermissionsGranted = hasStoragePermission && hasNotificationPermission

    Scaffold(
        topBar = { TopAppBar(title = { Text("Welcome to PhotoSync") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
        ) {

            // --- STEP 1: PERMISSIONS UI ---
            Text("Step 1: Required Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            PermissionCard(
                title = "Storage Access",
                description = "Required to scan your device for photos and videos to back up to your private server.",
                icon = Icons.Default.PhotoLibrary,
                isGranted = hasStoragePermission
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                title = "Notifications",
                description = "Required to show you live upload progress when the app is running safely in the background.",
                icon = Icons.Default.NotificationsActive,
                isGranted = hasNotificationPermission
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!allPermissionsGranted) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val toRequest = storagePermissions.toMutableList()
                        if (notificationPermission != null) toRequest.add(notificationPermission)
                        permissionLauncher.launch(toRequest.toTypedArray())
                    }
                ) {
                    Text("Grant Permissions Now")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- STEP 2: FOLDER UI (Only visible if permissions are granted) ---
            AnimatedVisibility(visible = allPermissionsGranted) {
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Step 2: Choose Folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Select your Camera folder, WhatsApp images, or any other folder you want to keep safe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { FolderPicker.launch(folderLauncher) }
                    ) {
                        Text("+ Add Folder to Sync")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(selectedFolders) { uriString ->
                            val uri = Uri.parse(uriString)
                            val displayName = uri.lastPathSegment?.substringAfterLast(":") ?: "Unknown Folder"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(displayName, style = MaterialTheme.typography.bodyLarge)
                                }
                                IconButton(onClick = {
                                    folderStore.remove(uri)
                                    selectedFolders = folderStore.getAll().toList()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedFolders.isNotEmpty(),
                        onClick = {
                            settingsStore.isSetupComplete = true
                            onFinishSetup()
                        }
                    ) {
                        Text("Finish Setup")
                    }
                }
            }
        }
    }
}

// --- HELPER UI COMPONENT ---
@Composable
fun PermissionCard(title: String, description: String, icon: ImageVector, isGranted: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (isGranted) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.CheckCircle, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}