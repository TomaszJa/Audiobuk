@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.audiobuk.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import com.example.audiobuk.ui.components.*
import com.example.audiobuk.ui.dialogs.ChapterSeekDialog
import com.example.audiobuk.ui.dialogs.ChaptersDialog
import com.example.audiobuk.ui.dialogs.PlaybackSpeedDialog
import com.example.audiobuk.ui.dialogs.SleepTimerDialog
import com.example.audiobuk.viewmodel.AudioBookViewModel
import kotlinx.coroutines.delay
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

    // Help State
    var currentHelpStepIndex by remember { mutableIntStateOf(-1) }
    val helpSteps = listOf(
        HelpStep(HelpTarget.PRECISION_SEEK, "Slide your finger UP while dragging the seek bar for 10x more precision!"),
        HelpStep(HelpTarget.CHAPTER_ZOOM, "Long press the seek bar to open Chapter Zoom for high-precision chapter navigation."),
        HelpStep(HelpTarget.SPEED, "Tap here to change the playback speed (0.5x to 2.0x)."),
        HelpStep(HelpTarget.TIMER, "Set a sleep timer or choose to stop playback at the end of the chapter."),
        HelpStep(HelpTarget.BROWSE, "View and navigate through all chapters in the book."),
        HelpStep(HelpTarget.CONTROLS, "Standard playback controls to play, pause, skip, or rewind.")
    )
    val targetCoordinates = remember { mutableStateMapOf<HelpTarget, LayoutCoordinates>() }

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
                actions = {
                    IconButton(onClick = { currentHelpStepIndex = 0 }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help", tint = MaterialTheme.colorScheme.onSurface)
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
        
        Box(modifier = Modifier.fillMaxSize()) {
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
                    onChapterPopupRequest = { showChapterSeekDialog = true },
                    onTargetPositioned = { target, coords -> targetCoordinates[target] = coords }
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
                    onChapterPopupRequest = { showChapterSeekDialog = true },
                    onTargetPositioned = { target, coords -> targetCoordinates[target] = coords }
                )
            }

            // Help Overlay
            if (currentHelpStepIndex >= 0 && currentHelpStepIndex < helpSteps.size) {
                HelpOverlay(
                    step = helpSteps[currentHelpStepIndex],
                    stepIndex = currentHelpStepIndex,
                    totalSteps = helpSteps.size,
                    targetCoordinates = targetCoordinates[helpSteps[currentHelpStepIndex].target],
                    onNext = { currentHelpStepIndex++ },
                    onDismiss = { currentHelpStepIndex = -1 }
                )
            }
        }
    }
}
