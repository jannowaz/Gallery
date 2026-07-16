package org.fossify.gallery.compose.util

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.fossify.gallery.R

/**
 * Guards a screen whose state is expensive to rebuild (a long storage/duplicate scan, a running
 * swipe session) against accidental exits: while [enabled], the first back press only shows a
 * "press again" toast and the second within the grace window actually leaves. Returns the guarded
 * exit action - wire it to the top-bar back arrow too, so both exit paths behave identically.
 */
@Composable
fun rememberDoubleBackGuard(enabled: Boolean, onExit: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var lastBackMs by remember { mutableLongStateOf(0L) }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnExit by rememberUpdatedState(onExit)
    val guarded = remember {
        {
            val now = System.currentTimeMillis()
            if (!currentEnabled || now - lastBackMs < GRACE_MS) {
                currentOnExit()
            } else {
                lastBackMs = now
                Toast.makeText(ctx, ctx.getString(R.string.back_again_to_exit), Toast.LENGTH_SHORT).show()
            }
        }
    }
    BackHandler(enabled = enabled) { guarded() }
    return guarded
}

private const val GRACE_MS = 2500L
