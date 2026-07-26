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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import java.io.File

/**
 * The one list row used by every folder-style list in the app - Albums, Collections, Favorites and
 * the Explorer's folder section - so they read as a single, consistent list instead of four
 * slightly different ones (the Explorer used to hand-roll its own near-identical variant).
 *
 * Material list-item shape: a leading cover thumbnail, a two-line title/subtitle block that takes
 * the remaining width, and a trailing affordance - a check when selected, otherwise an optional
 * chevron for rows that navigate. Grid and mosaic layouts are unaffected; this is the LIST view only.
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
) {
    val s = LocalSpacing.current
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    AppCard(modifier = modifier.fillMaxWidth(), color = container) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (dense) s.sm else s.md, vertical = s.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumCover(coverPath = coverPath, showThumbnail = showThumbnail, size = if (dense) 40.dp else 56.dp)
            Spacer(Modifier.width(if (dense) s.sm else s.md))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (dense) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                selected -> {
                    Spacer(Modifier.width(s.sm))
                    Icon(Icons.Default.Check, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (dense) 20.dp else 24.dp))
                }
                showChevron && !dense -> {
                    Spacer(Modifier.width(s.xs))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Leading cover: image, video frame, or a folder-icon placeholder for empty/thumbnail-off. */
@Composable
private fun AlbumCover(coverPath: String, showThumbnail: Boolean, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val shape = RoundedCornerShape(Radius.sm)
    val hasThumb = showThumbnail && coverPath.isNotEmpty() && File(coverPath).exists()
    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        if (hasThumb) {
            val isVideo = coverPath.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
            if (isVideo) {
                VideoThumbnail(videoPath = coverPath, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                GalleryImage(path = coverPath, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 16.dp, thumbnailSize = 160)
            }
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
            }
        }
    }
}
