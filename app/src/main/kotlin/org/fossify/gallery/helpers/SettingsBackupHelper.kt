package org.fossify.gallery.helpers

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

object SettingsBackupHelper {
    private val gson = Gson()

    suspend fun exportSettings(context: Context, outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(org.fossify.commons.helpers.PREFS_KEY, Context.MODE_PRIVATE)
            val allEntries = prefs.all
            val json = gson.toJson(allEntries)
            outputStream.bufferedWriter().use { it.write(json) }
            true
        } catch (e: Exception) {
            android.util.Log.e("BackupHelper", "Export failed", e)
            false
        }
    }

    suspend fun importSettings(context: Context, inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = inputStream.bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val entries: Map<String, Any?> = gson.fromJson(json, type)
            
            val prefs = context.getSharedPreferences(org.fossify.commons.helpers.PREFS_KEY, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            entries.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is List<*> -> {
                        if (value.all { it is String }) {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, (value as List<String>).toSet())
                        }
                    }
                }
            }
            editor.apply()
            true
        } catch (e: Exception) {
            android.util.Log.e("BackupHelper", "Import failed", e)
            false
        }
    }
}
