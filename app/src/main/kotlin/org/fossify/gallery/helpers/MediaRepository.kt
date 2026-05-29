package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.Medium
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
        } catch (_: Exception) { }
    }

    override fun getRating(path: String): Int {
        return try { context.mediaDB.getMediaFromPath(path).firstOrNull()?.rating ?: 0 } catch (_: Exception) { 0 }
    }

    override fun updateRating(path: String, rating: Int) {
        try { context.mediaDB.updateRating(path, rating) } catch (_: Exception) { }
    }

    override fun getTags(path: String): Set<String> {
        return try { TagWriter.readTags(path).toSet() } catch (_: Exception) { emptySet() }
    }

    override fun addTag(path: String, tag: String) {
        try { TagWriter.addTag(path, tag) } catch (_: Exception) { }
    }

    override fun removeTag(path: String, tag: String) {
        try {
            val current = TagWriter.readTags(path).toMutableList()
            if (current.remove(tag)) TagWriter.writeTags(path, current)
        } catch (_: Exception) { }
    }

    override fun deleteMedium(path: String) {
        try { context.mediaDB.deleteMediumPath(path); File(path).delete() } catch (_: Exception) { }
    }
}
