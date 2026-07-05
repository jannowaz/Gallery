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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.util.selectableItem
import org.fossify.gallery.compose.util.throttledBoundsReporting
import org.fossify.gallery.models.Medium

/**
 * Single row used by the list view (both the paged, unfiltered browse and the filtered/override
 * path) - thumbnail, name/size, an optional preview button while selecting, and a selection
 * checkmark. Kept in one place so the two call sites in MediaScreen can't drift from each other.
 */
@Composable
fun MediaListRow(
    medium: Medium,
    isVideo: Boolean,
    isSelected: Boolean,
    hasSelection: Boolean,
    cardColor: Color,
    fileSizeLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToSelect: () -> Unit,
    onPreview: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth()
            .background(cardColor, RoundedCornerShape(Radius.sm))
            .throttledBoundsReporting(onBoundsChanged = onBoundsChanged)
            .selectableItem(isSelectionMode = hasSelection, onClick = onClick, onLongClick = onLongClick, onSwipeToSelect = onSwipeToSelect),
        color = Color.Transparent,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(Radius.sm))) {
                if (isVideo) VideoThumbnail(videoPath = medium.path, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else GalleryImage(path = medium.path, contentDescription = medium.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 18.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(medium.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fileSizeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (hasSelection) {
                IconButton(onClick = onPreview, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Visibility, stringResource(R.string.cd_preview), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            if (isSelected) Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
