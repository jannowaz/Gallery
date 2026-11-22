package org.fossify.gallery.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.compose.screens.ViewSettings
import org.fossify.gallery.compose.screens.ViewType
import org.fossify.gallery.helpers.GroupBy

/** Pure formatter shared by [viewSettingsSummary] and unit tests. */
internal fun formatViewSettingsSummary(
    settings: ViewSettings,
    includeGrouping: Boolean,
    sortLabel: (SortField) -> String,
    viewLabel: (ViewType) -> String,
    groupLabel: (GroupBy) -> String,
): String {
    val sort = sortLabel(settings.sortBy) + if (settings.sortDesc) " ↓" else " ↑"
    // Grid column count is immediately visible right below the header; keep the subtitle calm
    // and only call out non-default layout modes (Mosaic / List).
    val view = if (settings.viewType == ViewType.GRID) null else viewLabel(settings.viewType)

    return buildList {
        add(sort)
        if (view != null) add(view)
        if (includeGrouping && settings.groupBy != GroupBy.NONE) {
            // The header only has room for the active value. Repeating the generic label made the
            // useful part ("Month", "Tags", …) disappear behind an ellipsis on phones.
            val group = groupLabel(settings.groupBy)
            if (group.isNotEmpty()) add(group)
        }
    }.joinToString(" · ")
}

/** One compact, localized description of the active sort, layout and grouping settings. */
@Composable
fun viewSettingsSummary(settings: ViewSettings, includeGrouping: Boolean = true): String {
    val sortLabel = when (settings.sortBy) {
        SortField.NAME -> stringResource(R.string.sort_name)
        SortField.DATE -> stringResource(R.string.sort_date)
        SortField.SIZE -> stringResource(R.string.sort_size)
        SortField.RATING -> stringResource(R.string.sort_rating)
        SortField.COUNT -> stringResource(R.string.sort_by_item_count)
        SortField.RECENT -> stringResource(R.string.sort_recently_used)
    }
    val viewLabel = when (settings.viewType) {
        ViewType.GRID -> stringResource(R.string.view_type_grid)
        ViewType.MOSAIC -> stringResource(R.string.view_type_mosaic)
        ViewType.LIST -> stringResource(R.string.view_type_list)
    }
    val groupLabel = when (settings.groupBy) {
        GroupBy.MONTH -> stringResource(R.string.group_by_month)
        GroupBy.TAG -> stringResource(R.string.group_by_tag)
        GroupBy.RATING -> stringResource(R.string.group_by_rating)
        GroupBy.SIZE -> stringResource(R.string.group_by_size)
        GroupBy.ALPHABET -> stringResource(R.string.group_by_alphabet)
        GroupBy.NONE -> ""
    }
    return formatViewSettingsSummary(
        settings = settings,
        includeGrouping = includeGrouping,
        sortLabel = { sortLabel },
        viewLabel = { viewLabel },
        groupLabel = { groupLabel },
    )
}
