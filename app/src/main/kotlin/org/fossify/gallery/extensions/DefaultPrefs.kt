package org.fossify.gallery.extensions

import android.content.Context
import android.content.SharedPreferences

/**
 * Opens the app's default SharedPreferences file (`<packageName>_preferences`) without invoking
 * the deprecated `android.preference.PreferenceManager.getDefaultSharedPreferences`. Preserves
 * the exact same underlying file name so existing user settings (mover pairs, view settings,
 * pinned picker folders) remain intact.
 */
fun Context.defaultSharedPrefs(): SharedPreferences =
    getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
