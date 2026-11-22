package org.fossify.gallery.compose.components

import org.fossify.gallery.compose.screens.ListDensity
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.compose.screens.ViewSettings
import org.fossify.gallery.compose.screens.ViewType
import org.fossify.gallery.helpers.GroupBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ViewSettingsSummaryTest {

    private val sortLabels = mapOf(
        SortField.NAME to "Name",
        SortField.DATE to "Date",
        SortField.SIZE to "Size",
        SortField.RATING to "Rating",
        SortField.COUNT to "Items",
        SortField.RECENT to "Recent",
    )

    private val viewLabels = mapOf(
        ViewType.GRID to "Tiles",
        ViewType.LIST to "List",
        ViewType.MOSAIC to "Mosaic",
    )

    private val groupLabels = mapOf(
        GroupBy.MONTH to "Month",
        GroupBy.TAG to "Tags",
        GroupBy.RATING to "Rating",
        GroupBy.SIZE to "Size",
        GroupBy.ALPHABET to "A–Z",
        GroupBy.NONE to "",
    )

    private fun format(settings: ViewSettings, includeGrouping: Boolean = true): String =
        formatViewSettingsSummary(
            settings = settings,
            includeGrouping = includeGrouping,
            sortLabel = { sortLabels.getValue(it) },
            viewLabel = { viewLabels.getValue(it) },
            groupLabel = { groupLabels.getValue(it) },
        )

    @Test
    fun `default grid summary omits redundant column count and tiles label`() {
        val summary = format(
            ViewSettings(
                viewType = ViewType.GRID,
                columnCount = 4,
                sortBy = SortField.DATE,
                sortDesc = true,
                groupBy = GroupBy.MONTH,
            ),
        )
        assertEquals("Date ↓ · Month", summary)
        assertFalse(summary.contains("4"))
        assertFalse(summary.contains("Tiles"))
    }

    @Test
    fun `list and mosaic views include non-default layout name without verbose density`() {
        val listSummary = format(
            ViewSettings(
                viewType = ViewType.LIST,
                listDensity = ListDensity.COMPACT,
                sortBy = SortField.NAME,
                sortDesc = false,
                groupBy = GroupBy.NONE,
            ),
        )
        assertEquals("Name ↑ · List", listSummary)

        val mosaicSummary = format(
            ViewSettings(
                viewType = ViewType.MOSAIC,
                sortBy = SortField.SIZE,
                sortDesc = true,
                groupBy = GroupBy.TAG,
            ),
        )
        assertEquals("Size ↓ · Mosaic · Tags", mosaicSummary)
    }

    @Test
    fun `grouping is suppressed when includeGrouping is false`() {
        val summary = format(
            ViewSettings(
                viewType = ViewType.GRID,
                sortBy = SortField.COUNT,
                sortDesc = true,
                groupBy = GroupBy.MONTH,
            ),
            includeGrouping = false,
        )
        assertEquals("Items ↓", summary)
    }
}
