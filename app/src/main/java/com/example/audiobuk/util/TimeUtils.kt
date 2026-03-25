package com.example.audiobuk.util

import java.util.Locale

/**
 * Formats milliseconds into a H:MM:SS string.
 * This ensures that long audiobooks don't show thousands of minutes.
 */
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
}

/**
 * Formats remaining seconds into H:MM:SS string.
 */
fun formatTimerRemaining(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    
    return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
}
