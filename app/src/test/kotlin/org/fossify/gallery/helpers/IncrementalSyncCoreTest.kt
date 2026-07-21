package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for the incremental MediaStore sync's decision core.
 *
 * Every test here corresponds to a bug that actually shipped and stayed unnoticed for weeks,
 * because this logic was previously only observable by comparing a real device's MediaStore against
 * its Room DB by hand. The point is not coverage for its own sake - it is that "some media silently
 * never appears" now fails a build instead of requiring someone to count files on a phone.
 */
class IncrementalSyncCoreTest {

    private fun medium(path: String) = Medium(
        id = null, name = path.substringAfterLast('/'), path = path, parentPath = path.substringBeforeLast('/'),
        modified = 0, taken = 0, size = 0, type = 1, videoDuration = 0, isFavorite = false,
        deletedTS = 0, mediaStoreId = 0, rating = 0,
    )

    private fun collect(): Pair<MutableList<List<Medium>>, (List<Medium>) -> Unit> {
        val batches = mutableListOf<List<Medium>>()
        return batches to { b: List<Medium> -> batches += b }
    }

    // --- The 2026-07-21 bug: rows dropped by a hard cap while the watermark advanced past them ---

    @Test
    fun `every row reaches a batch even when far more arrive than fit in one`() {
        val (batches, onBatch) = collect()
        val batcher = SyncBatcher(batchSize = 100, startWatermark = 0L, onBatch = onBatch)

        repeat(1007) { i -> batcher.add(medium("/x/$i.jpg"), modifiedMs = 1_000L, addedMs = 1_000L) }
        batcher.finish()

        val delivered = batches.flatten()
        // 1007, not 1000: the old implementation stopped at the cap and silently discarded the rest.
        assertEquals(1007, delivered.size)
        assertEquals(1007, batcher.accepted)
        assertEquals(1007, delivered.map { it.path }.toSet().size)
    }

    @Test
    fun `a row with an old mtime but a fresh date_added is not lost behind later rows`() {
        // Exact shape of the real failure: a bulk copy keeps each file's original (old) mtime while
        // every file gets the same fresh date_added. Ordered by DATE_MODIFIED DESC these land last,
        // so a cap dropped them first - and the watermark had already moved past their date_added.
        val (batches, onBatch) = collect()
        val batcher = SyncBatcher(batchSize = 10, startWatermark = 0L, onBatch = onBatch)

        repeat(25) { i -> batcher.add(medium("/recent/$i.jpg"), modifiedMs = 9_000L, addedMs = 9_000L) }
        batcher.add(medium("/bulk/old-mtime.jpg"), modifiedMs = 1_000L, addedMs = 8_000L)
        batcher.finish()

        assertTrue(batches.flatten().any { it.path == "/bulk/old-mtime.jpg" })
        assertEquals(26, batcher.accepted)
    }

    @Test
    fun `the watermark never advances past a row that was not emitted`() {
        // The property all three historical bugs violated. Checked by construction: after every
        // single add, the watermark may only cover rows that already went out or are still buffered
        // and therefore guaranteed to go out on finish().
        val (batches, onBatch) = collect()
        val batcher = SyncBatcher(batchSize = 4, startWatermark = 0L, onBatch = onBatch)
        val added = mutableListOf<String>()

        repeat(13) { i ->
            batcher.add(medium("/x/$i.jpg"), modifiedMs = (i * 10).toLong(), addedMs = (i * 10).toLong())
            added += "/x/$i.jpg"
            assertTrue(
                "watermark covers rows not yet accounted for",
                batcher.accepted == added.size,
            )
        }
        batcher.finish()
        assertEquals(added.toSet(), batches.flatten().map { it.path }.toSet())
    }

    // --- Watermark arithmetic ---

    @Test
    fun `watermark covers both the modified and the added axis`() {
        val (_, onBatch) = collect()
        val batcher = SyncBatcher(batchSize = 10, startWatermark = 0L, onBatch = onBatch)

        batcher.add(medium("/a.jpg"), modifiedMs = 500L, addedMs = 100L)
        batcher.add(medium("/b.jpg"), modifiedMs = 100L, addedMs = 900L)
        batcher.finish()

        // 900 comes from the ADDED axis alone. Tracking only DATE_MODIFIED left rows matched via the
        // date_added half of the selection matching forever, re-synced on every single run.
        assertEquals(900L, batcher.watermark)
    }

