package org.fossify.gallery.helpers

interface MediaRepositoryInterface {
    fun getMediaFromPath(path: String): List<org.fossify.gallery.models.Medium>
    fun isFavorite(path: String): Boolean
    fun toggleFavorite(path: String, isFav: Boolean)
    fun getRating(path: String): Int
    fun updateRating(path: String, rating: Int)
    fun getTags(path: String): Set<String>
    fun addTag(path: String, tag: String)
    fun removeTag(path: String, tag: String)
    fun deleteMedium(path: String)
}
