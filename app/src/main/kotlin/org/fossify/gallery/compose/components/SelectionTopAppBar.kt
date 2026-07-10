package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
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
    val countDescription = stringResource(R.string.selection_count, count)
    TopAppBar(
        modifier = modifier,
        // This bar floats over already-inset-safe content (see the AnimatedVisibility/Box it's
        // placed in in MediaScreen/ExplorerScreen etc.), it isn't the physical top-of-screen app
        // bar - TopAppBar's own default windowInsets otherwise pads it for the status bar a
        // second time on top of that, showing as a large empty strip above the icon row.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.selection_clear)) }
        },
        // Shows just the bare number, not "N ausgewählt"/"N selected" - tried shrinking that full
        // phrase down to labelLarge (14sp) first, but it still clipped to "2 ausg…" on a real
        // OnePlus/ColorOS device: after the nav icon + 5 action icons (Rename/Tags/Rate/Delete/
        // More) there's only ~100dp left, and "ausgewählt" alone is too long to fit at any
        // reasonable size once you account for a real (non-Roboto) system font's actual metrics.
        // The full phrase is still exposed to screen readers via clearAndSetSemantics below -
        // sighted users already see per-item checkmarks, so the bare count is sufficient there.
        title = {
            Text(
                "$count", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { contentDescription = countDescription },
            )
        },
        actions = actions,
    )
}

/**
 * Media-specific selection bar. Built on top of [SelectionAppBar]. Used by the media grids.
 *
 * The visible actions are Rename/Tags/Rate/Delete - the repeated core loop for this app (rename ->
 * tag -> rate -> move on freshly downloaded batches) plus delete, which stays visible since it's
 * common independent of that workflow. Share, Copy/Move, select-all/invert and file info are all
 * lower-frequency here, so they move to the overflow menu instead of crowding the bar.
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
        IconButton(onClick = onRename) { Icon(Icons.Default.Edit, stringResource(R.string.action_rename)) }
        IconButton(onClick = onTags) { Icon(Icons.AutoMirrored.Filled.Label, stringResource(R.string.action_tags)) }
        IconButton(onClick = onRate) { Icon(Icons.Default.Star, stringResource(R.string.action_rate)) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_select_all)) }, onClick = { menuOpen = false; onSelectAll() })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_invert_selection)) }, onClick = { menuOpen = false; onInvert() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) }, onClick = { menuOpen = false; onShare() }, leadingIcon = { Icon(Icons.Default.Share, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_copy)) }, onClick = { menuOpen = false; onCopy() }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_move)) }, onClick = { menuOpen = false; onMove() }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_info)) }, onClick = { menuOpen = false; onInfo() }, leadingIcon = { Icon(Icons.Default.Info, null) })
            }
        }
    }
}
