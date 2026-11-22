package org.fossify.gallery.compose.screens

import org.fossify.gallery.helpers.GroupBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewSettingsTest {

    @Test
    fun `surface sort fields match the content shown by each tab`() {
        assertEquals(
            listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.RATING),
            sortFieldsFor(ViewSettingsSurface.MEDIA),
        )
        assertEquals(
            listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.COUNT),
            sortFieldsFor(ViewSettingsSurface.ALBUMS),
        )
        assertEquals(
            listOf(SortField.NAME, SortField.COUNT, SortField.RECENT),
            sortFieldsFor(ViewSettingsSurface.TAGS),
        )
    }

    @Test
    fun `sanitize repairs unsupported view sort and grouping values`() {
        val repaired = ViewSettings(
            viewType = ViewType.MOSAIC,
            sortBy = SortField.RATING,
            groupBy = GroupBy.TAG,
            onlyTopLevelTags = true,
        ).sanitizeFor(ViewSettingsSurface.ALBUMS)

        assertEquals(ViewType.GRID, repaired.viewType)
        assertEquals(SortField.NAME, repaired.sortBy)
        assertEquals(GroupBy.NONE, repaired.groupBy)
        assertFalse(repaired.onlyTopLevelTags)
    }

    @Test
    fun `media without tag grouping falls back to month grouping`() {
        val repaired = ViewSettings(groupBy = GroupBy.TAG, onlyTopLevelTags = true)
            .sanitizeFor(ViewSettingsSurface.MEDIA)

        assertEquals(GroupBy.MONTH, repaired.groupBy)
        assertTrue(repaired.onlyTopLevelTags.not())
    }

    @Test
    fun `default view settings keep media grids quiet without filename captions`() {
        assertFalse(ViewSettings().showFileNames)
    }

    @Test
    fun `collections and explorer surfaces expose only meaningful sort fields`() {
        assertEquals(
            listOf(SortField.NAME, SortField.COUNT),
            sortFieldsFor(ViewSettingsSurface.COLLECTIONS),
        )
        assertEquals(
            listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.COUNT),
            sortFieldsFor(ViewSettingsSurface.EXPLORER_ALBUMS),
        )
        assertEquals(
            listOf(SortField.NAME, SortField.DATE, SortField.SIZE, SortField.RATING),
            sortFieldsFor(ViewSettingsSurface.EXPLORER_MEDIA),
        )
    }

    @Test
    fun `collection media preserves tag grouping and top-level tag toggle`() {
        val kept = ViewSettings(
            viewType = ViewType.MOSAIC,
            sortBy = SortField.RATING,
            groupBy = GroupBy.TAG,
            onlyTopLevelTags = true,
        ).sanitizeFor(ViewSettingsSurface.COLLECTION_MEDIA)

        assertEquals(ViewType.MOSAIC, kept.viewType)
        assertEquals(SortField.RATING, kept.sortBy)
        assertEquals(GroupBy.TAG, kept.groupBy)
        assertTrue(kept.onlyTopLevelTags)
    }
}
