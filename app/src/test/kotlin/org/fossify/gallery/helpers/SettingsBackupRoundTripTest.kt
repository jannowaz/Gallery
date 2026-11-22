package org.fossify.gallery.helpers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip integrity of SettingsBackupHelper at the Gson layer (the part that needs no Context).
 * The historical bug: a plain Map<String, Any?> export/import dropped every Int/Long pref because
 * Gson decodes JSON numbers as Double. These tests pin down the type-preserving replacement.
 */
class SettingsBackupRoundTripTest {

    private val gson = Gson()

    private val sample: Map<String, Any?> = mapOf(
        "app_protection_type" to 1,             // Int  (PIN=1) - critical for unlock
        "column_count" to 4,                    // Int
        "last_sync_ts" to 1_700_000_000_000L,   // Long
        "brightness" to 0.75f,                  // Float
        "some_flag" to true,                    // Boolean
        "app_password_hash" to "abc123",        // String
        "favorite_folders" to setOf("a", "b"),  // String set
    )

    /** What SettingsBackupHelper.apply would end up writing, captured as a plain map. */
    private fun applied(entries: List<SettingsBackupHelper.Entry>, replaceAll: Boolean): Map<String, Any?> {
        val out = HashMap<String, Any?>()
        val editor = FakeEditor(out)
        SettingsBackupHelper.apply(editor, entries, replaceAll)
        return out
    }

    @Test
    fun `typed round-trip preserves every value including Int, Long and Float`() {
        val json = SettingsBackupHelper.encode(sample)
        val restored = applied(SettingsBackupHelper.decode(json), replaceAll = true)

        assertEquals(1, restored["app_protection_type"])
        assertEquals(4, restored["column_count"])
        assertEquals(1_700_000_000_000L, restored["last_sync_ts"])
        assertEquals(0.75f, restored["brightness"] as Float, 0.0001f)
        assertEquals(true, restored["some_flag"])
        assertEquals("abc123", restored["app_password_hash"])
        assertEquals(setOf("a", "b"), restored["favorite_folders"])
    }

    @Test
    fun `legacy plain-object backup still restores its booleans and strings`() {
        // A backup produced by the OLD format (plain map, numbers already unrecoverable).
        val legacyJson = gson.toJson(mapOf("some_flag" to true, "app_password_hash" to "abc123", "column_count" to 4))
        val decoded = SettingsBackupHelper.decodeBundle(legacyJson)
        val restored = applied(decoded.prefs, replaceAll = false)

        assertTrue(!decoded.isTypedFormat)
        assertEquals(null, decoded.defaultPrefs)
        assertTrue(restored.containsKey("some_flag"))
        assertTrue(restored.containsKey("app_password_hash"))
        // The legacy number is (still) lost - unavoidable, its type was never recorded - but at least
        // the merge doesn't wipe the user's existing value (replaceAll=false).
        assertTrue("column_count" !in restored)
    }

    @Test
    fun `v2 bundle round-trip preserves both primary prefs and defaultSharedPrefs`() {
        val defaultSample: Map<String, Any?> = mapOf(
            "mover_pairs" to """[{"source":"/a","destination":"/b"}]""",
            "compose_view_settings_albums_v2" to "0,3,1,true",
            "pinned_count" to 7,
        )
        val json = SettingsBackupHelper.encodeBundle(sample, defaultSample)
        val decoded = SettingsBackupHelper.decodeBundle(json)

        assertTrue(decoded.isTypedFormat)
        val restoredPrimary = applied(decoded.prefs, replaceAll = true)
        val restoredDefault = applied(decoded.defaultPrefs!!, replaceAll = true)

        assertEquals(1, restoredPrimary["app_protection_type"])
        assertEquals(1_700_000_000_000L, restoredPrimary["last_sync_ts"])
        assertEquals("""[{"source":"/a","destination":"/b"}]""", restoredDefault["mover_pairs"])
        assertEquals("0,3,1,true", restoredDefault["compose_view_settings_albums_v2"])
        assertEquals(7, restoredDefault["pinned_count"])
    }

    /** Minimal SharedPreferences.Editor capturing writes into a map; only the calls apply() makes. */
    private class FakeEditor(val out: MutableMap<String, Any?>) : android.content.SharedPreferences.Editor {
        override fun clear() = apply { out.clear() }
        override fun putBoolean(k: String, v: Boolean) = apply { out[k] = v }
        override fun putInt(k: String, v: Int) = apply { out[k] = v }
        override fun putLong(k: String, v: Long) = apply { out[k] = v }
        override fun putFloat(k: String, v: Float) = apply { out[k] = v }
        override fun putString(k: String, v: String?) = apply { out[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { out[k] = v }
        override fun remove(k: String) = apply { out.remove(k) }
        override fun commit() = true
        override fun apply() {}
    }
}
