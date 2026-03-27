package com.example.audiobuk.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.audiobuk.R
import com.example.audiobuk.viewmodel.SortOrder

@Composable
fun LibraryHeader(
    sortOrder: SortOrder,
    isGridView: Boolean,
    hideFinished: Boolean,
    onHelpClick: () -> Unit,
    onToggleHideFinished: () -> Unit,
    onToggleSortOrder: () -> Unit,
    onToggleViewMode: () -> Unit,
    onChangeRootDirectory: () -> Unit,
    onGloballyPositioned: (HelpTarget, LayoutCoordinates) -> Unit
) {
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
            IconButton(onClick = onHelpClick) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.help))
            }
            IconButton(
                onClick = onToggleHideFinished,
                modifier = Modifier.onGloballyPositioned { onGloballyPositioned(HelpTarget.LIB_FILTER, it) }
            ) {
                Icon(
                    imageVector = if (hideFinished) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (hideFinished) stringResource(R.string.show_finished) else stringResource(R.string.hide_finished)
                )
            }
            IconButton(
                onClick = onToggleSortOrder,
                modifier = Modifier.onGloballyPositioned { onGloballyPositioned(HelpTarget.LIB_SORT, it) }
            ) {
                Icon(
                    imageVector = if (sortOrder == SortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = stringResource(R.string.sort_order)
                )
            }
            IconButton(
                onClick = onToggleViewMode,
                modifier = Modifier.onGloballyPositioned { onGloballyPositioned(HelpTarget.LIB_VIEW, it) }
            ) {
                Icon(
                    if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = stringResource(R.string.toggle_view_mode)
                )
            }
            IconButton(
                onClick = onChangeRootDirectory,
                modifier = Modifier.onGloballyPositioned { onGloballyPositioned(HelpTarget.LIB_ROOT, it) }
            ) {
                Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.change_root_directory))
            }
        }
    }
}
