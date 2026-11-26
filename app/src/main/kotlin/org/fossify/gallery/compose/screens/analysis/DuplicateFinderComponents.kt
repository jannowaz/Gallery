package org.fossify.gallery.compose.screens.analysis

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.helpers.formatBytes
import java.io.File

private const val KEEP_GREEN_ARGB = 0xFF43A047
internal val KeepGreenColor = Color(KEEP_GREEN_ARGB)
private const val RECYCLE_BIN_DAYS = 30
private const val THRESHOLD_ALMOST_IDENTICAL = 2
private const val THRESHOLD_STRICT = 6
private const val THRESHOLD_MEDIUM = 10
private const val THRESHOLD_LOOSE = 14

/** How result groups are ordered: SAVINGS leads with groups reclaiming the most bytes. */
internal enum class DuplicateSort { SAVINGS, SIZE, COUNT }

/** Which result groups receive the next auto-selection strategy. */
internal enum class AutoSelectScope(val nextCount: Int? = null) {
    SELECTED,
    ALL,
    NEXT_10(10),
    NEXT_25(25),
    NEXT_50(50),
}

internal data class DuplicateDeletionSummary(
    val selectedCount: Int,
    val selectedBytes: Long,
    val keepingCopyGroups: Int,
    val fullyMarkedGroups: Int,
    val ratedOrFavoriteCount: Int,
)

internal fun sortAndFilterDuplicateGroups(
    groups: List<DuplicateGroup>,
    folderFilter: String?,
    sortMode: DuplicateSort,
): List<DuplicateGroup> {
    val shown = if (folderFilter == null) {
        groups
    } else {
        groups.filter { g -> g.files.any { File(it.path).parent == folderFilter } }
    }
    return when (sortMode) {
        DuplicateSort.SAVINGS -> shown.sortedByDescending { it.wastedBytes }
        DuplicateSort.SIZE -> shown.sortedByDescending { group -> group.files.sumOf { it.size } }
        DuplicateSort.COUNT -> shown.sortedByDescending { it.files.size }
    }
}

internal fun computeDuplicateDeletionSummary(
    groups: List<DuplicateGroup>,
    selected: Set<String>,
): DuplicateDeletionSummary {
    if (selected.isEmpty()) {
        return DuplicateDeletionSummary(0, 0L, 0, 0, 0)
    }
    val allFiles = groups.flatMap { it.files }
    val selectedBytes = allFiles.filter { it.path in selected }.sumOf { it.size }
    val affected = groups.filter { g -> g.files.any { it.path in selected } }
    val fullyMarked = affected.count { g -> g.files.all { it.path in selected } }
    val ratedAffected = allFiles.count { it.path in selected && (it.rating > 0 || it.isFavorite) }
    return DuplicateDeletionSummary(
        selectedCount = selected.size,
        selectedBytes = selectedBytes,
        keepingCopyGroups = affected.size - fullyMarked,
        fullyMarkedGroups = fullyMarked,
        ratedOrFavoriteCount = ratedAffected,
    )
}

@StringRes
internal fun thresholdLabelRes(threshold: Int): Int = when {
    threshold <= THRESHOLD_ALMOST_IDENTICAL -> R.string.dup_threshold_almost_identical
    threshold <= THRESHOLD_STRICT -> R.string.dup_threshold_strict
    threshold <= THRESHOLD_MEDIUM -> R.string.dup_threshold_medium
    threshold <= THRESHOLD_LOOSE -> R.string.dup_threshold_loose
    else -> R.string.dup_threshold_very_loose
}

internal fun resolveTreeDocumentPath(docId: String): String? {
    val split = docId.indexOf(':')
    if (split < 0) return null
    val type = docId.substring(0, split)
    val relative = docId.substring(split + 1)
    val root = if (type == "primary") "/storage/emulated/0" else "/storage/$type"
    return "$root/$relative".trimEnd('/')
}

@Composable
internal fun MarkedBadge(count: Int, modifier: Modifier = Modifier) {
    if (count > 0) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = KeepGreenColor,
                modifier = Modifier.size(13.dp),
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = KeepGreenColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun AutoSelectAction(
    folderFiltered: Boolean,
    selectedGroupCount: Int,
    hasSelection: Boolean,
    onApplySelection: (KeepStrategy) -> Unit,
    onApplyAll: (KeepStrategy) -> Unit,
    onApplyNext: (KeepStrategy, Int) -> Unit,
    onSelectShownFolder: () -> Unit,
    onInvert: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf(AutoSelectScope.ALL) }

    LaunchedEffect(selectedGroupCount > 0) {
        if (selectedGroupCount > 0) {
            scope = AutoSelectScope.SELECTED
        } else if (scope == AutoSelectScope.SELECTED) {
            scope = AutoSelectScope.ALL
        }
    }

    fun apply(strategy: KeepStrategy) {
        when (scope) {
            AutoSelectScope.SELECTED -> {
                if (selectedGroupCount > 0) onApplySelection(strategy) else onApplyAll(strategy)
            }
            AutoSelectScope.ALL -> onApplyAll(strategy)
            else -> onApplyNext(strategy, requireNotNull(scope.nextCount))
        }
        expanded = false
    }

    Box(modifier = modifier) {
        if (emphasized) {
            Button(onClick = { expanded = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAddCheck,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.auto_select))
            }
        } else {
            TextButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.auto_select))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                stringResource(R.string.keep_scope_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            AutoSelectScopeChips(
                scope = scope,
                selectedGroupCount = selectedGroupCount,
                onSelectScope = { scope = it },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                stringResource(R.string.keep_per_group_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            KEEP_STRATEGY_ITEMS.forEach { (strategy, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    leadingIcon = if (strategy == KeepStrategy.HIGHEST_RATED) {
                        { Icon(Icons.Default.Star, null, tint = RatingStarColor) }
                    } else {
                        null
                    },
                    onClick = { apply(strategy) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (folderFiltered) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dup_select_all_shown)) },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                    onClick = {
                        onSelectShownFolder()
                        expanded = false
                    },
                )
            }
            if (hasSelection) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dup_invert_selection)) },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                    onClick = {
                        onInvert()
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.selection_clear)) },
                onClick = {
                    onClear()
                    expanded = false
                },
            )
        }
    }
}

