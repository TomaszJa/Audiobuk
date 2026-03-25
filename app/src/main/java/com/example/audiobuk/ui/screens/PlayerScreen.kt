@file:OptIn(ExperimentalMaterial3Api::class)
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.window.DialogProperties
import com.example.audiobuk.R
import com.example.audiobuk.model.AudioFile
import com.example.audiobuk.ui.components.AudioArtwork
import com.example.audiobuk.ui.dialogs.ChaptersDialog
import com.example.audiobuk.ui.dialogs.PlaybackSpeedDialog
import com.example.audiobuk.ui.dialogs.SleepTimerDialog
import com.example.audiobuk.ui.theme.*
import com.example.audiobuk.util.formatTime
import com.example.audiobuk.util.formatTimerRemaining
import com.example.audiobuk.viewmodel.AudioBookViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PlayerScreen(viewModel: AudioBookViewModel, onBack: () -> Unit) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val globalPosition by viewModel.globalPosition.collectAsState()
    val totalDuration by viewModel.totalBookDuration.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val chapterDuration by viewModel.duration.collectAsState()
    val remainingInChapter by viewModel.remainingInChapter.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val stopAfterCurrentTrack by viewModel.stopAfterCurrentTrack.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()
    val currentPlaylist by viewModel.currentAudioBook.collectAsState()
    
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showChaptersDialog by remember { mutableStateOf(false) }
    var showChapterSeekDialog by remember { mutableStateOf(false) }

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

    if (showChapterSeekDialog) {
        ChapterSeekDialog(
            currentTrack = currentTrack,
            currentPosition = currentPosition,
            duration = chapterDuration,
            onSeek = { viewModel.seekTo(it) },
            onDismiss = { showChapterSeekDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playing Now", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
                },
                onChapterPopupRequest = { showChapterSeekDialog = true }
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
                },
                onChapterPopupRequest = { showChapterSeekDialog = true }
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
    onGestureDelta: (Long, Boolean) -> Unit,
    onChapterPopupRequest: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Artwork - Flexible with weight to shrink on shorter 16:9 screens
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            AudioArtwork(
                uri = currentTrack?.uri,
                modifier = Modifier.fillMaxSize(),
                iconSize = 120.dp
            )
            UndoPrompt(visible = showUndoPrompt, undoTime = undoPosition, onUndo = onUndo)
        }

        // Titles
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTrack?.artist ?: "Unknown Book",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = currentTrack?.title ?: "No Chapter Selected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "Chapter ends in: ${formatTime(remainingInChapter)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ProgressBar(
            sliderPosition = sliderPosition,
            totalDuration = totalDuration,
            isPrecise = isPrecise,
            sliderInteractionSource = sliderInteractionSource,
            onSliderValueChange = onSliderValueChange,
            onGestureStart = onGestureStart,
            onGestureEnd = onGestureEnd,
            onGestureDelta = onGestureDelta,
            onChapterPopupRequest = onChapterPopupRequest
        )

        Spacer(modifier = Modifier.height(8.dp))

        PlaybackControls(
            isPlaying = isPlaying,
            onPrevious = onPrevious,
            onNext = onNext,
            onRewind = onRewind,
            onForward = onForward,
            onTogglePlayPause = onTogglePlayPause,
            playSize = 80.dp
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsRow(
            playbackSpeed = playbackSpeed,
            stopAfterCurrentTrack = stopAfterCurrentTrack,
            sleepTimerRemaining = sleepTimerRemaining,
            onShowSpeed = onShowSpeed,
            onShowTimer = onShowTimer
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onShowChapters,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Browse chapters", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
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
    onGestureDelta: (Long, Boolean) -> Unit,
    onChapterPopupRequest: () -> Unit
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
                .weight(0.4f) // Constrain artwork width
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            AudioArtwork(
                uri = currentTrack?.uri,
                modifier = Modifier.fillMaxSize(),
                iconSize = 80.dp
            )
            UndoPrompt(visible = showUndoPrompt, undoTime = undoPosition, onUndo = onUndo)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTrack?.artist ?: "Unknown Book",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack?.title ?: "No Chapter",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "Chapter ends in: ${formatTime(remainingInChapter)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
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
                onGestureDelta = onGestureDelta,
                onChapterPopupRequest = onChapterPopupRequest
            )

            PlaybackControls(
                isPlaying = isPlaying,
                onPrevious = onPrevious,
                onNext = onNext,
                onRewind = onRewind,
                onForward = onForward,
                onTogglePlayPause = onTogglePlayPause,
                iconSize = 32.dp,
                playSize = 64.dp
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
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse chapters", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
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
                    fontSize = 18.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
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
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onGestureDelta: (Long, Boolean) -> Unit,
    onChapterPopupRequest: () -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPrecise) {
                Text(
                    "PRECISE SEEKING (BOOK)",
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
                .height(48.dp)
        ) {
            Slider(
                enabled = false,
                value = sliderPosition.toFloat(),
                onValueChange = { },
                onValueChangeFinished = { },
                valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                interactionSource = sliderInteractionSource,
                modifier = Modifier.align(Alignment.Center),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
                        modifier = Modifier.height(6.dp),
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
                        detectTapGestures(
                            onTap = { offset ->
                                val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                onGestureStart()
                                onSliderValueChange((ratio * totalDuration).toLong())
                                onGestureEnd()
                            },
                            onLongPress = { onChapterPopupRequest() }
                        )
                    }
                    .pointerInput(totalDuration) {
                        detectDragGestures(
                            onDragStart = { _ ->
                                onGestureStart()
                            },
                            onDragEnd = { onGestureEnd() },
                            onDragCancel = { onGestureEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                
                                // Stability Zone: Horizontal movement is ignored in the bottom half 
                                // to ensure the long-press remains stable and accidental jumps are prevented.
                                val isStabilityZone = change.position.y > size.height * 0.5f
                                
                                if (!isStabilityZone) {
                                    val precise = change.position.y < 0
                                    val sensitivity = if (precise) 0.1f else 1.0f
                                    val deltaMs = ((dragAmount.x * sensitivity) / size.width.toFloat()) * totalDuration
                                    onGestureDelta(deltaMs.toLong(), precise)
                                }
                            }
                        )
                    }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(sliderPosition), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(formatTime(totalDuration), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ChapterSeekDialog(
    currentTrack: AudioFile?,
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderPos by remember { mutableLongStateOf(currentPosition) }
    var isPreciseMode by remember { mutableStateOf(false) }
    
    val adjustBy: (Long) -> Unit = { ms ->
        sliderPos = (sliderPos + ms).coerceIn(0L, duration)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        containerColor = MidnightGreen,
        shape = RoundedCornerShape(32.dp),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Chapter Zoom", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text(currentTrack?.title ?: "Current Chapter", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(formatTime(sliderPos), style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    onClick = { isPreciseMode = !isPreciseMode },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isPreciseMode) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                    border = if (isPreciseMode) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isPreciseMode) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isPreciseMode) "PRECISE SEEKING ON" else "Standard Seeking", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Slider(
                    value = sliderPos.toFloat(),
                    onValueChange = { sliderPos = it.toLong() },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    thumb = {
                        Box(modifier = Modifier.size(44.dp).graphicsLayer(rotationZ = -15f)) {
                            Image(painter = painterResource(id = R.mipmap.ic_leaf_colorful_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(6.dp),
                            thumbTrackGapSize = 0.dp,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        )
                    }
                )
                
                if (isPreciseMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { adjustBy(-1000) }) { Icon(Icons.Default.RemoveCircleOutline, contentDescription = "-1s", tint = Color.White, modifier = Modifier.size(32.dp)) }
                        Text("-1s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("+1s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { adjustBy(1000) }) { Icon(Icons.Default.AddCircleOutline, contentDescription = "+1s", tint = Color.White, modifier = Modifier.size(32.dp)) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { adjustBy(-10000) }) { Icon(Icons.Default.RemoveCircleOutline, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(32.dp)) }
                        Text("-10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("+10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { adjustBy(10000) }) { Icon(Icons.Default.AddCircleOutline, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(32.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSeek(sliderPos); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MidnightGreen), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("APPLY POSITION", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("CANCEL", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onTogglePlayPause: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp = 44.dp,
    playSize: androidx.compose.ui.unit.Dp = 80.dp
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onRewind) {
            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.onSurface)
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
            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.onSurface)
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${"%.1f".format(playbackSpeed)}x",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Surface(
            onClick = onShowTimer,
            shape = RoundedCornerShape(20.dp),
            color = if (sleepTimerRemaining != null || stopAfterCurrentTrack) 
                MaterialTheme.colorScheme.onSurface 
            else 
                MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Default.Timer, 
                    contentDescription = null, 
                    modifier = Modifier.size(20.dp), 
                    tint = if (sleepTimerRemaining != null || stopAfterCurrentTrack) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        stopAfterCurrentTrack -> "Ch. End"
                        sleepTimerRemaining != null -> formatTimerRemaining(sleepTimerRemaining)
                        else -> "Timer"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (sleepTimerRemaining != null || stopAfterCurrentTrack) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
