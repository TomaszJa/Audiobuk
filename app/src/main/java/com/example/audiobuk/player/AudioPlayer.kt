package com.example.audiobuk.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.audiobuk.model.AudioFile
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioPlayer(context: Context, private val onProgressUpdate: (Uri, Long) -> Unit) {
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

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                
                _isPlaying.value = controller.isPlaying
                _duration.value = controller.duration.coerceAtLeast(0L)
                _playbackSpeed.value = controller.playbackParameters.speed
                updateCurrentTrack(controller.currentMediaItem)

                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (!isPlaying) {
                            // Save when paused
                            saveCurrentState()
                        }
                    }
                    
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        // Save when track changes
                        saveCurrentState()
                        updateCurrentTrack(mediaItem)
                        _duration.value = controller.duration.coerceAtLeast(0L)
                        
                        if (_stopAfterCurrentTrack.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            controller.pause()
                            _stopAfterCurrentTrack.value = false
                        }
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
                
                // Periodic position update for UI ONLY
                scope.launch {
                    while (isActive) {
                        val currentCtrl = mediaController
                        if (currentCtrl != null) {
                            _currentPosition.value = currentCtrl.currentPosition
                        }
                        delay(100) // 10fps for smooth UI
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun saveCurrentState() {
        val controller = mediaController ?: return
        val currentUri = controller.currentMediaItem?.localConfiguration?.uri ?: return
        onProgressUpdate(currentUri, controller.currentPosition)
    }

    private fun updateCurrentTrack(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            _currentTrack.value = null
            return
        }
        val metadata = mediaItem.mediaMetadata
        _currentTrack.value = AudioFile(
            id = mediaItem.mediaId.toLongOrNull() ?: -1L,
            uri = mediaItem.localConfiguration?.uri ?: Uri.EMPTY,
            displayName = metadata.title?.toString() ?: "",
            artist = metadata.artist?.toString() ?: "Unknown",
            duration = 0, // Duration will be updated from duration flow
            title = metadata.title?.toString() ?: "Unknown"
        )
    }

    fun playPlaylist(audioFiles: List<AudioFile>, startUri: Uri? = null, startPositionMs: Long = 0L) {
        val mediaItems = audioFiles.map { audioFile ->
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

        mediaController?.apply {
            setMediaItems(mediaItems)
            
            val startIndex = if (startUri != null) {
                audioFiles.indexOfFirst { it.uri == startUri }.takeIf { it != -1 } ?: 0
            } else 0
            
            seekTo(startIndex, startPositionMs)
            prepare()
            play()
        }
    }

    fun playTrack(uri: Uri) {
        mediaController?.let { controller ->
            for (i in 0 until controller.mediaItemCount) {
                if (controller.getMediaItemAt(i).localConfiguration?.uri == uri) {
                    controller.seekTo(i, 0)
                    controller.play()
                    break
                }
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }
    
    fun seekTo(itemIndex: Int, positionMs: Long) {
        mediaController?.seekTo(itemIndex, positionMs)
    }

    fun seekForward() {
        mediaController?.let {
            it.seekTo(it.currentPosition + 10000)
        }
    }

    fun seekBack() {
        mediaController?.let {
            it.seekTo((it.currentPosition - 10000).coerceAtLeast(0L))
        }
    }

    fun next() {
        mediaController?.seekToNext()
    }

    fun previous() {
        mediaController?.seekToPrevious()
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.playbackParameters = PlaybackParameters(speed)
    }

    fun setStopAfterCurrentTrack(stop: Boolean) {
        _stopAfterCurrentTrack.value = stop
    }

    fun release() {
        saveCurrentState()
        scope.cancel()
        MediaController.releaseFuture(controllerFuture)
    }
}
