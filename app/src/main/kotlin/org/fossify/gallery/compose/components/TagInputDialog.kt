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
import androidx.compose.runtime.getValue
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInputDialog(
    initialTags: Set<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    suggestedTags: List<String> = emptyList(),
    suggestedTagCounts: Map<String, Int> = emptyMap(),
    folderSuggestions: Map<String, Int> = emptyMap(),
    onDismiss: () -> Unit,
    batchCount: Int = 1,
) {
    var tagInput by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>().also { it.addAll(initialTags) } }
    var showSuggestions by remember { mutableStateOf(false) }

    val filteredSuggestions = if (tagInput.isBlank()) {
        suggestedTags.filter { it !in tags }.take(8)
    } else {
        suggestedTags.filter { it.contains(tagInput, ignoreCase = true) && it !in tags }.take(12)
    }

    fun addCurrentTag() {
        val t = tagInput.trim().replace(",", "").replace(";", "")
        if (t.isNotBlank() && t !in tags) {
            tags.add(t)
            onAddTag(t)
            tagInput = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.action_tags), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (batchCount > 1) Text("$batchCount Dateien", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { },
                                label = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) },
                                trailingIcon = {
                                    IconButton(onClick = { tags.remove(tag); onRemoveTag(tag) }, modifier = Modifier.size(18.dp)) {
                                        Icon(Icons.Default.Close, stringResource(R.string.action_remove), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                shape = RoundedCornerShape(Radius.sm),
                                colors = InputChipDefaults.inputChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            )
                        }
                    }
                }

                if (folderSuggestions.isNotEmpty() && tags.isEmpty() && tagInput.isBlank()) {
                    Text(stringResource(R.string.frequent_in_folder), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        folderSuggestions.entries.sortedByDescending { it.value }.take(10).forEach { (tag, _) ->
                            Surface(
                                onClick = { if (tag !in tags) { tags.add(tag); onAddTag(tag) } },
                                shape = RoundedCornerShape(Radius.sm),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(tag, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it; showSuggestions = it.isNotEmpty() || suggestedTags.any { s -> s !in tags } },
                        label = { Text(stringResource(R.string.add_tag)) },
                        placeholder = { Text(stringResource(R.string.tags_example_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.md),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
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
                                    onClick = { tagInput = suggestion; showSuggestions = false },
                                )
                            }
                        }
                    }
                }

                if (tagInput.isNotBlank() && filteredSuggestions.none { it.equals(tagInput, ignoreCase = true) }) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { addCurrentTag() }) {
                        Text(stringResource(R.string.add_new_tag, tagInput.trim()))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}
