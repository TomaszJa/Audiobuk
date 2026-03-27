package com.example.audiobuk.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobuk.model.AudioBook
import com.example.audiobuk.model.AudioFile
import com.example.audiobuk.player.AudioPlayer
import com.example.audiobuk.repository.AudioBookRepository
import com.example.audiobuk.util.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AudioBookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioBookRepository(application)
    private val prefs = PreferenceManager(application)
    
    private val _playlists = MutableStateFlow<List<AudioBook>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOrder = MutableStateFlow(prefs.sortOrder)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    private val _isGridView = MutableStateFlow(prefs.isGridView)
    val isGridView: StateFlow<Boolean> = _isGridView

    private val _hideFinished = MutableStateFlow(prefs.hideFinished)
    val hideFinished: StateFlow<Boolean> = _hideFinished

    val filteredPlaylists: StateFlow<List<AudioBook>> = combine(
        _playlists, _searchQuery, _sortOrder, _hideFinished
    ) { playlists, query, sort, hideFinished ->
        var filtered = if (query.isEmpty()) {
            playlists
        } else {
            playlists.filter { it.name.contains(query, ignoreCase = true) }
        }
        
        if (hideFinished) {
            filtered = filtered.filter { getProgress(it) < 100 }
        }
        
        when (sort) {
            SortOrder.ASCENDING -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.DESCENDING -> filtered.sortedByDescending { it.name.lowercase() }
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

    private val player = AudioPlayer(application) { trackId, uri, position ->
        savePlaybackProgress(trackId, uri, position)
    }
    
    val isPlaying = player.isPlaying
    val currentTrack = player.currentTrack
    val currentPosition = player.currentPosition
    val duration = player.duration
    val playbackSpeed = player.playbackSpeed
    val stopAfterCurrentTrack = player.stopAfterCurrentTrack
    val sleepTimerRemaining = player.sleepTimerRemaining

    // Whole book progress logic
    val totalBookDuration: StateFlow<Long> = _currentAudioBook.map { book ->
        book?.audioFiles?.sumOf { it.duration } ?: 0L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val globalPosition: StateFlow<Long> = combine(currentTrack, currentPosition, _currentAudioBook) { track, pos, book ->
        if (track == null || book == null) return@combine 0L
        val precedingDuration = book.audioFiles
            .takeWhile { it.id != track.id }
            .sumOf { it.duration }
        precedingDuration + pos
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val remainingInChapter: StateFlow<Long> = combine(currentPosition, duration) { pos, dur ->
        (dur - pos).coerceAtLeast(0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    init {
        // Handle migration or first run cleanup
        if (prefs.isFirstRun("v5")) {
            viewModelScope.launch {
                repository.clearDatabase()
            }
        }

        val savedUri = prefs.rootUri
        if (savedUri != null) {
            _rootUri.value = savedUri
            observePlaylists()
            refreshLibrary(savedUri)
        }
        
        startProgressSafetyNet()
    }

    private fun startProgressSafetyNet() {
        viewModelScope.launch {
            while (true) {
                delay(10000)
                if (isPlaying.value) {
                    currentTrack.value?.let { track ->
                        savePlaybackProgress(track.id, track.uri, currentPosition.value)
                    }
                }
            }
        }
    }

    private var observeJob: Job? = null
    private fun observePlaylists() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.getPlaylistsFlow().collectLatest {
                _playlists.value = it
                val currentTrackId = player.currentTrack.value?.id
                if (currentTrackId != null) {
                    _currentAudioBook.value = it.find { p -> p.audioFiles.any { f -> f.id == currentTrackId } }
                }
            }
        }
    }

    private fun savePlaybackProgress(trackId: String, uri: Uri, position: Long) {
        viewModelScope.launch {
            _playlists.value.find { playlist -> 
                playlist.audioFiles.any { it.id == trackId }
            }?.let { playlist ->
                repository.updatePlaybackState(playlist.uri, trackId, uri, position)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
        prefs.sortOrder = _sortOrder.value
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
        prefs.isGridView = _isGridView.value
    }

    fun toggleHideFinished() {
        _hideFinished.value = !_hideFinished.value
        prefs.hideFinished = _hideFinished.value
    }

    fun setRootUri(uri: Uri) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.rootUri = uri
            viewModelScope.launch {
                repository.clearDatabase()
                _rootUri.value = uri
                observePlaylists()
                refreshLibrary(uri)
            }
        } catch (e: Exception) {
            Log.e("AudioBookViewModel", "Failed to take permission for URI: $uri", e)
        }
    }

    fun refresh() {
        _rootUri.value?.let { refreshLibrary(it) }
    }

    private fun refreshLibrary(uri: Uri) {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refreshLibrary(uri)
            _isRefreshing.value = false
        }
    }

    fun playPlaylist(audioBook: AudioBook) {
        val currentTrackId = currentTrack.value?.id
        val isAlreadyPlayingThisPlaylist = audioBook.audioFiles.any { it.id == currentTrackId }

        if (!isAlreadyPlayingThisPlaylist) {
            player.playPlaylist(
                audioFiles = audioBook.audioFiles,
                startTrackId = audioBook.lastPlayedTrackId ?: audioBook.audioFiles.find { it.uri == audioBook.lastPlayedUri }?.id,
                startPositionMs = audioBook.lastPositionMs
            )
        }
        _currentAudioBook.value = audioBook
        _showPlayerScreen.value = true
    }

    fun playTrack(track: AudioFile) {
        player.playTrack(track.id)
    }

    fun togglePlayPause() {
        player.togglePlayPause()
    }

    fun seekToGlobal(globalPosMs: Long) {
        val book = _currentAudioBook.value ?: return
        var accumulated = 0L
        book.audioFiles.forEachIndexed { index, audioFile ->
            if (globalPosMs < accumulated + audioFile.duration) {
                val localPos = globalPosMs - accumulated
                player.seekTo(index, localPos)
                return
            }
            accumulated += audioFile.duration
        }
        player.seekTo(book.audioFiles.size - 1, book.audioFiles.last().duration)
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
        when (minutes) {
            null -> {
                player.setSleepTimer(0)
                player.setStopAfterChapter(false)
            }
            -1 -> {
                player.setStopAfterChapter(true)
            }
            else -> {
                player.setSleepTimer(minutes)
            }
        }
    }

    fun getProgress(audioBook: AudioBook): Int {
        val totalDuration = audioBook.audioFiles.sumOf { it.duration }
        if (totalDuration == 0L) return 0
        
        val precedingDuration = audioBook.lastPlayedTrackId?.let { id ->
            audioBook.audioFiles
                .takeWhile { it.id != id }
                .sumOf { it.duration }
        } ?: audioBook.lastPlayedUri?.let { uri ->
            audioBook.audioFiles
                .takeWhile { it.uri != uri }
                .sumOf { it.duration }
        } ?: 0L
        
        val currentPosition = precedingDuration + audioBook.lastPositionMs
        return ((currentPosition * 100) / totalDuration).toInt().coerceIn(0, 100)
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
