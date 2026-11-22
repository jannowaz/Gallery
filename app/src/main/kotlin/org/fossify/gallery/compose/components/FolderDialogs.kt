package org.fossify.gallery.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.formatSize
import org.fossify.gallery.R
import org.fossify.gallery.compose.util.ExplorerNavigationBus
import java.io.File
import java.text.DateFormat
import java.util.Date

internal data class FolderInfoData(
    val name: String,
    val path: String,
    val directFileCount: Int,
    val directSubfolderCount: Int,
    val totalSizeBytes: Long,
    val modifiedMillis: Long,
)

internal object FolderInfoReader {
    fun read(path: String): FolderInfoData {
        val dir = File(path)
        val children = dir.listFiles().orEmpty()
        var files = 0
        var folders = 0
        var totalSize = 0L
        for (child in children) {
            if (child.isDirectory) {
                folders++
            } else {
                files++
                totalSize += child.length().coerceAtLeast(0L)
            }
        }
        return FolderInfoData(
            name = dir.name.ifEmpty { path },
            path = path,
            directFileCount = files,
            directSubfolderCount = folders,
            totalSizeBytes = totalSize,
            modifiedMillis = dir.lastModified(),
        )
    }
}

/** Material 3 bottom sheet displaying folder properties (replaces legacy XML PropertiesDialog). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderInfoDialog(
    path: String,
    onDismiss: () -> Unit,
    mediaCountHint: Int? = null,
    totalSizeHint: Long? = null,
) {
    var info by remember(path) { mutableStateOf<FolderInfoData?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(path) {
        info = null
        error = null
        withContext(Dispatchers.IO) { runCatching { FolderInfoReader.read(path) } }
            .onSuccess { info = it }
            .onFailure { error = it.message.orEmpty() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            LibrarySheetHeader(
                title = stringResource(R.string.action_properties),
                onDismiss = onDismiss,
            )
            Spacer(Modifier.height(8.dp))
            when {
                info != null -> FolderInfoContent(
                    info = requireNotNull(info),
                    mediaCountHint = mediaCountHint,
                    totalSizeHint = totalSizeHint,
                )
                error != null -> Text(
                    text = stringResource(R.string.info_error, error.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = {
                    ExplorerNavigationBus.open(path)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.show_in_explorer))
            }
        }
    }
}

@Composable
private fun FolderInfoContent(
    info: FolderInfoData,
    mediaCountHint: Int?,
    totalSizeHint: Long?,
) {
    val fileCount = if (info.directFileCount > 0) info.directFileCount else (mediaCountHint ?: 0)
    val totalSize = if (info.totalSizeBytes > 0L) info.totalSizeBytes else (totalSizeHint ?: 0L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        FolderInfoRow(label = stringResource(R.string.name), value = info.name)
        FolderInfoRow(label = stringResource(R.string.path), value = info.path)
        FolderInfoRow(
            label = stringResource(R.string.folders),
            value = buildString {
                append(stringResource(R.string.files_count, fileCount))
                if (info.directSubfolderCount > 0) {
                    append(" · ")
                    append(stringResource(R.string.folder_count, info.directSubfolderCount))
                }
            },
            tabular = true,
        )
        FolderInfoRow(
            label = stringResource(R.string.sort_size),
            value = totalSize.formatSize(),
            tabular = true,
        )
        if (info.modifiedMillis > 0L) {
            FolderInfoRow(
                label = stringResource(R.string.last_modified),
                value = formatFolderDateTime(info.modifiedMillis),
                tabular = true,
            )
        }
    }
}

@Composable
private fun FolderInfoRow(label: String, value: String, tabular: Boolean = false) {
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
                style = if (tabular) {
                    MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun formatFolderDateTime(timestamp: Long): String {
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

/** Material 3 notice shown once before locking a folder (replaces legacy XML FolderLockingNoticeDialog). */
@Composable
fun FolderLockingNoticeDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(org.fossify.commons.R.string.lock_folder)) },
        text = { Text(stringResource(org.fossify.commons.R.string.lock_folder_notice)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(org.fossify.commons.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(org.fossify.commons.R.string.cancel))
            }
        },
    )
}
