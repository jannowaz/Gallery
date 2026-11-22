package org.fossify.gallery.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.AppMotion

/**
 * Small, non-layout-shifting progress feedback for a refresh that keeps existing content visible.
 * A full-screen loader is appropriate for the first load, but replacing a usable list during a
 * refresh makes scrolling and tab round-trips feel broken. Keeping this primitive shared makes
 * the same distinction on every library screen.
 */
@Composable
fun InlineRefreshIndicator(
    visible: Boolean,
    contentAvailable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val refreshDescription = stringResource(R.string.scanning_short)
    AnimatedVisibility(
        visible = visible && contentAvailable,
        enter = fadeIn(AppMotion.short),
        exit = fadeOut(AppMotion.short),
        modifier = modifier,
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = refreshDescription
                },
        )
    }
}
