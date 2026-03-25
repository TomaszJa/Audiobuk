package com.example.audiobuk.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AudioArtwork(
    uri: Uri?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    iconSize: Dp = 48.dp
) {
    val context = LocalContext.current
    var artwork by remember(uri) { mutableStateOf<ByteArray?>(null) }
    
    LaunchedEffect(uri) {
        if (uri == null) {
            artwork = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                artwork = retriever.embeddedPicture
            } catch (_: Exception) {
                artwork = null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }

    if (artwork != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(artwork)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
