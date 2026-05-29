package org.fossify.gallery.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import org.fossify.gallery.helpers.MediaRepository

val LocalMediaRepository = staticCompositionLocalOf<MediaRepository> {
    error("No MediaRepository provided")
}

@Composable
fun AppProviders(repository: MediaRepository, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMediaRepository provides repository) {
        content()
    }
}
