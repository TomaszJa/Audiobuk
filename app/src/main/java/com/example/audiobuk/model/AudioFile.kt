package com.example.audiobuk.model

import android.net.Uri

data class AudioFile(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val artist: String,
    val duration: Long,
    val title: String
)
