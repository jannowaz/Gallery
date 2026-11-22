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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
 * slightly different ones (the Explorer used to hand-roll its own near-identical variant).
 *
 * Material list-item shape: a leading cover thumbnail, a title/subtitle block that takes the
 * remaining width, and a trailing affordance - a check when selected, otherwise an optional chevron
 * for rows that navigate. Folder rows may also carry a faint [pathHint] third line. Grid and mosaic
 * layouts are unaffected; this is the LIST view only.
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
    // Two-columns-side-by-side layout (see LibraryAlbumGrid): each row is only half the width, so
    // the cover shrinks, the chevron is dropped and the name may wrap to two lines - without this
    // the name gets clipped to "Zu..." with no room to read it.
    dense: Boolean = false,
    // Single-column compact mode keeps the row readable while reducing vertical rhythm. Unlike
    // dense two-column rows it keeps the trailing navigation affordance.
    compact: Boolean = false,
    badgeSubtitle: Boolean = false,
    // Faint path line for folder rows (see folderPathHint). When present the name stays on one line
    // so the extra line doesn't stack with a two-line name; null on containers (Collections/Tags).
    pathHint: String? = null,
    imageCount: Int = 0,
    videoCount: Int = 0,
) {
    val s = LocalSpacing.current
    val compactLayout = dense || compact
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val spokenCount = when {
        imageCount > 0 && videoCount > 0 -> "${pluralStringResource(R.plurals.image_count_short_plural, imageCount, imageCount)}, ${pluralStringResource(R.plurals.video_count_short_plural, videoCount, videoCount)}"
        imageCount > 0 -> pluralStringResource(R.plurals.image_count_short_plural, imageCount, imageCount)
        videoCount > 0 -> pluralStringResource(R.plurals.video_count_short_plural, videoCount, videoCount)
        else -> subtitle
    }
    val isItemSelected = selected
    AppCard(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "$name, $spokenCount"
            role = Role.Button
            this.selected = isItemSelected
        },
        color = container,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (compactLayout) s.sm else s.md, vertical = if (compactLayout) s.xs else s.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Preview off also removes the list's leading slot; titles stay aligned with compact
            // grid cards instead of following a blank placeholder.
            if (showThumbnail) {
                AlbumCover(coverPath = coverPath, showThumbnail = true, size = if (compactLayout) 44.dp else 56.dp)
                Spacer(Modifier.width(if (compactLayout) s.sm else s.md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = if (compactLayout) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    // Give regular rows room for long folder/collection names. Folder rows that
                    // also show a path keep one line so the count and path remain visually aligned.
                    maxLines = if (!compact && pathHint == null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (imageCount > 0 || videoCount > 0) {
                    val containerWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
                    val useCompactSummary = compactLayout || containerWidthDp < 420.dp
                    MediaTypeCountSummary(imageCount, videoCount, compact = useCompactSummary)
                } else if (subtitle.isNotEmpty()) {
                    if (badgeSubtitle) {
                        ItemCountBadge(subtitle, selected = selected)
                    } else {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
            when {
                selected -> {
                    Spacer(Modifier.width(s.sm))
                    Icon(Icons.Default.Check, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (compactLayout) 20.dp else 24.dp))
                }
                showChevron && !dense -> {
                    Spacer(Modifier.width(s.xs))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Leading cover: image, video frame, or a folder-icon placeholder when no cover is available. */
@Composable
private fun AlbumCover(coverPath: String, showThumbnail: Boolean, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val shape = RoundedCornerShape(Radius.sm)
    // Avoid a synchronous disk stat for every row while an Explorer/Albums list is composing.
    // GalleryImage and VideoThumbnail both expose their own placeholder/error state.
    val hasThumb = showThumbnail && coverPath.isNotEmpty()
    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (hasThumb) {
            val isVideo = coverPath.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
            if (isVideo) {
                VideoThumbnail(videoPath = coverPath, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, thumbnailSize = 160)
            } else {
                GalleryImage(path = coverPath, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 16.dp, thumbnailSize = 160)
            }
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f), modifier = Modifier.size(24.dp))
            }
        }
    }
}
