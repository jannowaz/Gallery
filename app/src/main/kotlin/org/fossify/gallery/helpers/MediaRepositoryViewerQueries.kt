package org.fossify.gallery.helpers

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.models.Medium
import org.fossify.gallery.viewmodels.MediaFilter

private data class ViewerOrderSpec(
    val primary: String,
    val primaryDescending: Boolean,
    val secondary: String? = null,
    val secondaryDescending: Boolean = false,
)

internal suspend fun MediaRepository.getViewerPathWindowImpl(
    filter: MediaFilter,
    field: SortField,
    desc: Boolean,
    groupBy: GroupBy,
    anchorPath: String,
    radius: Int,
): ViewerPathWindow = withContext(Dispatchers.IO) {
    val safeRadius = radius.coerceIn(1, 12)
    val anchor = getMediaByPaths(listOf(anchorPath)).firstOrNull()
        ?: return@withContext ViewerPathWindow(emptyList(), false, false)
    val (where, whereArgs) = buildFilterWhereClause(filter)
    val anchorIsIncluded = GalleryDatabase.getInstance(context.applicationContext).query(
        SimpleSQLiteQuery(
            "SELECT full_path FROM media WHERE full_path = ? COLLATE NOCASE AND $where LIMIT 1",
            arrayOf(anchorPath, *whereArgs.toTypedArray()),
        ),
    ).use { it.moveToFirst() }
    if (!anchorIsIncluded) return@withContext ViewerPathWindow(emptyList(), false, false)

    val before = queryViewerPaths(filter, field, desc, groupBy, anchor, ViewerLoadDirection.BEFORE, safeRadius + 1)
    val after = queryViewerPaths(filter, field, desc, groupBy, anchor, ViewerLoadDirection.AFTER, safeRadius + 1)
    val beforeWindow = before.take(safeRadius).asReversed()
    val afterWindow = after.take(safeRadius)
    ViewerPathWindow(
        paths = (beforeWindow + anchorPath + afterWindow).distinct(),
        hasPrevious = before.size > safeRadius,
        hasNext = after.size > safeRadius,
    )
}

internal suspend fun MediaRepository.getViewerPathChunkImpl(
    filter: MediaFilter,
    field: SortField,
    desc: Boolean,
    groupBy: GroupBy,
    anchorPath: String,
    direction: ViewerLoadDirection,
    limit: Int,
): ViewerPathChunk = withContext(Dispatchers.IO) {
    val anchor = getMediaByPaths(listOf(anchorPath)).firstOrNull()
        ?: return@withContext ViewerPathChunk(emptyList(), false)
    val safeLimit = limit.coerceIn(1, 48)
    val loaded = queryViewerPaths(filter, field, desc, groupBy, anchor, direction, safeLimit + 1)
    val paths = if (direction == ViewerLoadDirection.BEFORE) loaded.asReversed() else loaded
    ViewerPathChunk(paths.take(safeLimit), loaded.size > safeLimit)
}

private fun viewerOrderSpec(field: SortField, desc: Boolean, groupBy: GroupBy, alias: String = ""): ViewerOrderSpec {
    fun col(name: String) = if (alias.isBlank()) name else "$alias.$name"
    val dateKey = "CASE WHEN ${col("date_sort_key")} > 0 THEN ${col("date_sort_key")} WHEN ${col("date_taken")} > 0 THEN ${col("date_taken")} ELSE ${col("last_modified")} END"
    val grouped = groupBy != GroupBy.NONE && groupBy != GroupBy.TAG
    if (!grouped) {
        return when (field) {
            SortField.NAME, SortField.COUNT, SortField.RECENT -> ViewerOrderSpec("${col("filename")} COLLATE NOCASE", desc)
            SortField.DATE -> ViewerOrderSpec(col("date_sort_key"), desc)
            SortField.SIZE -> ViewerOrderSpec(col("size"), desc)
            SortField.RATING -> ViewerOrderSpec(col("rating"), desc, col("last_modified"), secondaryDescending = true)
        }
    }

    val groupKey = when (groupBy) {
        GroupBy.MONTH -> "strftime('%Y-%m', ($dateKey) / 1000, 'unixepoch', 'localtime')"
        GroupBy.SIZE -> "CASE WHEN ${col("size")} >= 104857600 THEN 5 WHEN ${col("size")} >= 10485760 THEN 4 WHEN ${col("size")} >= 1048576 THEN 3 WHEN ${col("size")} >= 102400 THEN 2 ELSE 1 END"
        GroupBy.ALPHABET -> "CASE WHEN substr(${col("filename")}, 1, 1) GLOB '[A-Za-z]' THEN upper(substr(${col("filename")}, 1, 1)) ELSE '#' END"
        GroupBy.RATING -> col("rating")
        GroupBy.NONE, GroupBy.TAG -> "0"
    }
    val itemKey = when (field) {
        SortField.NAME, SortField.COUNT, SortField.RECENT -> "${col("filename")} COLLATE NOCASE"
        SortField.DATE -> dateKey
        SortField.SIZE -> col("size")
        SortField.RATING -> col("rating")
    }
    val groupDescending = if (groupBy == GroupBy.RATING) true else desc
    return ViewerOrderSpec(groupKey, groupDescending, itemKey, desc)
}

