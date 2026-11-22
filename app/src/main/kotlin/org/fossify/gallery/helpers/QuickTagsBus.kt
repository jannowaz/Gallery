package org.fossify.gallery.helpers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Small process-local invalidation signal for the preference-backed quick-tag list.
 * Preferences are not observable by Compose, so without this a tag added in settings only showed
 * up after leaving and recreating the Media/Viewer screen.
 */
object QuickTagsBus {
    private val _version = MutableStateFlow(0L)
    val version = _version.asStateFlow()

    fun notifyChanged() {
        _version.update { it + 1L }
    }
}
