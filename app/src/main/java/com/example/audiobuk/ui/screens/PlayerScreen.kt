package com.example.audiobuk.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobuk.R
import com.example.audiobuk.model.AudioFile
import com.example.audiobuk.ui.components.AudioArtwork
import com.example.audiobuk.ui.dialogs.ChaptersDialog
import com.example.audiobuk.ui.dialogs.PlaybackSpeedDialog
import com.example.audiobuk.ui.dialogs.SleepTimerDialog
import com.example.audiobuk.util.formatTime
import com.example.audiobuk.util.formatTimerRemaining
import com.example.audiobuk.viewmodel.AudioBookViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

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

    var sliderPosition by remember { mutableLongStateOf(0L) }
    var isDraggingGesture by remember { mutableStateOf(false) }
    var isSliderActive by remember { mutableStateOf(false) }
    var isPrecise by remember { mutableStateOf(false) }
    
    var originalPositionBeforeSeek by remember { mutableLongStateOf(0L) }
    var undoPosition by remember { mutableLongStateOf(0L) }
    var showUndoPrompt by remember { mutableStateOf(false) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }
    
    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isSliderDragged by sliderInteractionSource.collectIsDraggedAsState()
    val isCurrentlyDragging = isDraggingGesture || isSliderDragged || isSliderActive

    LaunchedEffect(globalPosition) {
        val now = System.currentTimeMillis()
        if (!isCurrentlyDragging && now - lastSeekTime > 1000) {
            sliderPosition = globalPosition
        }
    }

    LaunchedEffect(showUndoPrompt) {
        if (showUndoPrompt) {
            delay(5000)
            showUndoPrompt = false
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
                title = { Text("Playing Now", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        val onSeekFinished: () -> Unit = {
            val finalPos = sliderPosition
            if (abs(finalPos - originalPositionBeforeSeek) > 5000) {
                undoPosition = originalPositionBeforeSeek
                showUndoPrompt = true
            }
            viewModel.seekToGlobal(finalPos)
            lastSeekTime = System.currentTimeMillis()
            isSliderActive = false
            isDraggingGesture = false
            isPrecise = false
        }

        val layoutModifier = Modifier.padding(padding)
        if (isLandscape) {
            LandscapeLayout(
                modifier = layoutModifier,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                sliderPosition = sliderPosition,
                totalDuration = totalDuration,
                remainingInChapter = remainingInChapter,
                playbackSpeed = playbackSpeed,
                stopAfterCurrentTrack = stopAfterCurrentTrack,
                sleepTimerRemaining = sleepTimerRemaining,
                isPrecise = isPrecise,
                showUndoPrompt = showUndoPrompt,
                undoPosition = undoPosition,
                onUndo = {
                    viewModel.seekToGlobal(undoPosition)
                    sliderPosition = undoPosition
                    lastSeekTime = System.currentTimeMillis()
                    showUndoPrompt = false
                },
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
                onGestureStart = { 
                    originalPositionBeforeSeek = sliderPosition
                    isDraggingGesture = true
                    isPrecise = false 
                },
                onGestureEnd = onSeekFinished,
                onGestureDelta = { deltaMs, precise -> 
                    sliderPosition = (sliderPosition + deltaMs).coerceIn(0L, totalDuration)
                    isPrecise = precise
                }
            )
        } else {
            PortraitLayout(
                modifier = layoutModifier,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                sliderPosition = sliderPosition,
                totalDuration = totalDuration,
                remainingInChapter = remainingInChapter,
                playbackSpeed = playbackSpeed,
                stopAfterCurrentTrack = stopAfterCurrentTrack,
                sleepTimerRemaining = sleepTimerRemaining,
                isPrecise = isPrecise,
                showUndoPrompt = showUndoPrompt,
                undoPosition = undoPosition,
                onUndo = {
                    viewModel.seekToGlobal(undoPosition)
                    sliderPosition = undoPosition
                    lastSeekTime = System.currentTimeMillis()
                    showUndoPrompt = false
                },
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
                onGestureStart = { 
                    originalPositionBeforeSeek = sliderPosition
                    isDraggingGesture = true
                    isPrecise = false 
                },
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
    showUndoPrompt: Boolean,
    undoPosition: Long,
    onUndo: () -> Unit,
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            AudioArtwork(
                uri = currentTrack?.uri,
                modifier = Modifier.fillMaxSize(),
                iconSize = 140.dp
            )
            UndoPrompt(visible = showUndoPrompt, undoTime = undoPosition, onUndo = onUndo)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentTrack?.title ?: "No Track Selected",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentTrack?.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "Chapter ends in: ${formatTime(remainingInChapter)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )

        ProgressBar(
            sliderPosition = sliderPosition,
            totalDuration = totalDuration,
            isPrecise = isPrecise,
            sliderInteractionSource = sliderInteractionSource,
            onSliderValueChange = onSliderValueChange,
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
            onGestureDelta = onGestureDelta
        )

        PlaybackControls(
            isPlaying = isPlaying,
            onPrevious = onPrevious,
            onNext = onNext,
            onRewind = onRewind,
            onForward = onForward,
            onTogglePlayPause = onTogglePlayPause
        )

        SettingsRow(
            playbackSpeed = playbackSpeed,
            stopAfterCurrentTrack = stopAfterCurrentTrack,
            sleepTimerRemaining = sleepTimerRemaining,
            onShowSpeed = onShowSpeed,
            onShowTimer = onShowTimer
        )

        TextButton(
            onClick = onShowChapters,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Browse chapters", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
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
    showUndoPrompt: Boolean,
    undoPosition: Long,
    onUndo: () -> Unit,
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
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            AudioArtwork(
                uri = currentTrack?.uri,
                modifier = Modifier.fillMaxSize(),
                iconSize = 100.dp
            )
            UndoPrompt(visible = showUndoPrompt, undoTime = undoPosition, onUndo = onUndo)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTrack?.title ?: "No Track",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "Chapter ends in: ${formatTime(remainingInChapter)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )

            ProgressBar(
                sliderPosition = sliderPosition,
                totalDuration = totalDuration,
                isPrecise = isPrecise,
                sliderInteractionSource = sliderInteractionSource,
                onSliderValueChange = onSliderValueChange,
                onGestureStart = onGestureStart,
                onGestureEnd = onGestureEnd,
                onGestureDelta = onGestureDelta
            )

            PlaybackControls(
                isPlaying = isPlaying,
                onPrevious = onPrevious,
                onNext = onNext,
                onRewind = onRewind,
                onForward = onForward,
                onTogglePlayPause = onTogglePlayPause,
                iconSize = 40.dp,
                playSize = 72.dp
            )

            SettingsRow(
                playbackSpeed = playbackSpeed,
                stopAfterCurrentTrack = stopAfterCurrentTrack,
                sleepTimerRemaining = sleepTimerRemaining,
                onShowSpeed = onShowSpeed,
                onShowTimer = onShowTimer
            )

            TextButton(
                onClick = onShowChapters,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse chapters", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun UndoPrompt(
    visible: Boolean,
    undoTime: Long,
    onUndo: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onUndo),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Go back to: ${formatTime(undoTime)}?",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressBar(
    sliderPosition: Long,
    totalDuration: Long,
    isPrecise: Boolean,
    sliderInteractionSource: MutableInteractionSource,
    onSliderValueChange: (Long) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onGestureDelta: (Long, Boolean) -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPrecise) {
                Text(
                    "PRECISE SEEKING",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Slider(
                value = sliderPosition.toFloat(),
                onValueChange = { },
                onValueChangeFinished = { },
                valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                interactionSource = sliderInteractionSource,
                modifier = Modifier.align(Alignment.Center),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .graphicsLayer(rotationZ = -20f),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_leaf_colorful_foreground),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(8.dp),
                        thumbTrackGapSize = 0.dp,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    )
                }
            )
            
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(totalDuration) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                onGestureStart()
                                val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                onSliderValueChange((ratio * totalDuration).toLong())
                            },
                            onDragEnd = { onGestureEnd() },
                            onDragCancel = { onGestureEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val precise = change.position.y < 0
                                val sensitivity = if (precise) 0.1f else 1.0f
                                val deltaMs = ((dragAmount.x * sensitivity) / size.width.toFloat()) * totalDuration
                                onGestureDelta(deltaMs.toLong(), precise)
                            }
                        )
                    }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(sliderPosition), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(formatTime(totalDuration), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    iconSize: androidx.compose.ui.unit.Dp = 48.dp,
    playSize: androidx.compose.ui.unit.Dp = 84.dp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onRewind) {
            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.primary)
        }
        Surface(
            onClick = onTogglePlayPause,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(playSize),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(playSize * 0.6f),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        IconButton(onClick = onForward) {
            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.primary)
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onShowSpeed,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${"%.1f".format(playbackSpeed)}x",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Surface(
            onClick = onShowTimer,
            shape = RoundedCornerShape(20.dp),
            color = if (sleepTimerRemaining != null || stopAfterCurrentTrack) 
                MaterialTheme.colorScheme.primary 
            else 
                MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Default.Timer, 
                    contentDescription = null, 
                    modifier = Modifier.size(24.dp), 
                    tint = if (sleepTimerRemaining != null || stopAfterCurrentTrack) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when {
                        stopAfterCurrentTrack -> "Ch. End"
                        sleepTimerRemaining != null -> formatTimerRemaining(sleepTimerRemaining)
                        else -> "Timer"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (sleepTimerRemaining != null || stopAfterCurrentTrack) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
