package org.fossify.gallery.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.MoverWidgetProvider
import org.fossify.gallery.helpers.MyWidgetProvider
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.flattenMoverPairs
import org.fossify.gallery.helpers.loadMoverPairs
import org.fossify.gallery.models.BatchJobItem
import org.fossify.gallery.workers.BatchOperation
import org.fossify.gallery.workers.MediaBatchWorker
import java.io.File

/**
 * Non-exported receiver for interactive home-screen widget PendingIntents.
 * Keeps destructive/state-mutating widget actions (`ACTION_RENAME`, `ACTION_MOVE_NOW`) off the
 * exported `AppWidgetProvider` receivers so external apps cannot trigger them via broadcasts.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MyWidgetProvider.ACTION_RENAME -> handleRename(context, intent)
            MoverWidgetProvider.ACTION_MOVE_NOW -> handleMoveNow(context)
        }
    }

    private fun handleRename(context: Context, intent: Intent) {
        val count = intent.getIntExtra(MyWidgetProvider.EXTRA_COUNT, 5).coerceIn(1, 50)
        val rawPrefix = intent.getStringExtra(MyWidgetProvider.EXTRA_PREFIX) ?: return
        val safePrefix = sanitizeFilePrefix(rawPrefix)
        val pending = goAsync()
        ensureBackgroundThread {
            try {
                val media = context.mediaDB.getNewestMedia(count)
                val repo = MediaRepository(context.applicationContext)
                var renamed = 0
                val scanPaths = mutableListOf<String>()
                media.forEachIndexed { idx, m ->
                    val file = File(m.path)
                    val parent = file.parentFile ?: return@forEachIndexed
                    if (!file.exists()) return@forEachIndexed
                    val ext = file.extension
                    var suffix = idx + 1
                    var newFile = File(parent, if (ext.isNotEmpty()) "${safePrefix}_$suffix.$ext" else "${safePrefix}_$suffix")
                    while (newFile.exists() && newFile.absolutePath != file.absolutePath) {
                        suffix++
                        newFile = File(parent, if (ext.isNotEmpty()) "${safePrefix}_$suffix.$ext" else "${safePrefix}_$suffix")
                    }
                    if (newFile.absolutePath == file.absolutePath) return@forEachIndexed
                    if (file.renameTo(newFile)) {
                        try {
                            context.mediaDB.updateMedium(m.path, parent.absolutePath, newFile.name, newFile.absolutePath)
                            repo.updateReviewPath(m.path, newFile.absolutePath)
                        } catch (_: Exception) {
                        }
                        scanPaths.add(m.path)
                        scanPaths.add(newFile.absolutePath)
                        renamed++
                    }
                }
                if (scanPaths.isNotEmpty()) {
                    runCatching { MediaScannerConnection.scanFile(context, scanPaths.toTypedArray(), null, null) }
                    RefreshBus.trigger()
                }
                android.util.Log.i("WidgetRename", "Renamed $renamed of $count media with prefix '$safePrefix'")
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun handleMoveNow(context: Context) {
        val pending = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val moves = flattenMoverPairs(loadMoverPairs(context))
                if (moves.isEmpty()) {
                    showToast(context, context.getString(R.string.no_files_found))
                } else {
                    val items = moves.map { (src, dst) -> BatchJobItem(jobId = "", sourcePath = src, targetPath = dst) }
                    MediaBatchWorker.enqueue(context, BatchOperation.MOVE_COPY_DELETE, items)
                    showToast(context, context.getString(R.string.mover_widget_move_started, moves.size))
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun showToast(context: Context, text: String) {
        withContext(Dispatchers.Main) { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private val UNSAFE_CHARS = Regex("[/\\\\:*?\"<>|\\u0000-\\u001F]")

        fun sanitizeFilePrefix(raw: String): String {
            val cleaned = raw
                .replace("..", "")
                .replace(UNSAFE_CHARS, "_")
                .trim()
                .trim('.', '_')
            return cleaned.ifBlank { "IMG" }.take(64)
        }
    }
}
