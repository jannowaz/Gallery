package org.fossify.gallery.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.helpers.rootOf

/**
 * Hierarchical tag picker used both for choosing an existing tag and for choosing the parent of a
 * newly created tag. The first level deliberately feels more like a radial launcher: large,
 * circular root targets make the hierarchy inviting and quick to scan. Deeper levels switch to a
 * denser list because long names and narrow phone screens need more horizontal space.
 *
 * [onConfirm] receives null when [allowNoParent] is true and the user explicitly chose no parent.
 */
@Composable
fun TagHierarchyPickerDialog(
    title: String,
    subtitle: String? = null,
    tags: Collection<String>,
    hierarchy: Map<String, String>,
    tagCounts: Map<String, Int> = emptyMap(),
    allowNoParent: Boolean = false,
    noParentLabel: String? = null,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    val allTags = remember(tags, hierarchy) {
        (tags + hierarchy.keys + hierarchy.values)
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
    }
    val childrenByParent = remember(hierarchy) {
        hierarchy.entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, children) -> children.distinct() }
    }
    val tagComparator = remember(tagCounts) {
        compareByDescending<String> { tagCounts[it] ?: 0 }.thenBy { it.lowercase() }
    }
    val rootTags = remember(allTags, hierarchy, tagComparator) {
        val roots = allTags.filter { rootOf(it, hierarchy) == it }
        (roots.ifEmpty { allTags }).sortedWith(tagComparator)
    }

    var path by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    val currentParent = path.lastOrNull()
    val levelTags = remember(path, rootTags, childrenByParent, allTags, tagComparator) {
        (if (currentParent == null) rootTags else childrenByParent[currentParent].orEmpty())
            .filter { it in allTags }
            .distinct()
            .sortedWith(tagComparator)
    }

    fun choose(tag: String) {
        selectedTag = tag
        // A node remains selectable as a parent even when it has children. Moving down immediately
        // keeps the common "root -> child -> leaf" flow fast while the footer still confirms the
        // node that was last tapped.
        if (childrenByParent[tag].orEmpty().any { it in allTags }) path = path + tag
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(min = 360.dp, max = 620.dp),
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (path.isNotEmpty()) {
                        IconButton(
                            onClick = { path = path.dropLast(1) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(R.string.cd_back))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }

                if (path.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        path.forEachIndexed { index, tag ->
                            if (index > 0) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 2.dp))
                            TextButton(
                                onClick = { path = path.take(index + 1) },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))

                if (allowNoParent && path.isEmpty()) {
                    Surface(
                        onClick = { selectedTag = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = selectedTag == null },
                        shape = RoundedCornerShape(Radius.md),
                        color = if (selectedTag == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(noParentLabel.orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (selectedTag == null) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    if (currentParent == null) androidx.compose.ui.res.stringResource(R.string.tag_parent_root_level)
                    else androidx.compose.ui.res.stringResource(R.string.tag_parent_children_level, currentParent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))

                if (levelTags.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(androidx.compose.ui.res.stringResource(R.string.tag_parent_no_tags), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                } else if (path.isEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 108.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(levelTags, key = { it }) { tag ->
                            val isSelected = selectedTag == tag
                            val childCount = childrenByParent[tag].orEmpty().count { it in allTags }
                            Surface(
                                onClick = { choose(tag) },
                                modifier = Modifier
                                    .height(116.dp)
                                    .semantics(mergeDescendants = true) { selected = isSelected },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = if (isSelected) 4.dp else 1.dp,
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Box(Modifier.size(30.dp).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                                        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                        else Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Text(tag, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val count = tagCounts[tag]
                                    Text(
                                        if (childCount > 0) androidx.compose.ui.res.stringResource(R.string.tag_parent_children_count, childCount)
                                        else count?.let { androidx.compose.ui.res.stringResource(R.string.files_count, it) }.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(levelTags, key = { it }) { tag ->
                            val isSelected = selectedTag == tag
                            val childCount = childrenByParent[tag].orEmpty().count { it in allTags }
                            Surface(
                                onClick = { choose(tag) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = true) { selected = isSelected },
                                shape = RoundedCornerShape(Radius.md),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                                    }
                                    Spacer(Modifier.size(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(tag, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val count = tagCounts[tag]
                                        Text(
                                            if (childCount > 0) androidx.compose.ui.res.stringResource(R.string.tag_parent_children_count, childCount)
                                            else count?.let { androidx.compose.ui.res.stringResource(R.string.files_count, it) }.orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    if (childCount > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                selectedTag?.let {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.tag_parent_selected, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(R.string.cancel)) }
                    Button(
                        onClick = { onConfirm(selectedTag) },
                        enabled = selectedTag != null || allowNoParent,
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}
