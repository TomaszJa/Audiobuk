package com.example.audiobuk.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.audiobuk.R
import com.example.audiobuk.model.AudioFile
import com.example.audiobuk.ui.theme.MidnightGreen
import com.example.audiobuk.util.formatTime

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
                Text(stringResource(R.string.chapter_zoom), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text(currentTrack?.title ?: stringResource(R.string.current_chapter_placeholder), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        Text(if (isPreciseMode) stringResource(R.string.precise_seeking_on) else stringResource(R.string.standard_seeking), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                Text(stringResource(R.string.apply_position), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel_uppercase), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold) }
        }
    )
}
