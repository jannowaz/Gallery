package org.fossify.gallery.viewmodels

import org.fossify.gallery.models.MediaReviewFilter

/**
 * UI-facing filter state shared by every surface that can show media.
 *
 * [MediaViewModel.MediaFilter] is the query model; this model additionally carries the display
 * labels and clear actions needed by the different screens. Keeping the shape here prevents
 * Favorites and Explorer from inventing their own partial filter parameter sets.
 */
data class LibraryFilterState(
    val rating: Int = 0,
    val tagNames: Set<String>? = null,
    val tagMatchAll: Boolean = false,
    val pathFilter: Set<String>? = null,
    val excludePathFilter: Set<String>? = null,
    val activeTagName: String? = null,
    val activePathName: String? = null,
    val activeCollectionName: String? = null,
    val activeFolderFilterName: String? = null,
    val additionalMediaPaths: Set<String> = emptySet(),
    val minSize: Long = 0L,
    val dateRange: Int = 0,
    val addedSince: Long = 0L,
    val typeFilter: Int = 0,
    val reviewFilter: MediaReviewFilter = MediaReviewFilter.NONE,
    val textQuery: String = "",
) {
    val isActive: Boolean
        get() = rating > 0 || tagNames != null || pathFilter != null || excludePathFilter != null ||
            activeTagName != null || activePathName != null || activeCollectionName != null ||
            activeFolderFilterName != null || additionalMediaPaths.isNotEmpty() || textQuery.isNotBlank() || minSize > 0L ||
            dateRange > 0 || addedSince > 0L || typeFilter > 0 || reviewFilter != MediaReviewFilter.NONE
}

/** Clear/update operations kept together so the same filter chips behave identically everywhere. */
data class LibraryFilterActions(
    val clearAll: () -> Unit = {},
    val clearRating: () -> Unit = {},
    val clearTags: () -> Unit = {},
    /** Removes one explicitly selected tag while keeping the rest of the filter intact. */
    val removeTag: (String) -> Unit = {},
    /** Turns off the “all selected tags” rule without discarding the selected tags. */
    val clearTagMatchAll: () -> Unit = {},
    val clearPath: () -> Unit = {},
    val clearSize: () -> Unit = {},
    val clearDate: () -> Unit = {},
    val clearAddedSince: () -> Unit = {},
    val clearType: () -> Unit = {},
    val clearReview: () -> Unit = {},
)

fun ExplorerUiState.toLibraryFilterState(): LibraryFilterState = LibraryFilterState(
    rating = activeRatingFilter,
    tagNames = activeTagFilter,
    tagMatchAll = activeTagMatchAll,
    pathFilter = activePathFilter,
    excludePathFilter = activeExcludePathFilter,
    activeTagName = activeTagName,
    activePathName = activePathName,
    activeCollectionName = activeCollectionName,
    activeFolderFilterName = activeFolderFilterName,
    additionalMediaPaths = activeCollectionMediaPaths,
    minSize = activeMinSizeFilter,
    dateRange = activeDateRangeFilter,
    addedSince = activeAddedSince,
    typeFilter = activeTypeFilter,
    reviewFilter = activeReviewFilter,
    textQuery = activeTextQuery,
)
