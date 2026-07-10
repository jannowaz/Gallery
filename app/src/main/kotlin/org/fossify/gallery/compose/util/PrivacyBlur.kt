package org.fossify.gallery.compose.util

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * `Modifier.blur()` is backed by `RenderEffect`, which is silently a no-op below API 31 (per
 * Compose UI's own doc comment on `blur()`) - so on API 26-30 (this app's `minSdk`), turning on
 * "blur all media" did nothing at all, with zero indication to the user that the real, unblurred
 * media was still on screen. Below API 31 this instead draws an opaque scrim over the content -
 * not a soft blur, but a privacy-safe full censor, which is an intentional, honest degrade rather
 * than a silent failure.
 */
fun Modifier.privacyBlur(radius: Dp, enabled: Boolean): Modifier = when {
    !enabled -> this
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> this.blur(radius)
    else -> this.drawWithContent { drawContent(); drawRect(Color.Black) }
}

/**
 * `privacyBlur()` above is a real `RenderEffect` baked into the rendered frame, so it already
 * carries correctly into a task-switcher/recents snapshot - that part isn't a leak. What isn't
 * covered by it is everything *around* the media (filenames, tags, ratings, folder names, app
 * bars), which stays fully legible in that same snapshot. This closes that specific gap: the
 * instant the hosting Activity is paused (recents snapshots are captured around this point), and
 * only while "blur all media" is on, it covers the *entire* window with a solid scrim - not just
 * the media. This never affects a deliberate foreground screenshot (e.g. to verify/share a blurred
 * view), since taking one doesn't pause the Activity; it only fires on a real backgrounding
 * transition (home, recents, another app/dialog taking focus).
 */
@Composable
fun PrivacyPauseScrim() {
    val lifecycleOwner = LocalContext.current as? LifecycleOwner
    var paused by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> paused = true
                Lifecycle.Event.ON_RESUME -> paused = false
                else -> {}
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    // No enter animation - a recents snapshot can be taken a frame or two after ON_PAUSE, so the
    // cover needs to already be opaque by then, not mid-fade-in. Exit fades since that direction is
    // only ever seen live (the user has already resumed the app by the time it plays).
    AnimatedVisibility(visible = paused && BlurState.enabled, enter = EnterTransition.None, exit = fadeOut(tween(150))) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }
}
