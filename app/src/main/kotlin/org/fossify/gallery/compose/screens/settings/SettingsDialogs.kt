package org.fossify.gallery.compose.screens.settings

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.EXT_CAMERA_MODEL
import org.fossify.gallery.helpers.EXT_DATE_TAKEN
import org.fossify.gallery.helpers.EXT_EXIF_PROPERTIES
import org.fossify.gallery.helpers.EXT_GPS
import org.fossify.gallery.helpers.EXT_LAST_MODIFIED
import org.fossify.gallery.helpers.EXT_NAME
import org.fossify.gallery.helpers.EXT_PATH
import org.fossify.gallery.helpers.EXT_RESOLUTION
import org.fossify.gallery.helpers.EXT_SIZE
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.SettingsBackupHelper
import org.fossify.gallery.workers.MediaSyncWorker
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ManageFoldersDialog(
    isExcluded: Boolean,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val conf = ctx.config
    var folders by remember(isExcluded) {
        mutableStateOf(
            (if (isExcluded) conf.excludedFolders else conf.includedFolders).sorted()
        )
    }
    var showPicker by remember { mutableStateOf(false) }

    fun refreshFolders() {
        folders = (if (isExcluded) conf.excludedFolders else conf.includedFolders).sorted()
        RefreshBus.trigger()
        MediaSyncWorker.scheduleIncrementalSync(ctx)
        onChanged()
    }

    val title = stringResource(if (isExcluded) R.string.set_excluded_folders else R.string.set_included_folders)
    val emptyText = stringResource(if (isExcluded) R.string.excluded_activity_placeholder else R.string.included_activity_placeholder)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (folders.isEmpty()) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    folders.forEach { path ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = File(path).name.ifBlank { path },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (isExcluded) conf.removeExcludedFolder(path) else conf.removeIncludedFolder(path)
                                    refreshFolders()
                                },
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(org.fossify.commons.R.string.remove))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showPicker = true }) {
                Text(stringResource(R.string.add_folder))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(org.fossify.commons.R.string.ok))
            }
        },
    )

    if (showPicker) {
        org.fossify.gallery.compose.screens.FolderPathPickerSheet(
            title = title,
            initialPath = conf.lastFilepickerPath,
            onDismiss = { showPicker = false },
            onPathSelected = { selectedPath ->
                conf.lastFilepickerPath = selectedPath
                if (isExcluded) {
                    conf.addExcludedFolder(selectedPath)
                } else {
                    conf.addIncludedFolder(selectedPath)
                }
                showPicker = false
                refreshFolders()
            },
        )
    }
}

