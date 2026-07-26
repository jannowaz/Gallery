package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MediaStoreOps.entriesUnder] replaces an O(N) `filter { it.path.startsWith("root/") }` with an
 * O(log N + k) binary range search over a path-sorted list. These tests pin it to the exact same
 * result set as that reference filter across the cases that could break a naive range trick:
 * sibling prefixes (`foo` vs `foo2` vs `foo/...`), the folder's own entry, non-ASCII names, and the
 * `/`-boundary the upper bound relies on.
 */
class EntriesUnderTest {

    private fun entry(path: String) =
        MediaStoreOps.MediaEntry(path = path, name = path.substringAfterLast('/'), modified = 0L, size = 0L)

    /** The behaviour entriesUnder must reproduce, over any order. */
    private fun referenceFilter(entries: List<MediaStoreOps.MediaEntry>, root: String) =
        entries.filter { it.path.startsWith(root.trimEnd('/') + "/") }

    private fun assertMatchesReference(paths: List<String>, root: String) {
        val entries = paths.map { entry(it) }
        val sorted = entries.sortedBy { it.path }
        val expected = referenceFilter(entries, root).map { it.path }.toSet()
        val actual = MediaStoreOps.entriesUnder(sorted, root).map { it.path }.toSet()
        assertEquals("root=$root", expected, actual)
    }

    @Test
    fun `sibling folders sharing a name prefix are not bled in`() {
        // foo2 / foobar sort adjacent to foo but must NOT count as being under foo.
        assertMatchesReference(
            listOf(
                "/s/0/foo/a.jpg",
                "/s/0/foo/sub/b.jpg",
                "/s/0/foo2/c.jpg",
                "/s/0/foobar/d.jpg",
                "/s/0/fo/e.jpg",
            ),
            root = "/s/0/foo",
        )
    }

    @Test
    fun `the folder's own path entry is excluded`() {
        // A file literally at "/s/0/foo" (no trailing segment) is not "under" foo.
        assertMatchesReference(
            listOf("/s/0/foo", "/s/0/foo/a.jpg", "/s/0/foo/b.jpg"),
            root = "/s/0/foo",
        )
    }

    @Test
    fun `trailing slash on root is normalised`() {
        assertMatchesReference(
            listOf("/s/0/foo/a.jpg", "/s/0/foo2/b.jpg"),
            root = "/s/0/foo/",
        )
    }

    @Test
    fun `non-ASCII folder names sort and match consistently`() {
        assertMatchesReference(
            listOf(
                "/s/0/Über/a.jpg",
                "/s/0/Über/tief/b.jpg",
                "/s/0/Übermut/c.jpg",
                "/s/0/Zoo/d.jpg",
                "/s/0/Apfel/e.jpg",
            ),
            root = "/s/0/Über",
        )
    }

    @Test
    fun `deep nesting returns the whole subtree`() {
        assertMatchesReference(
            listOf(
                "/s/0/a/b/c/d/e/1.jpg",
                "/s/0/a/b/c/2.jpg",
                "/s/0/a/x/3.jpg",
                "/s/0/a2/4.jpg",
            ),
            root = "/s/0/a",
        )
    }

    @Test
    fun `no matches yields empty`() {
        assertMatchesReference(
            listOf("/s/0/foo/a.jpg", "/s/0/bar/b.jpg"),
            root = "/s/0/nope",
        )
    }

    @Test
    fun `empty input yields empty`() {
        assertEquals(emptyList<MediaStoreOps.MediaEntry>(), MediaStoreOps.entriesUnder(emptyList(), "/s/0/foo"))
    }

    @Test
    fun `character right after slash boundary is handled`() {
        // The upper bound is root+"0" (since '/' 0x2F +1 == '0' 0x30). A sibling starting with '0'
        // ("/s/0/foo0...") is the tightest adversarial case for that bound and must stay excluded.
        assertMatchesReference(
            listOf(
                "/s/0/foo/a.jpg",
                "/s/0/foo0/b.jpg",
                "/s/0/foo.txt.d/c.jpg", // '.' 0x2E < '/' 0x2F, sorts just before the subtree
            ),
            root = "/s/0/foo",
        )
    }
}
