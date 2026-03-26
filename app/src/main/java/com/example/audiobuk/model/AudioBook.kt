package com.example.audiobuk.model

import android.net.Uri

data class AudioBook(
    val uri: Uri,
    val name: String,
    val audioFiles: List<AudioFile>,
    val lastPlayedUri: Uri? = null,
    val lastPlayedTrackId: String? = null,
    val lastPositionMs: Long = 0L
)
