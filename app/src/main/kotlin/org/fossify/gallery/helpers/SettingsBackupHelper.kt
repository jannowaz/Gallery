package org.fossify.gallery.helpers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Full settings/pins backup as JSON.
 *
 * The value type MUST be preserved across the round-trip. The previous format dumped
 * `SharedPreferences.all` as a plain `Map<String, Any?>`; on import Gson decodes every JSON number as
 * a `Double`, which matched none of the `putInt/putLong/putFloat` branches, so **every integer and
 * long setting was silently dropped** - column counts, sort orders, sync watermarks, and
 * `app_protection_type` (whose loss breaks unlocking with a restored PIN). Booleans/strings/string-sets
 * were the only things that ever came back.
 *
 * The current format tags each entry with its type so numbers are coerced back correctly. Legacy
 * plain-object backups are still importable (best-effort: their numbers are unrecoverable, but their
 * booleans/strings/string-sets restore as before).
 */
object SettingsBackupHelper {
    private val gson = Gson()

    private const val T_BOOL = "bool"
    private const val T_INT = "int"
    private const val T_LONG = "long"
    private const val T_FLOAT = "float"
    private const val T_STRING = "string"
    private const val T_STRING_SET = "stringset"

    internal data class Entry(val key: String, val type: String, val value: Any?)

    /** Pure (Context-free) prefs -> typed JSON, so it can be unit-tested without Android. */
    internal fun encode(all: Map<String, Any?>): String {
        val entries = all.mapNotNull { (k, v) ->
            when (v) {
                is Boolean -> Entry(k, T_BOOL, v)
                is Int -> Entry(k, T_INT, v)
                is Long -> Entry(k, T_LONG, v)
                is Float -> Entry(k, T_FLOAT, v)
                is String -> Entry(k, T_STRING, v)
                is Set<*> -> Entry(k, T_STRING_SET, v.filterIsInstance<String>())
                else -> null
            }
        }
        return gson.toJson(entries)
    }

    /** Pure (Context-free) typed JSON -> entries, coercing Gson's Double back to the tagged numeric
     * type. Accepts both the current typed-array format and the legacy plain-object format. */
    internal fun decode(json: String): List<Entry> {
        return if (json.trimStart().startsWith("[")) {
            gson.fromJson(json, object : TypeToken<List<Entry>>() {}.type)
        } else {
            val map: Map<String, Any?> = gson.fromJson(json, object : TypeToken<Map<String, Any?>>() {}.type)
            map.mapNotNull { (k, v) ->
                when (v) {
                    is Boolean -> Entry(k, T_BOOL, v)
                    is String -> Entry(k, T_STRING, v)
                    is List<*> -> Entry(k, T_STRING_SET, v.filterIsInstance<String>())
                    else -> null // numbers can't be recovered - their type was lost in the old format
                }
            }
        }
    }

    /** Applies decoded [entries] onto [editor]. When [replaceAll] (a full typed backup covers every
     * pref, so "Overwrite all settings" means a clean replace), current prefs are cleared first. */
    internal fun apply(editor: android.content.SharedPreferences.Editor, entries: List<Entry>, replaceAll: Boolean) {
        if (replaceAll) editor.clear()
        entries.forEach { e ->
            when (e.type) {
                T_BOOL -> (e.value as? Boolean)?.let { editor.putBoolean(e.key, it) }
                T_INT -> (e.value as? Number)?.let { editor.putInt(e.key, it.toInt()) }
                T_LONG -> (e.value as? Number)?.let { editor.putLong(e.key, it.toLong()) }
                T_FLOAT -> (e.value as? Number)?.let { editor.putFloat(e.key, it.toFloat()) }
                T_STRING -> (e.value as? String)?.let { editor.putString(e.key, it) }
                T_STRING_SET -> (e.value as? List<*>)?.let { editor.putStringSet(e.key, it.filterIsInstance<String>().toSet()) }
            }
        }
    }

    suspend fun exportSettings(context: Context, outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(org.fossify.commons.helpers.PREFS_KEY, Context.MODE_PRIVATE)
            outputStream.bufferedWriter().use { it.write(encode(prefs.all)) }
            true
        } catch (e: Exception) {
            android.util.Log.e("BackupHelper", "Export failed", e)
            false
        }
    }

    suspend fun importSettings(context: Context, inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = inputStream.bufferedReader().use { it.readText() }
            val isTypedFormat = json.trimStart().startsWith("[")
            val entries = decode(json)
            val prefs = context.getSharedPreferences(org.fossify.commons.helpers.PREFS_KEY, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            // Only a full typed backup is safe to replace-all with; a legacy backup can't restore
            // numbers, so clearing would wipe the user's current numeric settings for good - merge instead.
            apply(editor, entries, replaceAll = isTypedFormat)
            editor.apply()
            true
        } catch (e: Exception) {
            android.util.Log.e("BackupHelper", "Import failed", e)
            false
        }
    }
}
