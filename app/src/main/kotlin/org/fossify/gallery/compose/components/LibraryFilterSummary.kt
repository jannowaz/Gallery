package org.fossify.gallery.compose.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.viewmodels.LibraryFilterActions
import org.fossify.gallery.viewmodels.LibraryFilterState

/**
 * The compact, shared representation of active media filters.
 *
 * It deliberately keeps the result count and clear action fixed while only the chips scroll,
 * so long tag/path names cannot push the primary action off-screen.
 */
@Composable
fun LibraryFilterSummary(
    filter: LibraryFilterState,
    resultCount: Int,
    actions: LibraryFilterActions,
    modifier: Modifier = Modifier,
    onSaveAsCollection: (() -> Unit)? = null,
    newMediaCount: Int = 0,
    onMarkNewMediaSeen: () -> Unit = {},
) {
    if (!filter.isActive) return

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (filter.addedSince > 0L) ActiveFilterChip(stringResource(R.string.new_media_filter), onRemove = actions.clearAddedSince)
                if (filter.activeCollectionName != null) ActiveFilterChip(stringResource(R.string.filter_collection, filter.activeCollectionName), onRemove = actions.clearPath)
                if (filter.activeFolderFilterName != null) ActiveFilterChip(stringResource(R.string.filter_path, filter.activeFolderFilterName), onRemove = actions.clearPath)
                if (filter.activePathName != null) ActiveFilterChip(stringResource(R.string.filter_search_query, filter.activePathName), onRemove = actions.clearPath)
                // The row is horizontally scrollable, so every independently selected tag stays
                // directly visible and individually removable instead of hiding behind “+N”.
                val selectedTagNames = filter.tagNames.orEmpty()
                    .filter(String::isNotBlank)
                    .sortedBy { it.lowercase() }
                if (filter.activeTagName != null) {
                    val label = filter.activeTagName.take(24).let { if (filter.activeTagName.length > 24) "$it…" else it }
                    ActiveFilterChip(label, onRemove = actions.clearTags)
                } else {
                    selectedTagNames.forEach { tag ->
                        val label = tag.take(24).let { if (tag.length > 24) "$it…" else it }
                        ActiveFilterChip(label, onRemove = { actions.removeTag(tag) })
                    }
                }
                if (filter.tagMatchAll) ActiveFilterChip(stringResource(R.string.filter_tags_match_all), onRemove = actions.clearTagMatchAll)
                if (filter.rating > 0) ActiveFilterChip(stringResource(R.string.filter_rating, filter.rating), onRemove = actions.clearRating)
                if (filter.minSize > 0L) ActiveFilterChip("> ${formatLibraryFileSize(filter.minSize)}", onRemove = actions.clearSize)
                if (filter.dateRange > 0) ActiveFilterChip(
                    when (filter.dateRange) {
                        1 -> stringResource(R.string.today)
                        2 -> stringResource(R.string.date_range_last_7_days)
                        3 -> stringResource(R.string.date_range_last_30_days)
                        4 -> stringResource(R.string.date_range_last_year)
                        else -> ""
                    },
                    onRemove = actions.clearDate,
                )
                if (filter.reviewFilter == MediaReviewFilter.UNDECIDED) ActiveFilterChip(stringResource(R.string.filter_review_undecided), onRemove = actions.clearReview)
                if (filter.typeFilter > 0) ActiveFilterChip(
                    when (filter.typeFilter) {
                        TYPE_IMAGES -> stringResource(R.string.images)
                        TYPE_VIDEOS -> stringResource(R.string.videos)
                        TYPE_GIFS -> stringResource(R.string.gif)
                        TYPE_RAWS -> stringResource(R.string.raw)
                        TYPE_SVGS -> stringResource(R.string.svg)
                        else -> stringResource(R.string.videos)
                    },
                    onRemove = actions.clearType,
                )
                Surface(
                    shape = RoundedCornerShape(Radius.md),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        pluralStringResource(R.plurals.result_count, resultCount, resultCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
        }
        if (filter.addedSince > 0L && newMediaCount > 0) {
            val markSeenDescription = stringResource(R.string.mark_new_media_seen)
            TextButton(
                onClick = onMarkNewMediaSeen,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = markSeenDescription },
            ) {
                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.mark_new_media_seen_short), maxLines = 1)
            }
        }
        if (onSaveAsCollection != null) {
            IconButton(
                onClick = onSaveAsCollection,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Default.BookmarkAdd,
                    contentDescription = stringResource(if (filter.activeCollectionName != null) R.string.update_collection else R.string.save_as_collection),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(
            onClick = actions.clearAll,
            modifier = Modifier.size(48.dp).heightIn(min = 48.dp),
        ) {
            Icon(Icons.Default.Close, stringResource(R.string.clear_all_filters), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatLibraryFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0).replace(",0 ", " ").replace(".0 ", " ")
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0).replace(",0 ", " ").replace(".0 ", " ")
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
