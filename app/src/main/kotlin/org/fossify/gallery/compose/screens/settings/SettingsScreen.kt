package org.fossify.gallery.compose.screens.settings
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import org.fossify.gallery.workers.MetadataSyncWorker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.gallery.activities.IncludedFoldersActivity
import org.fossify.gallery.activities.ExcludedFoldersActivity
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.BOTTOM_ACTION_CHANGE_ORIENTATION
import org.fossify.gallery.helpers.BOTTOM_ACTION_COPY
import org.fossify.gallery.helpers.BOTTOM_ACTION_DELETE
import org.fossify.gallery.helpers.BOTTOM_ACTION_EDIT
import org.fossify.gallery.helpers.BOTTOM_ACTION_MOVE
import org.fossify.gallery.helpers.BOTTOM_ACTION_PROPERTIES
import org.fossify.gallery.helpers.BOTTOM_ACTION_RATING
import org.fossify.gallery.helpers.BOTTOM_ACTION_RENAME
import org.fossify.gallery.helpers.BOTTOM_ACTION_RESIZE
import org.fossify.gallery.helpers.BOTTOM_ACTION_ROTATE
import org.fossify.gallery.helpers.BOTTOM_ACTION_SET_AS
import org.fossify.gallery.helpers.BOTTOM_ACTION_SHARE
import org.fossify.gallery.helpers.BOTTOM_ACTION_SHOW_ON_MAP
import org.fossify.gallery.helpers.BOTTOM_ACTION_SLIDESHOW
import org.fossify.gallery.helpers.BOTTOM_ACTION_TOGGLE_FAVORITE
import org.fossify.gallery.helpers.BOTTOM_ACTION_TOGGLE_VISIBILITY
import org.fossify.gallery.helpers.EXT_CAMERA_MODEL
import org.fossify.gallery.helpers.EXT_DATE_TAKEN
import org.fossify.gallery.helpers.EXT_EXIF_PROPERTIES
import org.fossify.gallery.helpers.EXT_GPS
import org.fossify.gallery.helpers.EXT_LAST_MODIFIED
import org.fossify.gallery.helpers.EXT_NAME
import org.fossify.gallery.helpers.EXT_PATH
import org.fossify.gallery.helpers.EXT_RESOLUTION
import org.fossify.gallery.helpers.EXT_SIZE
import org.fossify.gallery.helpers.ROTATE_BY_ASPECT_RATIO
import org.fossify.gallery.helpers.ROTATE_BY_DEVICE_ROTATION
import org.fossify.gallery.helpers.ROTATE_BY_SYSTEM_SETTING
import org.fossify.gallery.models.MediaCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigateToAbout: () -> Unit = {}) {
    val ctx = LocalContext.current
    val a = ctx as? Activity
    val conf = ctx.config
    val scope = rememberCoroutineScope()
    var showExtendedDialog by remember { mutableStateOf(false) }
    var showBottomActionsDialog by remember { mutableStateOf(false) }
    var settingsVersion by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            @Suppress("UNUSED_EXPRESSION") settingsVersion // re-read config-derived labels after a change

            SectionLabel(stringResource(R.string.set_general))
            SettingsSwitch(stringResource(R.string.set_force_dark), conf.forceDarkMode) { conf.forceDarkMode = it }
            SettingsSwitch(stringResource(R.string.set_dynamic_colors), conf.useDynamicColors) { conf.useDynamicColors = it }
            SettingsSwitch(stringResource(R.string.set_show_hidden), conf.showHiddenMedia) { conf.showHiddenMedia = it }
            SettingsSwitch(stringResource(R.string.set_animate_gifs), conf.animateGifs) { conf.animateGifs = it }
            SettingsSwitch(stringResource(R.string.set_max_brightness), conf.maxBrightness) { conf.maxBrightness = it }
            SettingsSwitch(stringResource(R.string.set_search_all_files), conf.searchAllFilesByDefault) { conf.searchAllFilesByDefault = it }
            SettingsSwitch(stringResource(R.string.set_scroll_horizontally), conf.scrollHorizontally) { conf.scrollHorizontally = it }
            SettingsSwitch(stringResource(R.string.set_pull_refresh), conf.enablePullToRefresh) { conf.enablePullToRefresh = it }
            SettingsNav(stringResource(R.string.set_folder_type), getViewTypeLabel(ctx, conf.viewTypeFolders)) { conf.viewTypeFolders = if (conf.viewTypeFolders == VIEW_TYPE_GRID) VIEW_TYPE_LIST else VIEW_TYPE_GRID; settingsVersion++ }
            SettingsNav(stringResource(R.string.set_included_folders)) { a?.startActivity(Intent(a, IncludedFoldersActivity::class.java)) }
            SettingsNav(stringResource(R.string.set_excluded_folders)) { a?.startActivity(Intent(a, ExcludedFoldersActivity::class.java)) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.set_video))
            SettingsSwitch(stringResource(R.string.set_autoplay), conf.autoplayVideos) { conf.autoplayVideos = it }
            SettingsSwitch(stringResource(R.string.set_loop_videos), conf.loopVideos) { conf.loopVideos = it }
            SettingsSwitch(stringResource(R.string.set_mute_videos), conf.muteVideos) { conf.muteVideos = it }
            SettingsSwitch(stringResource(R.string.set_separate_player), conf.gestureVideoPlayer) { conf.gestureVideoPlayer = it }
            SettingsSwitch(stringResource(R.string.set_remember_position), conf.rememberLastVideoPosition) { conf.rememberLastVideoPosition = it }
            SettingsSwitch(stringResource(R.string.set_video_gestures), conf.allowVideoGestures) { conf.allowVideoGestures = it }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.set_fullscreen))
            SettingsSwitch(stringResource(R.string.set_black_bg), conf.blackBackground) { conf.blackBackground = it }
            SettingsSwitch(stringResource(R.string.set_hide_system_ui), conf.hideSystemUI) { conf.hideSystemUI = it }
            SettingsSwitch(stringResource(R.string.set_keep_screen_on), conf.keepScreenOn) { conf.keepScreenOn = it }
            SettingsSwitch(stringResource(R.string.set_show_notch), conf.showNotch) { conf.showNotch = it }
            SettingsSwitch(stringResource(R.string.set_swipe_close), conf.allowDownGesture) { conf.allowDownGesture = it }
            SettingsSwitch(stringResource(R.string.set_instant_change), conf.allowInstantChange) { conf.allowInstantChange = it }
            SettingsNav(stringResource(R.string.set_screen_rotation), getRotationLabel(ctx, conf.screenRotation)) {
                conf.screenRotation = when (conf.screenRotation) {
                    ROTATE_BY_SYSTEM_SETTING -> ROTATE_BY_DEVICE_ROTATION
                    ROTATE_BY_DEVICE_ROTATION -> ROTATE_BY_ASPECT_RATIO
                    else -> ROTATE_BY_SYSTEM_SETTING
                }
                settingsVersion++
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.set_thumbnails))
            SettingsSwitch(stringResource(R.string.set_crop_thumbnails), conf.cropThumbnails) { conf.cropThumbnails = it }
            SettingsSwitch(stringResource(R.string.show_video_duration), conf.showThumbnailVideoDuration) { conf.showThumbnailVideoDuration = it }
            SettingsSwitch(stringResource(R.string.set_show_file_type), conf.showThumbnailFileTypes) { conf.showThumbnailFileTypes = it }
            SettingsSwitch(stringResource(R.string.set_mark_favorites), conf.markFavoriteItems) { conf.markFavoriteItems = it }
            SettingsSwitch(stringResource(R.string.show_rating), conf.showRatingOnThumbnails) { conf.showRatingOnThumbnails = it }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.nav_recycle_bin))
            SettingsSwitch(stringResource(R.string.set_use_recycle_bin), conf.useRecycleBin) { conf.useRecycleBin = it }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.set_files))
            SettingsSwitch(stringResource(R.string.set_delete_empty), conf.deleteEmptyFolders) { conf.deleteEmptyFolders = it }
            SettingsSwitch(stringResource(R.string.set_keep_modified), conf.keepLastModified) { conf.keepLastModified = it }
            SettingsSwitch(stringResource(R.string.set_skip_delete_confirm), conf.skipDeleteConfirmation) { conf.skipDeleteConfirmation = it }
            SettingsNav(stringResource(R.string.set_load_files), getFileLoadingLabel(ctx, conf.fileLoadingPriority)) {
                conf.fileLoadingPriority = (conf.fileLoadingPriority + 1) % 3
                settingsVersion++
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.set_extended_details))
            SettingsSwitch(stringResource(R.string.set_show_details), conf.showExtendedDetails) { conf.showExtendedDetails = it }
            SettingsSwitch(stringResource(R.string.set_hide_details), conf.hideExtendedDetails) { conf.hideExtendedDetails = it }
            SettingsNav(stringResource(R.string.set_manage_details), getExtendedDetailsSummary(ctx, conf.extendedDetails)) { showExtendedDialog = true }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.set_screen_actions))
            SettingsSwitch(stringResource(R.string.set_show_actions), conf.bottomActions) { conf.bottomActions = it }
            SettingsNav(stringResource(R.string.set_manage_actions), getBottomActionsSummary(ctx, conf.visibleBottomActions)) { showBottomActionsDialog = true }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.set_security))
            SecurityNav(stringResource(R.string.set_app_password), conf.isAppPasswordProtectionOn) {
                SecurityDialog(a!!, conf.appPasswordHash, if (conf.isAppPasswordProtectionOn) conf.appProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                    if (success) {
                        val wasOn = conf.isAppPasswordProtectionOn
                        conf.isAppPasswordProtectionOn = !wasOn
                        conf.appPasswordHash = if (wasOn) "" else hash
                        conf.appProtectionType = type
                    }
                }
            }
            SecurityNav(stringResource(R.string.set_hidden_password), conf.isHiddenPasswordProtectionOn) {
                SecurityDialog(a!!, conf.hiddenPasswordHash, if (conf.isHiddenPasswordProtectionOn) conf.hiddenProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                    if (success) {
                        val wasOn = conf.isHiddenPasswordProtectionOn
                        conf.isHiddenPasswordProtectionOn = !wasOn
                        conf.hiddenPasswordHash = if (wasOn) "" else hash
                        conf.hiddenProtectionType = type
                    }
                }
            }
            SecurityNav(stringResource(R.string.set_excluded_password), conf.isExcludedPasswordProtectionOn) {
                SecurityDialog(a!!, conf.excludedPasswordHash, if (conf.isExcludedPasswordProtectionOn) conf.excludedProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                    if (success) {
                        val wasOn = conf.isExcludedPasswordProtectionOn
                        conf.isExcludedPasswordProtectionOn = !wasOn
                        conf.excludedPasswordHash = if (wasOn) "" else hash
                        conf.excludedProtectionType = type
                    }
                }
            }
            SecurityNav(stringResource(R.string.set_delete_password), conf.isDeletePasswordProtectionOn) {
                SecurityDialog(a!!, conf.deletePasswordHash, if (conf.isDeletePasswordProtectionOn) conf.deleteProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                    if (success) {
                        val wasOn = conf.isDeletePasswordProtectionOn
                        conf.isDeletePasswordProtectionOn = !wasOn
                        conf.deletePasswordHash = if (wasOn) "" else hash
                        conf.deleteProtectionType = type
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.set_tags_ratings))
            SettingsNav(stringResource(R.string.set_read_metadata)) { MetadataSyncWorker.scheduleFullScan(ctx); Toast.makeText(ctx, "Scan gestartet – Fortschritt in der Benachrichtigung", Toast.LENGTH_SHORT).show() }
            SettingsNav(stringResource(R.string.set_cancel_scan)) { MetadataSyncWorker.cancel(ctx); Toast.makeText(ctx, ctx.getString(R.string.set_scan_cancelled), Toast.LENGTH_SHORT).show() }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.set_manage_data))
            FavoritesExportNav(ctx, conf, scope)
            FavoritesImportNav(ctx, conf, scope)
            SettingsNav(stringResource(R.string.set_export_settings)) { a?.startActivity(Intent(a, SettingsActivity::class.java).putExtra("open_section", "export_settings")) }
            SettingsNav(stringResource(R.string.set_import_settings)) { a?.startActivity(Intent(a, SettingsActivity::class.java).putExtra("open_section", "import_settings")) }
            ClearCacheNav(ctx, scope)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel(stringResource(R.string.set_information))
            SettingsNav(stringResource(R.string.set_about), onClick = onNavigateToAbout)

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showExtendedDialog) {
        ExtendedDetailsDialog(
            current = conf.extendedDetails,
            onDismiss = { showExtendedDialog = false },
            onSave = { conf.extendedDetails = it }
        )
    }

    if (showBottomActionsDialog) {
        BottomActionsDialog(
            current = conf.visibleBottomActions,
            onDismiss = { showBottomActionsDialog = false },
            onSave = { conf.visibleBottomActions = it }
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
internal fun SettingsSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    var internalChecked by remember(checked) { mutableStateOf(checked) }
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = internalChecked, onCheckedChange = { internalChecked = it; onChange(it) })
        }
    }
}

