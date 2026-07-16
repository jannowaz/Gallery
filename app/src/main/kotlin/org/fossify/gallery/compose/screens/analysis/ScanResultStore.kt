package org.fossify.gallery.compose.screens.analysis

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the last completed scan of the storage analysis and the duplicate finder as JSON in
 * app-internal storage. A scan takes minutes; before this, any process death (or just leaving and
 * reopening the app) silently threw the results away. Loaders drop entries whose files no longer
 * exist, so a restored list never shows media that was deleted/moved since the scan.
 */
object ScanResultStore {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class StorageScan(val folder: String, val timestamp: Long, val results: List<AnalysisResult>)

    @Serializable
    data class DuplicateScan(val folder: String, val timestamp: Long, val mode: String, val groups: List<DuplicateGroup>)

    fun saveStorageScan(context: Context, folder: String, results: List<AnalysisResult>) {
        runCatching { storageFile(context).writeText(json.encodeToString(StorageScan(folder, System.currentTimeMillis(), results))) }
    }

    fun loadStorageScan(context: Context): StorageScan? {
        val scan = runCatching {
            val f = storageFile(context)
            if (f.exists()) json.decodeFromString<StorageScan>(f.readText()) else null
        }.getOrNull() ?: return null
        val stillThere = scan.results.filter { File(it.path).exists() }
        return if (stillThere.isEmpty()) null else scan.copy(results = stillThere)
    }

    fun saveDuplicateScan(context: Context, folder: String, mode: String, groups: List<DuplicateGroup>) {
        runCatching { duplicateFile(context).writeText(json.encodeToString(DuplicateScan(folder, System.currentTimeMillis(), mode, groups))) }
    }

    fun loadDuplicateScan(context: Context): DuplicateScan? {
        val scan = runCatching {
            val f = duplicateFile(context)
            if (f.exists()) json.decodeFromString<DuplicateScan>(f.readText()) else null
        }.getOrNull() ?: return null
        // A group only stays meaningful while at least two of its files still exist.
        val stillThere = scan.groups
            .map { g -> g.copy(files = g.files.filter { File(it.path).exists() }) }
            .filter { it.files.size >= 2 }
        return if (stillThere.isEmpty()) null else scan.copy(groups = stillThere)
    }

    private fun storageFile(context: Context) = File(context.filesDir, "last_storage_scan.json")
    private fun duplicateFile(context: Context) = File(context.filesDir, "last_duplicate_scan.json")
}
