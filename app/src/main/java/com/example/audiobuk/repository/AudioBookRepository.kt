package com.example.audiobuk.repository

import android.content.Context
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.example.audiobuk.db.*
import com.example.audiobuk.model.AudioBook
import com.example.audiobuk.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import android.media.MediaExtractor

class AudioBookRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val musicDao = db.musicDao()

    fun getPlaylistsFlow(): Flow<List<AudioBook>> {
        return musicDao.getPlaylistsWithAudioFiles().map { entities ->
            entities.map { playlistWithFiles ->
                val entity = playlistWithFiles.playlist
                AudioBook(
                    uri = entity.uri.toUri(),
                    name = entity.name,
                    audioFiles = playlistWithFiles.audioFiles
                        .map { it.toModel() }
                        .sortedWith(compareBy<AudioFile> { it.trackNumber ?: Int.MAX_VALUE }.thenBy { it.title }),
                    lastPlayedUri = entity.lastPlayedAudioUri?.toUri(),
                    lastPlayedTrackId = entity.lastPlayedTrackId,
                    lastPositionMs = entity.lastPositionMs
                )
            }
        }
    }

    suspend fun getLastPlayedAudioBook(): AudioBook? = withContext(Dispatchers.IO) {
        musicDao.getLastPlayedPlaylist()?.let { playlistWithFiles ->
            val entity = playlistWithFiles.playlist
            AudioBook(
                uri = entity.uri.toUri(),
                name = entity.name,
                audioFiles = playlistWithFiles.audioFiles
                    .map { it.toModel() }
                    .sortedWith(compareBy<AudioFile> { it.trackNumber ?: Int.MAX_VALUE }.thenBy { it.title }),
                lastPlayedUri = entity.lastPlayedAudioUri?.toUri(),
                lastPlayedTrackId = entity.lastPlayedTrackId,
                lastPositionMs = entity.lastPositionMs
            )
        }
    }

    suspend fun refreshLibrary(rootUri: Uri) = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext
        val existingPlaylistUris = musicDao.getAllPlaylistUris().toSet()
        
        val items = rootDoc.listFiles()
        val currentItems = items.filter { it.isDirectory || isAudioFile(it.name) }
        val currentItemUris = currentItems.map { it.uri.toString() }.toSet()

        existingPlaylistUris.forEach { uri ->
            if (!currentItemUris.contains(uri)) {
                musicDao.deletePlaylistCompletely(uri)
            }
        }

        currentItems.forEach { item ->
            val itemUri = item.uri.toString()
            val audioFilesToInsert = mutableListOf<AudioFileEntity>()
            val playlistName: String
            
            val existingFiles = if (existingPlaylistUris.contains(itemUri)) {
                musicDao.getAudioFilesForPlaylist(itemUri).groupBy { it.uri }
            } else {
                emptyMap()
            }

            if (item.isDirectory) {
                playlistName = item.name ?: "Unknown"
                item.listFiles().forEach { file ->
                    if (file.isFile && isAudioFile(file.name)) {
                        processFile(file, itemUri, playlistName, existingFiles, audioFilesToInsert)
                    }
                }
            } else {
                val metadata = getBasicMetadata(item.uri)
                playlistName = metadata.title ?: item.name?.substringBeforeLast(".") ?: "Unknown"
                processFile(item, itemUri, playlistName, existingFiles, audioFilesToInsert)
            }
            
            if (!existingPlaylistUris.contains(itemUri)) {
                if (audioFilesToInsert.isNotEmpty()) {
                    musicDao.refreshLibrary(
                        listOf(PlaylistEntity(uri = itemUri, name = playlistName)),
                        audioFilesToInsert
                    )
                }
            } else if (audioFilesToInsert.isNotEmpty()) {
                musicDao.insertAudioFiles(audioFilesToInsert)
            }
        }
    }

    private fun processFile(
        file: DocumentFile,
        playlistUri: String,
        playlistName: String,
        existingFiles: Map<String, List<AudioFileEntity>>,
        output: MutableList<AudioFileEntity>
    ) {
        val fileUri = file.uri.toString()
        val existingEntries = existingFiles[fileUri]
        
        if (existingEntries == null) {
            val chapters = extractChapters(file)
            if (chapters.isEmpty()) {
                val duration = getDuration(file.uri)
                val metadata = getBasicMetadata(file.uri)
                output.add(AudioFileEntity(
                    id = fileUri,
                    uri = fileUri,
                    playlistUri = playlistUri,
                    displayName = file.name ?: "Unknown",
                    artist = metadata.artist ?: playlistName,
                    title = metadata.title ?: file.name?.substringBeforeLast(".") ?: "Unknown",
                    duration = duration,
                    startOffsetMs = 0L,
                    trackNumber = metadata.trackNumber
                ))
            } else {
                chapters.forEachIndexed { index, chapter ->
                    output.add(AudioFileEntity(
                        id = "${fileUri}_${chapter.startOffsetMs}",
                        uri = fileUri,
                        playlistUri = playlistUri,
                        displayName = chapter.title,
                        artist = playlistName,
                        title = chapter.title,
                        duration = chapter.duration,
                        startOffsetMs = chapter.startOffsetMs,
                        trackNumber = index + 1 // Use chapter index as track number
                    ))
                }
            }
        }
    }

    private fun getDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private data class BasicMetadata(val title: String?, val artist: String?, val trackNumber: Int?)

    private fun getBasicMetadata(uri: Uri): BasicMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val trackNumberStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            // trackNumberStr can be "1", "1/10", etc.
            val trackNumber = trackNumberStr?.substringBefore('/')?.toIntOrNull()
            BasicMetadata(title, artist, trackNumber)
        } catch (e: Exception) {
            BasicMetadata(null, null, null)
        } finally {
            retriever.release()
        }
    }

    private data class ChapterInfo(val title: String, val startOffsetMs: Long, val duration: Long)

    private fun extractChapters(file: DocumentFile): List<ChapterInfo> {
        val fileName = file.name?.lowercase() ?: ""
        if (!fileName.endsWith(".m4a") && !fileName.endsWith(".m4b")) return emptyList()

        val chapters = mutableListOf<ChapterInfo>()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, file.uri, null)
            val trackCount = extractor.trackCount
            var chapterTrackIndex = -1
            
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime == "text/3gpp-tt" || mime == "text/chapters") {
                    chapterTrackIndex = i
                    break
                }
            }

            if (chapterTrackIndex != -1) {
                extractor.selectTrack(chapterTrackIndex)
                val buffer = ByteBuffer.allocate(2048)
                
                while (true) {
                    val sampleTime = extractor.sampleTime
                    if (sampleTime == -1L) break
                    
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize > 0) {
                        val textLength = if (sampleSize >= 2) buffer.getShort(0).toInt() and 0xFFFF else 0
                        val title = if (textLength > 0 && textLength <= sampleSize - 2) {
                            val bytes = ByteArray(textLength)
                            buffer.position(2)
                            buffer.get(bytes)
                            String(bytes)
                        } else {
                            "Chapter ${chapters.size + 1}"
                        }
                        
                        if (chapters.isNotEmpty()) {
                            val prev = chapters.last()
                            chapters[chapters.size - 1] = prev.copy(duration = (sampleTime / 1000) - prev.startOffsetMs)
                        }
                        
                        chapters.add(ChapterInfo(title, sampleTime / 1000, 0L))
                    }
                    extractor.advance()
                }
                
                if (chapters.isNotEmpty()) {
                    val totalDuration = getDuration(file.uri)
                    val last = chapters.last()
                    chapters[chapters.size - 1] = last.copy(duration = totalDuration - last.startOffsetMs)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            extractor.release()
        }
        return chapters
    }

    suspend fun updatePlaybackState(playlistUri: Uri, trackId: String, audioUri: Uri, positionMs: Long) = withContext(Dispatchers.IO) {
        val playlist = musicDao.getPlaylistByUri(playlistUri.toString())
        if (playlist != null) {
            musicDao.updatePlaylist(playlist.copy(
                lastPlayedAudioUri = audioUri.toString(),
                lastPlayedTrackId = trackId,
                lastPositionMs = positionMs,
                lastPlayedTimestamp = System.currentTimeMillis()
            ))
        }
    }

    private fun isAudioFile(fileName: String?): Boolean {
        return fileName?.lowercase()?.let { 
            it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".m4b") || it.endsWith(".wav") || it.endsWith(".aac")
        } ?: false
    }

    private fun AudioFileEntity.toModel() = AudioFile(
        id = id,
        uri = uri.toUri(),
        displayName = displayName,
        artist = artist,
        duration = duration,
        title = title,
        startOffsetMs = startOffsetMs,
        trackNumber = trackNumber
    )
}
