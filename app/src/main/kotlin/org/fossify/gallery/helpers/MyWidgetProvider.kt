package org.fossify.gallery.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
            context.widgetsDB.getWidgets().filter { appWidgetIds.contains(it.widgetId) }.forEach {
                val views = RemoteViews(context.packageName, R.layout.widget).apply {
                    applyColorFilter(R.id.widget_background, config.widgetBgColor)
                    setVisibleIf(R.id.widget_folder_name, config.showWidgetFolderName)
                    setTextColor(R.id.widget_folder_name, config.widgetTextColor)
                    setText(R.id.widget_folder_name, context.getFolderNameFromPath(it.folderPath))
                }

                val path = context.directoryDB.getDirectoryThumbnail(it.folderPath) ?: return@forEach
                val options = RequestOptions()
                    .signature(path.getFileSignature())
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

                if (context.config.cropThumbnails) {
                    options.centerCrop()
                } else {
                    options.fitCenter()
                }

                val density = context.resources.displayMetrics.density
                val appWidgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetIds.first())
                val width = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val height = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

                val widgetSize = (Math.max(width, height) * density).toInt()
                try {
                    val image = Glide.with(context)
                        .asBitmap()
                        .load(path)
                        .apply(options)
                        .submit(widgetSize, widgetSize)
                        .get()
                    views.setImageViewBitmap(R.id.widget_imageview, image)
                } catch (e: Exception) {
                }

                setupAppOpenIntent(context, views, R.id.widget_holder, it)
                setupRenameIntent(context, views, R.id.widget_rename_btn, it.widgetId)
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
