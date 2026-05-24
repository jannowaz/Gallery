package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.commons.extensions.formatDate
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import org.fossify.gallery.models.ThumbnailSection
import java.util.*

object TimelineHelper {
    fun getGroupedMedia(media: List<Medium>, context: Context): ArrayList<ThumbnailItem> {
        val grouped = ArrayList<ThumbnailItem>()
        if (media.isEmpty()) return grouped

        var lastDate = ""
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis() / 1000

        for (medium in media) {
            val date = medium.modified.formatDate(context)
            if (date != lastDate) {
                grouped.add(ThumbnailSection(date))
                lastDate = date
            }
            grouped.add(medium)
        }
        return grouped
    }
}
