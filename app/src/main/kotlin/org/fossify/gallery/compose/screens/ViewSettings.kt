package org.fossify.gallery.compose.screens

import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupOrder

enum class ViewType(val value: Int) {
    GRID(0), LIST(1), MOSAIC(2);

    companion object {
        fun from(value: Int) = entries.find { it.value == value } ?: GRID
    }
}

enum class SortField(val value: Int) {
    NAME(0), DATE(1), SIZE(2), RATING(3), COUNT(4);

    companion object {
        fun from(value: Int) = entries.find { it.value == value } ?: DATE
    }
}

/** The one grouping that actually matches each sort order - grouping is never offered as a free
 * choice independent of sorting (see ViewSettingsSheet's single "Gruppierung" toggle), so a sorted-
 * by-size list always groups into size buckets, never into e.g. month headers. COUNT (folder item
 * count) has no matching bucket scheme, since it's a folder-only sort and folders never group. */
fun SortField.autoGroupBy(): GroupBy = when (this) {
    SortField.NAME -> GroupBy.ALPHABET
    SortField.DATE -> GroupBy.MONTH
    SortField.SIZE -> GroupBy.SIZE
    SortField.RATING -> GroupBy.RATING
    SortField.COUNT -> GroupBy.NONE
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
    val anchorBottom: Boolean = false,
    // MONTH by default, not NONE - FolderMediaScreen/Favorites already always showed month headers
    // before this setting existed, so defaulting to NONE would be a silent behavior regression for
    // every existing user until they opened this sheet and picked Monat back.
    val groupBy: GroupBy = GroupBy.MONTH,
    val groupOrder: GroupOrder = GroupOrder.ALPHABETICAL,
    val onlyTopLevelTags: Boolean = false,
) {
    /** Coerces a loaded/persisted [groupBy] back onto [SortField.autoGroupBy] whenever it drifted
     * from the current [sortBy] (e.g. a value saved before grouping followed sorting, or before
     * [GroupBy.SIZE]/[GroupBy.ALPHABET] existed at all). NONE (grouping off) and TAG (the one
     * explicit override) are left alone - only a stale "auto" value gets corrected. */
    fun sanitizeGrouping(): ViewSettings {
        if (groupBy == GroupBy.NONE || groupBy == GroupBy.TAG) return this
        val auto = sortBy.autoGroupBy()
        return if (groupBy == auto) this else copy(groupBy = auto)
    }
}
