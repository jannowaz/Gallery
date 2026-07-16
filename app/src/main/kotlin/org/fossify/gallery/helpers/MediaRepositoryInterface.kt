package org.fossify.gallery.helpers

interface MediaRepositoryInterface {
    fun getMediaFromPath(path: String): List<org.fossify.gallery.models.Medium>
    fun isFavorite(path: String): Boolean
    /** Returns whether the change actually took effect, so callers can revert optimistic UI state
     * instead of showing a favorite/rating/tag as applied when it silently wasn't - see
     * MediaRepository.toggleFavorite/updateRating/addTag/removeTag. */
    fun toggleFavorite(path: String, isFav: Boolean): Boolean
    fun getRating(path: String): Int
    fun updateRating(path: String, rating: Int): Boolean
    fun getTags(path: String): Set<String>
    fun addTag(path: String, tag: String): Boolean
    fun removeTag(path: String, tag: String): Boolean
    /** Permanently deletes [path]. Returns false (and leaves the DB/cache rows untouched, so the
     * item stays visible to retry) if the underlying file itself couldn't actually be removed -
     * see MediaRepository.deleteMedium. */
    fun deleteMedium(path: String): Boolean

    /** Batch variant of [deleteMedium]: fires a single RefreshBus tick at the end instead of one
     * per file, reporting per-file progress. Returns the number of failed deletions. */
    fun deleteMediaBatch(paths: List<String>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        var failed = 0
        paths.forEachIndexed { i, p ->
            if (!deleteMedium(p)) failed++
            onProgress(i + 1, paths.size)
        }
        return failed
    }
}
