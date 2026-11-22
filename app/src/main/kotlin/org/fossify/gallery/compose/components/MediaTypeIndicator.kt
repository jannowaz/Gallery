package org.fossify.gallery.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim

/**
 * The two visual cues that make a video unmistakable on top of an arbitrary frame.
 * Keeping them here prevents Explorer, Media and folder views from slowly diverging.
 */
@Composable
fun VideoPlayOverlay(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
) {
    Box(
        modifier = modifier.size(size).background(Scrim.a35, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(size * 0.68f),
        )
    }
}

@Composable
fun VideoTypeBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        color = Scrim.a60,
        contentColor = Color.White,
        shape = RoundedCornerShape(Radius.xs),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 3.dp else 5.dp,
                vertical = if (compact) 1.dp else 2.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp),
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 10.dp else 12.dp),
            )
            Text(
                stringResource(R.string.media_type_video),
                style = MaterialTheme.typography.labelSmall,
                fontSize = if (compact) 8.sp else 10.sp,
                maxLines = 1,
            )
        }
    }
}
