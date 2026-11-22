package org.fossify.gallery.helpers

import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File

/**
 * Keeps the initial MediaStore import bounded.
 *
 * A fresh library can contain hundreds of thousands of rows. Retaining every [Medium] until the
 * cursor has finished multiplies the app's peak heap just as Room is allocating its own insert
 * buffers. This accumulator retains only one tiny aggregate per directory; callers can persist
 * media in small batches as they stream the cursor.
 */
internal class LibraryBootstrapAccumulator {
    private data class Aggregate(
        var thumbnail: String = "",
        var thumbnailSortKey: Long = Long.MIN_VALUE,
        var mediaCount: Int = 0,
        var latestModified: Long = 0L,
        var latestTaken: Long = 0L,
        var totalSize: Long = 0L,
        var hasImages: Boolean = false,
        var hasVideos: Boolean = false,
    )

    private val directories = LinkedHashMap<String, Aggregate>()
    var latestSeenTimestamp: Long = 0L
        private set

    fun add(medium: Medium) {
        val aggregate = directories.getOrPut(medium.parentPath) { Aggregate() }
        val sortKey = maxOf(medium.dateAdded, medium.taken, medium.modified)
        if (sortKey >= aggregate.thumbnailSortKey) {
            aggregate.thumbnailSortKey = sortKey
            aggregate.thumbnail = medium.path
        }
        aggregate.mediaCount++
        aggregate.latestModified = maxOf(aggregate.latestModified, medium.modified)
        aggregate.latestTaken = maxOf(aggregate.latestTaken, medium.taken)
        aggregate.totalSize += medium.size.coerceAtLeast(0L)
        aggregate.hasImages = aggregate.hasImages || medium.type == 1
        aggregate.hasVideos = aggregate.hasVideos || medium.type == 2
        latestSeenTimestamp = maxOf(latestSeenTimestamp, medium.modified, medium.dateAdded, medium.taken)
    }

    fun directories(): List<Directory> = directories.map { (path, aggregate) ->
        val types = when {
            aggregate.hasImages && aggregate.hasVideos -> 3
            aggregate.hasVideos -> 2
            else -> 1
        }
        Directory(
            id = null,
            path = path,
            tmb = aggregate.thumbnail,
            name = File(path).name,
            mediaCnt = aggregate.mediaCount,
            modified = aggregate.latestModified,
            taken = aggregate.latestTaken,
            size = aggregate.totalSize,
            location = LOCATION_INTERNAL,
            types = types,
            sortValue = "",
        )
    }
}
