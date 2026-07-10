package org.fossify.gallery.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getFileSignature
import org.fossify.commons.extensions.setText
import org.fossify.commons.extensions.setVisibleIf
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.getFolderNameFromPath
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.widgetsDB
import org.fossify.gallery.models.Widget
import java.io.File

class MyWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_RENAME = "org.fossify.gallery.RENAME_LAST_MEDIA"
        const val EXTRA_COUNT = "count"
        const val EXTRA_PREFIX = "prefix"

        // Widgets otherwise only redraw on the OS's own periodic schedule / launcher-driven refresh
        // - toggling "blur all media" would leave an unblurred thumbnail sitting on the home screen
        // until whenever that next cycle happens to land. Call this right after writing the setting
        // so the widget picks it up immediately instead. Safe to call with zero widgets placed.
        fun requestImmediateUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MyWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                MyWidgetProvider().onUpdate(context, manager, ids)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_RENAME) {
            val count = intent.getIntExtra(EXTRA_COUNT, 5)
            val prefix = intent.getStringExtra(EXTRA_PREFIX) ?: return
            val pending = goAsync()
            ensureBackgroundThread {
                try {
                    val media = context.mediaDB.getNewestMedia(count)
                    var renamed = 0
                    media.forEachIndexed { idx, m ->
                        val file = java.io.File(m.path)
                        if (!file.exists()) return@forEachIndexed
                        val ext = file.extension
                        val newName = "${prefix}_${idx + 1}.$ext"
                        val newFile = java.io.File(file.parent, newName)
                        if (file.renameTo(newFile)) {
                            try { context.mediaDB.updateMedium(m.path, file.parent ?: "", newName, newFile.absolutePath) } catch (_: Exception) { }
                            renamed++
                        }
                    }
                    android.util.Log.i("WidgetRename", "Renamed $renamed of $count media with prefix '$prefix'")
                } catch (_: Exception) { }
                pending.finish()
            }
        }
    }
    private fun setupAppOpenIntent(context: Context, views: RemoteViews, id: Int, widget: Widget) {
        val intent = Intent(context, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, widget.folderPath)
        }

        val pendingIntent = PendingIntent.getActivity(context, widget.widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(id, pendingIntent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        ensureBackgroundThread {
            val config = context.config
            context.widgetsDB.getWidgets().filter { appWidgetIds.contains(it.widgetId) }.forEach { widget ->
                val views = RemoteViews(context.packageName, R.layout.widget).apply {
                    applyColorFilter(R.id.widget_background, config.widgetBgColor)
                    setVisibleIf(R.id.widget_folder_name, config.showWidgetFolderName)
                    setTextColor(R.id.widget_folder_name, config.widgetTextColor)
                    setText(R.id.widget_folder_name, context.getFolderNameFromPath(widget.folderPath))
                }

                // Was `return@forEach` on a null thumbnail - bailed out of the whole widget update
                // (including the app-open/rename PendingIntents and the actual push below) just
                // because a folder had no cached thumbnail yet. Now only the image step is skipped;
                // everything else still gets set up and pushed.
                val path = context.directoryDB.getDirectoryThumbnail(widget.folderPath)
                if (path != null) {
                    val options = RequestOptions()
                        .signature(path.getFileSignature())
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

                    if (config.cropThumbnails) {
                        options.centerCrop()
                    } else {
                        options.fitCenter()
                    }

                    val density = context.resources.displayMetrics.density
                    // Was appWidgetIds.first() - every widget instance's thumbnail was sized using
                    // the *first* placed widget's dimensions instead of its own, so a second folder
                    // widget of a different size got a wrongly-sized (stretched/cropped) thumbnail.
                    val appWidgetOptions = appWidgetManager.getAppWidgetOptions(widget.widgetId)
                    val width = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val height = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

                    val widgetSize = (Math.max(width, height) * density).toInt()
                    try {
                        var image = Glide.with(context)
                            .asBitmap()
                            .load(path)
                            .apply(options)
                            .submit(widgetSize, widgetSize)
                            .get()
                        // RemoteViews (this widget runs in the host launcher's process) can't apply a
                        // live Modifier.blur()/RenderEffect - the only thing it can display is a plain
                        // static Bitmap, so "blur all media" is honored here by baking a solid scrim
                        // directly into the bitmap before handing it off. Previously this ignored the
                        // setting entirely and always showed the real thumbnail on the home screen.
                        if (config.blurAllMedia) {
                            image = image.withPrivacyScrim()
                        }
                        views.setImageViewBitmap(R.id.widget_imageview, image)
                    } catch (e: Exception) {
                    }
                }

                setupAppOpenIntent(context, views, R.id.widget_holder, widget)
                setupRenameIntent(context, views, R.id.widget_rename_btn, widget.widgetId)

                // The actual bug: everything above was computed and then just discarded - nothing
                // ever pushed these RemoteViews to the real widget surface, so the home screen kept
                // showing whatever WidgetConfigureActivity's own one-time save had pushed (just a
                // background color) - no thumbnail, no folder name, no working PendingIntents, and
                // the rename button stuck on its static XML placeholder text forever.
                appWidgetManager.updateAppWidget(widget.widgetId, views)
            }
        }
    }

    private fun setupRenameIntent(context: Context, views: RemoteViews, id: Int, widgetId: Int) {
        val config = context.config
        val count = config.widgetRenameCount.coerceIn(1, 50)
        val prefix = config.widgetRenamePrefix.ifBlank { "IMG" }
        val intent = Intent(context, MyWidgetProvider::class.java).apply {
            action = ACTION_RENAME
            putExtra(EXTRA_COUNT, count)
            putExtra(EXTRA_PREFIX, prefix)
        }
        val pi = PendingIntent.getBroadcast(context, widgetId + 1000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(id, pi)

        // Update text to show what will be renamed
        val media = context.mediaDB.getNewestMedia(count)
        if (media.isNotEmpty()) {
            val first = java.io.File(media.first().path).name
            val last = java.io.File(media.last().path).name
            views.setTextViewText(R.id.widget_rename_btn, "$prefix → $first … $last")
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        ensureBackgroundThread {
            appWidgetIds.forEach {
                context.widgetsDB.deleteWidgetId(it)
            }
        }
    }
}
