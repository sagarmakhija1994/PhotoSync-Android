package com.sagar.prosync.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sagar.prosync.data.api.RemotePhoto
import com.sagar.prosync.ui.utils.ImageUtils
import kotlinx.coroutines.launch

@kotlin.OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    photos: List<RemotePhoto>,
    initialIndex: Int,
    activeBaseUrl: String,
    token: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    // Controls the swiping logic
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })

    // DYNAMIC DATA: Get info for the EXACT photo/video the user is currently looking at
    val currentPhoto = photos[pagerState.currentPage]
    val currentUrl = "${activeBaseUrl}photos/file/${currentPhoto.id}?thumbnail=false"
    val currentMimeType = if (currentPhoto.media_type == "video") "video/mp4" else "image/jpeg"
    val tempFileName = currentPhoto.filename ?: "PhotoSync_${currentPhoto.id}.${if (currentPhoto.media_type == "video") "mp4" else "jpg"}"

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 1. The Swipeable Gallery
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos[page]
            val pageUrl = "${activeBaseUrl}photos/file/${photo.id}?thumbnail=false"

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (photo.media_type == "video") {
                    // --- EXOPLAYER VIDEO PLAYER ---
                    VideoPlayer(videoUrl = pageUrl, token = token)
                } else {
                    // --- SMART ZOOMABLE IMAGE ---
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }

                    // BONUS UX: Automatically reset the zoom if the user swipes to a different photo
                    LaunchedEffect(pagerState.currentPage) {
                        if (pagerState.currentPage != page) {
                            scale = 1f
                            offset = Offset.Zero
                        }
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(pageUrl)
                            .addHeader("Authorization", "Bearer $token")
                            .crossfade(true)
                            .build(),
                        contentDescription = "Full Screen Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            // 1. Double Tap to quickly zoom in/out
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f // Zoom in slightly
                                        }
                                    }
                                )
                            }
                            // 2. The Smart Pinch & Pan
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    do {
                                        val event = awaitPointerEvent()
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()

                                        scale = (scale * zoom).coerceIn(1f, 5f)

                                        if (scale > 1f) {
                                            offset += pan
                                            // The image is zoomed in. CONSUME the touch so we can pan around.
                                            event.changes.forEach { it.consume() }
                                        } else {
                                            offset = Offset.Zero
                                            // The image is normal size. DO NOT consume the touch!
                                            // This allows the gesture to pass through to the HorizontalPager so you can swipe!
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
            }
        }

        // 2. The Top App Bar with Your Custom Actions
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
                            // ImageUtils will now safely download either the MP4 or the JPG!
                            val success = ImageUtils.saveToGallery(context, currentUrl, token, tempFileName)
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
                            val uri = ImageUtils.downloadToCache(context, currentUrl, token, tempFileName)
                            isProcessing = false
                            if (uri != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = currentMimeType // Dynamically changes for video/image
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                            }
                        }
                    }
                ) { Icon(Icons.Default.Share, "Share", tint = Color.White) }

                // OPEN WITH BUTTON
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            isProcessing = true
                            val uri = ImageUtils.downloadToCache(context, currentUrl, token, tempFileName)
                            isProcessing = false
                            if (uri != null) {
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, currentMimeType) // Dynamically changes for video/image
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

        // Loading Spinner overlay
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}

// --- SECURE VIDEO PLAYER COMPONENT ---
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(videoUrl: String, token: String) {
    val context = LocalContext.current

    // Initialize ExoPlayer and attach the JWT token so the server allows it
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))

            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)))

            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true // Auto-play when swiped to
        }
    }

    // Safely destroy the player when swiping away to save RAM
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Embed the Android XML PlayerView into Compose
    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true // Shows play/pause/timeline controls
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}