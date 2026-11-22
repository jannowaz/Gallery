package org.fossify.gallery.helpers

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.models.MediaReviewStatus
import org.fossify.gallery.viewmodels.MediaFilter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class GroupSummary(
    val key: String,
    val label: String,
    val sqlValue: String,
    val count: Int,
    val ratingValue: Int? = null,
)

private val monthSummaryFormat = ThreadLocal.withInitial { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

private fun formatYearMonthLabel(yearMonth: String?): String {
    val parts = yearMonth?.split("-")
    val year = parts?.getOrNull(0)?.toIntOrNull()
    val month = parts?.getOrNull(1)?.toIntOrNull()
    val cal = Calendar.getInstance().apply {
        clear()
        if (year != null && month != null && month in 1..12) {
            set(year, month - 1, 1, 12, 0, 0)
        } else {
            timeInMillis = 0L
        }
    }
    return monthSummaryFormat.get()!!.format(cal.time).replaceFirstChar { it.uppercase() }
}

private const val SQL_DATE_KEY =
    "CASE WHEN date_sort_key > 0 THEN date_sort_key WHEN date_taken > 0 THEN date_taken ELSE last_modified END"

private fun sqlGroupKeyExpression(groupBy: GroupBy): String = when (groupBy) {
    GroupBy.MONTH -> "strftime('%Y-%m', ($SQL_DATE_KEY) / 1000, 'unixepoch', 'localtime')"
    GroupBy.SIZE -> "CASE WHEN size >= 104857600 THEN 5 WHEN size >= 10485760 THEN 4 WHEN size >= 1048576 THEN 3 WHEN size >= 102400 THEN 2 ELSE 1 END"
    GroupBy.ALPHABET -> "CASE WHEN substr(filename, 1, 1) GLOB '[A-Za-z]' THEN upper(substr(filename, 1, 1)) ELSE '#' END"
    GroupBy.RATING -> "rating"
    GroupBy.NONE, GroupBy.TAG -> "0"
}

private fun escapeLikePrefix(dirPath: String): String =
    dirPath.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "/%"

/** Appends an `IN (...)`/`LIKE` predicate for a path set (literal files chunked under the same
 * [SQLITE_BATCH_CHUNK_SIZE] bound-parameter cap used elsewhere, directories as escaped LIKE
 * prefixes). */
private fun appendPathPredicate(where: StringBuilder, args: MutableList<Any?>, paths: Set<String>, negate: Boolean) {
    if (paths.isEmpty()) return
    val dirs = paths.filter { File(it).isDirectory }
    val files = (paths - dirs.toSet()).toList()
    val clauses = mutableListOf<String>()
    files.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach { chunk ->
        clauses.add("full_path IN (${chunk.joinToString(",") { "?" }})")
        args.addAll(chunk)
    }
    dirs.forEach { d ->
        clauses.add("full_path LIKE ? ESCAPE '\\'")
        args.add(escapeLikePrefix(d))
    }
    if (clauses.isEmpty()) return
    val combined = clauses.joinToString(" OR ")
    where.append(if (negate) " AND NOT ($combined)" else " AND ($combined)")
}

private fun dateRangeCutoff(dateRange: Int): Long = when (dateRange) {
    1 -> System.currentTimeMillis() - 86400000L
    2 -> System.currentTimeMillis() - 7 * 86400000L
    3 -> System.currentTimeMillis() - 30 * 86400000L
    4 -> System.currentTimeMillis() - 365 * 86400000L
    else -> 0L
}

/** Builds the WHERE clause + bound args shared by every filtered media query. */
internal fun MediaRepository.buildFilterWhereClauseImpl(filter: MediaFilter): Pair<String, List<Any?>> {
    val where = StringBuilder("deleted_ts = 0")
    val args = mutableListOf<Any?>()

    if (filter.rating > 0) {
        where.append(" AND rating >= ?")
        args.add(filter.rating)
    }
    filter.tagNames?.let { names ->
        if (names.isEmpty()) {
            where.append(" AND 0")
        } else {
            val placeholders = names.joinToString(",") { "?" }
            if (filter.tagMatchAll && names.size > 1) {
                where.append(" AND full_path IN (SELECT media_path FROM media_tags WHERE tag IN ($placeholders) GROUP BY media_path HAVING COUNT(DISTINCT tag) = ?)")
                args.addAll(names)
                args.add(names.size)
                return@let
            }
            where.append(" AND full_path IN (SELECT DISTINCT media_path FROM media_tags WHERE tag IN ($placeholders))")
            args.addAll(names)
        }
    }
    filter.pathFilter?.let { appendPathPredicate(where, args, it, negate = false) }
    filter.excludePaths?.let { appendPathPredicate(where, args, it, negate = true) }
    if (filter.minSize > 0) {
        where.append(" AND size >= ?")
        args.add(filter.minSize)
    }
    if (filter.dateRange > 0) {
        where.append(" AND (CASE WHEN date_taken > 0 THEN date_taken ELSE last_modified END) >= ?")
        args.add(dateRangeCutoff(filter.dateRange))
    }
    if (filter.addedSince > 0L) {
        where.append(" AND date_sort_key > ?")
        args.add(filter.addedSince)
    }
    if (filter.typeFilter > 0) {
        where.append(" AND type = ?")
        args.add(filter.typeFilter)
    }
    filter.textQuery.trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(8)
        .forEach { term ->
            val escaped = term.lowercase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            val pattern = "%$escaped%"
            where.append(
                " AND (filename LIKE ? COLLATE NOCASE ESCAPE '\\' OR full_path LIKE ? COLLATE NOCASE ESCAPE '\\' " +
                    "OR EXISTS (SELECT 1 FROM media_tags mt WHERE mt.media_path = full_path AND mt.tag LIKE ? COLLATE NOCASE ESCAPE '\\'))",
            )
            args.add(pattern)
            args.add(pattern)
            args.add(pattern)
        }

    filter.additionalPaths?.let { paths ->
        if (paths.isEmpty()) {
            where.append(" AND 0")
        } else {
            val regularWhere = where.toString()
            val manualWhere = StringBuilder()
            appendPathPredicate(manualWhere, args, paths, negate = false)
            val manualPredicate = manualWhere.toString().removePrefix(" AND ")
            if (manualPredicate.isNotEmpty()) {
                where.clear()
                if (regularWhere == "deleted_ts = 0") {
                    where.append(regularWhere).append(" AND ").append(manualPredicate)
                } else {
                    where.append("(").append(regularWhere).append(" OR (deleted_ts = 0 AND ").append(manualPredicate).append(")")
                        .append(")")
                }
            }
        }
    }

    if (filter.reviewFilter == MediaReviewFilter.UNDECIDED) {
        where.append(" AND NOT EXISTS (SELECT 1 FROM media_review_states AS review WHERE review.media_path = media.full_path COLLATE NOCASE AND review.status IN (?, ?))")
        args.add(MediaReviewStatus.SKIPPED.databaseValue)
        args.add(MediaReviewStatus.DECIDED.databaseValue)
    }
    return where.toString() to args
}

/** Orders the stream by a contiguous, SQL-computable group key first and then by the user's
 * selected sort field within that group. */
private fun buildGroupedOrderBy(groupBy: GroupBy, sortField: SortField, desc: Boolean): String {
    val direction = if (desc) "DESC" else "ASC"
    val groupDirection = if (groupBy == GroupBy.RATING) "DESC" else direction
    val groupKey = sqlGroupKeyExpression(groupBy)
    val itemKey = when (sortField) {
        SortField.NAME, SortField.COUNT, SortField.RECENT -> "filename COLLATE NOCASE"
        SortField.DATE -> SQL_DATE_KEY
        SortField.SIZE -> "size"
        SortField.RATING -> "rating"
    }
    return "ORDER BY $groupKey $groupDirection, $itemKey $direction, full_path COLLATE NOCASE ASC"
}

internal fun MediaRepository.buildFilteredMediaQueryImpl(
    filter: MediaFilter,
    sortField: SortField,
    desc: Boolean,
    pathsOnly: Boolean,
    groupBy: GroupBy = GroupBy.NONE,
    excludedGroupValues: Set<String> = emptySet(),
): SupportSQLiteQuery {
    val (baseWhere, baseArgs) = buildFilterWhereClause(filter)
    val where = StringBuilder(baseWhere)
    val args = baseArgs.toMutableList()

    if (excludedGroupValues.isNotEmpty() && groupBy != GroupBy.NONE && groupBy != GroupBy.TAG) {
        val groupExpr = sqlGroupKeyExpression(groupBy)
        val typedValues: List<Any> = when (groupBy) {
            GroupBy.SIZE, GroupBy.RATING -> excludedGroupValues.mapNotNull { it.toIntOrNull() }
            GroupBy.MONTH, GroupBy.ALPHABET -> excludedGroupValues.toList()
            GroupBy.NONE, GroupBy.TAG -> emptyList()
        }
        if (typedValues.isNotEmpty()) {
            val placeholders = typedValues.joinToString(",") { "?" }
            where.append(" AND ($groupExpr) NOT IN ($placeholders)")
            args.addAll(typedValues)
        }
    }

    val mult = if (desc) -1 else 1
    val regularOrderBy = when (sortField) {
        SortField.NAME, SortField.COUNT, SortField.RECENT -> "ORDER BY filename COLLATE NOCASE ${if (desc) "DESC" else "ASC"}, full_path COLLATE NOCASE ASC"
        SortField.DATE -> "ORDER BY $mult * date_sort_key, full_path COLLATE NOCASE ASC"
        SortField.SIZE -> "ORDER BY $mult * size, full_path COLLATE NOCASE ASC"
        SortField.RATING -> "ORDER BY $mult * rating, last_modified DESC, full_path COLLATE NOCASE ASC"
    }
    val orderBy = if (groupBy == GroupBy.NONE || groupBy == GroupBy.TAG) regularOrderBy else buildGroupedOrderBy(groupBy, sortField, desc)

    val columns = if (pathsOnly) "full_path" else MEDIA_COLUMNS
    return SimpleSQLiteQuery("SELECT $columns FROM media WHERE $where $orderBy", args.toTypedArray())
}

internal fun MediaRepository.getFilteredMediaCountImpl(filter: MediaFilter): Int = try {
    val (where, args) = buildFilterWhereClause(filter)
    val query = SimpleSQLiteQuery("SELECT COUNT(*) FROM media WHERE $where", args.toTypedArray())
    GalleryDatabase.getInstance(context.applicationContext).query(query).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
} catch (_: Exception) {
    0
}

/**
 * Returns the exact per-group counts in display order for Paging-backed grouped views.
 * Keeps section header badges accurate beyond the initial page load and allows collapsed
 * groups to be excluded cleanly at SQL level without stalling Paging 3.
 */
internal fun MediaRepository.getGroupedMediaSummariesImpl(
    filter: MediaFilter,
    groupBy: GroupBy,
    desc: Boolean,
    unratedLabel: String,
): List<GroupSummary> {
    if (groupBy == GroupBy.NONE || groupBy == GroupBy.TAG) return emptyList()
    return try {
        val (where, args) = buildFilterWhereClause(filter)
        val groupExpr = sqlGroupKeyExpression(groupBy)
        val groupDirection = if (groupBy == GroupBy.RATING) "DESC" else if (desc) "DESC" else "ASC"
        val query = SimpleSQLiteQuery(
            "SELECT $groupExpr AS g, COUNT(*) FROM media WHERE $where GROUP BY g ORDER BY g $groupDirection",
            args.toTypedArray(),
        )
        GalleryDatabase.getInstance(context.applicationContext).query(query).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val count = cursor.getInt(1)
                    when (groupBy) {
                        GroupBy.MONTH -> {
                            val ym = cursor.getString(0).orEmpty()
                            val label = formatYearMonthLabel(ym)
                            add(GroupSummary(key = "month:$label", label = label, sqlValue = ym, count = count))
                        }
                        GroupBy.SIZE -> {
                            val bucket = cursor.getInt(0)
                            val sampleSize = when (bucket) {
                                5 -> 104_857_600L
                                4 -> 10_485_760L
                                3 -> 1_048_576L
                                2 -> 102_400L
                                else -> 0L
                            }
                            val label = sizeLabelFor(sampleSize)
                            add(GroupSummary(key = "size:$label", label = label, sqlValue = bucket.toString(), count = count))
                        }
                        GroupBy.ALPHABET -> {
                            val letter = cursor.getString(0).orEmpty().ifEmpty { "#" }
                            add(GroupSummary(key = "alphabet:$letter", label = letter, sqlValue = letter, count = count))
                        }
                        GroupBy.RATING -> {
                            val rating = cursor.getInt(0)
                            val label = ratingLabelFor(rating, unratedLabel)
                            add(
                                GroupSummary(
                                    key = "rating:$label",
                                    label = label,
                                    sqlValue = rating.toString(),
                                    count = count,
                                    ratingValue = rating.takeIf { it > 0 },
                                ),
                            )
                        }
                        GroupBy.NONE, GroupBy.TAG -> Unit
                    }
                }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