private val KEEP_STRATEGY_ITEMS = listOf(
    KeepStrategy.NEWEST to R.string.keep_newest,
    KeepStrategy.OLDEST to R.string.keep_oldest,
    KeepStrategy.LARGEST to R.string.keep_largest,
    KeepStrategy.SMALLEST to R.string.keep_smallest,
    KeepStrategy.SHORTEST_NAME to R.string.keep_shortest_name,
    KeepStrategy.SHORTEST_PATH to R.string.keep_shortest_path,
    KeepStrategy.HIGHEST_RATED to R.string.keep_highest_rated,
)

@Composable
private fun AutoSelectScopeChips(
    scope: AutoSelectScope,
    selectedGroupCount: Int,
    onSelectScope: (AutoSelectScope) -> Unit,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selectedGroupCount > 0) {
            FilterChip(
                selected = scope == AutoSelectScope.SELECTED,
                onClick = { onSelectScope(AutoSelectScope.SELECTED) },
                label = { Text(stringResource(R.string.dup_batch_selected, selectedGroupCount)) },
            )
        }
        FilterChip(
            selected = scope == AutoSelectScope.ALL,
            onClick = { onSelectScope(AutoSelectScope.ALL) },
            label = { Text(stringResource(R.string.dup_batch_all)) },
        )
        listOf(
            AutoSelectScope.NEXT_10,
            AutoSelectScope.NEXT_25,
            AutoSelectScope.NEXT_50,
        ).forEach { nextScope ->
            val count = requireNotNull(nextScope.nextCount)
            FilterChip(
                selected = scope == nextScope,
                onClick = { onSelectScope(nextScope) },
                label = { Text(stringResource(R.string.dup_batch_next, count)) },
            )
        }
    }
}

@Composable
internal fun GroupSelectionBar(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    onSelectVisible: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSelection = selectedCount > 0
    val containerColor = if (hasSelection) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val iconTint = if (hasSelection) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textColor = if (hasSelection) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(Radius.md),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistAddCheck,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (hasSelection) {
                    pluralStringResource(R.plurals.dup_groups_selected, selectedCount, selectedCount)
                } else {
                    stringResource(R.string.dup_group_selection_hint)
                },
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!allVisibleSelected) {
                TextButton(onClick = onSelectVisible) {
                    Text(stringResource(R.string.dup_select_visible_groups))
                }
            }
            if (hasSelection) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, stringResource(R.string.dup_clear_group_selection))
                }
            }
        }
    }
}

@Composable
internal fun DuplicateDeleteConfirmDialog(
    summary: DuplicateDeletionSummary,
    isSimilarMode: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.move_to_recycle_bin_confirm_size,
                        summary.selectedCount,
                        formatBytes(summary.selectedBytes),
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.dup_confirm_retention, RECYCLE_BIN_DAYS),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isSimilarMode) {
                    ConfirmNoticeRow(
                        icon = Icons.Default.Warning,
                        tint = MaterialTheme.colorScheme.error,
                        text = stringResource(R.string.dup_confirm_similar),
                        textColor = MaterialTheme.colorScheme.error,
                    )
                }
                if (summary.keepingCopyGroups > 0) {
                    ConfirmNoticeRow(
                        icon = Icons.Default.CheckCircle,
                        tint = KeepGreenColor,
                        text = stringResource(R.string.dup_confirm_keeps_copies, summary.keepingCopyGroups),
                        textColor = KeepGreenColor,
                        topSpaceDp = 6,
                    )
                }
                if (summary.fullyMarkedGroups > 0) {
                    ConfirmNoticeRow(
                        icon = Icons.Default.Warning,
                        tint = MaterialTheme.colorScheme.error,
                        text = stringResource(R.string.dup_confirm_all_deleted, summary.fullyMarkedGroups),
                        textColor = MaterialTheme.colorScheme.error,
                    )
                }
                if (summary.ratedOrFavoriteCount > 0) {
                    ConfirmNoticeRow(
                        icon = Icons.Default.Star,
                        tint = RatingStarColor,
                        text = stringResource(R.string.dup_confirm_rated, summary.ratedOrFavoriteCount),
                        textColor = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(org.fossify.commons.R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ConfirmNoticeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String,
    textColor: Color,
    topSpaceDp: Int = 4,
) {
    Row(
        modifier = Modifier.padding(top = topSpaceDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}
