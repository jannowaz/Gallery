package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

/**
 * Generic Material3 selection app bar: a close action, an "{n} selected" title and a caller-provided
 * set of actions. Reused by every screen that supports multi-select so the selection chrome looks
 * and behaves identically everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionAppBar(
    count: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.selection_clear)) }
        },
        title = { Text(stringResource(R.string.selection_count, count), style = MaterialTheme.typography.titleLarge) },
        actions = actions,
    )
}

/**
 * Media-specific selection bar: primary Share / Delete actions plus an overflow menu. Built on top of
 * [SelectionAppBar]. Used by the media grids.
 */
@Composable
fun SelectionTopAppBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRate: () -> Unit,
    onTags: () -> Unit,
    onRename: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    SelectionAppBar(count = count, onClose = onClose, modifier = modifier) {
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, stringResource(R.string.action_share)) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_select_all)) }, onClick = { menuOpen = false; onSelectAll() })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_invert_selection)) }, onClick = { menuOpen = false; onInvert() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.action_copy)) }, onClick = { menuOpen = false; onCopy() }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_move)) }, onClick = { menuOpen = false; onMove() }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_rate)) }, onClick = { menuOpen = false; onRate() }, leadingIcon = { Icon(Icons.Default.Star, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_tags)) }, onClick = { menuOpen = false; onTags() }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menuOpen = false; onRename() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_info)) }, onClick = { menuOpen = false; onInfo() }, leadingIcon = { Icon(Icons.Default.Info, null) })
            }
        }
    }
}
