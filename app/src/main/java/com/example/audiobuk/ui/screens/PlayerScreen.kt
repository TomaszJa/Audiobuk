package com.example.audiobuk.ui.screens

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audiobuk.model.AudioFile
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
    
    // Lock sync for a short period after seeking to allow the player to catch up
    var lastSeekTime by remember { mutableLongStateOf(0L) }
    
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isSliderDragged by sliderInteractionSource.collectIsDraggedAsState()
    
    // Combined dragging state
    val isCurrentlyDragging = isDraggingGesture || isSliderDragged || isSliderActive

    // Sync slider with global position ONLY when not dragging and not immediately after a seek
    LaunchedEffect(globalPosition) {
        val now = System.currentTimeMillis()
        if (!isCurrentlyDragging && now - lastSeekTime > 1000) {
            sliderPosition = globalPosition
        }
    }

    BackHandler(onBack = onBack)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
        val onSeekFinished: () -> Unit = {
            viewModel.seekToGlobal(sliderPosition)
            lastSeekTime = System.currentTimeMillis()
            isSliderActive = false
            isDraggingGesture = false
            isPrecise = false
        }

        if (isLandscape) {
            LandscapeLayout(
                modifier = Modifier.padding(padding),
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                sliderPosition = sliderPosition,
                totalDuration = totalDuration,
                remainingInChapter = remainingInChapter,
                playbackSpeed = playbackSpeed,
                stopAfterCurrentTrack = stopAfterCurrentTrack,
                sleepTimerRemaining = sleepTimerRemaining,
                isPrecise = isPrecise,
                sliderInteractionSource = sliderInteractionSource,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.previous() },
                onNext = { viewModel.next() },
                onRewind = { viewModel.seekBack() },
                onForward = { viewModel.seekForward() },
                onShowSpeed = { showSpeedDialog = true },
                onShowTimer = { showSleepTimerDialog = true },
                onShowChapters = { showChaptersDialog = true },
                onSliderValueChange = { sliderPosition = it; isSliderActive = true },
                onSliderValueChangeFinished = onSeekFinished,
                onGestureStart = { isDraggingGesture = true; isPrecise = false },
                onGestureEnd = onSeekFinished,
                onGestureDelta = { deltaMs, precise -> 
                    sliderPosition = (sliderPosition + deltaMs).coerceIn(0L, totalDuration)
                    isPrecise = precise
                }
            )
        } else {
            PortraitLayout(
                modifier = Modifier.padding(padding),
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                sliderPosition = sliderPosition,
                totalDuration = totalDuration,
                remainingInChapter = remainingInChapter,
                playbackSpeed = playbackSpeed,
                stopAfterCurrentTrack = stopAfterCurrentTrack,
                sleepTimerRemaining = sleepTimerRemaining,
                isPrecise = isPrecise,
                sliderInteractionSource = sliderInteractionSource,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.previous() },
                onNext = { viewModel.next() },
                onRewind = { viewModel.seekBack() },
                onForward = { viewModel.seekForward() },
                onShowSpeed = { showSpeedDialog = true },
                onShowTimer = { showSleepTimerDialog = true },
                onShowChapters = { showChaptersDialog = true },
                onSliderValueChange = { sliderPosition = it; isSliderActive = true },
                onSliderValueChangeFinished = onSeekFinished,
                onGestureStart = { isDraggingGesture = true; isPrecise = false },
                onGestureEnd = onSeekFinished,
                onGestureDelta = { deltaMs, precise -> 
                    sliderPosition = (sliderPosition + deltaMs).coerceIn(0L, totalDuration)
                    isPrecise = precise
                }
            )
        }
    }
}

@Composable
fun PortraitLayout(
    modifier: Modifier = Modifier,
    currentTrack: AudioFile?,
    isPlaying: Boolean,
    sliderPosition: Long,
    totalDuration: Long,
    remainingInChapter: Long,
    playbackSpeed: Float,
    stopAfterCurrentTrack: Boolean,
    sleepTimerRemaining: Long?,
    isPrecise: Boolean,
    sliderInteractionSource: MutableInteractionSource,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onShowSpeed: () -> Unit,
    onShowTimer: () -> Unit,
    onShowChapters: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onGestureDelta: (Long, Boolean) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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

        // Progress Bar
        ProgressBar(
            sliderPosition = sliderPosition,
            totalDuration = totalDuration,
            isPrecise = isPrecise,
            sliderInteractionSource = sliderInteractionSource,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
            onGestureDelta = onGestureDelta
        )

        // Controls
        PlaybackControls(
            isPlaying = isPlaying,
            onPrevious = onPrevious,
            onNext = onNext,
            onRewind = onRewind,
            onForward = onForward,
            onTogglePlayPause = onTogglePlayPause
        )

        // Settings Row
        SettingsRow(
            playbackSpeed = playbackSpeed,
            stopAfterCurrentTrack = stopAfterCurrentTrack,
            sleepTimerRemaining = sleepTimerRemaining,
            onShowSpeed = onShowSpeed,
            onShowTimer = onShowTimer
        )

        // Browse Chapters
        TextButton(
            onClick = onShowChapters,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FormatListBulleted, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Browse chapters")
        }
    }
}

