package org.fossify.gallery.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.GroupToggleButton
import org.fossify.gallery.compose.components.LibraryFilterSummary
import org.fossify.gallery.compose.components.LibraryScreenHeader
import org.fossify.gallery.compose.components.RefreshActionButton
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.viewmodels.LibraryFilterActions
import org.fossify.gallery.viewmodels.LibraryFilterState

@Composable
internal fun MediaToolbar(
    settingsSummary: String,
    newMediaCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenNewMedia: () -> Unit,
    showGroupToggle: Boolean,
    groupsExpanded: Boolean,
    onToggleGroups: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenViewSettings: (() -> Unit)? = null,
) {
    LibraryScreenHeader(
        title = stringResource(R.string.media),
        subtitle = settingsSummary,
        leadingIcon = Icons.Default.Image,
        modifier = modifier,
        actions = {
            if (newMediaCount > 0) {
                val newMediaLabel = stringResource(R.string.new_media_content_description, newMediaCount)
                val countText = if (newMediaCount > 99) "99+" else newMediaCount.toString()
                Box(
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        onClick = onOpenNewMedia,
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = newMediaLabel
                                role = Role.Button
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(R.string.new_media_badge_short, countText),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (showGroupToggle) {
                GroupToggleButton(expanded = groupsExpanded, onToggle = onToggleGroups, modifier = Modifier.size(48.dp))
            }
            if (onOpenViewSettings != null) {
                IconButton(onClick = onOpenViewSettings, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = stringResource(R.string.view_settings_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RefreshActionButton(isRefreshing = isRefreshing, onRefresh = onRefresh)
        },
    )
}

@Composable
internal fun FilterBreadcrumbs(
    ratingFilter: Int,
    activeTagName: String?,
    activePathName: String?,
    activeCollectionName: String?,
    activeFolderFilterName: String?,
    minSizeFilter: Long,
    dateRangeFilter: Int,
    typeFilter: Int,
    resultCount: Int,
    onClearRating: () -> Unit,
    onClearTag: () -> Unit,
    onClearPath: () -> Unit,
    onClearSize: () -> Unit,
    onClearDate: () -> Unit,
    onClearType: () -> Unit,
    onClearAll: () -> Unit,
    onSaveAsCollection: (() -> Unit)? = null,
    tagMatchAll: Boolean = false,
    addedSince: Long = 0L,
    onClearAddedSince: () -> Unit = {},
    reviewFilter: MediaReviewFilter = MediaReviewFilter.NONE,
    onClearReview: () -> Unit = {},
    newMediaCount: Int = 0,
    onMarkNewMediaSeen: () -> Unit = {},
    tagFilterNames: Set<String>? = null,
    onRemoveTag: (String) -> Unit = {},
    onClearTagMatchAll: () -> Unit = {},
) {
    LibraryFilterSummary(
        filter = LibraryFilterState(
            rating = ratingFilter,
            tagNames = tagFilterNames,
            tagMatchAll = tagMatchAll,
            activeTagName = activeTagName,
            activePathName = activePathName,
            activeCollectionName = activeCollectionName,
            activeFolderFilterName = activeFolderFilterName,
            minSize = minSizeFilter,
            dateRange = dateRangeFilter,
            addedSince = addedSince,
            typeFilter = typeFilter,
            reviewFilter = reviewFilter,
        ),
        resultCount = resultCount,
        actions = LibraryFilterActions(
            clearAll = onClearAll,
            clearRating = onClearRating,
            clearTags = onClearTag,
            removeTag = onRemoveTag,
            clearTagMatchAll = onClearTagMatchAll,
            clearPath = onClearPath,
            clearSize = onClearSize,
            clearDate = onClearDate,
            clearAddedSince = onClearAddedSince,
            clearType = onClearType,
            clearReview = onClearReview,
        ),
        onSaveAsCollection = onSaveAsCollection,
        newMediaCount = newMediaCount,
        onMarkNewMediaSeen = onMarkNewMediaSeen,
    )
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0).replace(",0 ", " ").replace(".0 ", " ")
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0).replace(",0 ", " ").replace(".0 ", " ")
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
