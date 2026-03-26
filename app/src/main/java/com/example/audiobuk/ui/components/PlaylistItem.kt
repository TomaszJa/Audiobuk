package com.example.audiobuk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobuk.R
import com.example.audiobuk.model.AudioBook

@Composable
fun PlaylistItem(audioBook: AudioBook, progress: Int, isGrid: Boolean, onClick: () -> Unit) {
    val firstTrack = audioBook.audioFiles.firstOrNull()
    val isFinished = progress >= 100
    
    if (isGrid) {
        Card(
            modifier = Modifier
                .padding(8.dp)
                .aspectRatio(1f)
                .clickable { onClick() },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AudioArtwork(
                    uri = firstTrack?.uri,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Progress Indicator (Top Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    if (isFinished) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.finished),
                            tint = Color.Green,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.progress_percent, progress),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = audioBook.name,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.tracks_count, audioBook.audioFiles.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable { onClick() },
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    AudioArtwork(
                        uri = firstTrack?.uri,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = audioBook.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.tracks_count, audioBook.audioFiles.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Progress Indicator
                if (isFinished) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.finished),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.progress_percent, progress),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}
