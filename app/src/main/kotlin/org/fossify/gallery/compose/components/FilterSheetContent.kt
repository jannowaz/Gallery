package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.components.LibrarySearchField
import org.fossify.gallery.compose.components.LibrarySheetHeader
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.models.MediaReviewFilter

private val sizePresets = listOf(1_000_000L, 5_000_000L, 20_000_000L)
private val sizePresetLabelIds = listOf(R.string.filter_size_1mb, R.string.filter_size_5mb, R.string.filter_size_20mb)
private val dateRangePresets = listOf(1, 2, 3, 4)

/** Persistent grid-filter controls (rating/type/size/date/tag), reachable from the compact "Tune"
 * icon in [BottomSearchField] - reads and writes the same [org.fossify.gallery.viewmodels.ExplorerUiState]
 * fields as [org.fossify.gallery.activities.OmniSearchPanel]'s own quick filter, so a change made in
 * either place is immediately visible in the other. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(
    ratingFilter: Int,
    onRatingChange: (Int) -> Unit,
    selectedTagNames: Set<String>,
    onToggleTag: (String) -> Unit,
    tagMatchAll: Boolean = false,
    onTagMatchAllChange: (Boolean) -> Unit = {},
    minSizeFilter: Long,
    onMinSizeChange: (Long) -> Unit,
    dateRangeFilter: Int,
    onDateRangeChange: (Int) -> Unit,
    typeFilter: Int,
    onTypeFilterChange: (Int) -> Unit,
    reviewFilter: MediaReviewFilter = MediaReviewFilter.NONE,
    onReviewFilterChange: (MediaReviewFilter) -> Unit = {},
    hasActiveFilter: Boolean = false,
    onClearAll: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val repo = LocalMediaRepository.current
    // This sheet only needs counts and names, not every path carrying every tag. Reading the
    // indexed aggregate keeps the quick-filter surface small even when the library has millions of
    // (media, tag) pairs, and the query order is deterministic (most-used first).
    var allTags by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var tagQuery by remember { mutableStateOf("") }
    var showMoreFilters by rememberSaveable { mutableStateOf(false) }
    val normalizedTagQuery = tagQuery.trim()
    // Matching "all" has no effect with fewer than two tags. Resetting it here avoids a hidden
    // mode unexpectedly changing the next tag the user selects.
    LaunchedEffect(selectedTagNames.size) {
        if (selectedTagNames.size < 2 && tagMatchAll) onTagMatchAllChange(false)
    }
    LaunchedEffect(reviewFilter, minSizeFilter, dateRangeFilter) {
        if (reviewFilter != MediaReviewFilter.NONE || minSizeFilter > 0L || dateRangeFilter > 0) {
            showMoreFilters = true
        }
    }
    LaunchedEffect(Unit) {
        suspend fun reloadTags() {
            allTags = withContext(Dispatchers.IO) { repo.getTagCountsCached() }
        }
        reloadTags()
        RefreshBus.events.collect { reloadTags() }
    }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        LibrarySheetHeader(
            title = stringResource(R.string.filter_label),
            onDismiss = onDismiss,
            trailing = if (hasActiveFilter) {
                { TextButton(onClick = onClearAll) { Text(stringResource(R.string.clear_all_filters)) } }
            } else null,
        )
        Text(
            stringResource(R.string.filter_applies_immediately),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LibrarySectionHeader(stringResource(R.string.filter_rating_label), showDivider = false)
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..5) {
                IconButton(
                    onClick = { onRatingChange(if (ratingFilter == i) 0 else i) },
                    modifier = Modifier.size(48.dp).semantics { selected = i <= ratingFilter },
                ) {
                    Icon(
                        if (i <= ratingFilter) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(R.string.cd_rating_star, i),
                        tint = if (i <= ratingFilter) RatingStarColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.filter_rating_minimum_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        LibrarySectionHeader(stringResource(R.string.filter_type_label), showDivider = false)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                stringResource(R.string.everything) to 0,
                stringResource(R.string.images) to TYPE_IMAGES,
                stringResource(R.string.videos) to TYPE_VIDEOS,
                stringResource(R.string.gif) to TYPE_GIFS,
                stringResource(R.string.raw) to TYPE_RAWS,
                stringResource(R.string.svg) to TYPE_SVGS,
            ).forEach { (label, v) ->
                val selected = typeFilter == v
                FilterChip(
                    selected = selected,
                    onClick = { onTypeFilterChange(v) },
                    label = { Text(label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        TextButton(onClick = { showMoreFilters = !showMoreFilters }) {
            Text(stringResource(if (showMoreFilters) R.string.filter_show_less else R.string.filter_show_more))
        }
        if (showMoreFilters) {
            LibrarySectionHeader(stringResource(R.string.filter_review_label), showDivider = false)
            FilterChip(
                selected = reviewFilter == MediaReviewFilter.UNDECIDED,
                onClick = {
                    onReviewFilterChange(
                        if (reviewFilter == MediaReviewFilter.UNDECIDED) MediaReviewFilter.NONE else MediaReviewFilter.UNDECIDED
                    )
                },
                label = { Text(stringResource(R.string.filter_review_undecided)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )

            LibrarySectionHeader(stringResource(R.string.filter_date_label), showDivider = false)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val labels = listOf(
                    stringResource(R.string.today),
                    stringResource(R.string.date_range_last_7_days),
                    stringResource(R.string.date_range_last_30_days),
                    stringResource(R.string.date_range_last_year),
                )
                dateRangePresets.forEachIndexed { i, range ->
                    val selected = dateRangeFilter == range
                    FilterChip(
                        selected = selected,
                        onClick = { onDateRangeChange(if (selected) 0 else range) },
                        label = { Text(labels[i]) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            LibrarySectionHeader(stringResource(R.string.filter_size_label), showDivider = false)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sizePresets.forEachIndexed { i, bytes ->
                    val selected = minSizeFilter == bytes
                    FilterChip(
                        selected = selected,
                        onClick = { onMinSizeChange(if (selected) 0L else bytes) },
                        label = { Text("> ${stringResource(sizePresetLabelIds[i])}") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }

        if (allTags.isNotEmpty()) {
            LibrarySectionHeader(stringResource(R.string.action_tags), showDivider = false)
            LibrarySearchField(
                value = tagQuery,
                onValueChange = { tagQuery = it },
                placeholderRes = R.string.search_tag,
            )
            Spacer(Modifier.height(4.dp))
            if (selectedTagNames.size >= 2) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !tagMatchAll,
                        onClick = { onTagMatchAllChange(false) },
                        label = { Text(stringResource(R.string.filter_tags_match_any)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    FilterChip(
                        selected = tagMatchAll,
                        onClick = { onTagMatchAllChange(true) },
                        label = { Text(stringResource(R.string.filter_tags_match_all)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val selected = selectedTagNames
                val visibleTags = allTags.entries
                    .filter { normalizedTagQuery.isBlank() || it.key.contains(normalizedTagQuery, ignoreCase = true) }
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.key in selected }.thenByDescending { it.value })
                    .take(40)
                visibleTags.forEach { (tag, _) ->
                    val isSelected = tag in selectedTagNames
                    TagFilterChip(
                        tag = tag,
                        selected = isSelected,
                        onClick = { onToggleTag(tag) },
                    )
                }
            }
        }
    }
}
