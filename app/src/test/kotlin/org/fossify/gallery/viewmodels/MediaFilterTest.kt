package org.fossify.gallery.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [MediaFilter] is a plain data class with no Android dependency - these cover [MediaFilter.isActive]
 * across every filter dimension, including [MediaFilter.typeFilter] (added when the persistent grid
 * filter and the search panel's quick filter were consolidated onto the same image/video dimension). */
class MediaFilterTest {

    @Test
    fun `isActive is false for the default filter`() {
        assertFalse(MediaFilter().isActive)
    }

    @Test
    fun `isActive is true when rating is set`() {
        assertTrue(MediaFilter(rating = 4).isActive)
    }

    @Test
    fun `isActive is true when tagNames is set`() {
        assertTrue(MediaFilter(tagNames = setOf("beach")).isActive)
    }

    @Test
    fun `isActive is true for an empty tagNames set too`() {
        // Deliberately distinct from `null` - MediaRepository.buildFilterWhereClause treats an empty
        // (non-null) tagNames set as "match nothing" (AND 0), not "no tag filter at all".
        assertTrue(MediaFilter(tagNames = emptySet()).isActive)
    }

    @Test
    fun `isActive is true when pathFilter is set`() {
        assertTrue(MediaFilter(pathFilter = setOf("/sdcard/DCIM")).isActive)
    }

    @Test
    fun `isActive is true when excludePaths is set`() {
        assertTrue(MediaFilter(excludePaths = setOf("/sdcard/Screenshots")).isActive)
    }

    @Test
    fun `isActive is true when minSize is set`() {
        assertTrue(MediaFilter(minSize = 1_000_000L).isActive)
    }

    @Test
    fun `isActive is true when dateRange is set`() {
        assertTrue(MediaFilter(dateRange = 4).isActive)
    }

    @Test
    fun `isActive is true when typeFilter is set`() {
        assertTrue(MediaFilter(typeFilter = 1).isActive)
        assertTrue(MediaFilter(typeFilter = 2).isActive)
    }

    @Test
    fun `two filters with the same values are equal`() {
        // MediaViewModel.setFilter/prefetchFilteredPathsAsync rely on MediaFilter's generated
        // equals()/hashCode() to skip redundant Pager/prefetch work when nothing actually changed -
        // confirms adding typeFilter didn't silently break that (e.g. via a hand-written equals()).
        val a = MediaFilter(rating = 3, tagNames = setOf("beach"), minSize = 5_000_000L, dateRange = 2, typeFilter = 1)
        val b = MediaFilter(rating = 3, tagNames = setOf("beach"), minSize = 5_000_000L, dateRange = 2, typeFilter = 1)
        assertTrue(a == b)
    }

    @Test
    fun `filters differing only in typeFilter are not equal`() {
        val a = MediaFilter(typeFilter = 1)
        val b = MediaFilter(typeFilter = 2)
        assertFalse(a == b)
    }
}
