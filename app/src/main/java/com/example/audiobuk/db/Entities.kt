package com.example.audiobuk.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val lastPlayedAudioUri: String? = null,
    val lastPositionMs: Long = 0L,
    val lastPlayedTimestamp: Long = 0L,
    val lastPlayedTrackId: String? = null
)

@Entity(tableName = "audio_files")
data class AudioFileEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val playlistUri: String,
    val displayName: String,
    val artist: String,
    val title: String,
    val duration: Long,
    val startOffsetMs: Long = 0L,
    val trackNumber: Int? = null // New field for sorting
)

data class PlaylistWithAudioFiles(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "uri",
        entityColumn = "playlistUri"
    )
    val audioFiles: List<AudioFileEntity>
)
