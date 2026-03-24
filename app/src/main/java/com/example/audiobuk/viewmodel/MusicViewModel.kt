package com.example.audiobuk.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobuk.model.AudioFile
import com.example.audiobuk.model.AudioBook
import com.example.audiobuk.player.AudioPlayer
import com.example.audiobuk.repository.AudioBookRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioBookRepository(application)
    private val prefs = application.getSharedPreferences("audiobuk_prefs", Context.MODE_PRIVATE)
    
    private val _playlists = MutableStateFlow<List<AudioBook>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredPlaylists: StateFlow<List<AudioBook>> = combine(_playlists, _searchQuery) { playlists, query ->
        if (query.isEmpty()) {
            playlists
        } else {
            playlists.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rootUri = MutableStateFlow<Uri?>(null)
    val rootUri: StateFlow<Uri?> = _rootUri

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _showPlayerScreen = MutableStateFlow(false)
    val showPlayerScreen: StateFlow<Boolean> = _showPlayerScreen

    private val _currentAudioBook = MutableStateFlow<AudioBook?>(null)
    val currentAudioBook: StateFlow<AudioBook?> = _currentAudioBook

    private val player = AudioPlayer(application) { uri, position ->
        viewModelScope.launch {
            _playlists.value.find { playlist -> 
                playlist.audioFiles.any { it.uri == uri }
            }?.let { playlist ->
                repository.updatePlaybackState(playlist.uri, uri, position)
            }
        }
    }
    
    val isPlaying = player.isPlaying
    val currentTrack = player.currentTrack
    val currentPosition = player.currentPosition
    val duration = player.duration
    val playbackSpeed = player.playbackSpeed
    val stopAfterCurrentTrack = player.stopAfterCurrentTrack

    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null) // Remaining time in seconds
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining
    private var sleepTimerJob: Job? = null

    init {
        val savedUri = prefs.getString("root_uri", null)
        if (savedUri != null) {
            val uri = Uri.parse(savedUri)
            _rootUri.value = uri
            observePlaylists()
            refreshLibrary(uri)
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            repository.getPlaylistsFlow().collectLatest {
                _playlists.value = it
                // If we are playing something, try to update the current playlist reference
                val currentTrackUri = player.currentTrack.value?.uri
                if (currentTrackUri != null) {
                    _currentAudioBook.value = it.find { p -> p.audioFiles.any { f -> f.uri == currentTrackUri } }
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setRootUri(uri: Uri) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString("root_uri", uri.toString()).apply()
        _rootUri.value = uri
        observePlaylists()
        refreshLibrary(uri)
    }

    fun refresh() {
        val uri = _rootUri.value
        if (uri != null) {
            refreshLibrary(uri)
        }
    }

    private fun refreshLibrary(uri: Uri) {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refreshLibrary(uri)
            _isRefreshing.value = false
        }
    }

    fun playPlaylist(audioBook: AudioBook) {
        val currentUri = currentTrack.value?.uri
        val isAlreadyPlayingThisPlaylist = audioBook.audioFiles.any { it.uri == currentUri }

        if (!isAlreadyPlayingThisPlaylist) {
            player.playPlaylist(
                audioFiles = audioBook.audioFiles,
                startUri = audioBook.lastPlayedUri,
                startPositionMs = audioBook.lastPositionMs
            )
            _currentAudioBook.value = audioBook
        } else {
            // Even if already playing, update current playlist to the latest one from DB
            _currentAudioBook.value = audioBook
        }
        _showPlayerScreen.value = true
    }

    fun playTrack(track: AudioFile) {
        player.playTrack(track.uri)
    }

    fun togglePlayPause() {
        player.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun seekForward() {
        player.seekForward()
    }

    fun seekBack() {
        player.seekBack()
    }

    fun next() {
        player.next()
    }

    fun previous() {
        player.previous()
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    fun setShowPlayerScreen(show: Boolean) {
        _showPlayerScreen.value = show
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        player.setStopAfterCurrentTrack(false)
        
        if (minutes == null) {
            _sleepTimerRemaining.value = null
            return
        }

        if (minutes == -1) { // -1 represents "Chapter end"
            player.setStopAfterCurrentTrack(true)
            _sleepTimerRemaining.value = null
            return
        }

        val totalSeconds = minutes * 60L
        _sleepTimerRemaining.value = totalSeconds
        
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _sleepTimerRemaining.value = remaining
            }
            player.pause()
            _sleepTimerRemaining.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
