package org.fossify.gallery.compose.util

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.fossify.gallery.navigation.LocalAnimatedVisibilityScope
import org.fossify.gallery.navigation.LocalSharedTransitionScope

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementKey(key: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalAnimatedVisibilityScope.current
    val state = sharedScope.rememberSharedContentState(key = key)
    return with(sharedScope) {
        this@sharedElementKey.sharedElement(
            state = state,
            animatedVisibilityScope = visibilityScope,
        )
    }
}
