package com.example.audiobuk.model

import android.net.Uri

data class Playlist(
    val uri: Uri,
    val name: String,
    val audioFiles: List<AudioFile>,
    val lastPlayedUri: Uri? = null,
    val lastPositionMs: Long = 0L
)
