package org.fossify.gallery.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Home

@Serializable
data class Folder(val folderPath: String)

@Serializable
data object Settings

@Serializable
data object ManageCollections

@Serializable
data object About

@Serializable
data object TagBrowser

@Serializable
data object StorageAnalysis

@Serializable
data class DuplicateFinder(val folderPath: String = "")

@Serializable
data object FoldersMover

@Serializable
data object RecycleBin

@Serializable
data class Viewer(val startIndex: Int)

/**
 * Transient holder for the viewer's media list. Passing the full list through the typed route
 * serialises every path into the destination URL, which overflows and fails to match for large
 * libraries (IllegalArgumentException: destination ... cannot be found in the navigation graph).
 */
object ViewerArgs {
    var paths: List<String> = emptyList()
}
