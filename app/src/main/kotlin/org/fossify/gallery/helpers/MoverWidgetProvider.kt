package org.fossify.gallery.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.getFileSignature
import org.fossify.commons.extensions.setVisibleIf
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.ComposeExplorerActivity
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.BatchJobItem
import org.fossify.gallery.workers.BatchOperation
import org.fossify.gallery.workers.MediaBatchWorker

/**
 * Home-screen "Quick Mover" widget: shows the last few media items across the whole library (not
 * scoped to one folder, unlike [MyWidgetProvider]) and a button that moves every file currently
 * sitting in a configured [FolderPair]'s source folder into its destination - the same source/dest
 * pairs the in-app Folders Mover screen edits (see [loadMoverPairs]/[FolderPair]). Renaming/moving
 * an individual recent item isn't done in the widget itself (RemoteViews has no text input and no
 * per-item action UI worth the RemoteViewsService complexity for 5 thumbnails) - tapping a thumbnail
 * just opens the app to its normal media grid, where rename/move already fully exist.
 *
 * No per-instance configuration screen: unlike the folder widget, there is nothing to configure per
 * widget placement - the mover pairs and "recent media" are both global, so the widget is fully
 * functional the instant it's dropped on the home screen.
 */
class MoverWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_MOVE_NOW = "org.fossify.gallery.MOVER_WIDGET_MOVE_NOW"
        const val EXTRA_NAVIGATE_TO = "org.fossify.gallery.NAVIGATE_TO"
        const val NAVIGATE_TARGET_MOVER = "folders_mover"

        private val THUMB_IDS = intArrayOf(R.id.mover_thumb_1, R.id.mover_thumb_2, R.id.mover_thumb_3, R.id.mover_thumb_4, R.id.mover_thumb_5)

        // Widgets otherwise only redraw on the OS's own periodic schedule / launcher-driven refresh
        // - a move just triggered from this same widget (or a rename/move done in-app) would leave
        // stale thumbnails/button text on the home screen until whenever that next cycle lands.
        fun requestImmediateUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MoverWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                MoverWidgetProvider().onUpdate(context, manager, ids)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MOVE_NOW) {
            val pending = goAsync()
            // MediaBatchWorker.enqueue is a suspend fun and this is a plain background thread, not
            // a coroutine scope - GlobalScope is deliberate here (mirrors the same headless-warm-up
            // pattern App.kt already uses), the receiver's own goAsync() lease is what actually keeps
            // the process alive long enough, not structured concurrency.
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
    }

    private suspend fun showToast(context: Context, text: String) {
        withContext(Dispatchers.Main) { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    private fun openAppIntent(context: Context, requestCode: Int, navigateTo: String? = null): PendingIntent {
        val intent = Intent(context, ComposeExplorerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (navigateTo != null) putExtra(EXTRA_NAVIGATE_TO, navigateTo)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        ensureBackgroundThread {
            val config = context.config
            val recent = try { context.mediaDB.getNewestMedia(THUMB_IDS.size) } catch (_: Exception) { emptyList() }
            val configuredPairs = loadMoverPairs(context)
            val pendingMoveCount = flattenMoverPairs(configuredPairs).size

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_mover)

                THUMB_IDS.forEachIndexed { i, viewId ->
                    val medium = recent.getOrNull(i)
                    views.setVisibleIf(viewId, medium != null)
                    if (medium != null) {
                        try {
                            var bitmap = Glide.with(context)
                                .asBitmap()
                                .load(medium.path)
                                .apply(
                                    RequestOptions()
                                        .centerCrop()
                                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                        .signature(medium.path.getFileSignature())
                                )
                                .submit(200, 200)
                                .get()
                            // Same privacy scrim as the folder widget - a home-screen surface can't
                            // apply a live blur, so "blur all media" is honored by baking it into
                            // the bitmap instead of leaving these thumbnails unprotected.
                            if (config.blurAllMedia) bitmap = bitmap.withPrivacyScrim()
                            views.setImageViewBitmap(viewId, bitmap)
                        } catch (_: Exception) {
                        }
                    }
                }

                views.setOnClickPendingIntent(R.id.mover_thumbs_row, openAppIntent(context, widgetId))

                when {
                    pendingMoveCount > 0 -> {
                        views.setTextViewText(R.id.mover_move_btn, context.getString(R.string.mover_widget_move_now, pendingMoveCount))
                        val movePendingIntent = PendingIntent.getBroadcast(
                            context,
                            widgetId + 3000,
                            Intent(context, MoverWidgetProvider::class.java).setAction(ACTION_MOVE_NOW),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        views.setOnClickPendingIntent(R.id.mover_move_btn, movePendingIntent)
                    }
                    // Pairs exist but their source folders are currently empty - distinct from
                    // "nothing configured yet" (below), which would otherwise wrongly nudge a user
                    // who already set this up to "configure" it again every time it's briefly caught
                    // up. Still opens the Mover screen on tap (handy to check/edit pairs), just with
                    // accurate copy.
                    configuredPairs.isNotEmpty() -> {
                        views.setTextViewText(R.id.mover_move_btn, context.getString(R.string.mover_widget_up_to_date))
                        views.setOnClickPendingIntent(R.id.mover_move_btn, openAppIntent(context, widgetId + 4000, NAVIGATE_TARGET_MOVER))
                    }
                    else -> {
                        views.setTextViewText(R.id.mover_move_btn, context.getString(R.string.mover_widget_configure))
                        views.setOnClickPendingIntent(R.id.mover_move_btn, openAppIntent(context, widgetId + 4000, NAVIGATE_TARGET_MOVER))
                    }
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }
}
