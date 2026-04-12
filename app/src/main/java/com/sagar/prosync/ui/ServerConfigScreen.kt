package com.sagar.prosync.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sagar.prosync.data.SettingsStore

@Composable
fun ServerConfigScreen(onConfigSaved: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }

    var urlInput by remember { mutableStateOf(settingsStore.serverUrl) }
    var localUrlInput by remember { mutableStateOf(settingsStore.localServerUrl) }
    var useLocalServer by remember { mutableStateOf(settingsStore.useLocalServer) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to PhotoSync", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Enter the address of your private home server.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(32.dp))

            // 1. Primary Cloudflare / Public URL
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it; errorMessage = null },
                label = { Text("Main Server URL (Public)") },
                placeholder = { Text("https://photos.yourdomain.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // 2. Dual-URL Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = useLocalServer,
                    onCheckedChange = { useLocalServer = it }
                )
                Text("I am on the same local network (Fast Sync)")
            }

            // 3. Conditional Local IP Field
            if (useLocalServer) {
                OutlinedTextField(
                    value = localUrlInput,
                    onValueChange = { localUrlInput = it; errorMessage = null },
                    label = { Text("Local IP Address") },
                    placeholder = { Text("http://192.168.0.x:8000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    // Helper function to clean URLs
                    fun formatUrl(input: String): String {
                        var cleaned = input.trim()
                        if (cleaned.isBlank()) return ""
                        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
                            cleaned = "http://$cleaned"
                        }
                        if (!cleaned.endsWith("/")) {
                            cleaned = "$cleaned/"
                        }
                        return cleaned
                    }

                    val finalMainUrl = formatUrl(urlInput)
                    val finalLocalUrl = formatUrl(localUrlInput)

                    if (finalMainUrl.isBlank()) {
                        errorMessage = "Main URL cannot be empty"
                        return@Button
                    }
                    if (useLocalServer && finalLocalUrl.isBlank()) {
                        errorMessage = "Local IP cannot be empty if checkbox is checked"
                        return@Button
                    }

                    settingsStore.serverUrl = finalMainUrl
                    settingsStore.localServerUrl = finalLocalUrl
                    settingsStore.useLocalServer = useLocalServer

                    onConfigSaved()
                }
            ) {
                Text("Connect")
            }
        }
    }
}