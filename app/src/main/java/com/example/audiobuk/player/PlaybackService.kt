package com.example.audiobuk.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.example.audiobuk.MainActivity
import com.example.audiobuk.repository.AudioBookRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: AudioBookRepository

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        repository = AudioBookRepository(this)
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Handles audio focus (phone calls)
            .setHandleAudioBecomingNoisy(true) // Pauses on headphone disconnect
            .build()
            
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.example.audiobuk.OPEN_PLAYER"
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaLibrarySession.Callback {
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                return serviceScope.future {
                    val lastBook = repository.getLastPlayedAudioBook()
                    if (lastBook != null) {
                        val mediaItems = lastBook.audioFiles.map { audioFile ->
                            val metadata = MediaMetadata.Builder()
                                .setTitle(audioFile.title)
                                .setArtist(audioFile.artist)
                                .build()
                            MediaItem.Builder()
                                .setUri(audioFile.uri)
                                .setMediaId(audioFile.id.toString())
                                .setMediaMetadata(metadata)
                                .build()
                        }
                        val startIndex = if (lastBook.lastPlayedUri != null) {
                            lastBook.audioFiles.indexOfFirst { it.uri == lastBook.lastPlayedUri }.takeIf { it != -1 } ?: 0
                        } else 0
                        
                        MediaSession.MediaItemsWithStartPosition(
                            mediaItems,
                            startIndex,
                            lastBook.lastPositionMs
                        )
                    } else {
                        throw UnsupportedOperationException("No last played book found")
                    }
                }
            }
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }
}
