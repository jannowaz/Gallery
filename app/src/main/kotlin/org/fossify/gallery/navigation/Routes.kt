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
data class Viewer(val paths: List<String>, val startIndex: Int)
