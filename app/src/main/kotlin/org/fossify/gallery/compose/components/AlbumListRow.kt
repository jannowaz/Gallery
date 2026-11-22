package org.fossify.gallery.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS

/**
 * The last up to [maxSegments] path segments of the folder that CONTAINS [fullPath] - shown as a
 * faint path hint on folder rows so two folders with the same name in different locations (e.g. two
 * "Screenshots") can be told apart. Empty for a top-level folder with nothing above it.
 */
fun folderPathHint(fullPath: String, internalStorageLabel: String, maxSegments: Int = 3): String {
    val parent = fullPath.trim().trimEnd('/').substringBeforeLast('/', "").trim('/')
    if (parent.isEmpty()) return ""

    // MediaStore, SAF and legacy paths can describe the same primary storage root in several ways.
    // Canonicalise those implementation details before shortening the path, so neighbouring rows do
    // not appear to live on different volumes merely because one source included "/storage".
    val internalRoots = listOf(
        "storage/emulated/0",
        "storage/emulated",
        "mnt/sdcard",
        "sdcard",
        "emulated/0",
        "0",
    )
    val root = internalRoots.firstOrNull { parent == it || parent.startsWith("$it/") }
    if (root != null) {
        val relativeSegments = parent.removePrefix(root).trim('/').split('/').filter(String::isNotEmpty)
        val visibleTail = relativeSegments.takeLast((maxSegments - 1).coerceAtLeast(0))
        return (listOf(internalStorageLabel) + visibleTail).joinToString("/")
    }

    return parent.split('/').filter(String::isNotEmpty).takeLast(maxSegments).joinToString("/")
}

/**
 * The one list row used by every folder-style list in the app - Albums, Collections, Favorites and
 * the Explorer's folder section - so they read as a single, consistent list instead of four
 * slightly different ones.
 */
@Composable
fun AlbumListRow(
    name: String,
    subtitle: String,
    coverPath: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    showChevron: Boolean = false,
    showThumbnail: Boolean = true,
    roundedCorners: Boolean = true,
    containerColor: Color? = null,
    dense: Boolean = false,
    compact: Boolean = false,
    badgeSubtitle: Boolean = false,
    pathHint: String? = null,
    imageCount: Int = 0,
    videoCount: Int = 0,
) {
    val s = LocalSpacing.current
    val compactLayout = dense || compact
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        containerColor ?: MaterialTheme.colorScheme.surfaceContainer
    }
    val cardShape = if (roundedCorners) MaterialTheme.shapes.medium else RoundedCornerShape(0.dp)
    val spokenDescription = albumSpokenDescription(name, subtitle, pathHint, imageCount, videoCount)
    val isItemSelected = selected
    AppCard(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = spokenDescription
            role = Role.Button
            this.selected = isItemSelected
        },
        color = container,
        shape = cardShape,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {}
                .padding(
                    horizontal = if (compactLayout) s.sm else s.md,
                    vertical = if (compactLayout) s.xs else s.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showThumbnail) {
                AlbumCover(
                    coverPath = coverPath,
                    showThumbnail = true,
                    roundedCorners = roundedCorners,
                    size = if (compactLayout) 44.dp else 56.dp,
                )
                Spacer(Modifier.width(if (compactLayout) s.sm else s.md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = if (compactLayout) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = FontWeight.Medium,
                    maxLines = if (!compact && pathHint == null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AlbumListRowSubtitle(
                    subtitle = subtitle,
                    imageCount = imageCount,
                    videoCount = videoCount,
                    compactLayout = compactLayout,
                    badgeSubtitle = badgeSubtitle,
                    selected = selected,
                )
                if (!pathHint.isNullOrEmpty()) {
                    Text(
                        pathHint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
            }
            AlbumListRowTrailing(
                selected = selected,
                showChevron = showChevron,
                dense = dense,
                compactLayout = compactLayout,
            )
        }
    }
}

@Composable
private fun albumSpokenDescription(
    name: String,
    subtitle: String,
    pathHint: String?,
    imageCount: Int,
    videoCount: Int,
): String {
    val spokenCount = when {
        imageCount > 0 && videoCount > 0 -> {
            val imgs = pluralStringResource(R.plurals.image_count_short_plural, imageCount, imageCount)
            val vids = pluralStringResource(R.plurals.video_count_short_plural, videoCount, videoCount)
            "$imgs, $vids"
        }
        imageCount > 0 -> pluralStringResource(R.plurals.image_count_short_plural, imageCount, imageCount)
        videoCount > 0 -> pluralStringResource(R.plurals.video_count_short_plural, videoCount, videoCount)
        else -> subtitle
    }
    return buildString {
        append(name)
        if (spokenCount.isNotEmpty()) append(", ").append(spokenCount)
        if (!pathHint.isNullOrEmpty()) append(", ").append(pathHint)
    }
}

@Composable
private fun AlbumListRowSubtitle(
    subtitle: String,
    imageCount: Int,
    videoCount: Int,
    compactLayout: Boolean,
    badgeSubtitle: Boolean,
    selected: Boolean,
) {
    when {
        imageCount > 0 || videoCount > 0 -> {
            val containerWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
            MediaTypeCountSummary(imageCount, videoCount, compact = compactLayout || containerWidthDp < 420.dp)
        }
        subtitle.isNotEmpty() && badgeSubtitle -> ItemCountBadge(subtitle, selected = selected)
        subtitle.isNotEmpty() -> Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlbumListRowTrailing(
    selected: Boolean,
    showChevron: Boolean,
    dense: Boolean,
    compactLayout: Boolean,
) {
    val s = LocalSpacing.current
    when {
        selected -> Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(s.sm))
            Icon(
                Icons.Default.Check,
                stringResource(R.string.cd_selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (compactLayout) 20.dp else 24.dp),
            )
        }
        showChevron && !dense -> Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(s.xs))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Leading cover: image, video frame, or a folder-icon placeholder when no cover is available. */
@Composable
private fun AlbumCover(
    coverPath: String,
    showThumbnail: Boolean,
    roundedCorners: Boolean = true,
    size: Dp = 56.dp,
) {
    val shape = if (roundedCorners) RoundedCornerShape(Radius.sm) else RoundedCornerShape(0.dp)
    val hasThumb = showThumbnail && coverPath.isNotEmpty()
    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (hasThumb) {
            val isVideo = coverPath.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
            if (isVideo) {
                VideoThumbnail(
                    videoPath = coverPath,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    thumbnailSize = 160,
                )
            } else {
                GalleryImage(
                    path = coverPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholderIconSize = 16.dp,
                    thumbnailSize = 160,
                )
            }
        } else {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
