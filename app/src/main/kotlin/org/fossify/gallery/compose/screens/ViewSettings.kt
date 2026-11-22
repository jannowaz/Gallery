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
    NAME(0), DATE(1), SIZE(2), RATING(3), COUNT(4), RECENT(5);

    companion object {
        fun from(value: Int) = entries.find { it.value == value } ?: DATE
    }
}

/** Legacy migration helper for callers that need a sensible default grouping. New settings keep
 * grouping independent from the within-group sort field. */
fun SortField.autoGroupBy(): GroupBy = when (this) {
    SortField.NAME -> GroupBy.ALPHABET
    SortField.DATE -> GroupBy.MONTH
    SortField.SIZE -> GroupBy.SIZE
    SortField.RATING -> GroupBy.RATING
    SortField.COUNT -> GroupBy.NONE
    SortField.RECENT -> GroupBy.NONE
}

enum class DisplayMode(val value: Int) {
    COMPACT(0), NORMAL(1), DARK(2);

    companion object {
        fun from(value: Int) = entries.find { it.value == value } ?: NORMAL
    }
}

/** Density for the single-column list layout. Columns belong to grids/mosaics; list rows stay
 * readable and use density instead of pretending that a grid column count controls their width. */
enum class ListDensity(val value: Int) {
    COMFORTABLE(0), COMPACT(1);

    companion object {
        fun from(value: Int) = entries.find { it.value == value } ?: COMFORTABLE
    }
}

enum class SettingsMode { ALBUMS, MEDIA }

/** The content contract of a screen that consumes [ViewSettings]. Keeping this explicit prevents
 * a setting saved by one surface from silently becoming a meaningless value on another surface. */
enum class ViewSettingsSurface(
    val supportsMosaic: Boolean,
    val supportsGrouping: Boolean,
    val supportsTagGrouping: Boolean,
) {
    MEDIA(supportsMosaic = true, supportsGrouping = true, supportsTagGrouping = false),
    COLLECTION_MEDIA(supportsMosaic = true, supportsGrouping = true, supportsTagGrouping = true),
    ALBUMS(supportsMosaic = false, supportsGrouping = false, supportsTagGrouping = false),
    EXPLORER_ALBUMS(supportsMosaic = false, supportsGrouping = false, supportsTagGrouping = false),
    EXPLORER_MEDIA(supportsMosaic = true, supportsGrouping = true, supportsTagGrouping = true),
    FOLDER_MEDIA(supportsMosaic = true, supportsGrouping = true, supportsTagGrouping = true),
    FAVORITES(supportsMosaic = true, supportsGrouping = true, supportsTagGrouping = true),
    COLLECTIONS(supportsMosaic = false, supportsGrouping = false, supportsTagGrouping = false),
    TAGS(supportsMosaic = false, supportsGrouping = false, supportsTagGrouping = false),
}

fun sortFieldsFor(surface: ViewSettingsSurface): List<SortField> = when (surface) {
    ViewSettingsSurface.MEDIA,
    ViewSettingsSurface.COLLECTION_MEDIA,
    ViewSettingsSurface.EXPLORER_MEDIA,
    ViewSettingsSurface.FOLDER_MEDIA,
    ViewSettingsSurface.FAVORITES,
    -> listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.RATING)

    ViewSettingsSurface.ALBUMS,
    ViewSettingsSurface.EXPLORER_ALBUMS,
    -> listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.COUNT)

    ViewSettingsSurface.COLLECTIONS -> listOf(SortField.NAME, SortField.COUNT)
    ViewSettingsSurface.TAGS -> listOf(SortField.NAME, SortField.COUNT, SortField.RECENT)
}

/** Removes values that the target surface cannot render or apply. This also repairs settings
 * created by older app versions, so every visible choice in the shared sheet is selected. */
fun ViewSettings.sanitizeFor(surface: ViewSettingsSurface): ViewSettings {
    val supportedSortFields = sortFieldsFor(surface)
    val safeGroupBy = when {
        !surface.supportsGrouping -> GroupBy.NONE
        groupBy == GroupBy.TAG && !surface.supportsTagGrouping -> GroupBy.MONTH
        else -> groupBy
    }
    return copy(
        viewType = if (!surface.supportsMosaic && viewType == ViewType.MOSAIC) ViewType.GRID else viewType,
        sortBy = if (sortBy in supportedSortFields) sortBy else supportedSortFields.first(),
        groupBy = safeGroupBy,
        onlyTopLevelTags = onlyTopLevelTags && safeGroupBy == GroupBy.TAG,
    )
}

data class ViewSettings(
    val viewType: ViewType = ViewType.GRID,
    val columnCount: Int = 4,
    val displayMode: DisplayMode = DisplayMode.NORMAL,
    val showFileNames: Boolean = false,
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
    val listDensity: ListDensity = ListDensity.COMFORTABLE,
) {
    /** Kept as a source-compatible migration hook. Grouping is intentionally independent now. */
    fun sanitizeGrouping(): ViewSettings = this
}
