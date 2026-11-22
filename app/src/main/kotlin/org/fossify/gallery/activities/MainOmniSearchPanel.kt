package org.fossify.gallery.activities

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.ActiveFilterChip
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.models.MediaCollection
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OmniSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    resultListState: LazyListState,
    recentSearches: List<String> = emptyList(),
    onUseRecentSearch: (String) -> Unit = {},
    onClearRecentSearches: () -> Unit = {},
    onDismiss: () -> Unit,
    storagePath: String,
    onNavigate: (String) -> Unit,
    ratingFilter: Int,
    onRatingFilterChange: (Int) -> Unit,
    selectedTagNames: Set<String>,
    onToggleTag: (String) -> Unit,
    tagMatchAll: Boolean,
    onTagMatchAllChange: (Boolean) -> Unit,
    typeFilter: Int,
    onTypeFilterChange: (Int) -> Unit,
    dateFilter: Int,
    onDateFilterChange: (Int) -> Unit,
    minSizeFilter: Long,
    onMinSizeFilterChange: (Long) -> Unit,
    onResetFilters: () -> Unit,
    onEditFilters: () -> Unit,
    onApplyTagOnly: (tag: String) -> Unit,
    onApplyResults: (textQuery: String) -> Unit,
    onOpenCollection: (MediaCollection) -> Unit,
    onOpenFavorite: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    localScopeLabel: String? = null,
    onSwitchToLocalScope: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = LocalMediaRepository.current
    var allTags by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isSearching by remember { mutableStateOf(false) }
    var textMatches by remember { mutableStateOf<MediaRepository.TextSearchPreview?>(null) }
    var folderResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var tagResults by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var collectionResults by remember { mutableStateOf<List<MediaCollection>>(emptyList()) }
    var favoriteResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val searchCache = remember { mutableMapOf<String, MediaRepository.TextSearchPreview>() }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var pendingJobs by remember { mutableIntStateOf(0) }
    val searchJobs = remember { mutableListOf<Job>() }

    LaunchedEffect(Unit) {
        allTags = withContext(Dispatchers.IO) { repo.getTagCounts() }
    }
    LaunchedEffect(Unit) {
        RefreshBus.events.collect {
            allTags = withContext(Dispatchers.IO) { repo.getTagCounts() }
        }
    }

    fun performSearch() {
        searchJobs.forEach { it.cancel() }
        searchJobs.clear()
        val currentQuery = query.trim()
        val myGen = ++searchGeneration
        if (currentQuery.length < 2) {
            textMatches = null; folderResults = emptyList(); tagResults = emptyList()
            collectionResults = emptyList(); favoriteResults = emptyList(); isSearching = false
            return
        }
        val qParts = currentQuery.lowercase().split(" ").filter { it.isNotBlank() }
        if (qParts.isEmpty()) {
            textMatches = null; folderResults = emptyList(); tagResults = emptyList()
            collectionResults = emptyList(); favoriteResults = emptyList(); isSearching = false
            return
        }
        pendingJobs = 2
        isSearching = true
        suspend fun jobDone() {
            withContext(Dispatchers.Main.immediate) {
                if (myGen == searchGeneration) {
                    pendingJobs--
                    if (pendingJobs <= 0) isSearching = false
                }
            }
        }

        searchJobs += scope.launch(Dispatchers.IO) {
            val tags = try {
                val result = mutableListOf<Pair<String, Int>>()
                if (allTags.isNotEmpty()) {
                    qParts.forEach { qp ->
                        allTags.entries.forEach { (tag, count) ->
                            if (tag.lowercase().contains(qp) && result.none { it.first == tag }) result.add(tag to count)
                        }
                    }
                }
                result.sortedByDescending { it.second }.take(15)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("Explorer", "Tag search job failed", e); emptyList() }
            val folders = try {
                ctx.directoryDB.getAll().mapNotNull { d ->
                    val name = d.name.lowercase()
                    if (qParts.all { it in name }) d.name to d.path else null
                }.sortedBy { it.first }.take(15)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
            val colls = try {
                repo.getCollections().filter { c ->
                    val name = c.name.lowercase()
                    qParts.all { it in name }
                }.take(10)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
            val favs = try {
                repo.getFavorites().mapNotNull { m ->
                    val name = m.name.lowercase()
                    if (qParts.all { it in name }) m.name to m.path else null
                }.take(10)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main.immediate) {
                if (myGen == searchGeneration) {
                    tagResults = tags
                    folderResults = folders
                    collectionResults = colls
                    favoriteResults = favs
                }
            }
            jobDone()
        }
        searchJobs += scope.launch(Dispatchers.IO) {
            val cacheKey = "${RefreshBus.pathGeneration}_${currentQuery}_${typeFilter}_${dateFilter}"
            val cachedMatches = synchronized(searchCache) { searchCache[cacheKey] }
            cachedMatches?.let { c ->
                withContext(Dispatchers.Main.immediate) {
                    if (myGen == searchGeneration) textMatches = c
                }
                jobDone(); return@launch
            }
            synchronized(searchCache) {
                if (searchCache.size > 30) searchCache.clear()
            }
            val matched = try {
                repo.searchActiveMediaPreview(qParts, typeFilter, dateFilter)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("Explorer", "Room search job failed", e)
                MediaRepository.TextSearchPreview(emptyList(), 0)
            }
            synchronized(searchCache) { searchCache[cacheKey] = matched }
            withContext(Dispatchers.Main.immediate) {
                if (myGen == searchGeneration) textMatches = matched
            }
            jobDone()
        }
    }

    LaunchedEffect(query, typeFilter, dateFilter) {
        delay(300)
        performSearch()
    }

    val hasAnyFilter = ratingFilter > 0 || selectedTagNames.isNotEmpty() || typeFilter > 0 || dateFilter > 0 || minSizeFilter > 0
    val hasResults = (textMatches?.totalCount ?: 0) > 0
    val mc = textMatches?.totalCount ?: 0

    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg, bottomEnd = 0.dp, bottomStart = 0.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (localScopeLabel != null && onSwitchToLocalScope != null) {
                SearchScopeBar(
                    isLocal = false,
                    localLabel = localScopeLabel,
                    onSelectEverywhere = {},
                    onSelectLocal = onSwitchToLocalScope,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
            }
            if (query.isBlank() && recentSearches.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.recent_searches),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearRecentSearches) {
                        Text(stringResource(R.string.clear_recent_searches))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recentSearches.forEach { recent ->
                        Surface(
                            onClick = { onUseRecentSearch(recent) },
                            shape = RoundedCornerShape(Radius.lg),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                recent,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = onEditFilters, shape = RoundedCornerShape(Radius.md), color = if (hasAnyFilter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FilterList, null, modifier = Modifier.size(14.dp), tint = if (hasAnyFilter) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.filter_label), style = MaterialTheme.typography.labelSmall, color = if (hasAnyFilter) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (hasAnyFilter) {
                    Spacer(Modifier.width(4.dp))
                    Surface(onClick = onResetFilters, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(stringResource(R.string.reset), Modifier.padding(horizontal = 8.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (hasAnyFilter) {
                val minSizeLabel = when (minSizeFilter) {
                    1_000_000L -> "> ${stringResource(R.string.filter_size_1mb)}"
                    5_000_000L -> "> ${stringResource(R.string.filter_size_5mb)}"
                    20_000_000L -> "> ${stringResource(R.string.filter_size_20mb)}"
                    else -> "> ${minSizeFilter / 1_000_000} MB"
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ratingFilter > 0) ActiveFilterChip("★$ratingFilter") { onRatingFilterChange(0) }
                    selectedTagNames.sorted().forEach { tag ->
                        ActiveFilterChip(tag) { onToggleTag(tag) }
                    }
                    if (tagMatchAll) ActiveFilterChip(stringResource(R.string.filter_tags_match_all)) { onTagMatchAllChange(false) }
                    if (typeFilter > 0) ActiveFilterChip(
                        when (typeFilter) {
                            org.fossify.gallery.helpers.TYPE_IMAGES -> stringResource(R.string.images)
                            org.fossify.gallery.helpers.TYPE_VIDEOS -> stringResource(R.string.videos)
                            org.fossify.gallery.helpers.TYPE_GIFS -> stringResource(R.string.gif)
                            org.fossify.gallery.helpers.TYPE_RAWS -> stringResource(R.string.raw)
                            org.fossify.gallery.helpers.TYPE_SVGS -> stringResource(R.string.svg)
                            else -> stringResource(R.string.videos)
                        },
                    ) { onTypeFilterChange(0) }
                    if (dateFilter > 0) ActiveFilterChip(
                        when (dateFilter) {
                            1 -> stringResource(R.string.today)
                            2 -> stringResource(R.string.date_range_last_7_days)
                            3 -> stringResource(R.string.date_range_last_30_days)
                            4 -> stringResource(R.string.date_range_last_year)
                            else -> stringResource(R.string.all_dates)
                        },
                    ) { onDateFilterChange(0) }
                    if (minSizeFilter > 0) ActiveFilterChip(minSizeLabel) { onMinSizeFilterChange(0L) }
                }
            }

            val hasAnyResults = hasResults || folderResults.isNotEmpty() || tagResults.isNotEmpty() || collectionResults.isNotEmpty() || favoriteResults.isNotEmpty()
            if (isSearching && !hasAnyResults) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            } else if (query.length >= 2 && !isSearching && !hasAnyResults) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (hasAnyResults || isSearching) {
                Spacer(Modifier.height(4.dp)); HorizontalDivider(); Spacer(Modifier.height(4.dp))
                LazyColumn(
                    state = resultListState,
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    if (folderResults.isNotEmpty()) {
                        item { Text(stringResource(R.string.folders), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(folderResults.take(5), key = { it.second }) { (name, path) ->
                            val parentLabel = remember(path) {
                                val parent = File(path).parent ?: ""
                                parent.removePrefix(storagePath).trim('/').ifEmpty { "/" }
                            }
                            Surface(modifier = Modifier.fillMaxWidth().clickable { onNavigate(path) }, color = Color.Transparent, shape = RoundedCornerShape(Radius.sm)) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                        Text(parentLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                    }
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (folderResults.size > 5) item { Text(stringResource(R.string.plus_n_more, folderResults.size - 5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)) }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (tagResults.isNotEmpty()) {
                        item { Text(stringResource(R.string.nav_tags), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(tagResults.take(8), key = { it.first }) { (tag, cnt) ->
                            Surface(modifier = Modifier.fillMaxWidth().clickable {
                                onApplyTagOnly(tag); onDismiss()
                            }, color = Color.Transparent, shape = RoundedCornerShape(Radius.sm)) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(tag, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).privacyBlur(BlurRadius.thumbnail, BlurState.enabled)); Text("$cnt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (collectionResults.isNotEmpty()) {
                        item { Text(stringResource(R.string.nav_collections), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(collectionResults, key = { it.id }) { coll ->
                            Surface(modifier = Modifier.fillMaxWidth().clickable { onOpenCollection(coll); onDismiss() }, color = Color.Transparent, shape = RoundedCornerShape(Radius.sm)) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(coll.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (favoriteResults.isNotEmpty()) {
                        item { Text(stringResource(R.string.nav_favorites), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(favoriteResults.take(8), key = { it.second }) { (name, path) ->
                            Surface(modifier = Modifier.fillMaxWidth().clickable { onOpenFavorite(path); onDismiss() }, color = Color.Transparent, shape = RoundedCornerShape(Radius.sm)) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                }
                            }
                        }
                        if (favoriteResults.size > 8) item { Text(stringResource(R.string.plus_n_more, favoriteResults.size - 8), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)) }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (hasResults) {
                        item { Text(stringResource(R.string.media_count_paren, mc), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 4.dp)) }
                        item {
                            LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(textMatches!!.paths, key = { it }) { path ->
                                    Surface(
                                        onClick = { onOpenMedia(path); onDismiss() },
                                        shape = RoundedCornerShape(Radius.sm),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        GalleryImage(
                                            path = path,
                                            contentDescription = File(path).name,
                                            modifier = Modifier.size(64.dp),
                                            thumbnailSize = 128,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (isSearching) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.searching), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (hasResults) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                        Surface(onClick = {
                            onApplyResults(query.trim())
                            onDismiss()
                        }, shape = RoundedCornerShape(Radius.xl), color = MaterialTheme.colorScheme.primary) {
                            Text(stringResource(R.string.show_results_count, mc), Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            } else if (!hasAnyFilter) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_min_chars_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun SearchScopeBar(
    isLocal: Boolean,
    localLabel: String,
    onSelectEverywhere: () -> Unit,
    onSelectLocal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = isLocal,
            onClick = onSelectLocal,
            label = { Text(localLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        FilterChip(
            selected = !isLocal,
            onClick = onSelectEverywhere,
            label = { Text(stringResource(R.string.search_scope_everywhere), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}
