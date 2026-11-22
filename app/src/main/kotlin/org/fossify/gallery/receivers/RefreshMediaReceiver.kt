package org.fossify.gallery.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.helpers.REFRESH_PATH
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.addPathToDBSync
import org.fossify.gallery.helpers.MEDIA_EXTENSIONS
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RefreshMediaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH_MEDIA) return
        val rawPath = intent.getStringExtra(REFRESH_PATH)
        if (!isSafeRefreshMediaPath(rawPath)) return

        // Canonicalization (`realpath`), existence checks on FUSE-backed storage, and the Room
        // insert all perform blocking disk/IPC I/O. Keep `onReceive` non-blocking on the main
        // thread while `goAsync()` keeps the broadcast alive until the DB update completes.
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        ensureBackgroundThread {
            try {
                val file = runCatching { File(rawPath!!).canonicalFile }.getOrNull() ?: return@ensureBackgroundThread
                val canonical = file.path
                // Re-validate after canonicalization so symlinks cannot escape into /data/ or private app dirs.
                if (!isSafeRefreshMediaPath(canonical) || !org.fossify.gallery.helpers.IntentSecurity.isSafeExternalPath(appContext, canonical)) {
                    return@ensureBackgroundThread
                }
                if (!file.exists() || !file.isFile) return@ensureBackgroundThread

                val now = System.currentTimeMillis()
                val last = recentPaths[canonical]
                if (last != null && now - last < THROTTLE_MS) return@ensureBackgroundThread
                if (recentPaths.size > MAX_TRACKED_PATHS) {
                    recentPaths.entries.removeIf { now - it.value >= THROTTLE_MS }
                }
                recentPaths[canonical] = now
                appContext.addPathToDBSync(canonical)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH_MEDIA = "org.fossify.REFRESH_MEDIA"
        private const val THROTTLE_MS = 2_000L
        private const val MAX_TRACKED_PATHS = 256
        private val recentPaths = ConcurrentHashMap<String, Long>()

        /**
         * Guards the exported `org.fossify.REFRESH_MEDIA` receiver against untrusted external
         * broadcasts carrying blank paths, directory traversal segments, private app-data paths,
         * or non-media files.
         */
        internal fun isSafeRefreshMediaPath(rawPath: String?): Boolean {
            if (rawPath.isNullOrBlank()) return false
            if (!rawPath.startsWith("/")) return false
            if (rawPath.contains('\u0000') || rawPath.split('/').any { it == ".." }) return false
            if (rawPath.startsWith("/data/") || rawPath.startsWith("/proc/") || rawPath.startsWith("/sys/")) {
                return false
            }
            val ext = rawPath.substringAfterLast('.', "").lowercase()
            return ext.isNotEmpty() && ext in MEDIA_EXTENSIONS
        }
    }
}
