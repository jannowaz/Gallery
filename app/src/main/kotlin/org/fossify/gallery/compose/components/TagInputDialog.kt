package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInputDialog(
    initialTags: Set<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    suggestedTags: List<String> = emptyList(),
    suggestedTagCounts: Map<String, Int> = emptyMap(),
    recentTags: List<String> = emptyList(),
    folderSuggestions: Map<String, Int> = emptyMap(),
    tagHierarchy: Map<String, String> = emptyMap(),
    onSetTagParent: (String, String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    batchCount: Int = 1,
) {
    var tagInput by remember { mutableStateOf("") }
    var debouncedTagInput by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>().also { it.addAll(initialTags) } }
    var showSuggestions by remember { mutableStateOf(false) }
    var showHierarchyPicker by remember { mutableStateOf(false) }
    var pendingNewTag by remember { mutableStateOf<String?>(null) }
    val tagFocusRequester = remember { FocusRequester() }

    // Debounce the in-memory filtering as well as any future provider-backed suggestion source.
    // Text entry stays responsive and the list is recalculated only after a short pause.
    LaunchedEffect(tagInput) {
        delay(120)
        debouncedTagInput = tagInput.trim()
    }
    LaunchedEffect(Unit) {
        delay(220)
        tagFocusRequester.requestFocus()
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.action_tags), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (batchCount > 1) Text(stringResource(R.string.files_count, batchCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
        Column(modifier = Modifier.fillMaxWidth()) {
                org.fossify.gallery.compose.util.XmpBatchIndicator(modifier = Modifier.padding(bottom = 8.dp))
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                // The whole chip removes the tag as well as the explicit close
                                // button. A clickable chip with a no-op handler was confusing for
                                // TalkBack and made a visible control feel broken.
                                onClick = { tags.remove(tag); onRemoveTag(tag) },
                                label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = {
                            IconButton(onClick = { tags.remove(tag); onRemoveTag(tag) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.Close, stringResource(R.string.action_remove), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                shape = RoundedCornerShape(Radius.sm),
                                colors = InputChipDefaults.inputChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, labelColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            )
                        }
                    }
                }

                if (folderSuggestions.isNotEmpty() && tags.isEmpty() && tagInput.isBlank()) {
                    Text(stringResource(R.string.frequent_in_folder), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        folderSuggestions.entries.sortedByDescending { it.value }.take(10).forEach { (tag, _) ->
                            Surface(
                                onClick = { if (tags.none { it.equals(tag, ignoreCase = true) }) { tags.add(tag); onAddTag(tag) } },
                                shape = RoundedCornerShape(Radius.sm),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it; showSuggestions = true },
                        label = { Text(stringResource(R.string.add_tag)) },
                        placeholder = { Text(stringResource(R.string.tags_example_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(tagFocusRequester),
                        trailingIcon = {
                            IconButton(onClick = { showHierarchyPicker = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.AccountTree, contentDescription = stringResource(R.string.browse_tag_hierarchy), modifier = Modifier.size(20.dp))
                            }
                        },
                        shape = RoundedCornerShape(Radius.md),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addCurrentTag() }),
                    )

                    if (showSuggestions && filteredSuggestions.isNotEmpty()) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = { showSuggestions = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            filteredSuggestions.forEach { suggestion ->
                                val count = suggestedTagCounts[suggestion]
                                DropdownMenuItem(
                                    text = {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                                            if (count != null) Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = { addExistingTag(suggestion) },
                                )
                            }
                        }
                    }
                }

                if (tagInput.isNotBlank() && candidateTags.none { it.equals(tagInput.trim(), ignoreCase = true) }) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { addCurrentTag() }) {
                        Text(stringResource(R.string.add_new_tag, tagInput.trim()))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )

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
