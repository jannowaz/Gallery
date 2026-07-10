package org.fossify.gallery.compose.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Compose-observable mirror of `Config.blurAllMedia`. `Config` is a plain SharedPreferences
 * wrapper with no change notification, so flipping the persisted boolean alone doesn't recompose
 * any composable that already read it - every blur call site and every toggle (Settings switch,
 * quick nav-bar icon) reads/writes through this object instead of `Config` directly, so toggling
 * from anywhere is reflected everywhere immediately without needing navigation.
 *
 * Seeded from the persisted value once at app start (`App.kt`); every write here must also write
 * through to `Config.blurAllMedia` at the call site so the choice survives a process restart.
 */
object BlurState {
    var enabled by mutableStateOf(false)
}
