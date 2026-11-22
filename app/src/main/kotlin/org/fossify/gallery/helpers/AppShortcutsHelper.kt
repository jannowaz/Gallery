package org.fossify.gallery.helpers

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.fossify.gallery.R
import org.fossify.gallery.activities.ComposeExplorerActivity

/**
 * Publishes dynamic launcher app shortcuts (long-press on the launcher icon) that work uniformly
 * across every `SplashActivity.*` icon alias without duplicating static XML metadata per alias.
 */
object AppShortcutsHelper {
    const val EXTRA_NAVIGATE_TO = "org.fossify.gallery.NAVIGATE_TO"
    const val NAVIGATE_TARGET_CLEANUP = "cleanup_hub"
    const val NAVIGATE_TARGET_RECYCLE_BIN = "recycle_bin"
    const val NAVIGATE_TARGET_MOVER = "folders_mover"

    fun publish(context: Context) {
        val appCtx = context.applicationContext
        val shortcuts = listOf(
            buildShortcut(
                context = appCtx,
                id = "shortcut_cleanup",
                label = appCtx.getString(R.string.nav_cleanup),
                iconRes = R.drawable.ic_launcher_foreground,
                target = NAVIGATE_TARGET_CLEANUP,
            ),
            buildShortcut(
                context = appCtx,
                id = "shortcut_recycle_bin",
                label = appCtx.getString(R.string.nav_recycle_bin),
                iconRes = R.drawable.ic_launcher_foreground,
                target = NAVIGATE_TARGET_RECYCLE_BIN,
            ),
            buildShortcut(
                context = appCtx,
                id = "shortcut_mover",
                label = appCtx.getString(R.string.nav_mover),
                iconRes = R.drawable.ic_launcher_foreground,
                target = NAVIGATE_TARGET_MOVER,
            ),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(appCtx, shortcuts) }
    }

    private fun buildShortcut(
        context: Context,
        id: String,
        label: String,
        iconRes: Int,
        target: String,
    ): ShortcutInfoCompat {
        val intent = Intent(context, ComposeExplorerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_NAVIGATE_TO, target)
        }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, iconRes))
            .setIntent(intent)
            .build()
    }
}