@Composable
internal fun SettingsNav(label: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick), shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsNav(label: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick), shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SecurityNav(label: String, enabled: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick), shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(if (enabled) stringResource(R.string.set_on) else stringResource(R.string.set_off), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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

@Composable
internal fun BottomActionsDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var result by remember { mutableStateOf(current) }
    val items = listOf(
        BOTTOM_ACTION_TOGGLE_FAVORITE to stringResource(R.string.favorite), BOTTOM_ACTION_EDIT to stringResource(R.string.edit),
        BOTTOM_ACTION_SHARE to stringResource(R.string.action_share), BOTTOM_ACTION_DELETE to stringResource(org.fossify.commons.R.string.delete),
        BOTTOM_ACTION_ROTATE to stringResource(R.string.action_rotate), BOTTOM_ACTION_PROPERTIES to stringResource(R.string.action_properties),
        BOTTOM_ACTION_CHANGE_ORIENTATION to stringResource(R.string.action_change_orientation), BOTTOM_ACTION_SLIDESHOW to stringResource(R.string.action_slideshow),
        BOTTOM_ACTION_SHOW_ON_MAP to stringResource(R.string.action_show_on_map), BOTTOM_ACTION_TOGGLE_VISIBILITY to stringResource(R.string.action_toggle_visibility),
        BOTTOM_ACTION_RENAME to stringResource(R.string.action_rename), BOTTOM_ACTION_SET_AS to stringResource(R.string.action_set_as),
        BOTTOM_ACTION_COPY to stringResource(R.string.action_copy), BOTTOM_ACTION_MOVE to stringResource(R.string.action_move),
        BOTTOM_ACTION_RESIZE to stringResource(R.string.action_resize), BOTTOM_ACTION_RATING to stringResource(R.string.rating_title)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_screen_actions)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                items.forEach { (flag, label) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = result and flag != 0, onCheckedChange = { if (it) result += flag else result -= flag })
                        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { if (result and flag == 0) result += flag else result -= flag })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(result); onDismiss() }) { Text(stringResource(org.fossify.commons.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
internal fun ClearCacheNav(ctx: Context, scope: CoroutineScope) {
    val a = ctx as? Activity
    var cacheSize by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { cacheSize = withContext(Dispatchers.IO) { a?.cacheDir?.let { dir -> if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }.let { bytes -> if (bytes > 1_000_000) "${bytes / 1_000_000} MB" else "${bytes / 1_000} KB" } else "" } ?: "" } }
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
                withContext(Dispatchers.Main) { Toast.makeText(ctx, "${paths.size} Favoriten exportiert", Toast.LENGTH_SHORT).show() }
            } catch (_: Exception) { }
        }
    }
    SettingsNav(stringResource(R.string.set_export_favorites)) { exportLauncher.launch("gallery-favorites_${java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US).format(java.util.Date())}.txt") }
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
                    if (java.io.File(line).exists()) { repo.addFavoriteByPath(line); imported++ }
                }
                withContext(Dispatchers.Main) { Toast.makeText(ctx, "$imported Favoriten importiert", Toast.LENGTH_SHORT).show() }
            } catch (_: Exception) { }
        }
    }
    SettingsNav(stringResource(R.string.set_import_favorites)) { importLauncher.launch(arrayOf("text/plain")) }
}

