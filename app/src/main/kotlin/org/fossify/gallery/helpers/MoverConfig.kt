package org.fossify.gallery.helpers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.gallery.extensions.defaultSharedPrefs
import java.io.File

/**
 * One configured source -> destination folder pair for the "Mover" feature in
 * [org.fossify.gallery.compose.screens.FoldersMoverScreen].
 */
data class FolderPair(val source: String = "", val destination: String = "")

private const val MOVER_PAIRS_PREF_KEY = "mover_pairs"

fun loadMoverPairs(context: Context): List<FolderPair> {
    val prefs = context.defaultSharedPrefs()
    val json = prefs.getString(MOVER_PAIRS_PREF_KEY, null) ?: return emptyList()
    return try {
        Gson().fromJson(json, object : TypeToken<List<FolderPair>>() {}.type)
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveMoverPairs(context: Context, pairs: List<FolderPair>) {
    context.defaultSharedPrefs().edit()
        .putString(MOVER_PAIRS_PREF_KEY, Gson().toJson(pairs))
        .apply()
}

/**
 * Appends one new pair - the shared "save a pair from wherever it was configured" path for the
 * Albums/Explorer "use as mover source" quick action as well as FoldersMoverScreen's own editor.
 */
fun addMoverPair(context: Context, source: String, destination: String) {
    saveMoverPairs(context, loadMoverPairs(context) + FolderPair(source, destination))
}

/** Same as [addMoverPair] but for several sources mapped to the one [destination] at once. */
fun addMoverPairs(context: Context, sources: List<String>, destination: String) {
    if (sources.isEmpty()) return
    saveMoverPairs(context, loadMoverPairs(context) + sources.map { FolderPair(it, destination) })
}

/**
 * Every (sourceFile, destFile) pair queued across all configured folder pairs right now - flattens
 * each pair's current top-level (non-recursive) file listing at call time.
 */
fun flattenMoverPairs(pairs: List<FolderPair>): List<Pair<String, String>> = pairs.flatMap { pair ->
    val srcDir = File(pair.source)
    if (!srcDir.isDirectory) return@flatMap emptyList<Pair<String, String>>()
    val destBase = pair.destination
    srcDir.listFiles()?.filter { it.isFile }?.map { it.absolutePath to "$destBase/${it.name}" } ?: emptyList()
}
