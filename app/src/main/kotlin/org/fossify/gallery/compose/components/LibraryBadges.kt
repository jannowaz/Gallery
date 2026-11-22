package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import org.fossify.gallery.R

@Composable
private fun countBadgeContainerColor(tone: CountBadgeTone) = when (tone) {
    // Media type is useful context, so use the app's primary/tertiary pair rather than two nearby
    // secondary tones. On the test device the old secondary + tertiary combination rendered both
    // badges almost lilac; the primary/tertiary pair keeps images and videos distinct while still
    // following whatever dynamic palette Android provides.
    CountBadgeTone.IMAGE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    CountBadgeTone.VIDEO -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
    CountBadgeTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
    CountBadgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun countBadgeContentColor(tone: CountBadgeTone) = when (tone) {
    CountBadgeTone.IMAGE -> MaterialTheme.colorScheme.onPrimaryContainer
    CountBadgeTone.VIDEO -> MaterialTheme.colorScheme.onTertiaryContainer
    CountBadgeTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    CountBadgeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

enum class CountBadgeTone { NEUTRAL, IMAGE, VIDEO, PRIMARY }

/** Small, non-interactive badge used for counts across album-style tiles and rows. */
@Composable
fun ItemCountBadge(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    tone: CountBadgeTone = CountBadgeTone.NEUTRAL,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> countBadgeContainerColor(tone)
    }
    val foreground = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> countBadgeContentColor(tone)
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = background,
        contentColor = foreground,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Shows the two useful folder totals without another media query. The compact form keeps grid
 * captions narrow (icon + number); the full form is used in list rows and exposes the labels too.
 * The content description always contains the full localized meaning for TalkBack.
 */
@Composable
fun MediaTypeCountSummary(
    imageCount: Int,
    videoCount: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    ultraNarrow: Boolean = false,
) {
    if (imageCount <= 0 && videoCount <= 0) return
    val imageLabel = pluralStringResource(R.plurals.image_count_short_plural, imageCount, imageCount)
    val videoLabel = pluralStringResource(R.plurals.video_count_short_plural, videoCount, videoCount)
    val spokenSummary = when {
        imageCount > 0 && videoCount > 0 -> "$imageLabel, $videoLabel"
        imageCount > 0 -> imageLabel
        else -> videoLabel
    }
    Row(
        modifier = modifier.semantics { contentDescription = spokenSummary },
        horizontalArrangement = Arrangement.spacedBy(if (ultraNarrow) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ultraNarrow && imageCount > 0 && videoCount > 0) {
            // On 5-6 column tiles (~50-70dp wide), two side-by-side badges clip the video
            // count off-screen. Show one crisp combined total while preserving the full
            // image/video breakdown in semantics.
            ItemCountBadge(
                text = formatCompactCount(imageCount + videoCount),
                tone = CountBadgeTone.PRIMARY,
            )
        } else {
            if (imageCount > 0) TypeCountBadge(Icons.Default.PhotoLibrary, imageLabel, imageCount, CountBadgeTone.IMAGE, compact, ultraNarrow)
            if (videoCount > 0) TypeCountBadge(Icons.Default.Videocam, videoLabel, videoCount, CountBadgeTone.VIDEO, compact, ultraNarrow)
        }
    }
}

private val integerNumberFormat: ThreadLocal<java.text.NumberFormat> =
    ThreadLocal.withInitial { java.text.NumberFormat.getIntegerInstance() }

internal fun formatIntegerCount(count: Int): String =
    integerNumberFormat.get()?.format(count) ?: count.toString()

internal fun formatCompactCount(count: Int): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0).replace(",0", "").replace(".0", "")
    count >= 1_000 -> "%.1fk".format(count / 1_000.0).replace(",0", "").replace(".0", "")
    else -> formatIntegerCount(count)
}

@Composable
private fun TypeCountBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    tone: CountBadgeTone,
    compact: Boolean,
    ultraNarrow: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = countBadgeContainerColor(tone),
        contentColor = countBadgeContentColor(tone),
    ) {
        Row(
            Modifier.padding(
                horizontal = when {
                    ultraNarrow -> 4.dp
                    compact -> 5.dp
                    else -> 7.dp
                },
                vertical = 2.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (ultraNarrow) 2.dp else 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (ultraNarrow) 11.dp else 13.dp))
            Text(
                text = when {
                    ultraNarrow -> formatCompactCount(count)
                    compact -> formatIntegerCount(count)
                    else -> label
                },
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
