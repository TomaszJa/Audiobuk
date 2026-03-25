package com.example.audiobuk.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.audiobuk.db.*
import com.example.audiobuk.model.AudioFile
import com.example.audiobuk.model.AudioBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AudioBookRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val musicDao = db.musicDao()

    fun getPlaylistsFlow(): Flow<List<AudioBook>> {
        return musicDao.getPlaylistsWithAudioFiles().map { entities ->
            entities.map { playlistWithFiles ->
                val entity = playlistWithFiles.playlist
                AudioBook(
                    uri = Uri.parse(entity.uri),
                    name = entity.name,
                    audioFiles = playlistWithFiles.audioFiles.map { it.toModel() },
                    lastPlayedUri = entity.lastPlayedAudioUri?.let { Uri.parse(it) },
                    lastPositionMs = entity.lastPositionMs
                )
            }
        }
    }

    suspend fun refreshLibrary(rootUri: Uri) = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext
        val existingPlaylistUris = musicDao.getAllPlaylistUris().toSet()
        
        val currentSubDirs = rootDoc.listFiles().filter { it.isDirectory }
        val currentSubDirUris = currentSubDirs.map { it.uri.toString() }.toSet()

        // 1. Remove missing playlists from database
        existingPlaylistUris.forEach { uri ->
            if (!currentSubDirUris.contains(uri)) {
                musicDao.deletePlaylistCompletely(uri)
            }
        }

        val retriever = MediaMetadataRetriever()

        // 2. Add or Update playlists
        currentSubDirs.forEach { subDir ->
            val subDirUri = subDir.uri.toString()
            val subDirName = subDir.name ?: "Unknown"
            
            val existingFiles = if (existingPlaylistUris.contains(subDirUri)) {
                musicDao.getAudioFilesForPlaylist(subDirUri).associateBy { it.uri }
            } else {
                emptyMap()
            }

            val audioFilesToInsert = mutableListOf<AudioFileEntity>()
            val audioFilesToUpdate = mutableListOf<AudioFileEntity>()
            
            subDir.listFiles().forEach { file ->
                if (file.isFile && isAudioFile(file.name)) {
                    val fileUri = file.uri.toString()
                    val existingFile = existingFiles[fileUri]
                    
                    if (existingFile == null || existingFile.duration == 0L) {
                        var duration = 0L
                        try {
                            retriever.setDataSource(context, file.uri)
                            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        val entity = AudioFileEntity(
                            uri = fileUri,
                            playlistUri = subDirUri,
                            displayName = file.name ?: "Unknown",
                            artist = subDirName,
                            title = file.name?.substringBeforeLast(".") ?: "Unknown",
                            duration = duration
                        )
                        
                        if (existingFile == null) audioFilesToInsert.add(entity)
                        else audioFilesToUpdate.add(entity)
                    }
                }
            }
            
            if (!existingPlaylistUris.contains(subDirUri)) {
                if (audioFilesToInsert.isNotEmpty()) {
                    musicDao.refreshLibrary(
                        listOf(PlaylistEntity(uri = subDirUri, name = subDirName)),
                        audioFilesToInsert
                    )
                }
            } else {
                if (audioFilesToInsert.isNotEmpty()) {
                    musicDao.insertAudioFiles(audioFilesToInsert)
                }
                if (audioFilesToUpdate.isNotEmpty()) {
                    musicDao.updateAudioFiles(audioFilesToUpdate)
                }
            }
        }
        retriever.release()
    }

    suspend fun updatePlaybackState(playlistUri: Uri, audioUri: Uri, positionMs: Long) = withContext(Dispatchers.IO) {
        val playlist = musicDao.getPlaylistByUri(playlistUri.toString())
        if (playlist != null) {
            musicDao.updatePlaylist(playlist.copy(
                lastPlayedAudioUri = audioUri.toString(),
                lastPositionMs = positionMs
            ))
        }
    }

    private fun isAudioFile(fileName: String?): Boolean {
        return fileName?.lowercase()?.let { 
            it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".m4b") || it.endsWith(".wav")
        } ?: false
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
