package org.fossify.gallery.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.util.XmpBatch

/**
 * Quick tags for a current selection.
 *
 * The compact row stays one-tap fast. The small handle can be swiped upwards (or tapped) to
 * reveal a bounded, vertically scrollable tag panel, so a selection with many recent tags never
 * turns into a long horizontal hunt. A tag that is present on only part of the selection is shown
 * with its count; tapping it adds it to the remaining items. Tapping a tag present everywhere
 * removes it from the whole selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickTagRow(
    quickTags: List<String>,
    visible: Boolean,
    selectedCommonTags: Set<String>,
    selectedTagCounts: Map<String, Int>,
    selectionCount: Int,
    onToggleTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    val batchBusy = XmpBatch.progress != null
    val tagScrollState = rememberScrollState()
    val context = LocalContext.current

    fun countFor(tag: String): Int = selectedTagCounts.entries
        .firstOrNull { it.key.equals(tag, ignoreCase = true) }
        ?.value
        ?: 0

    fun isCommon(tag: String): Boolean = selectedCommonTags.any { it.equals(tag, ignoreCase = true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .animateContentSize(),
    ) {
        // The handle is deliberately a generous touch target. Swiping up expands and swiping down
        // collapses, which makes the panel feel like it can be pulled out instead of being hidden
        // behind an unfamiliar overflow control.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(expanded) {
                    var dragDistance = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragDistance += dragAmount
                        },
                        onDragEnd = {
                            if (dragDistance < -24f) expanded = true
                            if (dragDistance > 24f) expanded = false
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(36.dp).height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
            ) {}
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, bottom = if (expanded) 6.dp else 0.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        quickTags.forEach { tag ->
                            QuickTagChip(
                                tag = tag,
                                count = countFor(tag),
                                selectionCount = selectionCount,
                                common = isCommon(tag),
                                enabled = !batchBusy,
                                onClick = { onToggleTag(tag) },
                            )
                        }
                    }
                    IconButton(
                        onClick = { expanded = !expanded },
                        enabled = !batchBusy,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(if (expanded) R.string.quick_tags_collapse else R.string.quick_tags_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(AppMotion.short),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(AppMotion.short),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = if (selectionCount > 1) {
                                context.getString(R.string.quick_tags_mixed_hint)
                            } else {
                                stringResource(R.string.recent_tags)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp),
                        )
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 264.dp)
                                .verticalScroll(tagScrollState)
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            quickTags.forEach { tag ->
                                QuickTagChip(
                                    tag = tag,
                                    count = countFor(tag),
                                    selectionCount = selectionCount,
                                    common = isCommon(tag),
                                    enabled = !batchBusy,
                                    onClick = { onToggleTag(tag) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTagChip(
    tag: String,
    count: Int,
    selectionCount: Int,
    common: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val partial = selectionCount > 1 && count in 1 until selectionCount
    val containerColor = when {
        common -> MaterialTheme.colorScheme.secondaryContainer
        partial -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        common -> MaterialTheme.colorScheme.onSecondaryContainer
        partial -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (partial) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)) else null

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(Radius.lg),
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
            )
            if (common) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
            } else if (partial) {
                Text(
                    text = "$count/$selectionCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                )
            }
        }
    }
}
