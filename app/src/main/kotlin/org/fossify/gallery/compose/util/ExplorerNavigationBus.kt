package org.fossify.gallery.compose.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A pending request to reveal a medium's containing folder in Explorer. A state flow is used on
 * purpose: the request can originate in the viewer, while Explorer's home screen is recreated
 * only after the viewer is popped from the navigation back stack.
 */
object ExplorerNavigationBus {
    private val _folderPath = MutableStateFlow<String?>(null)
    val folderPath = _folderPath.asStateFlow()

    fun open(folderPath: String) {
        if (folderPath.isNotBlank()) _folderPath.value = folderPath
    }

    fun consume(folderPath: String) {
        if (_folderPath.value == folderPath) _folderPath.value = null
    }
}
