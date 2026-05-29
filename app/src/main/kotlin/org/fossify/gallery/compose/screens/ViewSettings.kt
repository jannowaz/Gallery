package org.fossify.gallery.compose.screens

enum class ViewType(val value: Int) {
    GRID(0), LIST(1);

    companion object {
        fun from(value: Int) = entries.find { it.value == value } ?: GRID
    }
}

enum class SortField(val value: Int) {
    NAME(0), DATE(1), SIZE(2), RATING(3);

    companion object {
        fun from(value: Int) = entries.find { it.value == value } ?: DATE
    }
}

enum class DisplayMode(val value: Int) {
    COMPACT(0), NORMAL(1), DARK(2);

    companion object {
        fun from(value: Int) = entries.find { it.value == value } ?: NORMAL
    }
}

enum class SettingsMode { ALBUMS, MEDIA }

data class ViewSettings(
    val viewType: ViewType = ViewType.GRID,
    val columnCount: Int = 4,
    val displayMode: DisplayMode = DisplayMode.NORMAL,
    val showFileNames: Boolean = true,
    val roundedCorners: Boolean = true,
    val sortBy: SortField = SortField.DATE,
    val sortDesc: Boolean = true,
    val spacing: Int = 8,
    val showFolderThumbnails: Boolean = true,
)
