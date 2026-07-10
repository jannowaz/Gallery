package org.fossify.gallery.helpers

import android.content.Context
import android.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * One configured source -> destination folder pair for the "Mover" feature. Shared by
 * [org.fossify.gallery.compose.screens.FoldersMoverScreen] (the in-app editor) and
 * [MoverWidgetProvider] (the home-screen quick-move widget) - both read/write the exact same
 * persisted list, so a pair configured in one place is immediately usable from the other.
 */
data class FolderPair(val source: String = "", val destination: String = "")

private const val MOVER_PAIRS_PREF_KEY = "mover_pairs"

fun loadMoverPairs(context: Context): List<FolderPair> {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val json = prefs.getString(MOVER_PAIRS_PREF_KEY, null) ?: return emptyList()
    return try {
        Gson().fromJson(json, object : TypeToken<List<FolderPair>>() {}.type)
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveMoverPairs(context: Context, pairs: List<FolderPair>) {
    PreferenceManager.getDefaultSharedPreferences(context).edit()
        .putString(MOVER_PAIRS_PREF_KEY, Gson().toJson(pairs))
        .apply()
}

/**
 * Appends one new pair and immediately refreshes the Quick Mover widget - the shared "save a pair
 * from wherever it was configured" path for the Albums/Explorer "use as mover source" quick action
 * (see AlbumsScreen.kt/ExplorerScreen.kt) as well as FoldersMoverScreen's own editor, so the
 * widget's button text never lags behind a pair added from either place.
 */
fun addMoverPair(context: Context, source: String, destination: String) {
    saveMoverPairs(context, loadMoverPairs(context) + FolderPair(source, destination))
    MoverWidgetProvider.requestImmediateUpdate(context)
}

/** Same as [addMoverPair] but for several sources mapped to the one [destination] at once (e.g.
 * picking multiple folders as mover sources in one go) - a single widget refresh instead of one
 * per source. */
fun addMoverPairs(context: Context, sources: List<String>, destination: String) {
    if (sources.isEmpty()) return
    saveMoverPairs(context, loadMoverPairs(context) + sources.map { FolderPair(it, destination) })
    MoverWidgetProvider.requestImmediateUpdate(context)
}

/**
 * Every (sourceFile, destFile) pair queued across all configured folder pairs right now - flattens
 * each pair's current top-level (non-recursive) file listing at call time, matching what
 * `FoldersMoverScreen.startMove()` and the widget's "move now" action both actually move.
 */
fun flattenMoverPairs(pairs: List<FolderPair>): List<Pair<String, String>> = pairs.flatMap { pair ->
    val srcDir = File(pair.source)
    if (!srcDir.isDirectory) return@flatMap emptyList<Pair<String, String>>()
    val destBase = pair.destination
    srcDir.listFiles()?.filter { it.isFile }?.map { it.absolutePath to "$destBase/${it.name}" } ?: emptyList()
}
