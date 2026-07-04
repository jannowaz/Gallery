package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.RatingStarColor

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
    onRate: (Int) -> Unit,
    initialTags: Set<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    suggestedTags: List<String> = emptyList(),
    suggestedTagCounts: Map<String, Int> = emptyMap(),
    onDismiss: () -> Unit,
) {
    var rating by remember { mutableIntStateOf(currentRating) }
    val tags = remember { mutableStateListOf<String>().also { it.addAll(initialTags) } }
    var tagInput by remember { mutableStateOf("") }
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

    fun sectionLabel(text: String) = @Composable {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (batchCount > 1) stringResource(R.string.rate_tag_sheet_title_batch, batchCount) else stringResource(R.string.rate_tag_sheet_title_single),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))

            sectionLabel(stringResource(R.string.rating_title))()
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 1..5) {
                    IconButton(onClick = { rating = if (rating == i) 0 else i; onRate(rating) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.cd_rating_star, i),
                            tint = RatingStarColor,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            sectionLabel(stringResource(R.string.action_tags))()
            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = {},
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
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it; showSuggestions = it.isNotEmpty() || suggestedTags.any { s -> s !in tags } },
                    placeholder = { Text(stringResource(R.string.tags_example_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
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
                                onClick = { tagInput = suggestion; showSuggestions = false },
                            )
                        }
                    }
                }
            }
            if (tagInput.isNotBlank() && filteredSuggestions.none { it.equals(tagInput, ignoreCase = true) }) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.add_new_tag, tagInput.trim()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
        }
    }
}
