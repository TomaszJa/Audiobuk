package com.example.audiobuk.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.audiobuk.MainActivity
import com.example.audiobuk.model.AudioBook
import com.example.audiobuk.model.AudioFile
import com.example.audiobuk.repository.AudioBookRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: AudioBookRepository
    private lateinit var player: ExoPlayer

    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRemaining = 0L
    private var stopAfterChapter = false

    private val sleepTimerRunnable = object : Runnable {
        override fun run() {
            if (sleepTimerRemaining > 0) {
                sleepTimerRemaining--
                if (sleepTimerRemaining == 0L) {
                    player.pause()
                } else {
                    sleepTimerHandler.postDelayed(this, 1000)
                }
                updateSessionExtras()
            }
        }
    }

    companion object {
        const val COMMAND_SET_SLEEP_TIMER = "SET_SLEEP_TIMER"
        const val COMMAND_SET_STOP_CHAPTER = "SET_STOP_CHAPTER"
        const val EXTRA_SLEEP_TIMER_REMAINING = "SLEEP_TIMER_REMAINING"
        const val EXTRA_STOP_CHAPTER = "STOP_CHAPTER"
    }

    override fun onCreate() {
        super.onCreate()
        repository = AudioBookRepository(this)
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (stopAfterChapter && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    player.pause()
                    stopAfterChapter = false
                    updateSessionExtras()
                }
            }
        })
            
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.example.audiobuk.OPEN_PLAYER"
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                    .add(SessionCommand(COMMAND_SET_SLEEP_TIMER, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_SET_STOP_CHAPTER, Bundle.EMPTY))
                    .build()
                
                // Pass current state
                val extras = Bundle().apply {
                    putLong(EXTRA_SLEEP_TIMER_REMAINING, sleepTimerRemaining)
                    putBoolean(EXTRA_STOP_CHAPTER, stopAfterChapter)
                }
                
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(availableSessionCommands)
                    .setAvailablePlayerCommands(connectionResult.availablePlayerCommands)
                    .setSessionExtras(extras)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    COMMAND_SET_SLEEP_TIMER -> {
                        val minutes = args.getInt("minutes", 0)
                        startSleepTimer(minutes)
                    }
                    COMMAND_SET_STOP_CHAPTER -> {
                        val enabled = args.getBoolean("enabled", false)
                        stopAfterChapter = enabled
                        if (enabled) cancelSleepTimer()
                        updateSessionExtras()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

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

    private fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        stopAfterChapter = false
        if (minutes > 0) {
            sleepTimerRemaining = minutes * 60L
            sleepTimerHandler.post(sleepTimerRunnable)
        }
        updateSessionExtras()
    }

    private fun cancelSleepTimer() {
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable)
        sleepTimerRemaining = 0L
        updateSessionExtras()
    }

    private fun updateSessionExtras() {
        val extras = Bundle().apply {
            putLong(EXTRA_SLEEP_TIMER_REMAINING, sleepTimerRemaining)
            putBoolean(EXTRA_STOP_CHAPTER, stopAfterChapter)
        }
        mediaLibrarySession?.setSessionExtras(extras)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        cancelSleepTimer()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }
}
