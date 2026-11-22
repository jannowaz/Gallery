package org.fossify.gallery.compose.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.formatSize
import org.fossify.gallery.R
import org.fossify.gallery.compose.util.ExplorerNavigationBus
import org.fossify.gallery.helpers.MediaInfoData
import org.fossify.gallery.helpers.MediaInfoReader
import java.text.DateFormat
import java.util.Date
import java.io.File

/** A portrait-friendly replacement for the generic file-properties dialog for one medium. */
@Composable
fun MediaInfoDialog(
    path: String,
    onDismiss: () -> Unit,
) {
    var info by remember(path) { mutableStateOf<MediaInfoData?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        info = null
        error = null
        withContext(Dispatchers.IO) { runCatching { MediaInfoReader.read(path) } }
            .onSuccess { info = it }
            .onFailure { error = it.message.orEmpty() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.media_info_title)) },
        text = {
            when {
                info != null -> MediaInfoContent(info = requireNotNull(info))
                error != null -> Text(
                    stringResource(R.string.info_error, error.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    File(path).parent?.let(ExplorerNavigationBus::open)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.show_in_explorer))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cd_close))
            }
        },
    )
}

@Composable
private fun MediaInfoContent(info: MediaInfoData) {
    val descriptive = info.descriptive
    val technicalValues = listOfNotNull(
        info.camera?.let { stringResource(R.string.exif_camera) to it },
        info.iso?.let { stringResource(R.string.exif_iso) to it },
        info.focalLength?.let { stringResource(R.string.exif_focal_length) to it },
        info.aperture?.let { stringResource(R.string.exif_aperture) to it },
        info.exposure?.let { stringResource(R.string.exif_exposure) to it },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        InfoSectionTitle(stringResource(R.string.media_info_file_section))
        InfoValue(stringResource(R.string.name), info.name)
        InfoValue(stringResource(R.string.path), info.path)
        InfoValue(stringResource(R.string.sort_size), info.sizeBytes.formatSize())
        if (info.width != null && info.height != null) {
            InfoValue(stringResource(R.string.exif_resolution), "${info.width} × ${info.height}")
        }
        info.durationMillis?.takeIf { it >= 0L }?.let { duration ->
            InfoValue(
                stringResource(R.string.media_info_duration),
                DateUtils.formatElapsedTime(duration / 1000L),
            )
        }
        InfoValue(
            stringResource(R.string.last_modified),
            formatDateTime(info.modifiedMillis),
        )
        info.dateTakenMillis?.takeIf { it > 0L }?.let { dateTaken ->
            InfoValue(stringResource(R.string.date_taken_full), formatDateTime(dateTaken))
        }

        InfoDivider()
        InfoSectionTitle(stringResource(R.string.media_info_metadata_section))
        if (descriptive.isEmpty) {
            Text(
                stringResource(
                    if (info.metadataReadFailed) R.string.media_info_metadata_unavailable
                    else R.string.media_info_metadata_empty,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            descriptive.title?.let { InfoValue(stringResource(R.string.metadata_title), it) }
            descriptive.description?.let { InfoValue(stringResource(R.string.metadata_description), it) }
            descriptive.comment?.let { InfoValue(stringResource(R.string.metadata_comment), it) }
        }

        if (technicalValues.isNotEmpty()) {
            InfoDivider()
            InfoSectionTitle(stringResource(R.string.media_info_technical_section))
            technicalValues.forEach { (label, value) -> InfoValue(label, value) }
        }
    }
}

@Composable
private fun InfoSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun InfoValue(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun InfoDivider() {
    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun formatDateTime(timestamp: Long): String {
    // Observe the Compose configuration so dates update when the app language changes.
    val locales = LocalConfiguration.current.locales
    val locale = if (!locales.isEmpty) locales[0] else null
    return remember(timestamp, locale) {
        val date = Date(timestamp)
        val formattedDate = if (locale != null) {
            DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date)
        } else {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
        }
        val formattedTime = if (locale != null) {
            DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(date)
        } else {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
        }
        "$formattedDate · $formattedTime"
    }
}
