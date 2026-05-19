package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "collections")
data class MediaCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "included_paths") val includedPaths: String = "[]",
    @ColumnInfo(name = "excluded_paths") val excludedPaths: String = "[]",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0
) {
    fun getIncludedPaths(): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(includedPaths, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getExcludedPaths(): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(excludedPaths, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        fun createPathsJson(paths: List<String>): String = Gson().toJson(paths.filter { it.isNotEmpty() })
    }
}
