package com.example.audiobuk.model

import android.net.Uri

data class AudioFile(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val artist: String,
    val duration: Long,
    val title: String,
    val startOffsetMs: Long = 0L,
    val trackNumber: Int? = null
)
