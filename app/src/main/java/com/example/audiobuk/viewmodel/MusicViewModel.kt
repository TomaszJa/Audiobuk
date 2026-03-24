package com.example.audiobuk.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobuk.model.Playlist
import com.example.audiobuk.player.AudioPlayer
import com.example.audiobuk.repository.MusicRepository
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
    private val repository = MusicRepository(application)
    private val prefs = application.getSharedPreferences("audiobuk_prefs", Context.MODE_PRIVATE)
    
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredPlaylists: StateFlow<List<Playlist>> = combine(_playlists, _searchQuery) { playlists, query ->
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

    fun playPlaylist(playlist: Playlist) {
        val currentUri = currentTrack.value?.uri
        val isAlreadyPlayingThisPlaylist = playlist.audioFiles.any { it.uri == currentUri }

        if (!isAlreadyPlayingThisPlaylist) {
            player.playPlaylist(
                audioFiles = playlist.audioFiles,
                startUri = playlist.lastPlayedUri,
                startPositionMs = playlist.lastPositionMs
            )
        }
        _showPlayerScreen.value = true
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
