package com.example.audiobuk.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobuk.R
import com.example.audiobuk.ui.theme.MidnightGreen

enum class HelpTarget {
    PRECISION_SEEK, CHAPTER_ZOOM, SPEED, TIMER, BROWSE, CONTROLS,
    LIB_FILTER, LIB_SORT, LIB_VIEW, LIB_ROOT, LIB_SEARCH, LIB_PLAYBACK_BAR
}

data class HelpStep(
    val target: HelpTarget,
    val text: String
)

@Composable
fun HelpOverlay(
    step: HelpStep,
    stepIndex: Int,
    totalSteps: Int,
    targetCoordinates: LayoutCoordinates?,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Arrow")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ArrowAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(enabled = false) { }
    ) {
        if (targetCoordinates != null && targetCoordinates.isAttached) {
            val position = targetCoordinates.positionInRoot()
            val size = targetCoordinates.size
            
            // Arrow Drawing
            Canvas(modifier = Modifier.fillMaxSize()) {
                val arrowColor = Color(0xFF4CAF50) // Forest Green
                val arrowX = position.x + size.width / 2
                
                // If the target is at the bottom (like PlaybackBar), point UP
                // or if it's the PRECISION_SEEK step
                val pointUp = step.target == HelpTarget.PRECISION_SEEK || step.target == HelpTarget.LIB_PLAYBACK_BAR
                
                val startY: Float
                val endY: Float
                val wingTipY: Float
                
                if (pointUp) {
                    // Arrow pointing UP from the element
                    startY = position.y - 10f - arrowOffset
                    endY = position.y - 70f - arrowOffset
                    wingTipY = endY + 20f // Wings below tip
                } else {
                    // Arrow pointing DOWN to the element
                    startY = position.y - 70f - arrowOffset
                    endY = position.y - 10f - arrowOffset
                    wingTipY = endY - 20f // Wings above tip
                }

                drawLine(
                    color = arrowColor,
                    start = Offset(arrowX, startY),
                    end = Offset(arrowX, endY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val wingPath = Path().apply {
                    moveTo(arrowX - 20f, wingTipY)
                    lineTo(arrowX, endY)
                    lineTo(arrowX + 20f, wingTipY)
                }
                drawPath(
                    path = wingPath,
                    color = arrowColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Help Box
            Surface(
                modifier = Modifier
                    .align(if (position.y > 400) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 90.dp),
                shape = RoundedCornerShape(24.dp),
                color = MidnightGreen,
                contentColor = Color.White,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${stepIndex + 1}/$totalSteps",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = step.text,
                        color = Color.White, // Force white text for visibility
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.skip), color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onNext,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(if (stepIndex == totalSteps - 1) stringResource(R.string.finish) else stringResource(R.string.next_step), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}
