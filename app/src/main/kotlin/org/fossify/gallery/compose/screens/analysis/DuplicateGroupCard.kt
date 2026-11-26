package org.fossify.gallery.compose.screens.analysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.util.sharedElementKey
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_INLINE_FILES = 40

@Composable
internal fun DuplicateGroupCard(
    group: DuplicateGroup,
    similar: Boolean,
    selected: Set<String>,
    groupSelected: Boolean,
    onToggleGroup: () -> Unit,
    onToggle: (String) -> Unit,
    onView: (List<String>, Int) -> Unit,
    onSelectFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val groupPaths = remember(group.files) { group.files.map { it.path } }
    var expanded by remember(group.files.firstOrNull()?.path ?: group.hash) { mutableStateOf(false) }
    val totalSize = remember(group.files) { group.files.sumOf { it.size } }
    val markedInGroup = group.files.count { it.path in selected }
    val allMarked = markedInGroup > 0 && markedInGroup == group.files.size

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (groupSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        ),
        border = if (groupSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            DuplicateGroupHeader(
                group = group,
                similar = similar,
                totalSize = totalSize,
                markedInGroup = markedInGroup,
                allMarked = allMarked,
                groupSelected = groupSelected,
                expanded = expanded,
                onToggleGroup = onToggleGroup,
                onToggleExpand = { expanded = !expanded },
            )
            if (!expanded) {
                DuplicateCollapsedStrip(
                    files = group.files,
                    groupPaths = groupPaths,
                    selected = selected,
                    markedInGroup = markedInGroup,
                    onView = onView,
                    onToggle = onToggle,
                )
            } else {
                group.files.take(MAX_INLINE_FILES).forEachIndexed { index, file ->
                    DuplicateExpandedFileRow(
                        file = file,
                        index = index,
                        isSelected = file.path in selected,
                        markedInGroup = markedInGroup,
                        similar = similar,
                        dateFormat = dateFormat,
                        onView = { onView(groupPaths, index) },
                        onToggle = { onToggle(file.path) },
                        onSelectFolder = { onSelectFolder(file.path) },
                    )
                }
                if (group.files.size > MAX_INLINE_FILES) {
                    Text(
                        stringResource(
                            R.string.duplicate_group_more_files,
                            group.files.size - MAX_INLINE_FILES,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupHeader(
    group: DuplicateGroup,
    similar: Boolean,
    totalSize: Long,
    markedInGroup: Int,
    allMarked: Boolean,
    groupSelected: Boolean,
    expanded: Boolean,
    onToggleGroup: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = groupSelected, onCheckedChange = { onToggleGroup() })
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.dup_group_header,
                        group.files.size,
                        stringResource(if (similar) R.string.similar else R.string.identical),
                        formatBytes(totalSize),
                        formatBytes(group.wastedBytes),
                    ),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (!similar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = KeepGreenColor,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            stringResource(R.string.dup_verified_exact),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (allMarked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.dup_group_all_marked),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if (markedInGroup > 0) {
                    Text(
                        stringResource(R.string.dup_group_marked_count, markedInGroup),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DuplicateCollapsedStrip(
    files: List<DuplicateFile>,
    groupPaths: List<String>,
    selected: Set<String>,
    markedInGroup: Int,
    onView: (List<String>, Int) -> Unit,
    onToggle: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(files.take(MAX_INLINE_FILES), key = { it.path }) { file ->
                val isSel = file.path in selected
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .combinedClickable(
                            onClick = { onView(groupPaths, groupPaths.indexOf(file.path)) },
                            onLongClick = { onToggle(file.path) },
                        ),
                ) {
                    GalleryImage(
                        path = file.path,
                        contentDescription = file.name,
                        modifier = Modifier.size(72.dp).sharedElementKey("media_${file.path}"),
                    )
                    if (file.mediaType == TYPE_VIDEOS) {
                        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    if (isSel) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        )
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp),
                        )
                    } else if (markedInGroup > 0) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = KeepGreenColor,
                            modifier = Modifier.align(Alignment.TopStart).padding(3.dp).size(18.dp),
                        )
                    }
                }
            }
        }
        if (files.size > MAX_INLINE_FILES) {
            Text(
                stringResource(R.string.duplicate_group_more_files, files.size - MAX_INLINE_FILES),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun DuplicateExpandedFileRow(
    file: DuplicateFile,
    index: Int,
    isSelected: Boolean,
    markedInGroup: Int,
    similar: Boolean,
    dateFormat: SimpleDateFormat,
    onView: () -> Unit,
    onToggle: () -> Unit,
    onSelectFolder: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(onClick = onView),
        ) {
            GalleryImage(
                path = file.path,
                contentDescription = file.name,
                modifier = Modifier.size(64.dp).sharedElementKey("media_${file.path}"),
            )
            if (file.mediaType == TYPE_VIDEOS) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(onClick = onView),
        ) {
            DuplicateFileTitleRow(
                fileName = file.name,
                isSurvivor = markedInGroup > 0 && !isSelected,
                isNewestDefault = markedInGroup == 0 && index == 0,
            )
            Text(
                file.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildFileMetaLine(file, dateFormat),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DuplicateFileRatingTagsRow(file = file)
            if (!similar) {
                Surface(
                    onClick = onSelectFolder,
                    shape = RoundedCornerShape(Radius.lg),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.select_folder_duplicates_short),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun DuplicateFileTitleRow(
    fileName: String,
    isSurvivor: Boolean,
    isNewestDefault: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            fileName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isSurvivor) {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(Radius.xs),
                color = KeepGreenColor.copy(alpha = 0.18f),
            ) {
                Text(
                    stringResource(R.string.dup_stays),
                    Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = KeepGreenColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else if (isNewestDefault) {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(Radius.xs),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            ) {
                Text(
                    stringResource(R.string.newest),
                    Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DuplicateFileRatingTagsRow(file: DuplicateFile) {
    if (file.rating <= 0 && file.tags.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (file.rating > 0) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = RatingStarColor,
                modifier = Modifier.size(12.dp),
            )
            Text(
                "${file.rating}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (file.tags.isNotEmpty()) Spacer(Modifier.width(6.dp))
        }
        if (file.tags.isNotEmpty()) {
            Text(
                file.tags.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun buildFileMetaLine(file: DuplicateFile, dateFormat: SimpleDateFormat): String =
    buildString {
        if (file.width > 0 && file.height > 0) append("${file.width}×${file.height} · ")
        append(formatBytes(file.size))
        append(" · ")
        append(dateFormat.format(Date(file.modified)))
    }
