package com.example.audiobuk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobuk.util.formatTime

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
