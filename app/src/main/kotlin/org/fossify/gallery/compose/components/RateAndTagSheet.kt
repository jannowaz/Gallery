package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.helpers.MAX_RECENT_TAGS

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Rating and tags share the same "quick tap, immediately applied" interaction model, so they're one
 * sheet instead of two separate dialogs - the core rename->tag->rate loop no longer needs to close
 * and reopen a menu between the tag step and the rate step. Rename stays its own dialog: it's a
 * WorkManager-backed batch job with its own progress/consent flow, a different enough interaction
 * shape that folding it in here would confuse rather than simplify.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RateAndTagSheet(
    batchCount: Int,
    currentRating: Int,
    ratingIsMixed: Boolean = false,
    onRate: (Int) -> Unit,
    initialTags: Set<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    suggestedTags: List<String> = emptyList(),
    suggestedTagCounts: Map<String, Int> = emptyMap(),
    recentTags: List<String> = emptyList(),
    mixedTagCounts: Map<String, Int> = emptyMap(),
    tagHierarchy: Map<String, String> = emptyMap(),
    metadataStorageHint: String? = null,
    onSetTagParent: (String, String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    var rating by remember { mutableIntStateOf(currentRating) }
    // Zero is a valid "unrated" value, so it cannot also stand in for a mixed batch. Keep the
    // distinction visible until the user deliberately picks (or clears) a rating for the batch.
    var mixedRating by remember { mutableStateOf(ratingIsMixed) }
    val tags = remember { mutableStateListOf<String>().also { it.addAll(initialTags) } }
    var tagInput by remember { mutableStateOf("") }
    var debouncedTagInput by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var showHierarchyPicker by remember { mutableStateOf(false) }
    var pendingNewTag by remember { mutableStateOf<String?>(null) }
    val batchBusy = org.fossify.gallery.compose.util.XmpBatch.isBusy

    LaunchedEffect(currentRating, ratingIsMixed) {
        rating = currentRating
        mixedRating = ratingIsMixed
    }
    LaunchedEffect(initialTags) {
        tags.clear()
        tags.addAll(initialTags)
    }

    // Keep typing on the UI thread cheap. The old code rescanned the complete tag list on every
    // keystroke, which made a large library feel like it was starting a new search for each letter.
    // The input remains immediate, while the local suggestion list updates once the user pauses.
    LaunchedEffect(tagInput) {
        delay(120)
        debouncedTagInput = tagInput.trim()
    }
    val selectedTagKeys = remember(tags.toList()) { tags.map { it.lowercase(Locale.ROOT) }.toSet() }
    val candidateTags = remember(recentTags, suggestedTags) {
        (recentTags + suggestedTags)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val filteredSuggestions = remember(debouncedTagInput, candidateTags, selectedTagKeys, suggestedTagCounts) {
        val available = candidateTags.filter { it.lowercase(Locale.ROOT) !in selectedTagKeys }
        if (debouncedTagInput.isBlank()) {
            available.take(10)
        } else {
            val query = debouncedTagInput.lowercase(Locale.ROOT)
            available
                .filter { it.lowercase(Locale.ROOT).contains(query) }
                .sortedWith(
                    compareBy<String> {
                        when {
                            it.equals(debouncedTagInput, ignoreCase = true) -> 0
                            it.startsWith(debouncedTagInput, ignoreCase = true) -> 1
                            else -> 2
                        }
                    }.thenByDescending { tag -> suggestedTagCounts.entries.firstOrNull { it.key.equals(tag, ignoreCase = true) }?.value ?: 0 }
                        .thenBy { it.lowercase(Locale.ROOT) },
                )
                .take(12)
        }
    }

    fun addExistingTag(t: String) {
        if (t.isNotBlank() && tags.none { it.equals(t, ignoreCase = true) }) {
            tags.add(t)
            onAddTag(t)
            tagInput = ""
            showSuggestions = false
        }
    }

    fun addCurrentTag() {
        val t = tagInput.trim().replace(",", "").replace(";", "")
        if (t.isBlank() || tags.any { it.equals(t, ignoreCase = true) }) return
        if (candidateTags.any { it.equals(t, ignoreCase = true) }) addExistingTag(t)
        else { pendingNewTag = t; showSuggestions = false }
    }

    fun sectionLabel(text: String) = @Composable {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // Live progress of the tag/rating batch this sheet just triggered - hundreds of XMP
            // writes take real time and used to run without any visible signal.
            org.fossify.gallery.compose.util.XmpBatchIndicator()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (batchCount > 1) stringResource(R.string.rate_tag_sheet_title_batch, batchCount) else stringResource(R.string.rate_tag_sheet_title_single),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                }
            }
            Spacer(Modifier.height(16.dp))

            sectionLabel(stringResource(R.string.rating_title))()
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 1..5) {
                    IconButton(enabled = !batchBusy, onClick = {
                        rating = if (!mixedRating && rating == i) 0 else i
                        mixedRating = false
                        onRate(rating)
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.cd_rating_star, i),
                            tint = RatingStarColor,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            if (mixedRating) {
                Text(
                    stringResource(R.string.rating_mixed_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            metadataStorageHint?.takeIf(String::isNotBlank)?.let { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            sectionLabel(stringResource(R.string.action_tags))()
            val availableRecentTags = recentTags.filter { recent -> tags.none { it.equals(recent, ignoreCase = true) } }.take(MAX_RECENT_TAGS)
            if (availableRecentTags.isNotEmpty()) {
                Text(
                    stringResource(R.string.recent_tags),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    availableRecentTags.forEach { tag ->
                        Surface(
                            onClick = { if (!batchBusy) addExistingTag(tag) },
                            enabled = !batchBusy,
                            shape = RoundedCornerShape(Radius.lg),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            enabled = !batchBusy,
                            onClick = { if (!batchBusy) { tags.remove(tag); onRemoveTag(tag) } },
                            label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) },
                            trailingIcon = {
                                IconButton(enabled = !batchBusy, onClick = { tags.remove(tag); onRemoveTag(tag) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Close, stringResource(R.string.action_remove), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            shape = RoundedCornerShape(Radius.sm),
                            colors = InputChipDefaults.inputChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, labelColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        )
                    }
                }
            }
            val partialTags = mixedTagCounts.entries
                .filter { it.value in 1 until batchCount && tags.none { tag -> tag.equals(it.key, ignoreCase = true) } }
                .sortedByDescending { it.value }
                .take(MAX_RECENT_TAGS)
            if (partialTags.isNotEmpty()) {
                Text(
                    stringResource(R.string.tags_partial_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    partialTags.forEach { (tag, count) ->
                        Surface(
                            onClick = { if (!batchBusy) addExistingTag(tag) },
                            enabled = !batchBusy,
                            shape = RoundedCornerShape(Radius.lg),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$count/$batchCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it; showSuggestions = true },
                    placeholder = { Text(stringResource(R.string.tags_example_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(enabled = !batchBusy, onClick = { showHierarchyPicker = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.AccountTree, contentDescription = stringResource(R.string.browse_tag_hierarchy), modifier = Modifier.size(20.dp))
                        }
                    },
                    shape = RoundedCornerShape(Radius.md),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addCurrentTag() }),
                )
                if (showSuggestions && filteredSuggestions.isNotEmpty()) {
                    DropdownMenu(expanded = true, onDismissRequest = { showSuggestions = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
                        filteredSuggestions.forEach { suggestion ->
                            val count = suggestedTagCounts[suggestion]
                            DropdownMenuItem(
                                text = {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                                        if (count != null) Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { if (!batchBusy) addExistingTag(suggestion) },
                            )
                        }
                    }
                }
            }
            if (tagInput.isNotBlank() && candidateTags.none { it.equals(tagInput.trim(), ignoreCase = true) }) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(enabled = !batchBusy, onClick = { addCurrentTag() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_new_tag, tagInput.trim()))
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(enabled = !batchBusy, onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
        }
    }

    if (showHierarchyPicker) {
        TagHierarchyPickerDialog(
            title = stringResource(R.string.tag_picker_title),
            tags = suggestedTags,
            hierarchy = tagHierarchy,
            tagCounts = suggestedTagCounts,
            confirmLabel = stringResource(org.fossify.commons.R.string.save),
            onDismiss = { showHierarchyPicker = false },
            onConfirm = { selected ->
                selected?.let { addExistingTag(it) }
                showHierarchyPicker = false
            },
        )
    }

    pendingNewTag?.let { newTag ->
        TagHierarchyPickerDialog(
            title = stringResource(R.string.tag_parent_picker_title),
            subtitle = newTag,
            tags = suggestedTags,
            hierarchy = tagHierarchy,
            tagCounts = suggestedTagCounts,
            allowNoParent = true,
            noParentLabel = stringResource(R.string.tag_parent_none),
            confirmLabel = stringResource(org.fossify.commons.R.string.save),
            onDismiss = { pendingNewTag = null },
            onConfirm = { parent ->
                onSetTagParent(newTag, parent)
                addExistingTag(newTag)
                pendingNewTag = null
            },
        )
    }
}
