package org.fossify.gallery.helpers

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.MediaCache
import java.io.File

class MediaRepository(private val context: Context) : MediaRepositoryInterface {

    override fun getMediaFromPath(path: String): List<Medium> {
        return try { context.mediaDB.getMediaFromPath(path) } catch (_: Exception) { emptyList() }
    }

    override fun isFavorite(path: String): Boolean {
        return try { context.favoritesDB.isFavorite(path) } catch (_: Exception) { false }
    }

    override fun toggleFavorite(path: String, isFav: Boolean) {
        try {
            if (isFav) {
                val name = File(path).name
                val parentPath = File(path).parent ?: ""
                context.favoritesDB.insert(org.fossify.gallery.models.Favorite(id = null, fullPath = path, filename = name, parentPath = parentPath))
            } else {
                context.favoritesDB.deleteFavoritePath(path)
            }
            context.mediaDB.updateFavorite(path, isFav)
        } catch (_: Exception) { }
    }

    override fun getRating(path: String): Int {
        return XmpWriter.read(path).rating
    }

    override fun updateRating(path: String, rating: Int) {
        val current = XmpWriter.read(path)
        XmpWriter.write(path, current.tags, rating)
        try { context.mediaDB.updateRating(path, rating) } catch (_: Exception) { }
    }

    override fun getTags(path: String): Set<String> {
        return XmpWriter.read(path).tags.toSet()
    }

    override fun addTag(path: String, tag: String) {
        val current = XmpWriter.read(path)
        val tags = if (tag in current.tags) current.tags else current.tags + tag
        XmpWriter.write(path, tags, current.rating)
        syncCache(path, tags.joinToString(","), current.rating)
    }

    override fun removeTag(path: String, tag: String) {
        val current = XmpWriter.read(path)
        val tags = current.tags.filter { it != tag }
        XmpWriter.write(path, tags, current.rating)
        syncCache(path, tags.joinToString(","), current.rating)
    }

    private fun syncCache(path: String, tags: String, rating: Int) {
        try {
            runBlocking {
                context.mediaCacheDB.upsertAll(listOf(MediaCache(fullPath = path, tags = tags, rating = rating, lastScanned = System.currentTimeMillis())))
            }
        } catch (_: Exception) { }
    }

    override fun deleteMedium(path: String) {
        try { context.mediaDB.deleteMediumPath(path); File(path).delete() } catch (_: Exception) { }
    }

    fun moveToRecycleBin(path: String) {
        try {
            val now = System.currentTimeMillis()
            context.mediaDB.softDelete(path, now)
        } catch (_: Exception) { }
    }

    fun restoreFromRecycleBin(path: String) {
        try {
            context.mediaDB.restoreDeleted(path)
        } catch (_: Exception) { }
    }
}
