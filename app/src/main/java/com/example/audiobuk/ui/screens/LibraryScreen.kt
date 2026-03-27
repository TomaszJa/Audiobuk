package com.example.audiobuk.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audiobuk.R
import com.example.audiobuk.ui.components.*
import com.example.audiobuk.viewmodel.AudioBookViewModel
import com.example.audiobuk.viewmodel.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: AudioBookViewModel) {
    val context = LocalContext.current
    val playlists by viewModel.filteredPlaylists.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val rootUri by viewModel.rootUri.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val hideFinished by viewModel.hideFinished.collectAsState()
    val configuration = LocalConfiguration.current

    // Help State
    var currentHelpStepIndex by remember { mutableIntStateOf(-1) }
    val helpSteps = listOf(
        HelpStep(HelpTarget.LIB_FILTER, stringResource(R.string.help_step_lib_filter)),
        HelpStep(HelpTarget.LIB_SORT, stringResource(R.string.help_step_lib_sort)),
        HelpStep(HelpTarget.LIB_VIEW, stringResource(R.string.help_step_lib_view)),
        HelpStep(HelpTarget.LIB_ROOT, stringResource(R.string.help_step_lib_root)),
        HelpStep(HelpTarget.LIB_SEARCH, stringResource(R.string.help_step_lib_search))
    )
    val targetCoordinates = remember { mutableStateMapOf<HelpTarget, LayoutCoordinates>() }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.setRootUri(it) }
    }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsNotGranted.isNotEmpty()) {
            permissionLauncher.launch(permissionsNotGranted.toTypedArray())
        }
    }

    fun launchDirectoryPicker() {
        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsNotGranted.isEmpty()) {
            dirPickerLauncher.launch(null)
        } else {
            permissionLauncher.launch(permissionsNotGranted.toTypedArray())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            currentTrack?.let { track ->
                PlaybackControlBar(
                    track = track,
                    isPlaying = isPlaying,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onClick = { viewModel.setShowPlayerScreen(true) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.my_library),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Row {
                            IconButton(onClick = { currentHelpStepIndex = 0 }) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.help))
                            }
                            IconButton(
                                onClick = { viewModel.toggleHideFinished() },
                                modifier = Modifier.onGloballyPositioned { targetCoordinates[HelpTarget.LIB_FILTER] = it }
                            ) {
                                Icon(
                                    imageVector = if (hideFinished) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (hideFinished) stringResource(R.string.show_finished) else stringResource(R.string.hide_finished)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleSortOrder() },
                                modifier = Modifier.onGloballyPositioned { targetCoordinates[HelpTarget.LIB_SORT] = it }
                            ) {
                                Icon(
                                    imageVector = if (sortOrder == SortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = stringResource(R.string.sort_order)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleViewMode() },
                                modifier = Modifier.onGloballyPositioned { targetCoordinates[HelpTarget.LIB_VIEW] = it }
                            ) {
                                Icon(
                                    if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = stringResource(R.string.toggle_view_mode)
                                )
                            }
                            IconButton(
                                onClick = { launchDirectoryPicker() },
                                modifier = Modifier.onGloballyPositioned { targetCoordinates[HelpTarget.LIB_ROOT] = it }
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.change_root_directory))
                            }
                        }
                    }

                    if (rootUri != null) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .onGloballyPositioned { targetCoordinates[HelpTarget.LIB_SEARCH] = it },
                            placeholder = { Text(stringResource(R.string.search_audiobooks)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true
                        )
                    }

                    if (rootUri == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.select_root_directory_prompt))
                                Button(
                                    onClick = { launchDirectoryPicker() },
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text(stringResource(R.string.select_directory))
                                }
                            }
                        }
                    } else if (playlists.isEmpty() && searchQuery.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_audiobooks_found))
                        }
                    } else {
                        if (isGridView) {
                            val columns = if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 4 else 2
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
                            ) {
                                items(playlists) { playlist ->
                                    PlaylistItem(
                                        audioBook = playlist,
                                        progress = viewModel.getProgress(playlist),
                                        isGrid = true
                                    ) {
                                        viewModel.playPlaylist(playlist)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
                            ) {
                                items(playlists) { playlist ->
                                    PlaylistItem(
                                        audioBook = playlist,
                                        progress = viewModel.getProgress(playlist),
                                        isGrid = false
                                    ) {
                                        viewModel.playPlaylist(playlist)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Help Overlay
            if (currentHelpStepIndex >= 0 && currentHelpStepIndex < helpSteps.size) {
                HelpOverlay(
                    step = helpSteps[currentHelpStepIndex],
                    stepIndex = currentHelpStepIndex,
                    totalSteps = helpSteps.size,
                    targetCoordinates = targetCoordinates[helpSteps[currentHelpStepIndex].target],
                    onNext = { currentHelpStepIndex++ },
                    onDismiss = { currentHelpStepIndex = -1 }
                )
            }
        }
    }
}
