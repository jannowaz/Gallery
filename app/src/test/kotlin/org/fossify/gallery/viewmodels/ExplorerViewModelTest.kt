package org.fossify.gallery.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure state-transition logic backing [ExplorerViewModel]'s filter setters
 * ([ExplorerUiState.hasActiveFilter], [ExplorerUiState.withTagToggled], [ExplorerUiState.withFiltersCleared]).
 * Exercised directly against [ExplorerUiState] (a plain data class) instead of through the
 * ViewModel itself - [ExplorerViewModel] is an AndroidViewModel whose init{} touches real Android
 * APIs (Environment, SharedPreferences-backed Config) that aren't available in a plain JVM unit
 * test without Robolectric, but every filter-relevant transition lives in these pure functions.
 */
class ExplorerViewModelTest {

    @Test
    fun `hasActiveFilter is false for the default state`() {
        assertFalse(ExplorerUiState().hasActiveFilter)
    }

    @Test
    fun `hasActiveFilter is true when rating filter is set`() {
        assertTrue(ExplorerUiState(activeRatingFilter = 3).hasActiveFilter)
    }

    @Test
    fun `hasActiveFilter is true when tag filter is set`() {
        assertTrue(ExplorerUiState(activeTagFilter = setOf("beach")).hasActiveFilter)
    }

    @Test
    fun `hasActiveFilter is true when path filter is set`() {
        assertTrue(ExplorerUiState(activePathFilter = setOf("/sdcard/DCIM")).hasActiveFilter)
    }

    @Test
    fun `hasActiveFilter is true when min size filter is set`() {
        assertTrue(ExplorerUiState(activeMinSizeFilter = 1_000_000L).hasActiveFilter)
    }

    @Test
    fun `hasActiveFilter is true when date range filter is set`() {
        assertTrue(ExplorerUiState(activeDateRangeFilter = 2).hasActiveFilter)
    }

    @Test
    fun `hasActiveFilter is true when type filter is set`() {
        assertTrue(ExplorerUiState(activeTypeFilter = 1).hasActiveFilter)
    }

    @Test
    fun `withTagToggled adds a tag and expands its descendants`() {
        val hierarchy = mapOf("berlin" to "places") // child -> parent
        val state = ExplorerUiState().withTagToggled("places", hierarchy)
        assertEquals(setOf("places"), state.activeTagFilterRaw)
        assertEquals(setOf("places", "berlin"), state.activeTagFilter)
        assertEquals("places", state.activeTagName)
    }

    @Test
    fun `withTagToggled accumulates multiple raw tags`() {
        val state = ExplorerUiState()
            .withTagToggled("beach", emptyMap())
            .withTagToggled("sunset", emptyMap())
        assertEquals(setOf("beach", "sunset"), state.activeTagFilterRaw)
        assertEquals(setOf("beach", "sunset"), state.activeTagFilter)
        assertEquals("beach, sunset", state.activeTagName)
    }

    @Test
    fun `withTagToggled removes a tag already selected`() {
        val state = ExplorerUiState()
            .withTagToggled("beach", emptyMap())
            .withTagToggled("sunset", emptyMap())
            .withTagToggled("beach", emptyMap())
        assertEquals(setOf("sunset"), state.activeTagFilterRaw)
        assertEquals(setOf("sunset"), state.activeTagFilter)
    }

    @Test
    fun `withTagToggled clears tag filter fields once the last raw tag is removed`() {
        val state = ExplorerUiState()
            .withTagToggled("beach", emptyMap())
            .withTagToggled("beach", emptyMap())
        assertEquals(emptySet<String>(), state.activeTagFilterRaw)
        assertNull(state.activeTagFilter)
        assertNull(state.activeTagName)
    }

    @Test
    fun `withFiltersCleared resets every filter field including newly added dimensions`() {
        val dirty = ExplorerUiState(
            activeRatingFilter = 5,
            activeTagFilter = setOf("beach"),
            activeTagName = "beach",
            activeTagFilterRaw = setOf("beach"),
            activePathFilter = setOf("/sdcard/DCIM"),
            activeExcludePathFilter = setOf("/sdcard/Screenshots"),
            activeMinSizeFilter = 5_000_000L,
            activeDateRangeFilter = 3,
            activeTypeFilter = 2,
            activePathName = "search query",
            activeCollectionName = "Vacation",
            preFilterTab = 3,
        )

        val cleared = dirty.withFiltersCleared()

        assertFalse(cleared.hasActiveFilter)
        assertEquals(0, cleared.activeRatingFilter)
        assertNull(cleared.activeTagFilter)
        assertNull(cleared.activeTagName)
        assertEquals(emptySet<String>(), cleared.activeTagFilterRaw)
        assertNull(cleared.activePathFilter)
        assertNull(cleared.activeExcludePathFilter)
        assertEquals(0L, cleared.activeMinSizeFilter)
        assertEquals(0, cleared.activeDateRangeFilter)
        assertEquals(0, cleared.activeTypeFilter)
        assertNull(cleared.activePathName)
        assertNull(cleared.activeCollectionName)
        assertEquals(-1, cleared.preFilterTab)
    }

    @Test
    fun `withFiltersCleared does not touch unrelated state`() {
        val dirty = ExplorerUiState(selectedTab = 4, explorerPath = "/sdcard", mediaRefreshTrigger = 7, lastViewedPath = "/sdcard/foo.jpg")
        val cleared = dirty.withFiltersCleared()
        assertEquals(4, cleared.selectedTab)
        assertEquals("/sdcard", cleared.explorerPath)
        assertEquals(7, cleared.mediaRefreshTrigger)
        assertEquals("/sdcard/foo.jpg", cleared.lastViewedPath)
    }
}
