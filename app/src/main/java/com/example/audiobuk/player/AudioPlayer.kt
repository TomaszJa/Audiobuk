package com.example.audiobuk.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.audiobuk.model.AudioFile
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(UnstableApi::class)
class AudioPlayer(context: Context, private val onProgressUpdate: (String, Uri, Long) -> Unit) {
    private var controllerFuture: ListenableFuture<MediaController>
    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentTrack = MutableStateFlow<AudioFile?>(null)
    val currentTrack: StateFlow<AudioFile?> = _currentTrack

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _stopAfterCurrentTrack = MutableStateFlow(false)
    val stopAfterCurrentTrack: StateFlow<Boolean> = _stopAfterCurrentTrack

    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                
                syncState(controller)

                controller.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        val extras = controller.sessionExtras
                        _sleepTimerRemaining.value = extras.getLong(PlaybackService.EXTRA_SLEEP_TIMER_REMAINING, 0L).takeIf { it > 0 }
                        _stopAfterCurrentTrack.value = extras.getBoolean(PlaybackService.EXTRA_STOP_CHAPTER, false)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (!isPlaying) saveCurrentState()
                    }
                    
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        saveCurrentState()
                        updateCurrentTrack(mediaItem)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _duration.value = controller.duration.coerceAtLeast(0L)
                        }
                    }

                    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                        _playbackSpeed.value = playbackParameters.speed
                    }
                })
                
                scope.launch {
                    while (isActive) {
                        mediaController?.let { ctrl ->
                            _currentPosition.value = ctrl.currentPosition
                            val extras = ctrl.sessionExtras
                            val remaining = extras.getLong(PlaybackService.EXTRA_SLEEP_TIMER_REMAINING, 0L)
                            _sleepTimerRemaining.value = if (remaining > 0) remaining else null
                            _stopAfterCurrentTrack.value = extras.getBoolean(PlaybackService.EXTRA_STOP_CHAPTER, false)
                        }
                        delay(500)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun syncState(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _duration.value = controller.duration.coerceAtLeast(0L)
        _playbackSpeed.value = controller.playbackParameters.speed
        updateCurrentTrack(controller.currentMediaItem)
        
        val extras = controller.sessionExtras
        _sleepTimerRemaining.value = extras.getLong(PlaybackService.EXTRA_SLEEP_TIMER_REMAINING, 0L).takeIf { it > 0 }
        _stopAfterCurrentTrack.value = extras.getBoolean(PlaybackService.EXTRA_STOP_CHAPTER, false)
    }

    private fun saveCurrentState() {
        val controller = mediaController ?: return
        val mediaItem = controller.currentMediaItem ?: return
        val currentUri = mediaItem.localConfiguration?.uri ?: return
        onProgressUpdate(mediaItem.mediaId, currentUri, controller.currentPosition)
    }

    private fun updateCurrentTrack(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            _currentTrack.value = null
            _duration.value = 0
            return
        }
        val metadata = mediaItem.mediaMetadata
        
        // Use clipping configuration to determine duration if available
        var duration = mediaItem.clippingConfiguration.endPositionMs
        if (duration == C.TIME_END_OF_SOURCE) {
            duration = mediaController?.duration ?: 0L
        } else {
            duration -= mediaItem.clippingConfiguration.startPositionMs
        }

        _currentTrack.value = AudioFile(
            id = mediaItem.mediaId,
            uri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY,
            displayName = metadata.title?.toString() ?: "",
            artist = metadata.artist?.toString() ?: "Unknown",
            duration = duration,
            title = metadata.title?.toString() ?: "Unknown",
            startOffsetMs = mediaItem.clippingConfiguration.startPositionMs
        )
        _duration.value = duration.coerceAtLeast(0L)
    }

    fun playPlaylist(audioFiles: List<AudioFile>, startTrackId: String? = null, startPositionMs: Long = 0L) {
        val mediaItems = audioFiles.map { audioFile ->
            val metadata = MediaMetadata.Builder()
                .setTitle(audioFile.title)
                .setArtist(audioFile.artist)
                .build()
            
            val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(audioFile.startOffsetMs)
                .setEndPositionMs(if (audioFile.duration > 0) audioFile.startOffsetMs + audioFile.duration else C.TIME_END_OF_SOURCE)
                .build()

            MediaItem.Builder()
                .setUri(audioFile.uri)
                .setMediaId(audioFile.id)
                .setMediaMetadata(metadata)
                .setClippingConfiguration(clippingConfiguration)
                .build()
        }
        mediaController?.apply {
            setMediaItems(mediaItems)
            val startIndex = if (startTrackId != null) {
                audioFiles.indexOfFirst { it.id == startTrackId }.takeIf { it != -1 } ?: 0
            } else 0
            seekTo(startIndex, startPositionMs)
            prepare()
            play()
        }
    }

    fun playTrack(trackId: String) {
        mediaController?.let { controller ->
            for (i in 0 until controller.mediaItemCount) {
                if (controller.getMediaItemAt(i).mediaId == trackId) {
                    controller.seekTo(i, 0)
                    controller.play()
                    break
                }
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(positionMs: Long) { mediaController?.seekTo(positionMs) }
    fun seekTo(itemIndex: Int, positionMs: Long) { mediaController?.seekTo(itemIndex, positionMs) }
    fun seekForward() { mediaController?.let { it.seekTo(it.currentPosition + 10000) } }
    fun seekBack() { mediaController?.let { it.seekTo((it.currentPosition - 10000).coerceAtLeast(0L)) } }
    fun next() { mediaController?.seekToNext() }
    fun previous() { mediaController?.seekToPrevious() }
    fun setPlaybackSpeed(speed: Float) { mediaController?.playbackParameters = PlaybackParameters(speed) }

    fun setSleepTimer(minutes: Int) {
        val args = Bundle().apply { putInt("minutes", minutes) }
        mediaController?.sendCustomCommand(SessionCommand(PlaybackService.COMMAND_SET_SLEEP_TIMER, Bundle.EMPTY), args)
    }

    fun setStopAfterChapter(enabled: Boolean) {
        val args = Bundle().apply { putBoolean("enabled", enabled) }
        mediaController?.sendCustomCommand(SessionCommand(PlaybackService.COMMAND_SET_STOP_CHAPTER, Bundle.EMPTY), args)
    }

    fun release() {
        saveCurrentState()
        scope.cancel()
        MediaController.releaseFuture(controllerFuture)
    }
}