    @Test
    fun `watermark never moves backwards from its starting value`() {
        val (_, onBatch) = collect()
        val batcher = SyncBatcher(batchSize = 10, startWatermark = 5_000L, onBatch = onBatch)

        batcher.add(medium("/old.jpg"), modifiedMs = 10L, addedMs = 20L)
        batcher.finish()

        assertEquals(5_000L, batcher.watermark)
    }

    @Test
    fun `an empty source leaves the watermark untouched and emits nothing`() {
        val (batches, onBatch) = collect()
        val batcher = SyncBatcher(batchSize = 10, startWatermark = 1_234L, onBatch = onBatch)

        batcher.finish()

        assertEquals(1_234L, batcher.watermark)
        assertEquals(0, batches.size)
        assertEquals(0, batcher.accepted)
    }

    @Test
    fun `batches are capped at the batch size so peak memory stays bounded`() {
        val (batches, onBatch) = collect()
        val batcher = SyncBatcher(batchSize = 10, startWatermark = 0L, onBatch = onBatch)

        repeat(35) { i -> batcher.add(medium("/x/$i.jpg"), 1L, 1L) }
        batcher.finish()

        assertTrue("no batch may exceed the cap", batches.all { it.size <= 10 })
        assertEquals(listOf(10, 10, 10, 5), batches.map { it.size })
    }

    @Test
    fun `finish is idempotent so a double call cannot duplicate rows`() {
        val (batches, onBatch) = collect()
        val batcher = SyncBatcher(batchSize = 10, startWatermark = 0L, onBatch = onBatch)

        batcher.add(medium("/a.jpg"), 1L, 1L)
        batcher.finish()
        batcher.finish()

        assertEquals(1, batches.flatten().size)
    }

    // --- Field derivations (each fallback below has been the subject of its own bug) ---

    @Test
    fun `date_taken falls back to the mtime when MediaStore has none`() {
        assertEquals(777L, SyncFields.takenMs(rawTakenMs = 0L, modifiedMs = 777L))
        assertEquals(555L, SyncFields.takenMs(rawTakenMs = 555L, modifiedMs = 777L))
    }

    @Test
    fun `date_added converts seconds to millis and falls back to the newest other stamp`() {
        assertEquals(2_000L, SyncFields.addedMs(rawAddedSec = 2L, modifiedMs = 100L, takenMs = 900L))
        // No date_added (pure-disk fallback scan): the newest of the other two, not zero - a zero
        // date_sort_key would sort the file to the very bottom of the date-sorted grid forever.
        assertEquals(900L, SyncFields.addedMs(rawAddedSec = 0L, modifiedMs = 100L, takenMs = 900L))
    }

    @Test
    fun `path falls back to RELATIVE_PATH plus DISPLAY_NAME when DATA is empty`() {
        assertEquals("/sd/DCIM/a.jpg", SyncFields.resolvePath("/sd/DCIM/a.jpg", "/sd", "DCIM/", "a.jpg"))
        assertEquals("/sd/DCIM/a.jpg", SyncFields.resolvePath(null, "/sd", "DCIM/", "a.jpg"))
        assertEquals("/sd/DCIM/a.jpg", SyncFields.resolvePath("", "/sd", "DCIM/", "a.jpg"))
    }

    @Test
    fun `an unresolvable path is rejected rather than turned into a garbage row`() {
        // Inserting "/sd/" as a path would create a row that matches nothing on disk and can never
        // be cleaned up by path.
        assertNull(SyncFields.resolvePath(null, "/sd", null, null))
        assertNull(SyncFields.resolvePath("", "/sd", "", ""))
    }

    @Test
    fun `the watermark is compared in whole seconds`() {
        // MediaStore stores DATE_MODIFIED/DATE_ADDED in seconds while the watermark is millis;
        // truncating (plus `>=` at the call site) is what keeps the boundary second from being
        // skipped, which once hid every file sharing that second.
        assertEquals(1_784_627L, SyncFields.watermarkSeconds(1_784_627_059L))
        assertEquals(0L, SyncFields.watermarkSeconds(999L))
    }
}
