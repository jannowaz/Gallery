package org.fossify.gallery.compose.screens.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.fossify.commons.extensions.formatSize
import org.fossify.gallery.R
import java.io.File
import java.util.Locale

/**
 * One recovery state for every full-screen viewer failure.
 *
 * A missing file and a decoder failure are different technical causes, but the useful user actions
 * are identical: explain what may have happened, retry the source, and expose concise diagnostics.
 * Keeping this in one component prevents one branch from silently losing those actions again.
 */
@Composable
internal fun ViewerMediaErrorState(
    file: File,
    isAvailable: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDetails by remember(file.absolutePath, isAvailable) { mutableStateOf(false) }
    val secondaryColor = Color.White.copy(alpha = 0.72f)
    val availability = stringResource(
        if (isAvailable) R.string.media_file_available else R.string.media_file_unavailable,
    )
    val type = remember(file.extension) {
        file.extension.ifBlank { "—" }.uppercase(Locale.ROOT)
    }
    val size = if (isAvailable) file.length().formatSize() else "—"

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                Icons.Default.BrokenImage,
                contentDescription = null,
                tint = secondaryColor,
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(R.string.error_loading_media),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                stringResource(R.string.media_load_error_hint),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Button(onClick = {
                    showDetails = false
                    onRetry()
                }) {
                    Text(stringResource(R.string.retry))
                }
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(stringResource(R.string.media_error_details))
                }
            }
            if (showDetails) {
                Text(
                    stringResource(
                        R.string.media_error_details_value,
                        type,
                        size,
                        availability,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
