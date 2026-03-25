package com.example.audiobuk.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audiobuk.R
import com.example.audiobuk.util.formatTimerRemaining

@Composable
fun SettingsRow(
    playbackSpeed: Float,
    stopAfterCurrentTrack: Boolean,
    sleepTimerRemaining: Long?,
    onShowSpeed: () -> Unit,
    onShowTimer: () -> Unit,
    onSpeedPositioned: (LayoutCoordinates) -> Unit = {},
    onTimerPositioned: (LayoutCoordinates) -> Unit = {}
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
            tonalElevation = 2.dp,
            modifier = Modifier.onGloballyPositioned(onSpeedPositioned)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.playback_speed), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
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
            tonalElevation = 2.dp,
            modifier = Modifier.onGloballyPositioned(onTimerPositioned)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Default.Timer, 
                    contentDescription = stringResource(R.string.sleep_timer), 
                    modifier = Modifier.size(20.dp), 
                    tint = if (sleepTimerRemaining != null || stopAfterCurrentTrack) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        stopAfterCurrentTrack -> stringResource(R.string.ch_end_short)
                        sleepTimerRemaining != null -> formatTimerRemaining(sleepTimerRemaining)
                        else -> stringResource(R.string.timer_label)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (sleepTimerRemaining != null || stopAfterCurrentTrack) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
