package com.sagar.prosync.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
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

    // --- 1. DEFINE REQUIRED PERMISSIONS BASED ON ANDROID VERSION ---
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    // --- 2. PERMISSION LAUNCHER ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(context, "Storage permissions are required to backup photos.", Toast.LENGTH_LONG).show()
        }
    }

    // --- 3. ASK ON LAUNCH ---
    LaunchedEffect(Unit) {
        val hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermissions) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        FolderPicker.persistPermission(context, uri)
        folderStore.save(uri)
        selectedFolders = folderStore.getAll().toList()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Welcome Setup") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)
        ) {
            Text("Which folders should we backup?", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Select your Camera folder, WhatsApp images, or any other folder you want to keep safe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { FolderPicker.launch(launcher) }
            ) {
                Text("+ Add Folder")
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(selectedFolders) { uriString ->
                    val uri = Uri.parse(uriString)
                    val displayName = uri.lastPathSegment?.substringAfterLast(":") ?: "Unknown Folder"

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                    // --- 4. VERIFY ON FINISH ---
                    val isGranted = requiredPermissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }

                    if (isGranted) {
                        settingsStore.isSetupComplete = true
                        onFinishSetup()
                    } else {
                        Toast.makeText(context, "Please grant media permissions first!", Toast.LENGTH_SHORT).show()
                        permissionLauncher.launch(requiredPermissions)
                    }
                }
            ) {
                Text("Finish Setup")
            }
        }
    }
}