@Composable
fun LandscapeLayout(
    modifier: Modifier = Modifier,
    currentTrack: AudioFile?,
    isPlaying: Boolean,
    sliderPosition: Long,
    totalDuration: Long,
    remainingInChapter: Long,
    playbackSpeed: Float,
    stopAfterCurrentTrack: Boolean,
    sleepTimerRemaining: Long?,
    isPrecise: Boolean,
    sliderInteractionSource: MutableInteractionSource,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onShowSpeed: () -> Unit,
    onShowTimer: () -> Unit,
    onShowChapters: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onGestureDelta: (Long, Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork on the left
        AudioArtwork(
            uri = currentTrack?.uri,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraLarge),
            iconSize = 80.dp
        )

        // All controls on the right in a scrollable column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Track Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTrack?.title ?: "Unknown",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chapter countdown
            Text(
                text = "Chapter ends in: ${formatTime(remainingInChapter)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            // Progress Bar
            ProgressBar(
                sliderPosition = sliderPosition,
                totalDuration = totalDuration,
                isPrecise = isPrecise,
                sliderInteractionSource = sliderInteractionSource,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
                onGestureStart = onGestureStart,
                onGestureEnd = onGestureEnd,
                onGestureDelta = onGestureDelta
            )

            // Controls
            PlaybackControls(
                isPlaying = isPlaying,
                onPrevious = onPrevious,
                onNext = onNext,
                onRewind = onRewind,
                onForward = onForward,
                onTogglePlayPause = onTogglePlayPause,
                iconSize = 32.dp,
                playSize = 56.dp
            )

            // Settings Row
            SettingsRow(
                playbackSpeed = playbackSpeed,
                stopAfterCurrentTrack = stopAfterCurrentTrack,
                sleepTimerRemaining = sleepTimerRemaining,
                onShowSpeed = onShowSpeed,
                onShowTimer = onShowTimer
            )

            // Browse Chapters
            TextButton(
                onClick = onShowChapters,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse chapters")
            }
        }
    }
}

@Composable
fun ProgressBar(
    sliderPosition: Long,
    totalDuration: Long,
    isPrecise: Boolean,
    sliderInteractionSource: MutableInteractionSource,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onGestureDelta: (Long, Boolean) -> Unit
) {
    Column(
        modifier = Modifier.pointerInput(totalDuration) {
            detectDragGestures(
                onDragStart = { onGestureStart() },
                onDragEnd = { onGestureEnd() },
                onDragCancel = { onGestureEnd() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val precise = change.position.y < -100 
                    val totalWidth = size.width.toFloat()
                    val sensitivity = if (precise) 0.1f else 1.0f
                    val deltaPx = dragAmount.x * sensitivity
                    val deltaMs = (deltaPx / totalWidth) * totalDuration
                    onGestureDelta(deltaMs.toLong(), precise)
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
            onValueChange = { onSliderValueChange(it.toLong()) },
            onValueChangeFinished = onSliderValueChangeFinished,
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
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onTogglePlayPause: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp = 36.dp,
    playSize: androidx.compose.ui.unit.Dp = 72.dp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(iconSize))
        }
        IconButton(onClick = onRewind) {
            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", modifier = Modifier.size(iconSize))
        }
        Surface(
            onClick = onTogglePlayPause,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(playSize)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(playSize * 0.55f)
                )
            }
        }
        IconButton(onClick = onForward) {
            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(iconSize))
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun SettingsRow(
    playbackSpeed: Float,
    stopAfterCurrentTrack: Boolean,
    sleepTimerRemaining: Long?,
    onShowSpeed: () -> Unit,
    onShowTimer: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onShowSpeed,
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
            onClick = onShowTimer,
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
                        sleepTimerRemaining != null -> formatTimerRemaining(sleepTimerRemaining)
                        else -> "Timer"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
