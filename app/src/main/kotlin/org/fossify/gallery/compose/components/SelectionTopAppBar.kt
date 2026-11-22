package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.util.MediaSelectionMode

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
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val countDescription = stringResource(R.string.selection_count, count)
    TopAppBar(
        modifier = modifier,
        // This bar floats over already-inset-safe content (see the AnimatedVisibility/Box it's
        // placed in in MediaScreen/ExplorerScreen etc.), it isn't the physical top-of-screen app
        // bar - TopAppBar's own default windowInsets otherwise pads it for the status bar a
        // second time on top of that, showing as a large empty strip above the icon row.
        windowInsets = windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(
            // A stronger tonal surface makes selection mode unmistakable, especially when the
            // first row of thumbnails contains bright images. The drawer and normal app bars keep
            // their existing surfaces; this color is reserved for the temporary selection state.
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.selection_clear)) }
        },
        // Shows just the bare number, not "N ausgewählt"/"N selected" - tried shrinking that full
        // phrase down to labelLarge (14sp) first, but it still clipped to "2 ausg…" on a real
        // OnePlus/ColorOS device: after the nav icon + the visible bulk-action icons there's only
        // limited title space, and "ausgewählt" alone is too long to fit at any
        // reasonable size once you account for a real (non-Roboto) system font's actual metrics.
        // The full phrase is still exposed to screen readers via clearAndSetSemantics below -
        // sighted users already see per-item checkmarks and the high-contrast selection surface, so
        // the bare count is sufficient there.
        title = {
            Column(modifier = Modifier.clearAndSetSemantics { contentDescription = countDescription }) {
                Text("$count", style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        actions = actions,
    )
}

/**
 * Media-specific selection bar. Built on top of [SelectionAppBar]. Used by the media grids.
 *
 * Keep the most common cross-app action (Share) visible and give it the only action slot beside
 * the overflow menu. Move and destructive Delete stay available in that same menu, so the bar
 * remains readable even on narrow phones while sharing is still immediately discoverable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onPreview: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRate: (() -> Unit)? = null,
    onKeep: (() -> Unit)? = null,
    onTags: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onAddToCollection: (() -> Unit)? = null,
    onMarkDecided: (() -> Unit)? = null,
    onSkipReview: (() -> Unit)? = null,
    onResetReview: (() -> Unit)? = null,
    selectionMode: MediaSelectionMode = MediaSelectionMode.INDIVIDUAL,
    onSelectionModeChange: ((MediaSelectionMode) -> Unit)? = null,
    selectionScopeLabel: String? = null,
    selectAllLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }
    val hasReviewActions = onMarkDecided != null || onSkipReview != null || onResetReview != null
    fun closeSheetAnd(run: () -> Unit) {
        showActions = false
        run()
    }
    SelectionAppBar(count = count, onClose = onClose, subtitle = selectionScopeLabel, modifier = modifier) {
        if (count == 1 && onPreview != null) {
            IconButton(onClick = onPreview) { Icon(Icons.Default.ZoomIn, stringResource(R.string.cd_preview)) }
        }
        if (onSelectionModeChange != null) {
            SelectionModeMenuButton(selectionMode, onSelectionModeChange)
        }
        // Share is the primary cross-app action for a selection and must remain discoverable; it
        // used to be hidden behind More, which made multi-select appear unable to share.
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, stringResource(R.string.action_share)) }
        // Rating is the other high-frequency curation action. Keep it alongside Share instead of
        // making a selection appear to need a two-level menu just to assign stars.
        onRate?.let { action ->
            IconButton(onClick = action) { Icon(Icons.Default.Star, stringResource(R.string.action_rate)) }
        }
        onKeep?.let { action ->
            IconButton(onClick = action) { Icon(Icons.Default.Bookmark, stringResource(R.string.person_scan_keep)) }
        }
        IconButton(onClick = { showActions = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions)) }
    }
    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Text(
                    stringResource(R.string.more_actions),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
                )
                Text(stringResource(R.string.selection_actions_organize), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
                DropdownMenuItem(text = { Text(stringResource(R.string.action_move)) }, onClick = { closeSheetAnd(onMove) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_copy)) }, onClick = { closeSheetAnd(onCopy) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                onAddToCollection?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.add_to_collection)) }, onClick = { closeSheetAnd(action) }, leadingIcon = { Icon(Icons.Default.CollectionsBookmark, null) }) }
                onTags?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.action_tags)) }, onClick = { closeSheetAnd(action) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) }) }

                HorizontalDivider()
                Text(stringResource(R.string.selection_actions_selection), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 12.dp))
                DropdownMenuItem(text = { Text(selectAllLabel ?: stringResource(R.string.action_select_all)) }, onClick = { closeSheetAnd(onSelectAll) }, leadingIcon = { Icon(Icons.Default.SelectAll, null) })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_invert_selection)) }, onClick = { closeSheetAnd(onInvert) })
                onRename?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { closeSheetAnd(action) }, leadingIcon = { Icon(Icons.Default.Edit, null) }) }
                onInfo?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.action_info)) }, onClick = { closeSheetAnd(action) }, leadingIcon = { Icon(Icons.Default.Info, null) }) }

                if (hasReviewActions) {
                    HorizontalDivider()
                    Text(stringResource(R.string.selection_actions_review), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 12.dp))
                    onMarkDecided?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.review_mark_decided)) }, onClick = { closeSheetAnd(action) }) }
                    onSkipReview?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.review_skip)) }, onClick = { closeSheetAnd(action) }) }
                    onResetReview?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.review_reset)) }, onClick = { closeSheetAnd(action) }) }
                }

                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { closeSheetAnd(onDelete) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                )
            }
        }
    }
}

/** Shared selection-mode control so media surfaces cannot drift into different bulk-selection UX. */
@Composable
fun SelectionModeMenuButton(
    selectionMode: MediaSelectionMode,
    onSelectionModeChange: (MediaSelectionMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = if (selectionMode == MediaSelectionMode.INDIVIDUAL) Icons.Default.CheckBox else Icons.Default.SelectAll,
                contentDescription = stringResource(
                    if (selectionMode == MediaSelectionMode.INDIVIDUAL) R.string.selection_mode_individual
                    else R.string.selection_mode_range,
                ),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.selection_mode_individual)) },
                leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                onClick = { expanded = false; onSelectionModeChange(MediaSelectionMode.INDIVIDUAL) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.selection_mode_range)) },
                leadingIcon = { Icon(Icons.Default.SelectAll, null) },
                onClick = { expanded = false; onSelectionModeChange(MediaSelectionMode.RANGE) },
            )
        }
    }
}
