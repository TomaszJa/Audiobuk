package com.example.audiobuk.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.audiobuk.db.*
import com.example.audiobuk.model.AudioFile
import com.example.audiobuk.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MusicRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val musicDao = db.musicDao()

    fun getPlaylistsFlow(): Flow<List<Playlist>> {
        return musicDao.getAllPlaylists().map { entities ->
            entities.map { entity ->
                val audioFileEntities = musicDao.getAudioFilesForPlaylist(entity.uri)
                Playlist(
                    uri = Uri.parse(entity.uri),
                    name = entity.name,
                    audioFiles = audioFileEntities.map { it.toModel() },
                    lastPlayedUri = entity.lastPlayedAudioUri?.let { Uri.parse(it) },
                    lastPositionMs = entity.lastPositionMs
                )
            }
        }
    }

    suspend fun refreshLibrary(rootUri: Uri) {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return
        val existingPlaylistUris = musicDao.getAllPlaylistUris().toSet()
        
        val currentSubDirs = rootDoc.listFiles().filter { it.isDirectory }
        val currentSubDirUris = currentSubDirs.map { it.uri.toString() }.toSet()

        // 1. Remove missing playlists from database
        existingPlaylistUris.forEach { uri ->
            if (!currentSubDirUris.contains(uri)) {
                musicDao.deletePlaylistCompletely(uri)
            }
        }

        val newPlaylistEntities = mutableListOf<PlaylistEntity>()
        val newAudioFileEntities = mutableListOf<AudioFileEntity>()

        // 2. Add new playlists found in filesystem
        currentSubDirs.forEach { subDir ->
            val subDirUri = subDir.uri.toString()
            
            // Only scan if this directory is not already in our database
            if (!existingPlaylistUris.contains(subDirUri)) {
                val subDirName = subDir.name ?: "Unknown"
                val audioFiles = mutableListOf<AudioFileEntity>()
                
                subDir.listFiles().forEach { file ->
                    if (file.isFile && isAudioFile(file.name)) {
                        audioFiles.add(
                            AudioFileEntity(
                                uri = file.uri.toString(),
                                playlistUri = subDirUri,
                                displayName = file.name ?: "Unknown",
                                artist = subDirName,
                                title = file.name?.substringBeforeLast(".") ?: "Unknown",
                                duration = 0
                            )
                        )
                    }
                }
                
                if (audioFiles.isNotEmpty()) {
                    newPlaylistEntities.add(
                        PlaylistEntity(
                            uri = subDirUri,
                            name = subDirName,
                            lastPlayedAudioUri = null,
                            lastPositionMs = 0L
                        )
                    )
                    newAudioFileEntities.addAll(audioFiles)
                }
            }
        }
        
        if (newPlaylistEntities.isNotEmpty()) {
            musicDao.refreshLibrary(newPlaylistEntities, newAudioFileEntities)
        }
    }

    suspend fun updatePlaybackState(playlistUri: Uri, audioUri: Uri, positionMs: Long) {
        val playlist = musicDao.getPlaylistByUri(playlistUri.toString())
        if (playlist != null) {
            musicDao.updatePlaylist(playlist.copy(
                lastPlayedAudioUri = audioUri.toString(),
                lastPositionMs = positionMs
            ))
        }
    }

    private fun isAudioFile(fileName: String?): Boolean {
        return fileName?.lowercase()?.endsWith(".mp3") == true
    }

    private fun AudioFileEntity.toModel() = AudioFile(
        id = uri.hashCode().toLong(),
        uri = Uri.parse(uri),
        displayName = displayName,
        artist = artist,
        duration = duration,
        title = title
    )
}
