package org.fossify.gallery.helpers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.defaultSharedPrefs
import java.io.InputStream
import java.io.OutputStream

/**
 * Full settings/pins backup as JSON.
 *
 * The value type MUST be preserved across the round-trip. The historical plain `Map<String, Any?>`
 * format dropped every Int/Long pref because Gson decodes JSON numbers as Double.
 *
 * Supported formats:
 * - **V2 envelope** (`{ "version": 2, "prefs": [...], "defaultPrefs": [...] }`): preserves both
 *   the primary `Prefs` store and `defaultSharedPrefs()` (Mover pairs, tab/per-folder ViewSettings,
 *   pinned picker paths).
 * - **V1 typed array** (`[ { key, type, value }, ... ]`): primary `Prefs` store only.
 * - **Legacy plain object** (`{ "key": value, ... }`): best-effort merge of booleans/strings/sets.
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

    internal data class BundleEnvelope(
        val version: Int = 2,
        val prefs: List<Entry> = emptyList(),
        val defaultPrefs: List<Entry> = emptyList(),
    )

    internal data class DecodedBackup(
        val prefs: List<Entry>,
        val defaultPrefs: List<Entry>?,
        val isTypedFormat: Boolean,
    )

    private fun toEntries(all: Map<String, Any?>): List<Entry> = all.mapNotNull { (k, v) ->
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

    /** Pure (Context-free) primary prefs -> V1 typed JSON array. */
    internal fun encode(all: Map<String, Any?>): String = gson.toJson(toEntries(all))

    /** Pure (Context-free) V2 backup encoding both primary prefs and defaultSharedPrefs. */
    internal fun encodeBundle(prefsAll: Map<String, Any?>, defaultPrefsAll: Map<String, Any?>): String =
        gson.toJson(BundleEnvelope(version = 2, prefs = toEntries(prefsAll), defaultPrefs = toEntries(defaultPrefsAll)))

    /** Pure (Context-free) decoder supporting V2 envelope, V1 typed array, and legacy map. */
    internal fun decodeBundle(json: String): DecodedBackup {
        val trimmed = json.trimStart()
        if (trimmed.startsWith("[")) {
            val entries: List<Entry> = gson.fromJson(json, object : TypeToken<List<Entry>>() {}.type)
            return DecodedBackup(prefs = entries, defaultPrefs = null, isTypedFormat = true)
        }
        val root = JsonParser.parseString(json)
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            if (obj.has("version") && obj.has("prefs") && obj.get("prefs").isJsonArray) {
                val bundle = gson.fromJson(json, BundleEnvelope::class.java)
                return DecodedBackup(
                    prefs = bundle.prefs,
                    defaultPrefs = bundle.defaultPrefs,
                    isTypedFormat = true,
                )
            }
        }
        val map: Map<String, Any?> = gson.fromJson(json, object : TypeToken<Map<String, Any?>>() {}.type)
        val entries = map.mapNotNull { (k, v) ->
            when (v) {
                is Boolean -> Entry(k, T_BOOL, v)
                is String -> Entry(k, T_STRING, v)
                is List<*> -> Entry(k, T_STRING_SET, v.filterIsInstance<String>())
                else -> null // numbers can't be recovered - their type was lost in the old format
            }
        }
        return DecodedBackup(prefs = entries, defaultPrefs = null, isTypedFormat = false)
    }

    /** Pure (Context-free) typed JSON -> primary entries (preserved for existing callers/tests). */
    internal fun decode(json: String): List<Entry> = decodeBundle(json).prefs

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
            val defaultPrefs = context.defaultSharedPrefs()
            outputStream.bufferedWriter().use { it.write(encodeBundle(prefs.all, defaultPrefs.all)) }
            true
        } catch (e: Exception) {
            android.util.Log.e("BackupHelper", "Export failed", e)
            false
        }
    }

    suspend fun importSettings(context: Context, inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = inputStream.bufferedReader().use { it.readText() }
            val decoded = decodeBundle(json)
            val prefs = context.getSharedPreferences(org.fossify.commons.helpers.PREFS_KEY, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            // Only a full typed backup is safe to replace-all with; a legacy backup can't restore
            // numbers, so clearing would wipe the user's current numeric settings for good - merge instead.
            apply(editor, decoded.prefs, replaceAll = decoded.isTypedFormat)
            editor.apply()

            if (decoded.defaultPrefs != null) {
                val defEditor = context.defaultSharedPrefs().edit()
                apply(defEditor, decoded.defaultPrefs, replaceAll = true)
                defEditor.apply()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("BackupHelper", "Import failed", e)
            false
        }
    }
}
