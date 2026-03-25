package com.example.audiobuk.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobuk.util.formatTime

@Composable
fun LandscapeLayout(
    modifier: Modifier = Modifier,
    currentTrack: com.example.audiobuk.model.AudioFile?,
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
    onChapterPopupRequest: () -> Unit,
    onTargetPositioned: (HelpTarget, LayoutCoordinates) -> Unit
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
                .weight(0.4f)
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
                onChapterPopupRequest = onChapterPopupRequest,
                modifier = Modifier.onGloballyPositioned { coords ->
                    onTargetPositioned(HelpTarget.PRECISION_SEEK, coords)
                    onTargetPositioned(HelpTarget.CHAPTER_ZOOM, coords)
                }
            )

            PlaybackControls(
                isPlaying = isPlaying,
                onPrevious = onPrevious,
                onNext = onNext,
                onRewind = onRewind,
                onForward = onForward,
                onTogglePlayPause = onTogglePlayPause,
                iconSize = 32.dp,
                playSize = 64.dp,
                modifier = Modifier.onGloballyPositioned { onTargetPositioned(HelpTarget.CONTROLS, it) }
            )

            SettingsRow(
                playbackSpeed = playbackSpeed,
                stopAfterCurrentTrack = stopAfterCurrentTrack,
                sleepTimerRemaining = sleepTimerRemaining,
                onShowSpeed = onShowSpeed,
                onShowTimer = onShowTimer,
                onSpeedPositioned = { onTargetPositioned(HelpTarget.SPEED, it) },
                onTimerPositioned = { onTargetPositioned(HelpTarget.TIMER, it) }
            )

            TextButton(
                onClick = onShowChapters,
                modifier = Modifier.fillMaxWidth().onGloballyPositioned { onTargetPositioned(HelpTarget.BROWSE, it) }
            ) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse chapters", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}
