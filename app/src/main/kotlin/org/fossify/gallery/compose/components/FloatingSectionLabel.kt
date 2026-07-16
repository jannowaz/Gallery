package org.fossify.gallery.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.Radius

/** Grid/mosaic layouts render section headers (month, tag or rating) as plain scrolling items -
 * LazyVerticalGrid/LazyVerticalStaggeredGrid have no stickyHeader API, unlike LazyColumn's list
 * branch, which gets real sticky headers - so mid-scroll there was no way to tell which section
 * (or, for a nested tag hierarchy, which breadcrumb path) you were looking at. This renders a
 * small floating pill overlay instead of a true pinned-item replacement, visible only while
 * actively scrolling. */
@Composable
fun FloatingSectionLabel(label: String?, isScrolling: Boolean, modifier: Modifier = Modifier) {
    // Only while actively scrolling, not permanently - it used to float for as long as any content
    // was loaded (including with a modal sheet open over the grid), duplicating the inline header
    // directly below it for no reason once the user had stopped moving.
    AnimatedVisibility(visible = label != null && isScrolling, modifier = modifier, enter = fadeIn(AppMotion.short), exit = fadeOut(AppMotion.short)) {
        Surface(shape = RoundedCornerShape(Radius.xl), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp, tonalElevation = 2.dp) {
            Text(
                label.orEmpty(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                // liveRegion so TalkBack announces the current section as it changes - this pill is
                // the only place that information exists (grid/mosaic have no sticky header, unlike
                // the list branch), so without it a screen-reader user scrolling the grid has no way
                // to tell which section (or tag-hierarchy branch) they're currently looking at.
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
