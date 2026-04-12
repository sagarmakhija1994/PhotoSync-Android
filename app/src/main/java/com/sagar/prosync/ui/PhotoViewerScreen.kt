package com.sagar.prosync.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sagar.prosync.data.SettingsStore
import com.sagar.prosync.ui.utils.ImageUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    photoId: Int,
    token: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isProcessing by remember { mutableStateOf(false) }

    // --- DYNAMIC URL RESOLUTION ---
    val activeBaseUrl = remember(settingsStore.serverUrl, settingsStore.localServerUrl, settingsStore.useLocalServer) {
        var url = settingsStore.serverUrl.ifBlank { "http://127.0.0.1:8000/" }
        if (settingsStore.useLocalServer && settingsStore.localServerUrl.isNotBlank()) {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val isWifi = connManager.getNetworkCapabilities(connManager.activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (isWifi) {
                url = settingsStore.localServerUrl
            }
        }
        if (!url.endsWith("/")) "$url/" else url
    }

    val imageUrl = "${activeBaseUrl}photos/file/$photoId" // UPDATED HERE
    val tempFileName = "PhotoSync_$photoId.jpg"

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 1. The Zoomable Image
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .addHeader("Authorization", "Bearer $token")
                .crossfade(true)
                .build(),
            contentDescription = "Full Screen Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale == 1f) offset = Offset.Zero else offset += pan
                    }
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
        )

        // 2. The Top App Bar with Actions
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            },
            actions = {
                // DOWNLOAD BUTTON
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            isProcessing = true
                            val success = ImageUtils.saveToGallery(context, imageUrl, token, tempFileName)
                            isProcessing = false
                            val msg = if (success) "Saved to device Gallery!" else "Failed to save."
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Icon(Icons.Default.Download, "Download", tint = Color.White) }

                // SHARE BUTTON
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            isProcessing = true
                            val uri = ImageUtils.downloadToCache(context, imageUrl, token, tempFileName)
                            isProcessing = false
                            if (uri != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
                            }
                        }
                    }
                ) { Icon(Icons.Default.Share, "Share", tint = Color.White) }

                // OPEN WITH BUTTON
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            isProcessing = true
                            val uri = ImageUtils.downloadToCache(context, imageUrl, token, tempFileName)
                            isProcessing = false
                            if (uri != null) {
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "image/jpeg")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(openIntent, "Open with"))
                            }
                        }
                    }
                ) { Icon(Icons.Default.OpenInNew, "Open With", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.4f))
        )

        // Loading Spinner overlay when downloading
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}