internal fun getViewTypeLabel(ctx: Context, viewType: Int): String = when {
    viewType == VIEW_TYPE_GRID -> ctx.getString(R.string.view_type_grid)
    viewType == 2 -> ctx.getString(R.string.view_type_mosaic)
    else -> ctx.getString(R.string.view_type_list)
}
internal fun getRotationLabel(ctx: Context, rotation: Int): String = when (rotation) {
    ROTATE_BY_SYSTEM_SETTING -> ctx.getString(R.string.rot_system)
    ROTATE_BY_DEVICE_ROTATION -> ctx.getString(R.string.rot_device)
    ROTATE_BY_ASPECT_RATIO -> ctx.getString(R.string.rot_aspect)
    else -> ctx.getString(R.string.rot_system)
}
internal fun getFileLoadingLabel(ctx: Context, priority: Int): String = when (priority) {
    0 -> ctx.getString(R.string.load_speed)
    1 -> ctx.getString(R.string.load_compromise)
    else -> ctx.getString(R.string.load_validity)
}

internal fun getBottomActionsSummary(ctx: Context, actions: Int): String {
    val labels = mutableListOf<String>()
    if (actions and BOTTOM_ACTION_SHARE != 0) labels.add(ctx.getString(R.string.action_share))
    if (actions and BOTTOM_ACTION_DELETE != 0) labels.add(ctx.getString(org.fossify.commons.R.string.delete))
    if (actions and BOTTOM_ACTION_EDIT != 0) labels.add(ctx.getString(R.string.edit))
    if (actions and BOTTOM_ACTION_ROTATE != 0) labels.add(ctx.getString(R.string.action_rotate))
    if (actions and BOTTOM_ACTION_TOGGLE_FAVORITE != 0) labels.add(ctx.getString(R.string.favorite))
    if (actions and BOTTOM_ACTION_RATING != 0) labels.add(ctx.getString(R.string.rating_title))
    return if (labels.isEmpty()) ctx.getString(R.string.none_label) else labels.take(3).joinToString(", ") + if (labels.size > 3) "..." else ""
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