private fun anchorScalar(expression: String): String =
    "(SELECT $expression FROM media AS anchor WHERE anchor.full_path = ? COLLATE NOCASE LIMIT 1)"

private fun viewerBoundary(
    spec: ViewerOrderSpec,
    direction: ViewerLoadDirection,
    anchorPath: String,
): Pair<String, List<Any?>> {
    val before = direction == ViewerLoadDirection.BEFORE
    val primaryOperator = when {
        before && spec.primaryDescending -> ">"
        before -> "<"
        spec.primaryDescending -> "<"
        else -> ">"
    }
    val primaryAnchor = anchorScalar(viewerExpressionWithAlias(spec.primary, "anchor"))
    val args = mutableListOf<Any?>(anchorPath, anchorPath)
    val primaryBefore = "${spec.primary} $primaryOperator $primaryAnchor"
    val equalPrimary = "${spec.primary} = $primaryAnchor"

    if (spec.secondary == null) {
        val pathOperator = if (before) "<" else ">"
        val pathAnchor = anchorScalar("anchor.full_path COLLATE NOCASE")
        args += anchorPath
        return "($primaryBefore OR ($equalPrimary AND full_path COLLATE NOCASE $pathOperator $pathAnchor))" to args
    }

    val secondaryOperator = when {
        before && spec.secondaryDescending -> ">"
        before -> "<"
        spec.secondaryDescending -> "<"
        else -> ">"
    }
    val secondaryAnchor = anchorScalar(viewerExpressionWithAlias(spec.secondary, "anchor"))
    val equalSecondary = "${spec.secondary} = $secondaryAnchor"
    val pathOperator = if (before) "<" else ">"
    val pathAnchor = anchorScalar("anchor.full_path COLLATE NOCASE")
    args += anchorPath
    args += anchorPath
    args += anchorPath
    return "($primaryBefore OR ($equalPrimary AND (${spec.secondary} $secondaryOperator $secondaryAnchor OR ($equalSecondary AND full_path COLLATE NOCASE $pathOperator $pathAnchor))))" to args
}

private fun viewerExpressionWithAlias(expression: String, alias: String): String {
    val columns = listOf("date_sort_key", "date_taken", "last_modified", "filename", "size", "rating")
    var result = expression
    columns.forEach { column ->
        result = Regex("(?<![A-Za-z0-9_.])$column(?![A-Za-z0-9_])").replace(result, "$alias.$column")
    }
    return result
}

private fun MediaRepository.queryViewerPaths(
    filter: MediaFilter,
    field: SortField,
    desc: Boolean,
    groupBy: GroupBy,
    anchor: Medium,
    direction: ViewerLoadDirection,
    limit: Int,
): List<String> {
    val (where, whereArgs) = buildFilterWhereClause(filter)
    val spec = viewerOrderSpec(field, desc, groupBy)
    val (boundary, boundaryArgs) = viewerBoundary(spec, direction, anchor.path)
    val reverseOrder = direction == ViewerLoadDirection.BEFORE
    val primaryDirection = if (spec.primaryDescending != reverseOrder) "DESC" else "ASC"
    val secondary = spec.secondary?.let {
        val secondaryDirection = if (spec.secondaryDescending != reverseOrder) "DESC" else "ASC"
        ", $it $secondaryDirection"
    }.orEmpty()
    val pathDirection = if (reverseOrder) "DESC" else "ASC"
    val args = ArrayList<Any?>(whereArgs.size + boundaryArgs.size + 1)
    args.addAll(whereArgs)
    args.addAll(boundaryArgs)
    args.add(limit)
    val query = SimpleSQLiteQuery(
        "SELECT full_path FROM media WHERE $where AND $boundary ORDER BY ${spec.primary} $primaryDirection$secondary, full_path COLLATE NOCASE $pathDirection LIMIT ?",
        args.toTypedArray(),
    )
    return GalleryDatabase.getInstance(context.applicationContext).query(query).use { cursor ->
        buildList(cursor.count) { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }
}
