package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.RatingStarColor

private val sizePresets = listOf(1_000_000L, 5_000_000L, 20_000_000L)
private val sizePresetLabels = listOf("1 MB", "5 MB", "20 MB")
private val dateRangePresets = listOf(1, 2, 3)

/** Persistent grid-filter controls (rating/type/size/date/tag), reachable from the compact "Tune"
 * icon in [BottomSearchField] - reads and writes the same [org.fossify.gallery.viewmodels.ExplorerUiState]
 * fields as [org.fossify.gallery.activities.OmniSearchPanel]'s own quick filter, so a change made in
 * either place is immediately visible in the other. */
@Composable
fun FilterSheetContent(
    ratingFilter: Int,
    onRatingChange: (Int) -> Unit,
    selectedTagNames: Set<String>,
    onToggleTag: (String) -> Unit,
    minSizeFilter: Long,
    onMinSizeChange: (Long) -> Unit,
    dateRangeFilter: Int,
    onDateRangeChange: (Int) -> Unit,
    typeFilter: Int,
    onTypeFilterChange: (Int) -> Unit,
) {
    val repo = LocalMediaRepository.current
    var allTags by remember { mutableStateOf(repo.getTagsWithPathsCached()?.keys?.toList() ?: emptyList()) }
    LaunchedEffect(Unit) {
        if (repo.getTagsWithPathsCached() != null) return@LaunchedEffect
        val tags = withContext(Dispatchers.IO) { try { repo.refreshTagsWithPathsCache().keys.toList() } catch (_: Exception) { emptyList() } }
        allTags = tags
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.filter_rating_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..5) {
                IconButton(onClick = { onRatingChange(if (ratingFilter == i) 0 else i) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (i <= ratingFilter) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(R.string.cd_rating_star, i),
                        tint = if (i <= ratingFilter) RatingStarColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.filter_type_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(stringResource(R.string.everything) to 0, stringResource(R.string.images) to 1, stringResource(R.string.videos) to 2).forEach { (label, v) ->
                val selected = typeFilter == v
                Surface(
                    onClick = { onTypeFilterChange(v) },
                    shape = RoundedCornerShape(Radius.md),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        label, Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.filter_size_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sizePresets.forEachIndexed { i, bytes ->
                val selected = minSizeFilter == bytes
                Surface(
                    onClick = { onMinSizeChange(if (selected) 0L else bytes) },
                    shape = RoundedCornerShape(Radius.md),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        "> ${sizePresetLabels[i]}", Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.filter_date_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val labels = listOf(stringResource(R.string.today), stringResource(R.string.date_range_last_7_days), stringResource(R.string.date_range_last_30_days))
            dateRangePresets.forEachIndexed { i, range ->
                val selected = dateRangeFilter == range
                Surface(
                    onClick = { onDateRangeChange(if (selected) 0 else range) },
                    shape = RoundedCornerShape(Radius.md),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        labels[i], Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (allTags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.action_tags), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTags.take(20).forEach { tag ->
                    val selected = tag in selectedTagNames
                    Surface(
                        onClick = { onToggleTag(tag) },
                        shape = RoundedCornerShape(Radius.md),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            tag, Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
