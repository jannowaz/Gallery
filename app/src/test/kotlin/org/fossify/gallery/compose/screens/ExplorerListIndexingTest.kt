package org.fossify.gallery.compose.screens

import org.fossify.gallery.helpers.GroupBy
import org.fossify.gallery.helpers.GroupRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerListIndexingTest {

    private fun folder(name: String) = ExplorerItem(
        name = name,
        path = "/storage/emulated/0/$name",
        isDirectory = true,
        mediaCount = 3,
    )

    private fun file(name: String, size: Long = 1024L, rating: Int = 0) = ExplorerItem(
        name = name,
        path = "/storage/emulated/0/DCIM/$name",
        isDirectory = false,
        size = size,
        rating = rating,
    )

    @Test
    fun `buildExplorerFileGroupRows returns single Items row for GroupBy NONE`() {
        val files = listOf(file("a.jpg"), file("b.jpg"))
        val (itemMap, rows) = buildExplorerFileGroupRows(
            visibleFileItems = files,
            fileTagsByPath = emptyMap(),
            tagHierarchy = emptyMap(),
            mediaSettings = ViewSettings(groupBy = GroupBy.NONE),
            untaggedLabel = "Untagged",
            unratedLabel = "Unrated",
        )
        assertEquals(2, itemMap.size)
        assertEquals(1, rows.size)
        val itemsRow = rows.first() as GroupRow.Items
        assertEquals("all", itemsRow.sectionKey)
        assertEquals(2, itemsRow.media.size)
    }

    @Test
    fun `buildExplorerScrollIndexMaps maps grid chunks and list rows accurately`() {
        val folders = listOf(folder("A"), folder("B"), folder("C"))
        val files = listOf(file("1.jpg"), file("2.jpg"), file("3.jpg"), file("4.jpg"))
        val (itemMap, rows) = buildExplorerFileGroupRows(
            visibleFileItems = files,
            fileTagsByPath = emptyMap(),
            tagHierarchy = emptyMap(),
            mediaSettings = ViewSettings(groupBy = GroupBy.NONE),
            untaggedLabel = "Untagged",
            unratedLabel = "Unrated",
        )
        val (keyToPath, indexByPath) = buildExplorerScrollIndexMaps(
            visibleFolderItems = folders,
            visibleFileItems = files,
            fileGroupRows = rows,
            fileItemByPath = itemMap,
            folderSettings = ViewSettings(viewType = ViewType.GRID),
            mediaSettings = ViewSettings(viewType = ViewType.GRID),
            folderGridColumns = 2,
            mediaGridColumns = 3,
        )
        // Index 0 = folder section header; 1 = [A, B]; 2 = [C]; 3 = media section header; 4 = [1, 2, 3]; 5 = [4]
        assertEquals(1, indexByPath[folders[0].path])
        assertEquals(1, indexByPath[folders[1].path])
        assertEquals(2, indexByPath[folders[2].path])
        assertEquals(4, indexByPath[files[0].path])
        assertEquals(4, indexByPath[files[2].path])
        assertEquals(5, indexByPath[files[3].path])
        assertEquals(folders[0].path, keyToPath["folderrow_0"])
        assertEquals(files[3].path, keyToPath["all/gridrow_1"])
    }

    @Test
    fun `buildExplorerScrollIndexMaps handles empty sections without entries`() {
        val (keyToPath, indexByPath) = buildExplorerScrollIndexMaps(
            visibleFolderItems = emptyList(),
            visibleFileItems = emptyList(),
            fileGroupRows = emptyList(),
            fileItemByPath = emptyMap(),
            folderSettings = ViewSettings(),
            mediaSettings = ViewSettings(),
            folderGridColumns = 2,
            mediaGridColumns = 3,
        )
        assertTrue(keyToPath.isEmpty())
        assertTrue(indexByPath.isEmpty())
    }
}
