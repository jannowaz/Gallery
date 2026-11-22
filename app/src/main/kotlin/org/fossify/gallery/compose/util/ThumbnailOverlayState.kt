package org.fossify.gallery.compose.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.fossify.gallery.helpers.Config

/**
 * Snapshot-state mirror of the two "thumbnail overlay" config flags (show rating / show video
 * duration) so toggling them in the view sheet updates the media grids/lists immediately.
 *
 * These live in SharedPreferences, which isn't Compose-observable: reading `config.showRatingOnThumbnails`
 * straight inside a composable captures the value at composition time only, so a toggle didn't take
 * effect until the whole screen was recomposed from scratch (an app restart - even a bottom-tab switch
 * kept the retained tab composition, so nothing re-read the pref). The grids read these observable
 * values instead; the view sheet writes both the pref (persistence) and here (instant UI). Seeded once
 * from config at activity start via [sync].
 */
object ThumbnailOverlayState {
    var showRating by mutableStateOf(false)
    var showVideoDuration by mutableStateOf(true)
    var animateGifs by mutableStateOf(true)

    /** Load current persisted values into the observable state. Call once when the config is available. */
    fun sync(config: Config) {
        showRating = config.showRatingOnThumbnails
        showVideoDuration = config.showVideoDurationOnThumbnails
        animateGifs = config.animateGifs
    }
}