@Composable
internal fun SettingsChoiceDialog(
    title: String,
    selectedValue: Int,
    options: List<Pair<Int, String>>,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelected(value) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selectedValue, onClick = { onSelected(value) })
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun ExtendedDetailsDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var result by remember { mutableStateOf(current) }
    val items = listOf(
        EXT_NAME to stringResource(R.string.sort_name), EXT_PATH to stringResource(R.string.path), EXT_SIZE to stringResource(R.string.sort_size),
        EXT_RESOLUTION to stringResource(R.string.exif_resolution), EXT_LAST_MODIFIED to stringResource(R.string.last_modified),
        EXT_DATE_TAKEN to stringResource(R.string.date_taken_full), EXT_CAMERA_MODEL to stringResource(R.string.exif_camera),
        EXT_EXIF_PROPERTIES to "EXIF", EXT_GPS to "GPS"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_detail_items)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                items.forEach { (flag, label) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = result and flag != 0, onCheckedChange = { if (it) result += flag else result -= flag })
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { if (result and flag == 0) result += flag else result -= flag })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(result); onDismiss() }) { Text(stringResource(org.fossify.commons.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.US, "%.1f KB", bytes / 1_000.0)
    bytes > 0 -> "$bytes B"
    else -> "0 KB"
}

@Composable
internal fun LibraryHealthNav(ctx: Context, scope: CoroutineScope) {
    val repo = LocalMediaRepository.current
    var subtitle by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var gap by remember { mutableStateOf<MediaRepository.LibraryHealth?>(null) }

    SettingsNav(
        stringResource(R.string.library_health),
        if (checking) stringResource(R.string.library_health_checking) else subtitle,
    ) {
        if (checking) return@SettingsNav
        checking = true
        scope.launch {
            val health = withContext(Dispatchers.IO) { repo.libraryHealth() }
            checking = false
            subtitle = if (health.isSignificant) {
                ctx.getString(R.string.library_health_gap, health.missing)
            } else {
                ctx.getString(R.string.library_health_ok, health.inLibrary)
            }
            if (health.isSignificant) gap = health
        }
    }

    gap?.let { health ->
        AlertDialog(
            onDismissRequest = { gap = null },
            title = { Text(stringResource(R.string.library_health_dialog_title)) },
            text = { Text(stringResource(R.string.library_health_dialog_msg, health.inMediaStore, health.inLibrary)) },
            confirmButton = {
                TextButton(onClick = {
                    MediaSyncWorker.scheduleFullRescan(ctx)
                    Toast.makeText(ctx, R.string.library_health_started, Toast.LENGTH_SHORT).show()
                    gap = null
                }) { Text(stringResource(R.string.library_health_rescan)) }
            },
            dismissButton = { TextButton(onClick = { gap = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun ClearCacheNav(ctx: Context, scope: CoroutineScope) {
    val a = ctx as? Activity
    var cacheSize by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { cacheSize = withContext(Dispatchers.IO) { a?.cacheDir?.let { dir -> if (dir.exists()) formatCacheSize(dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }) else "" } ?: "" } }
    SettingsNav(stringResource(R.string.set_clear_cache), cacheSize.ifEmpty { "0 KB" }) {
        scope.launch(Dispatchers.IO) { a?.cacheDir?.deleteRecursively(); withContext(Dispatchers.Main) { cacheSize = "0 KB" } }
    }
}

@Composable
internal fun FavoritesExportNav(ctx: Context, conf: org.fossify.gallery.helpers.Config, scope: CoroutineScope) {
    val repo = LocalMediaRepository.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                val paths = repo.getValidFavoritePaths()
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w -> paths.forEach { w.write("$it\n") } }
                withContext(Dispatchers.Main) { Toast.makeText(ctx, ctx.getString(R.string.favorites_exported, paths.size), Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Favorites export failed", e)
                withContext(Dispatchers.Main) { Toast.makeText(ctx, ctx.getString(org.fossify.commons.R.string.unknown_error_occurred), Toast.LENGTH_SHORT).show() }
            }
        }
    }
    SettingsNav(stringResource(R.string.set_export_favorites)) {
        exportLauncher.launch("gallery-favorites_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.txt")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdvancedScanDialog(
    onDismiss: () -> Unit,
    onStart: (folder: String?, start: Long, end: Long, incremental: Boolean, chargingOnly: Boolean) -> Unit,
) {
    var folderPath by remember { mutableStateOf<String?>(null) }
    var startDate by remember { mutableLongStateOf(0L) }
    var endDate by remember { mutableLongStateOf(Long.MAX_VALUE) }
    var incremental by remember { mutableStateOf(false) }
    var chargingOnly by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_scan_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.select_folder), style = MaterialTheme.typography.labelSmall)
                Surface(
                    onClick = { showFolderPicker = true },
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(folderPath ?: stringResource(R.string.entire_device), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.date_range), style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radius.sm), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(if (startDate == 0L) stringResource(R.string.from_beginning) else DateFormat.getDateInstance().format(Date(startDate)), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radius.sm), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(if (endDate == Long.MAX_VALUE) stringResource(R.string.until_today) else DateFormat.getDateInstance().format(Date(endDate)), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = incremental, onValueChange = { incremental = it })
                    Text(stringResource(R.string.only_new_files_since_last_scan), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = chargingOnly, onValueChange = { chargingOnly = it })
                    Text(stringResource(R.string.only_while_charging), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(folderPath, startDate, endDate, incremental, chargingOnly); onDismiss() }) { Text(stringResource(R.string.start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (showFolderPicker) {
        org.fossify.gallery.compose.screens.FolderPathPickerSheet(
            title = stringResource(R.string.select_folder),
            initialPath = folderPath.orEmpty(),
            onDismiss = { showFolderPicker = false },
            onPathSelected = { path ->
                folderPath = path
                showFolderPicker = false
            },
        )
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = if (startDate > 0) startDate else System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = datePickerState.selectedDateMillis ?: 0L
                    showStartDatePicker = false
                }) { Text(stringResource(org.fossify.commons.R.string.ok)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = if (endDate < Long.MAX_VALUE) endDate else System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = datePickerState.selectedDateMillis ?: Long.MAX_VALUE
                    showEndDatePicker = false
                }) { Text(stringResource(org.fossify.commons.R.string.ok)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
internal fun Checkbox(checked: Boolean, onValueChange: (Boolean) -> Unit) {
    androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onValueChange)
}

@Composable
internal fun FavoritesImportNav(ctx: Context, conf: org.fossify.gallery.helpers.Config, scope: CoroutineScope) {
    val repo = LocalMediaRepository.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                val lines = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines() ?: emptyList()
                var imported = 0
                lines.forEach { line ->
                    if (File(line).exists()) { repo.addFavoriteByPath(line); imported++ }
                }
                withContext(Dispatchers.Main) { Toast.makeText(ctx, ctx.getString(R.string.favorites_imported, imported), Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Favorites import failed", e)
                withContext(Dispatchers.Main) { Toast.makeText(ctx, ctx.getString(org.fossify.commons.R.string.unknown_error_occurred), Toast.LENGTH_SHORT).show() }
            }
        }
    }
    SettingsNav(stringResource(R.string.set_import_favorites)) { importLauncher.launch(arrayOf("text/plain")) }
}

@Composable
internal fun FullBackupExportNav(ctx: Context, scope: CoroutineScope) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val out = ctx.contentResolver.openOutputStream(uri)
            if (out != null) {
                val success = SettingsBackupHelper.exportSettings(ctx, out)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(if (success) R.string.backup_created else R.string.export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    SettingsNav(stringResource(R.string.export_full_backup), stringResource(R.string.export_full_backup_subtitle)) {
        exportLauncher.launch("gallery-full-backup_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.json")
    }
}

@Composable
internal fun FullBackupImportNav(ctx: Context, scope: CoroutineScope) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ins = ctx.contentResolver.openInputStream(uri)
            if (ins != null) {
                val success = SettingsBackupHelper.importSettings(ctx, ins)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(if (success) R.string.backup_restored else R.string.import_failed), Toast.LENGTH_LONG).show()
                    if (success) {
                        (ctx as? Activity)?.let {
                            it.finish()
                            it.startActivity(it.intent)
                        }
                    }
                }
            }
        }
    }
    SettingsNav(stringResource(R.string.import_full_backup), stringResource(R.string.import_full_backup_subtitle)) {
        importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
    }
}

internal fun getViewTypeLabel(ctx: Context, viewType: Int): String = when {
    viewType == VIEW_TYPE_GRID -> ctx.getString(R.string.view_type_grid)
    viewType == 2 -> ctx.getString(R.string.view_type_mosaic)
    else -> ctx.getString(R.string.view_type_list)
}

internal fun getExtendedDetailsSummary(ctx: Context, details: Int): String {
    val labels = mutableListOf<String>()
    if (details and EXT_NAME != 0) labels.add(ctx.getString(R.string.sort_name))
    if (details and EXT_PATH != 0) labels.add(ctx.getString(R.string.path))
    if (details and EXT_SIZE != 0) labels.add(ctx.getString(R.string.sort_size))
    if (details and EXT_RESOLUTION != 0) labels.add(ctx.getString(R.string.exif_resolution))
    if (details and EXT_LAST_MODIFIED != 0) labels.add(ctx.getString(R.string.sort_date))
    if (details and EXT_DATE_TAKEN != 0) labels.add(ctx.getString(R.string.date_taken_short))
    if (details and EXT_CAMERA_MODEL != 0) labels.add(ctx.getString(R.string.exif_camera))
    if (details and EXT_EXIF_PROPERTIES != 0) labels.add("EXIF")
    if (details and EXT_GPS != 0) labels.add("GPS")
    return if (labels.isEmpty()) ctx.getString(R.string.default_value) else labels.take(3).joinToString(", ") + if (labels.size > 3) "..." else ""
}
