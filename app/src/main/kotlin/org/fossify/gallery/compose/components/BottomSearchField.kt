package org.fossify.gallery.compose.components
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.Radius

/**
 * Editable, thumb-reachable search field shown at the bottom (above the nav bar / keyboard).
 * You type directly in it; results are rendered dynamically above by the caller. No extra sheet.
 */
@Composable
fun BottomSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    searching: Boolean = false,
) {
    val s = LocalSpacing.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = s.md, vertical = s.xs),
        shape = RoundedCornerShape(Radius.xl),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = s.lg, vertical = s.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(s.md))
            Box(Modifier.weight(1f)) {
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
            if (searching) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (value.isNotEmpty()) {
                Icon(Icons.Default.Close, stringResource(R.string.action_empty), modifier = Modifier.size(20.dp).clickable { onClear() }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
