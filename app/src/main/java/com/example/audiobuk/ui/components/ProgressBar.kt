package com.example.audiobuk.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobuk.R
import com.example.audiobuk.util.formatTime

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
    onGestureDelta: (Long, Boolean) -> Unit,
    onChapterPopupRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPrecise) {
                Text(
                    text = stringResource(R.string.precise_seeking_10x),
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
                .height(64.dp)
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
                                val isPreciseMode = change.position.y < 0
                                val sensitivity = if (isPreciseMode) 0.1f else 1.0f
                                val deltaMs = ((dragAmount.x * sensitivity) / size.width.toFloat()) * totalDuration
                                onGestureDelta(deltaMs.toLong(), isPreciseMode)
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
