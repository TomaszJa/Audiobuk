package com.example.audiobuk.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audiobuk.ui.components.AudioArtwork
import com.example.audiobuk.ui.dialogs.ChaptersDialog
import com.example.audiobuk.ui.dialogs.PlaybackSpeedDialog
import com.example.audiobuk.ui.dialogs.SleepTimerDialog
import com.example.audiobuk.util.formatTime
import com.example.audiobuk.util.formatTimerRemaining
import com.example.audiobuk.viewmodel.AudioBookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: AudioBookViewModel, onBack: () -> Unit) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val globalPosition by viewModel.globalPosition.collectAsState()
    val totalDuration by viewModel.totalBookDuration.collectAsState()
    val remainingInChapter by viewModel.remainingInChapter.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val stopAfterCurrentTrack by viewModel.stopAfterCurrentTrack.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()
    val currentPlaylist by viewModel.currentAudioBook.collectAsState()
    
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showChaptersDialog by remember { mutableStateOf(false) }

    // State for local slider position to avoid jumping
    var sliderPosition by remember { mutableLongStateOf(0L) }
    var isDraggingGesture by remember { mutableStateOf(false) }
    var isSliderActive by remember { mutableStateOf(false) }
    var isPrecise by remember { mutableStateOf(false) }
    
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isSliderDragged by sliderInteractionSource.collectIsDraggedAsState()
    
    // Combined dragging state
    val isCurrentlyDragging = isDraggingGesture || isSliderDragged || isSliderActive

    // Sync slider with global position ONLY when not dragging
    LaunchedEffect(globalPosition) {
        if (!isCurrentlyDragging) {
            sliderPosition = globalPosition
        }
    }

    BackHandler(onBack = onBack)

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = playbackSpeed,
            onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            onSelect = { viewModel.setSleepTimer(it) },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showChaptersDialog) {
        ChaptersDialog(
            chapters = currentPlaylist?.audioFiles ?: emptyList(),
            currentTrackUri = currentTrack?.uri,
            onSelect = { 
                viewModel.playTrack(it)
                showChaptersDialog = false
            },
            onDismiss = { showChaptersDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playing Now") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Large Artwork
            AudioArtwork(
                uri = currentTrack?.uri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.extraLarge),
                iconSize = 120.dp
            )

            // Track Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTrack?.title ?: "Unknown",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Chapter countdown
            Text(
                text = "Chapter ends in: ${formatTime(remainingInChapter)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Progress Bar (Whole Book)
            Column(
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { 
                            isDraggingGesture = true 
                            isPrecise = false
                        },
                        onDragEnd = {
                            isDraggingGesture = false
                            isPrecise = false
                            viewModel.seekToGlobal(sliderPosition)
                        },
                        onDragCancel = {
                            isDraggingGesture = false
                            isPrecise = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Move finger up for precise seeking (threshold 100px)
                            isPrecise = change.position.y < -100 

                            val totalWidth = size.width.toFloat()
                            val sensitivity = if (isPrecise) 0.1f else 1.0f
                            val deltaPx = dragAmount.x * sensitivity
                            val deltaMs = (deltaPx / totalWidth) * totalDuration
                            sliderPosition = (sliderPosition + deltaMs.toLong()).coerceIn(0L, totalDuration)
                        }
                    )
                }
            ) {
                if (isPrecise) {
                    Text(
                        "Precise Seeking",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }
                
                Slider(
                    value = sliderPosition.toFloat(),
                    onValueChange = { 
                        isSliderActive = true
                        sliderPosition = it.toLong()
                    },
                    onValueChangeFinished = {
                        isSliderActive = false
                        // Only seek here if we aren't already handling it via the custom gesture
                        if (!isDraggingGesture) {
                            viewModel.seekToGlobal(sliderPosition)
                        }
                    },
                    valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                    interactionSource = sliderInteractionSource,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(sliderPosition), style = MaterialTheme.typography.labelMedium)
                    Text(formatTime(totalDuration), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { viewModel.seekBack() }) {
                    Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", modifier = Modifier.size(36.dp))
                }
                Surface(
                    onClick = { viewModel.togglePlayPause() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                IconButton(onClick = { viewModel.seekForward() }) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { viewModel.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                }
            }

            // Options Buttons (Moved below controls)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { showSpeedDialog = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${"%.1f".format(playbackSpeed)}x",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    onClick = { showSleepTimerDialog = true },
                    shape = MaterialTheme.shapes.medium,
                    color = if (sleepTimerRemaining != null || stopAfterCurrentTrack) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                stopAfterCurrentTrack -> "Chapter End"
                                sleepTimerRemaining != null -> formatTimerRemaining(sleepTimerRemaining!!)
                                else -> "Timer"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Browse Chapters Button
            TextButton(
                onClick = { showChaptersDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse chapters")
            }
        }
    }
}
