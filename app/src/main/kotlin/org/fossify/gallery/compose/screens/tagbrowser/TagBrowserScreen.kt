package org.fossify.gallery.compose.screens.tagbrowser
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.Radius

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.screens.MediaSkeleton
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.LibraryAlbumGrid
import org.fossify.gallery.compose.components.AlbumGridItem
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.helpers.expandTagsWithDescendants
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagBrowserScreen(
    onBack: () -> Unit,
    onTagFilterApplied: (tagNames: Set<String>, displayName: String) -> Unit,
    viewSettings: org.fossify.gallery.compose.screens.ViewSettings = org.fossify.gallery.compose.screens.ViewSettings(),
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = LocalMediaRepository.current

    // Seed from the repo-level cache instead of an empty map - this whole screen is disposed and
    // recreated whenever the user navigates away (e.g. to Viewer) and back, and without this the full
    // media_tags scan reran from scratch on every single round trip.
    var allTags by remember { mutableStateOf(repo.getTagsWithPathsCached() ?: emptyMap()) }
    var scanning by remember { mutableStateOf(false) }
    var deleteConfirmTags by remember { mutableStateOf<Set<String>>(emptySet()) }
                var mergeTargetTag by remember { mutableStateOf<String?>(null) }
                var renameTargetTag by remember { mutableStateOf<String?>(null) }
                var pendingParentAssign by remember { mutableStateOf<Set<String>?>(null) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tagSearchQuery by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val hierarchy = remember(refreshTrigger) { ctx.config.tagHierarchy }
    val filesCountFormat = stringResource(R.string.files_count)
    val childrenCountFormat = stringResource(R.string.tag_children_count)

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger == 0 && repo.getTagsWithPathsCached() != null) return@LaunchedEffect
        scanning = true
        withContext(Dispatchers.IO) {
            val tags = try { repo.refreshTagsWithPathsCache() } catch (_: Exception) { emptyMap() }
            withContext(Dispatchers.Main) { allTags = tags.entries.sortedByDescending { it.value.size }.associate { it.key to it.value }; scanning = false }
        }
    }

    // Without this, a tag added/removed elsewhere (MediaScreen's quick-tag row, the Viewer's tag
    // editor) never invalidated this screen's cache. This refreshes allTags directly instead of
    // bumping refreshTrigger, so an unrelated app-wide event (e.g. a rating change in another folder)
    // doesn't flip scanning=true and flash the loading skeleton while the user is browsing tags.
    LaunchedEffect(Unit) {
        org.fossify.gallery.helpers.RefreshBus.events.collect {
            withContext(Dispatchers.IO) {
                val tags = try { repo.refreshTagsWithPathsCache() } catch (_: Exception) { emptyMap() }
                withContext(Dispatchers.Main) { allTags = tags.entries.sortedByDescending { it.value.size }.associate { it.key to it.value } }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags_count_title, allTags.size), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Progress of a running tag merge/rename (their dialogs close immediately).
            org.fossify.gallery.compose.util.XmpBatchIndicator()
            OutlinedTextField(
                value = tagSearchQuery,
                onValueChange = { tagSearchQuery = it },
                placeholder = { Text(stringResource(R.string.search_tag)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, stringResource(R.string.cd_search), modifier = Modifier.size(18.dp)) },
                trailingIcon = { if (tagSearchQuery.isNotEmpty()) IconButton(onClick = { tagSearchQuery = "" }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, stringResource(R.string.action_empty), modifier = Modifier.size(16.dp)) } },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(Radius.md),
            )
            Spacer(Modifier.height(8.dp))
            // Crossfade + shimmer skeleton instead of a bare spinner - this is the same "grid of tiles
            // is loading" moment as the media grids elsewhere, so it should look consistent.
            val tagsContentState = if (scanning) "loading" else if (allTags.isEmpty()) "empty" else "content"
            Crossfade(targetState = tagsContentState, animationSpec = AppMotion.short, label = "tagsContent", modifier = Modifier.weight(1f)) { tcs ->
            if (tcs == "loading") {
                MediaSkeleton(columns = viewSettings.columnCount)
            } else if (tcs == "empty") {
                EmptyState(Icons.AutoMirrored.Filled.Label, stringResource(R.string.no_tags_found), subtitle = stringResource(R.string.no_tags_hint))
            } else {
                // Explicit Column - this "content" state now lives inside Crossfade's Box slot
                // instead of being a direct child of the outer Column, so without this the grid and
                // the action-buttons row below it would overlay instead of stacking vertically.
                Column(Modifier.fillMaxSize()) {
                // Both the sort/filter and the AlbumGridItem mapping were previously recomputed on
                // every recomposition of this screen (e.g. every tag selection toggle), not just
                // when allTags/hierarchy/the search query actually changed.
                val filteredTags = remember(allTags, hierarchy, tagSearchQuery) {
                    if (tagSearchQuery.isBlank()) allTags.entries.toList().sortedWith(compareByDescending<Map.Entry<String, List<String>>> { it.key in hierarchy.values }.thenBy { it.key }) else allTags.entries.filter { (tag, _) -> tag.contains(tagSearchQuery, ignoreCase = true) }.sortedByDescending { it.value.size }
                }
                val tagGridItems = remember(filteredTags, hierarchy) {
                    filteredTags.map { AlbumGridItem(key = it.key, name = if (it.key in hierarchy) "↳ ${it.key}" else it.key, thumbnailPath = it.value.firstOrNull() ?: "", count = it.value.size, previewPaths = it.value.take(3)) }
                }
                // Precomputed once instead of scanning the whole hierarchy map per visible row
                // inside subtitle = {...} below (was O(tags²) across all rows).
                val childrenByParent = remember(hierarchy) { hierarchy.entries.groupBy({ it.value }, { it.key }) }
                LibraryAlbumGrid(
                    items = tagGridItems,
                    viewSettings = viewSettings,
                    onClick = { item ->
                        if (selectedTags.isNotEmpty()) selectedTags = if (item.key in selectedTags) selectedTags - item.key else selectedTags + item.key
                        else {
                            // Include descendant tag names too, so filtering on a parent like "Places"
                            // also surfaces files only tagged with a nested child like "Berlin" -
                            // resolved to files in SQL by MediaViewModel, not here.
                            val tagNames = expandTagsWithDescendants(setOf(item.key), hierarchy)
                            onTagFilterApplied(tagNames, item.key)
                            onBack()
                        }
                    },
                    onLongClick = { item -> selectedTags = if (item.key in selectedTags) selectedTags - item.key else selectedTags + item.key },
                    countLabel = { filesCountFormat.format(it) },
                    selectedKeys = selectedTags,
                    subtitle = { item ->
                        val parent = hierarchy[item.key]
                        val children = childrenByParent[item.key] ?: emptyList()
                        val parts = mutableListOf(filesCountFormat.format(item.count))
                        if (parent != null) parts.add("← $parent")
                        if (children.isNotEmpty()) parts.add("→ ${childrenCountFormat.format(children.size)}")
                        parts.joinToString(" · ")
                    },
                    modifier = Modifier.weight(1f),
                )
                if (selectedTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(onClick = {
                            val tagNames = expandTagsWithDescendants(selectedTags, hierarchy)
                            onBack()
                            onTagFilterApplied(tagNames, selectedTags.joinToString(", "))
                        }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.action_filter), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary) }
                        }
                        Surface(onClick = { deleteConfirmTags = selectedTags }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(org.fossify.commons.R.string.delete), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                    if (selectedTags.size == 1) {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(onClick = { renameTargetTag = selectedTags.sorted().firstOrNull() }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.action_rename), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Surface(onClick = { pendingParentAssign = selectedTags }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.action_parent), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(onClick = { pendingParentAssign = selectedTags }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.action_parent), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Surface(onClick = { mergeTargetTag = selectedTags.sorted().firstOrNull() }, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.action_merge), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                }
            }
            }
        }
    }

    if (deleteConfirmTags.isNotEmpty()) {
        val tagsToDelete = deleteConfirmTags
        val totalFiles = tagsToDelete.flatMap { allTags[it] ?: emptyList() }.distinct().size
        AlertDialog(
            onDismissRequest = { deleteConfirmTags = emptySet() },
            title = { Text(if (tagsToDelete.size == 1) stringResource(R.string.remove_tag_title) else stringResource(R.string.remove_tags_title)) },
            text = {
                if (tagsToDelete.size == 1) Text(stringResource(R.string.remove_tag_confirm, tagsToDelete.first(), totalFiles))
                else Text(stringResource(R.string.remove_tags_confirm, tagsToDelete.size, tagsToDelete.joinToString(", "), totalFiles))
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        // repo.removeTag already keeps the XMP file, media_cache and the normalized
                        // media_tags table in sync per path - no separate cache cleanup pass needed.
                        tagsToDelete.forEach { tag ->
                            val pathsForTag = allTags[tag] ?: return@forEach
                            pathsForTag.forEach { p -> repo.removeTag(p, tag) }
                        }
                        // Drop orphaned hierarchy entries referencing the deleted tags.
                        try {
                            val h = ctx.config.tagHierarchy
                            val cleaned = h.filterKeys { it !in tagsToDelete }.filterValues { it !in tagsToDelete }
                            if (cleaned.size != h.size) ctx.config.tagHierarchy = cleaned.toMutableMap()
                        } catch (e: Exception) { android.util.Log.e("TagBrowser", "Orphaned hierarchy cleanup failed", e) }
                        withContext(Dispatchers.Main) {
                            ctx.toast(ctx.getString(R.string.tags_removed, tagsToDelete.size, totalFiles), Toast.LENGTH_SHORT)
                            deleteConfirmTags = emptySet(); refreshTrigger++; selectedTags = emptySet()
                        }
                    }
                }) { Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmTags = emptySet() }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (pendingParentAssign != null) {
        val tagsToAssign = pendingParentAssign!!
        val candidates = allTags.keys.filter { it !in tagsToAssign }
        var selectedParent by remember { mutableStateOf(candidates.firstOrNull() ?: "") }

        // Build a tree-sorted list of selectable parents (parents first, then children indented).
        val treeList = remember(candidates, hierarchy) {
            val hasParent = { tag: String -> tag in hierarchy }
            val isParent = { tag: String -> hierarchy.any { it.value == tag } }
            val available = candidates.toSet()
            val parents = available.filter(isParent).sorted()
            val children = available.filter(hasParent).sortedWith(compareBy<String> { hierarchy[it] ?: "" }.thenBy { it })
            val rest = (available - parents.toSet() - children.toSet()).sorted()
            val out = mutableListOf<Pair<String, String>>()
            parents.forEach { out.add(it to it) }
            children.forEach { out.add("↳ ${it}" to it) }
            rest.forEach { out.add(it to it) }
            out
        }

        AlertDialog(
            onDismissRequest = { pendingParentAssign = null },
            title = { Text(stringResource(R.string.assign_parent_title)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.set_parent_for, tagsToAssign.joinToString(", ")))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = selectedParent,
                        onValueChange = { selectedParent = it },
                        label = { Text(stringResource(R.string.parent_tag)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (candidates.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.existing_tags), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(max = 240.dp)) {
                            treeList.forEach { (label, tag) ->
                                Surface(
                                    onClick = { selectedParent = tag },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                    shape = RoundedCornerShape(Radius.sm),
                                    color = if (selectedParent == tag) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(horizontal = if (label.startsWith("↳")) 24.dp else 8.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selectedParent == tag) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedParent.isNotBlank()) {
                        val h = ctx.config.tagHierarchy.toMutableMap()
                        fun createsCycle(child: String, parent: String): Boolean {
                            var cur: String? = parent
                            val seen = HashSet<String>()
                            while (cur != null && seen.add(cur)) {
                                if (cur == child) return true
                                cur = h[cur]
                            }
                            return false
                        }
                        val invalid = selectedParent in tagsToAssign || tagsToAssign.any { createsCycle(it, selectedParent) }
                        if (invalid) {
                            ctx.toast(ctx.getString(R.string.invalid_parent_cycle), Toast.LENGTH_SHORT)
                        } else {
                            tagsToAssign.forEach { h[it] = selectedParent }
                            ctx.config.tagHierarchy = h
                            ctx.toast(ctx.getString(R.string.parent_tag_set), Toast.LENGTH_SHORT)
                            pendingParentAssign = null; selectedTags = emptySet(); refreshTrigger++
                            return@TextButton
                        }
                    }
                    pendingParentAssign = null; selectedTags = emptySet()
                }) { Text(stringResource(org.fossify.commons.R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pendingParentAssign = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (mergeTargetTag != null) {
        val defaultTarget = mergeTargetTag!!
        var mergeTargetName by remember(mergeTargetTag) { mutableStateOf(defaultTarget) }
        val existingTags = allTags.keys.filter { it != defaultTarget }.sorted()
        AlertDialog(
            onDismissRequest = { mergeTargetTag = null },
            title = { Text(stringResource(R.string.merge_tags_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.sources_label, selectedTags.joinToString(", ")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = mergeTargetName, onValueChange = { mergeTargetName = it }, label = { Text(stringResource(R.string.target_tag)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (existingTags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.existing_tags), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            existingTags.forEach { t ->
                                Surface(onClick = { mergeTargetName = t }, shape = RoundedCornerShape(Radius.md), color = if (t == mergeTargetName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(t, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = if (t == mergeTargetName) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = mergeTargetName.trim()
                    if (target.isBlank()) return@TextButton
                    val sources = selectedTags - target
                    // Close the dialog right away - the XmpBatch indicator carries the progress;
                    // the dialog used to sit visually frozen for the whole multi-minute merge.
                    mergeTargetTag = null
                    scope.launch {
                        // repo.addTag/removeTag already keep the XMP file, media_cache and the
                        // normalized media_tags table in sync per path. Short-circuit: if adding
                        // the target tag fails, the source tag is left in place.
                        val items = sources.flatMap { srcTag -> (allTags[srcTag] ?: emptyList()).map { it to srcTag } }
                        org.fossify.gallery.compose.util.XmpBatch.run(ctx, items, successMessage = ctx.getString(R.string.merged_into, target)) { (p, srcTag) ->
                            repo.addTag(p, target) && repo.removeTag(p, srcTag)
                        }
                        refreshTrigger++; selectedTags = emptySet()
                    }
                }) { Text(stringResource(R.string.action_merge)) }
            },
            dismissButton = { TextButton(onClick = { mergeTargetTag = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (renameTargetTag != null) {
        val oldName = renameTargetTag!!
        var newName by remember(renameTargetTag) { mutableStateOf(oldName) }
        val count = allTags[oldName]?.size ?: 0
        AlertDialog(
            onDismissRequest = { renameTargetTag = null },
            title = { Text(stringResource(R.string.rename_tag_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.rename_tag_prompt, oldName, count), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text(stringResource(R.string.rename_numbered)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = newName.trim()
                    if (target.isBlank() || target == oldName) return@TextButton
                    // Dialog closes immediately, the XmpBatch indicator shows the per-file progress.
                    renameTargetTag = null
                    scope.launch {
                        val paths = allTags[oldName] ?: emptyList()
                        // repo.addTag/removeTag already keep the XMP file, media_cache and the
                        // normalized media_tags table in sync per path.
                        org.fossify.gallery.compose.util.XmpBatch.run(ctx, paths, successMessage = ctx.getString(R.string.renamed_tag_result, oldName, target, count)) { p ->
                            repo.addTag(p, target) && repo.removeTag(p, oldName)
                        }
                        refreshTrigger++; selectedTags = emptySet()
                    }
                }) { Text(stringResource(R.string.action_rename)) }
            },
            dismissButton = { TextButton(onClick = { renameTargetTag = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
