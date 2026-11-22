package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.Radius

/**
 * Editable, thumb-reachable search field shown at the bottom (above the nav bar / keyboard).
 * You type directly in it; results are rendered dynamically above by the caller. No extra sheet.
 *
 * The leading icon doubles as the app's only drawer entry point (a Maps-style pattern: hamburger
 * when idle, back-arrow while search is active) so the screen doesn't need a separate FAB just to
 * open the drawer.
 */
@Composable
fun BottomSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onMenuClick: () -> Unit = {},
    isActive: Boolean = false,
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    searching: Boolean = false,
    onOpenFilterAndView: () -> Unit = {},
    filtersAvailable: Boolean = true,
    hasActiveFilter: Boolean = false,
    placeholderRes: Int = R.string.search_hint_compact,
) {
    val s = LocalSpacing.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = s.md, vertical = s.xs),
        shape = RoundedCornerShape(Radius.xl),
        // Keep the persistent chrome visually quiet. The search field is a dock above the
        // navigation bar, not a second content card; the outline supplies its edge after the
        // fill moved down one tonal step. This keeps the large pill from becoming the brightest
        // object on every screen.
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)),
        tonalElevation = 1.dp,
    ) {
        // Keep every action at Material's recommended 48dp touch target. The blur action already
        // establishes this row height, so the other controls gain usability without making the
        // bottom chrome any taller.
        Row(Modifier.padding(horizontal = s.xs, vertical = s.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = if (isActive) onClear else onMenuClick, modifier = Modifier.size(48.dp)) {
                // Keep the button's 48dp slot fixed. Crossfade only the glyph; AnimatedContent
                // made this slot participate in content measurement whenever focus or text state
                // changed, which was visible as a tiny horizontal/vertical hitch in the whole row.
                Crossfade(
                    targetState = isActive,
                    animationSpec = AppMotion.short,
                    label = "searchLeadingIcon",
                ) { active ->
                    Icon(
                        if (active) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                        contentDescription = stringResource(if (active) R.string.cd_close else R.string.nav_menu_open),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val placeholderText = stringResource(placeholderRes)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    // maxLines/overflow needed now that the row has one more trailing icon (the
                    // blur toggle) - the placeholder previously fit on one line at the old width,
                    // wrapping to two otherwise, which grew this row's height right back up again.
                    Text(
                        placeholderText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 20.dp)
                        .semantics { contentDescription = placeholderText }
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                )
            }
            // The slot is always exactly 48dp. Crossfade has no size transform, so switching from
            // spinner -> clear -> empty cannot remeasure the search row while the user is typing.
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Crossfade(
                    targetState = searching to value.isNotEmpty(),
                    animationSpec = AppMotion.short,
                    label = "searchTrailingIcon",
                ) { (isSearching, hasValue) ->
                    when {
                        isSearching -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                        hasValue -> IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, stringResource(R.string.cd_clear_search), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        else -> Spacer(Modifier.size(48.dp))
                    }
                }
            }
            // Strict 1:1 icon semantics across every screen: FilterList always opens Filters,
            // Tune always opens View & Sort settings.
            Box {
                IconButton(onClick = onOpenFilterAndView, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (filtersAvailable) Icons.Default.FilterList else Icons.Default.Tune,
                        contentDescription = stringResource(if (filtersAvailable) R.string.filter_label else R.string.view_settings_title),
                        tint = if (filtersAvailable && hasActiveFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filtersAvailable && hasActiveFilter) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 6.dp).size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
            }
        }
    }
}
