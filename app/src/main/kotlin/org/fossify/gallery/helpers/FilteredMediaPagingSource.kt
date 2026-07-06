package org.fossify.gallery.helpers

import android.database.Cursor
import androidx.room.RoomDatabase
import androidx.room.paging.LimitOffsetPagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import org.fossify.gallery.models.Medium

/** Backs [MediaRepository.getMediaPagedFiltered] - a hand-built dynamic-SQL counterpart to the
 * `@Query`-generated PagingSources in [org.fossify.gallery.interfaces.MediumDao]. Room only
 * generates a LimitOffsetPagingSource for a fixed, compile-time `@Query`; a filter combining any of
 * rating/tag/path-include/path-exclude/size/date needs its WHERE clause assembled at runtime, so
 * this extends Room's own public `LimitOffsetPagingSource` (the same base class `@Query` uses under
 * the hood) with a raw query instead - it gets LIMIT/OFFSET paging and InvalidationTracker-driven
 * refresh for free, observing the same tables ("media", "media_tags") the unfiltered grid does. */
class FilteredMediaPagingSource(
    query: SupportSQLiteQuery,
    db: RoomDatabase,
) : LimitOffsetPagingSource<Medium>(query, db, "media", "media_tags") {

    override fun convertRows(cursor: Cursor): List<Medium> {
        val result = ArrayList<Medium>(cursor.count)
        while (cursor.moveToNext()) {
            result.add(
                Medium(
                    id = null,
                    name = cursor.getString(0),
                    path = cursor.getString(1),
                    parentPath = cursor.getString(2),
                    modified = cursor.getLong(3),
                    taken = cursor.getLong(4),
                    size = cursor.getLong(5),
                    type = cursor.getInt(6),
                    videoDuration = cursor.getInt(7),
                    isFavorite = cursor.getInt(8) != 0,
                    deletedTS = cursor.getLong(9),
                    mediaStoreId = cursor.getLong(10),
                    rating = cursor.getInt(11),
                )
            )
        }
        return result
    }
}
