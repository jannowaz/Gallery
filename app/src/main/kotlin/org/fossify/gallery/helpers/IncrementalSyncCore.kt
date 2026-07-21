package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

/**
 * The decision core of the incremental MediaStore sync, separated from the ContentResolver/DB
 * plumbing in [MediaRepository.syncNewMediaFromStore] so it can actually be tested.
 *
 * This is not a convenience split: this exact logic has produced three separate "media silently
 * missing from the library" bugs, each found by accident weeks later, because nothing here was
 * verifiable without a device and a specific real-world MediaStore state.
 *
 * - the watermark advanced past rows a later MediaStore index pass had not written yet
 * - a strict `>` on DATE_MODIFIED skipped everything sharing the boundary second
 * - a 10,000-row cap dropped rows whose mtime was old but whose date_added was fresh, while the
 *   watermark still advanced past them (3,761 files permanently invisible on a real device)
 *
 * All three share one shape: **a row is observed for the watermark but not actually stored.** The
 * types below make that the property under test rather than something to be careful about.
 */

/**
 * Buffers rows into batches of at most [batchSize] and tracks the watermark that may be persisted
 * once the source is exhausted.
 *
 * The invariant, and the reason this class exists: a row only ever influences [watermark] through
 * [add], and [add] also guarantees the row reaches [onBatch]. There is no path that advances the
 * watermark past a row without emitting it - which is precisely what every bug above did.
 *
 * [finish] must be called after the last [add]; the trailing partial batch is emitted there.
 */
internal class SyncBatcher(
    private val batchSize: Int,
    startWatermark: Long,
    private val onBatch: (List<Medium>) -> Unit,
) {
    /** Highest timestamp seen across BOTH axes of the selection (modified and added). Safe to store
     * only once the source is fully drained - see the class doc. */
    var watermark: Long = startWatermark
        private set

    /** How many rows were handed to [onBatch]. Not the same as "new rows": dedup happens downstream. */
    var accepted: Int = 0
        private set

    private val buffer = mutableListOf<Medium>()

    fun add(medium: Medium, modifiedMs: Long, addedMs: Long) {
        // Both axes, because the selection matches on either. Tracking only DATE_MODIFIED is what
        // let rows found via the DATE_ADDED half keep matching forever.
        watermark = maxOf(watermark, modifiedMs, addedMs)
        accepted++
        buffer += medium
        if (buffer.size >= batchSize) flush()
    }

    fun finish() = flush()

    private fun flush() {
        if (buffer.isEmpty()) return
        // Copy: the callback may retain the list while this buffer keeps filling.
        onBatch(buffer.toList())
        buffer.clear()
    }
}

/**
 * Field derivations for one MediaStore row. Pure, because the fallbacks are load-bearing and have
 * been wrong before: DATE_TAKEN is absent on non-EXIF files, DATE_ADDED is absent on rows written
 * by a pure-disk fallback scan, and DATA is empty on some scoped-storage rows.
 */
internal object SyncFields {

    /** DATE_TAKEN is already millis; fall back to the mtime so `taken` is never an inconsistent 0. */
    fun takenMs(rawTakenMs: Long, modifiedMs: Long): Long = rawTakenMs.takeIf { it > 0 } ?: modifiedMs

    /**
     * DATE_ADDED (seconds) is what date_sort_key keys on, so a freshly downloaded photo with an old
     * EXIF date sorts to the top while a RELATIVE_PATH fast-move keeps its position. Falls back to
     * the newest of the other two when MediaStore reports nothing.
     */
    fun addedMs(rawAddedSec: Long, modifiedMs: Long, takenMs: Long): Long =
        (rawAddedSec * 1000L).takeIf { it > 0 } ?: maxOf(modifiedMs, takenMs)

    /**
     * DATA, or a path rebuilt from RELATIVE_PATH + DISPLAY_NAME when it is missing. Returns null
     * when neither yields anything usable, so the caller can skip the row instead of inserting a
     * row keyed on a garbage path.
     */
    fun resolvePath(data: String?, storageRoot: String, relativePath: String?, displayName: String?): String? {
        if (!data.isNullOrBlank()) return data
        val rel = relativePath ?: ""
        val name = displayName ?: ""
        if (rel.isBlank() && name.isBlank()) return null
        return "$storageRoot/$rel$name"
    }

    /** The incremental selection compares against whole seconds, so the stored millis watermark is
     * truncated. `>=` (not `>`) at the call site then re-scans the boundary second, which dedup
     * absorbs - a strict `>` permanently skipped anything sharing that second. */
    fun watermarkSeconds(watermarkMs: Long): Long = watermarkMs / 1000L
}
