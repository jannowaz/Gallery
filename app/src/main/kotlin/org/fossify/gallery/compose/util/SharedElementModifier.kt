package org.fossify.gallery.compose.util

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.fossify.gallery.navigation.LocalAnimatedVisibilityScope
import org.fossify.gallery.navigation.LocalSharedTransitionScope

// SharedTransitionScope.sharedElement's default boundsTransform is a spring, which settles on its
// own timeline - independent of (and slower than) the surrounding NavHost's fadeOut/fadeIn, both set
// to AppMotion.medium's duration/easing (GalleryNavHost's Viewer <-> Home pop transitions, 300ms
// FastOutSlowInEasing - can't reuse AppMotion.medium itself, it's typed TweenSpec<Float> for opacity,
// not FiniteAnimationSpec<Rect>). Without a matching explicit spec here, the fade would finish and
// make the Viewer transparent while the bounds morph was still catching up underneath - visually
// reading as "the image closes, then the thumbnail shrink plays" instead of one continuous motion.
// Tying both to the same duration/easing keeps them in lockstep.
@OptIn(ExperimentalSharedTransitionApi::class)
private val sharedBoundsTransform = BoundsTransform { _, _ -> tween(300, easing = FastOutSlowInEasing) }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementKey(key: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalAnimatedVisibilityScope.current
    val state = sharedScope.rememberSharedContentState(key = key)
    return with(sharedScope) {
        this@sharedElementKey.sharedElement(
            sharedContentState = state,
            animatedVisibilityScope = visibilityScope,
            boundsTransform = sharedBoundsTransform,
        )
    }
}
