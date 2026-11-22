package org.fossify.gallery.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.ListDensity
import org.fossify.gallery.compose.screens.SortField
import org.fossify.gallery.compose.screens.ViewSettings
import org.fossify.gallery.compose.screens.ViewType
import org.fossify.gallery.helpers.GroupBy

/** One compact, localized description of the active sort, layout and grouping settings. */
@Composable
fun viewSettingsSummary(settings: ViewSettings, includeGrouping: Boolean = true): String {
    val sort = when (settings.sortBy) {
        SortField.NAME -> stringResource(R.string.sort_name)
        SortField.DATE -> stringResource(R.string.sort_date)
        SortField.SIZE -> stringResource(R.string.sort_size)
        SortField.RATING -> stringResource(R.string.sort_rating)
        SortField.COUNT -> stringResource(R.string.sort_by_item_count)
        SortField.RECENT -> stringResource(R.string.sort_recently_used)
    } + if (settings.sortDesc) " ↓" else " ↑"

    val view = when (settings.viewType) {
        ViewType.GRID -> "${settings.columnCount} ${stringResource(R.string.columns)}"
        ViewType.MOSAIC -> "${stringResource(R.string.view_type_mosaic)} · ${settings.columnCount} ${stringResource(R.string.columns)}"
        ViewType.LIST -> stringResource(
            if (settings.listDensity == ListDensity.COMPACT) R.string.list_density_compact else R.string.list_density_comfortable,
        ).let { density -> "${stringResource(R.string.view_type_list)} · $density" }
    }

    return buildList {
        add(sort)
        add(view)
        if (includeGrouping && settings.groupBy != GroupBy.NONE) {
            val group = when (settings.groupBy) {
                GroupBy.MONTH -> stringResource(R.string.group_by_month)
                GroupBy.TAG -> stringResource(R.string.group_by_tag)
                GroupBy.RATING -> stringResource(R.string.group_by_rating)
                GroupBy.SIZE -> stringResource(R.string.group_by_size)
                GroupBy.ALPHABET -> stringResource(R.string.group_by_alphabet)
                GroupBy.NONE -> ""
            }
            // The header only has room for the active value. Repeating the generic label made the
            // useful part ("Month", "Tags", …) disappear behind an ellipsis on phones.
            add(group)
        }
    }.joinToString(" · ")
}
