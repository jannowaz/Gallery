package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
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
) {
    val s = LocalSpacing.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = s.md, vertical = s.xs),
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = s.xs, vertical = s.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = if (isActive) onClear else onMenuClick) {
                Icon(
                    if (isActive) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                    contentDescription = stringResource(if (isActive) R.string.cd_close else R.string.nav_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp).focusRequester(focusRequester).onFocusChanged { onFocusChanged(it.isFocused) },
                )
            }
            AnimatedContent(targetState = searching to value.isNotEmpty(), transitionSpec = { fadeIn(AppMotion.short) togetherWith fadeOut(AppMotion.short) }, label = "searchTrailingIcon") { (isSearching, hasValue) ->
                when {
                    isSearching -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                    hasValue -> IconButton(onClick = onClear) { Icon(Icons.Default.Close, stringResource(R.string.action_empty), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else -> Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}
