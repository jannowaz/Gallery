package org.fossify.gallery.compose.screens

import org.fossify.gallery.helpers.GroupSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedRowsLayoutTest {

    @Test
    fun `buildPagedRowsWithCollapsed replaces partial loaded count with exact SQL total and reinserts collapsed headers`() {
        val summaries = listOf(
            GroupSummary(key = "month:November 2026", sqlValue = "2026-11", label = "November 2026", count = 512),
            GroupSummary(key = "month:Oktober 2026", sqlValue = "2026-10", label = "Oktober 2026", count = 240),
            GroupSummary(key = "month:September 2026", sqlValue = "2026-09", label = "September 2026", count = 90),
        )
        // November was collapsed at SQL level, so Paging only loaded Oktober items (count=60 loaded so far).
        val loaded = listOf(
            PagedRow.Header(key = "month:Oktober 2026", label = "Oktober 2026", count = 60),
            PagedRow.Item(0),
            PagedRow.Item(1),
        )
        val merged = buildPagedRowsWithCollapsed(
            loadedRows = loaded,
            groupSummaries = summaries,
            collapsedKeys = setOf("month:November 2026"),
            endOfPaginationReached = false,
        )

        assertEquals(4, merged.size)
        val nov = merged[0] as PagedRow.Header
        assertEquals("November 2026", nov.label)
        assertEquals(512, nov.count)
        assertFalse(nov.isExpanded)

        val oct = merged[1] as PagedRow.Header
        assertEquals("Oktober 2026", oct.label)
        assertEquals(240, oct.count)
        assertTrue(oct.isExpanded)
    }

    @Test
    fun `buildPagedRowsWithCollapsed keeps all headers visible when every group is collapsed`() {
        val summaries = listOf(
            GroupSummary(key = "size:Groß", sqlValue = "3", label = "Groß", count = 12),
            GroupSummary(key = "size:Mittel", sqlValue = "2", label = "Mittel", count = 88),
        )
        val merged = buildPagedRowsWithCollapsed(
            loadedRows = emptyList(),
            groupSummaries = summaries,
            collapsedKeys = setOf("size:Groß", "size:Mittel"),
            endOfPaginationReached = true,
        )
        assertEquals(2, merged.size)
        assertTrue(merged.all { it is PagedRow.Header && !it.isExpanded })
    }

    @Test
    fun `orderPagedRowsForLayout moves each header after its items when reverseLayout is true`() {
        val h1 = PagedRow.Header(key = "m:1", label = "G1", count = 2)
        val i0 = PagedRow.Item(0)
        val i1 = PagedRow.Item(1)
        val h2 = PagedRow.Header(key = "m:2", label = "G2", count = 1)
        val i2 = PagedRow.Item(2)
        val rows = listOf(h1, i0, i1, h2, i2)

        assertEquals(rows, orderPagedRowsForLayout(rows, reverseLayout = false))
        assertEquals(listOf(i0, i1, h1, i2, h2), orderPagedRowsForLayout(rows, reverseLayout = true))
    }

    @Test
    fun `currentSectionLabel finds group header in both normal and reverseLayout directions`() {
        val h1 = PagedRow.Header(key = "m:1", label = "G1", count = 2)
        val i0 = PagedRow.Item(0)
        val i1 = PagedRow.Item(1)
        val h2 = PagedRow.Header(key = "m:2", label = "G2", count = 1)
        val i2 = PagedRow.Item(2)

        val normal = listOf(h1, i0, i1, h2, i2)
        assertEquals("G1", currentSectionLabel(normal, firstVisibleIndex = 2, reverseLayout = false))
        assertEquals("G2", currentSectionLabel(normal, firstVisibleIndex = 4, reverseLayout = false))

        val reversed = orderPagedRowsForLayout(normal, reverseLayout = true)
        assertEquals("G1", currentSectionLabel(reversed, firstVisibleIndex = 0, reverseLayout = true))
        assertEquals("G2", currentSectionLabel(reversed, firstVisibleIndex = 3, reverseLayout = true))
    }
}
