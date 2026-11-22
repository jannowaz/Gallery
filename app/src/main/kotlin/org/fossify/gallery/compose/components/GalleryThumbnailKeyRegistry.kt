package org.fossify.gallery.compose.components

import android.util.LruCache
import coil.memory.MemoryCache

/**
 * Lightweight in-memory lookup mapping a media file path to the most recent Coil
 * [MemoryCache.Key] produced by [GalleryImage].
 *
 * Keeping Coil's native key (which includes `file.lastModified()` for automatic cache invalidation
 * after in-place edits) while remembering the key per path lets
 * [org.fossify.gallery.compose.screens.viewer.ImagePage] pass it as `placeholderMemoryCacheKey`
 * and display the already-decoded grid thumbnail on frame 0 without flashing a loading spinner.
 */
internal object GalleryThumbnailKeyRegistry {
    private val cache = LruCache<String, MemoryCache.Key>(512)

    fun record(path: String, key: MemoryCache.Key?) {
        if (key == null || path.isEmpty()) return
        synchronized(cache) {
            cache.put(path, key)
        }
    }

    fun get(path: String): MemoryCache.Key? {
        if (path.isEmpty()) return null
        return synchronized(cache) { cache.get(path) }
    }
}
