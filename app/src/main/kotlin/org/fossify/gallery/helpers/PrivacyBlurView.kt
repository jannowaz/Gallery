package org.fossify.gallery.helpers

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * View-level equivalent of Compose's `privacyBlur()` modifier, for the legacy (non-Compose)
 * grid/viewer/video-player screens - those are still reachable via external "open with Gallery"
 * intents even though Compose is the primary UI, and previously ignored "blur all media" entirely.
 * `RenderEffect.createBlurEffect` is API 31+ only, the same limitation `Modifier.blur()` has;
 * below that this fades the view down to near-invisible instead of silently doing nothing.
 */
fun View.applyPrivacyBlur(enabled: Boolean, radiusPx: Float = 50f) {
    if (!enabled) {
        setRenderEffect(null)
        alpha = 1f
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setRenderEffect(RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP))
    } else {
        alpha = 0.04f
    }
}
