package org.fossify.gallery.compose.util

import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Compose-observable picture-in-picture state, mirroring [BlurState]'s pattern: the activity owns
 * the actual PiP transitions, VideoPage reports what's playing, and the viewer chrome reads
 * [inPip] to get out of the way inside the tiny window.
 */
object PipState {

    /** True while the activity is actually in picture-in-picture mode. */
    var inPip by mutableStateOf(false)

    /** Aspect ratio of the video currently playing in the viewer (already clamped to the
     * platform's allowed PiP range), null when nothing is playing. Non-null is what arms the
     * activity's home-press PiP entry. */
    var activeVideoAspect: Rational? by mutableStateOf(null)
}
