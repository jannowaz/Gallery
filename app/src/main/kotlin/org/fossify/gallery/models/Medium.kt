package org.fossify.gallery.models

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.room.*
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.helpers.*
import java.io.File
import java.io.Serializable
import java.util.Calendar
import java.util.Locale

@Stable
@Entity(
    tableName = "media",
    indices = [
        Index(value = ["full_path"], unique = true),
        Index(value = ["deleted_ts", "date_sort_key"]),
        Index(value = ["deleted_ts", "size"]),
        Index(value = ["deleted_ts", "rating", "last_modified"]),
        // Backs getMediaFromPath (WHERE deleted_ts = 0 AND parent_path = :path), the per-folder query
        // the Explorer file list and the drilled-into-album view now use as their single source of
        // truth (same media table as the Media tab) - without this it was a full table scan per folder
        // navigation on a large library.
        Index(value = ["deleted_ts", "parent_path"]),
        // Backs the Favorites tab (getFavorites/getFavoritesCount: WHERE deleted_ts = 0 AND
        // is_favorite = 1) - was a full table scan of the whole library every time the tab loaded.
        Index(value = ["deleted_ts", "is_favorite"]),
    ],
)
data class Medium(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "filename") var name: String,
    @ColumnInfo(name = "full_path", collate = ColumnInfo.NOCASE) var path: String,
    // NOCASE, matching full_path above. Every path comparison in every DAO is written
    // `... = :path COLLATE NOCASE` (inherited upstream convention), and against a BINARY-collated
    // column SQLite cannot use the index at all: EXPLAIN QUERY PLAN showed the per-folder query
    // falling back to `USING INDEX index_media_deleted_ts_is_favorite (deleted_ts=?)`, i.e. a scan
    // of every live row, once per folder. On a 163k-item/~2.8k-folder library that was ~465M row
    // visits per pass and the bulk of a 356s cold-start CPU burn.
    @ColumnInfo(name = "parent_path", collate = ColumnInfo.NOCASE) var parentPath: String,
    @ColumnInfo(name = "last_modified") var modified: Long,
    @ColumnInfo(name = "date_taken") var taken: Long,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "type") var type: Int,
    @ColumnInfo(name = "video_duration") var videoDuration: Int,
    @ColumnInfo(name = "is_favorite") var isFavorite: Boolean,
    @ColumnInfo(name = "deleted_ts") var deletedTS: Long,
    @ColumnInfo(name = "media_store_id") var mediaStoreId: Long,
    @ColumnInfo(name = "rating") var rating: Int = 0,

    @Ignore var gridPosition: Int = 0,   // used at grid view decoration at Grouping enabled

    // MediaStore DATE_ADDED (millis) - "when this file entered the device's media library", read
    // straight from MediaStore in the sync/scan paths. This is what feeds date_sort_key below now
    // (preferred over date_taken), so a freshly downloaded photo whose EXIF capture date is months
    // old still sorts to the very top of the date-sorted grid, matching what the Explorer tab (which
    // sorts by the file's own mtime) already does. Crucially it's *also* stable in the two cases the
    // old date_taken-based key was chosen to protect: it never changes on an in-app rating/tag/XMP
    // edit (those touch the file's mtime, not MediaStore's date_added), and MediaStore preserves it
    // across a RELATIVE_PATH "fast move", so a file moved into a target folder keeps its position
    // instead of jumping to the top as if brand new. 0 for rows whose writer didn't read it (e.g. a
    // pure-disk fallback scan) - date_sort_key falls back to date_taken/last_modified for those.
    @ColumnInfo(name = "date_added") var dateAdded: Long = 0L,

    // Denormalized effective sort date, auto-maintained (via two SQLite triggers - see
    // GalleryDatabase's createDateSortKeyTriggers/onCreate callback) as
    // `date_added > 0 ? date_added : (date_taken > 0 ? date_taken : last_modified)`. Kept as a real,
    // plain, indexed column purely so the date sort - the default one - is indexable: SQLite can only
    // use an index to skip a sort when the ORDER BY key is a literal column it covers, never a
    // computed expression - and Room's own schema validation can't represent a raw SQL expression
    // index at all (confirmed live: it crashes with "Migration didn't properly handle" every time,
    // since TableInfo introspection silently drops the expression term instead of matching it).
    @ColumnInfo(name = "date_sort_key") var dateSortKey: Long = 0L,
) : Serializable, ThumbnailItem() {

    constructor() : this(null, "", "", "", 0L, 0L, 0L, 0, 0, false, 0L, 0L, 0, 0)

    companion object {
        private const val serialVersionUID = -6553149366975655L
    }

    fun isWebP() = name.isWebP()

    fun isGIF() = type == TYPE_GIFS

    fun isImage() = type == TYPE_IMAGES

    fun isVideo() = type == TYPE_VIDEOS

    fun isRaw() = type == TYPE_RAWS

    fun isSVG() = type == TYPE_SVGS

    fun isPortrait() = type == TYPE_PORTRAITS

    fun isApng() = name.isApng()

    fun isAvif() = name.endsWith(".avif", true) // switch to commons extension.

    fun isHidden() = name.startsWith('.')

    fun isHeic() = name.lowercase(Locale.getDefault()).endsWith(".heic") || name.lowercase(Locale.getDefault()).endsWith(".heif")

    fun getBubbleText(sorting: Int, context: Context, dateFormat: String, timeFormat: String) = when {
        sorting and SORT_BY_NAME != 0 -> name
        sorting and SORT_BY_PATH != 0 -> path
        sorting and SORT_BY_SIZE != 0 -> size.formatSize()
        sorting and SORT_BY_DATE_MODIFIED != 0 -> modified.formatDate(context, dateFormat, timeFormat)
        sorting and SORT_BY_RANDOM != 0 -> name
        else -> taken.formatDate(context)
    }

    fun getGroupingKey(groupBy: Int): String {
        return when {
            groupBy and GROUP_BY_RATING != 0 -> rating.toString()
            groupBy and GROUP_BY_LAST_MODIFIED_DAILY != 0 -> getDayStartTS(modified, false)
            groupBy and GROUP_BY_LAST_MODIFIED_MONTHLY != 0 -> getDayStartTS(modified, true)
            groupBy and GROUP_BY_DATE_TAKEN_DAILY != 0 -> getDayStartTS(taken, false)
            groupBy and GROUP_BY_DATE_TAKEN_MONTHLY != 0 -> getDayStartTS(taken, true)
            groupBy and GROUP_BY_FILE_TYPE != 0 -> type.toString()
            groupBy and GROUP_BY_EXTENSION != 0 -> name.getFilenameExtension().lowercase(Locale.getDefault())
            groupBy and GROUP_BY_FOLDER != 0 -> parentPath
            else -> ""
        }
    }

    fun getIsInRecycleBin() = deletedTS != 0L

    private fun getDayStartTS(ts: Long, resetDays: Boolean): String {
        val calendar = Calendar.getInstance(Locale.ENGLISH).apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (resetDays) {
                set(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return calendar.timeInMillis.toString()
    }

    fun getSignature(): String {
        val lastModified = if (modified > 1) {
            modified
        } else {
            File(path).lastModified()
        }

        return "$path-$lastModified-$size"
    }

    fun getKey() = ObjectKey(getSignature())

    fun toFileDirItem() = FileDirItem(path, name, false, 0, size, modified, mediaStoreId)
}
