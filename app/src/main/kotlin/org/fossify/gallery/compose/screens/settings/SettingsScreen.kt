package org.fossify.gallery.compose.screens.settings
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.helpers.CrashLogger
import org.fossify.gallery.helpers.SettingsBackupHelper
import androidx.compose.ui.text.font.FontFamily

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import org.fossify.gallery.workers.MediaSyncWorker
import org.fossify.gallery.workers.MetadataSyncWorker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.components.LibrarySearchField
import org.fossify.gallery.compose.components.LibrarySectionHeader
import org.fossify.gallery.helpers.LibraryActivityKind
import org.fossify.gallery.helpers.LibraryActivityPhase
import org.fossify.gallery.helpers.LibraryActivityStatus
import org.fossify.gallery.helpers.MediaRepository
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
import org.fossify.gallery.helpers.ROTATE_BY_ASPECT_RATIO
import org.fossify.gallery.helpers.ROTATE_BY_DEVICE_ROTATION
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.helpers.ROTATE_BY_SYSTEM_SETTING
import java.util.Locale

private val LocalSettingsQuery = androidx.compose.runtime.compositionLocalOf { "" }
private class SettingsMatchTracker {
    var matchedCount = 0
}
private val LocalSettingsMatchTracker = androidx.compose.runtime.compositionLocalOf<SettingsMatchTracker?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigateToAbout: () -> Unit = {}) {
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val a = ctx as? Activity
    val conf = ctx.config
    val scope = rememberCoroutineScope()
    var showExtendedDialog by remember { mutableStateOf(false) }
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showFolderTypeDialog by remember { mutableStateOf(false) }
    var manageFoldersMode by remember { mutableStateOf<String?>(null) }
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    var settingsVersion by remember { mutableStateOf(0) }
    var expandedSections by rememberSaveable { mutableStateOf(setOf("general")) }
    val snackbarHostState = remember { SnackbarHostState() }
    val activityStates by LibraryActivityStatus.states.collectAsStateWithLifecycle()
    val isMetadataScanRunning = activityStates.any {
        it.kind == LibraryActivityKind.METADATA_SCAN && it.phase == LibraryActivityPhase.RUNNING
    }
    fun toggleSection(key: String) {
        expandedSections = if (key in expandedSections) expandedSections - key else expandedSections + key
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
        }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp + padding.calculateStartPadding(layoutDirection),
                    end = 16.dp + padding.calculateEndPadding(layoutDirection),
                    top = 8.dp,
                    bottom = 8.dp + padding.calculateBottomPadding(),
                ),
        ) {
            LibrarySearchField(
                value = settingsQuery,
                onValueChange = { settingsQuery = it },
                placeholderRes = R.string.settings_search_hint,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val matchTracker = SettingsMatchTracker()
            CompositionLocalProvider(
                LocalSettingsQuery provides settingsQuery.trim(),
                LocalSettingsMatchTracker provides matchTracker,
            ) {
            @Suppress("UNUSED_EXPRESSION") settingsVersion // re-read config-derived labels after a change

            SettingsCategory(stringResource(R.string.set_general), settingsQuery, "general" in expandedSections) { toggleSection("general") }
            if ("general" in expandedSections || settingsQuery.isNotBlank()) {
            val themeModeLabel = when {
                conf.forceDarkMode -> stringResource(R.string.theme_mode_dark)
                conf.forceLightMode -> stringResource(R.string.theme_mode_light)
                else -> stringResource(R.string.theme_mode_system)
            }
            SettingsNav(stringResource(R.string.theme_mode_label), themeModeLabel) { showThemeModeDialog = true }
            SettingsSwitch(stringResource(R.string.set_amoled_background), conf.useAmoledBackground) { conf.useAmoledBackground = it; org.fossify.gallery.compose.theme.ThemePrefsBus.invalidate() }
            SettingsSwitch(stringResource(R.string.set_dynamic_colors), conf.useDynamicColors) { conf.useDynamicColors = it; org.fossify.gallery.compose.theme.ThemePrefsBus.invalidate() }
            // Controlled (not the internal-state SettingsSwitch) so a cancelled hidden-items password
            // prompt visually reverts the toggle. Enabling "show hidden" behind the hidden-items
            // password mirrors the legacy viewer, where revealing hidden media asked for it first;
            // the Compose switch flipped it with no prompt at all.
            var showHiddenChecked by remember { mutableStateOf(conf.showHiddenMedia) }
            fun applyShowHidden(enabled: Boolean) {
                showHiddenChecked = enabled
                conf.showHiddenMedia = enabled
                org.fossify.gallery.helpers.RefreshBus.trigger()
                org.fossify.gallery.workers.MediaSyncWorker.scheduleIncrementalSync(ctx)
            }
            SettingsSwitchControlled(stringResource(R.string.set_show_hidden), showHiddenChecked) { want ->
                when {
                    !want -> applyShowHidden(false)
                    a != null && conf.isHiddenPasswordProtectionOn ->
                        a.handleHiddenFolderPasswordProtection { applyShowHidden(true) }
                    else -> applyShowHidden(true)
                }
            }
            SettingsSwitch(stringResource(R.string.set_animate_gifs), conf.animateGifs) {
                conf.animateGifs = it
                org.fossify.gallery.compose.util.ThumbnailOverlayState.animateGifs = it
            }
            SettingsSwitch(stringResource(R.string.set_max_brightness), conf.maxBrightness) { conf.maxBrightness = it }
            // Legacy-only toggles (searchAllFilesByDefault, scrollHorizontally) are intentionally
            // omitted in Compose settings because OmniSearch always searches globally and Compose
            // grids scroll vertically.
            SettingsSwitch(stringResource(R.string.set_pull_refresh), conf.enablePullToRefresh) { conf.enablePullToRefresh = it }
            SettingsNav(stringResource(R.string.set_folder_type), getViewTypeLabel(ctx, conf.viewTypeFolders)) { showFolderTypeDialog = true }
            val includedCount = conf.includedFolders.size
            val excludedCount = conf.excludedFolders.size
            SettingsNav(
                stringResource(R.string.set_included_folders),
                if (includedCount > 0) includedCount.toString() else "",
            ) { manageFoldersMode = "included" }
            SettingsNav(
                stringResource(R.string.set_excluded_folders),
                if (excludedCount > 0) excludedCount.toString() else "",
            ) {
                if (a != null && conf.isExcludedPasswordProtectionOn) {
                    SecurityDialog(a, conf.excludedPasswordHash, conf.excludedProtectionType) { _, _, success ->
                        if (success) manageFoldersMode = "excluded"
                    }
                } else {
                    manageFoldersMode = "excluded"
                }
            }
            SettingsDivider()
            }

            SettingsCategory(stringResource(R.string.set_video), settingsQuery, "video" in expandedSections) { toggleSection("video") }
            if ("video" in expandedSections || settingsQuery.isNotBlank()) {
            SettingsSwitch(stringResource(R.string.set_autoplay), conf.autoplayVideos) { conf.autoplayVideos = it }
            SettingsSwitch(stringResource(R.string.set_loop_videos), conf.loopVideos) { conf.loopVideos = it }
            SettingsSwitch(stringResource(R.string.set_mute_videos), conf.muteVideos) { conf.muteVideos = it }
            SettingsSwitch(stringResource(R.string.set_remember_position), conf.rememberLastVideoPosition) { conf.rememberLastVideoPosition = it }
            SettingsSwitch(stringResource(R.string.set_video_gestures), conf.allowVideoGestures) { conf.allowVideoGestures = it }
            SettingsDivider()
            }

            SettingsCategory(stringResource(R.string.set_fullscreen), settingsQuery, "fullscreen" in expandedSections) { toggleSection("fullscreen") }
            if ("fullscreen" in expandedSections || settingsQuery.isNotBlank()) {
            SettingsSwitch(stringResource(R.string.set_black_bg), conf.blackBackground) { conf.blackBackground = it }
            SettingsSwitch(stringResource(R.string.set_hide_system_ui), conf.hideSystemUI) { conf.hideSystemUI = it }
            SettingsSwitch(stringResource(R.string.set_keep_screen_on), conf.keepScreenOn) { conf.keepScreenOn = it }
            SettingsSwitch(stringResource(R.string.set_show_notch), conf.showNotch) { conf.showNotch = it }
            SettingsSwitch(stringResource(R.string.set_swipe_close), conf.allowDownGesture) { conf.allowDownGesture = it }
            if (org.fossify.gallery.helpers.ColorModeHelper.isGainmapSupported()) {
                SettingsSwitch(stringResource(R.string.render_ultra_hdr_photos), conf.ultraHdrRendering) { conf.ultraHdrRendering = it }
            }
            SettingsDivider()
            }

            SettingsCategory(stringResource(R.string.set_thumbnails), settingsQuery, "thumbnails" in expandedSections) { toggleSection("thumbnails") }
            if ("thumbnails" in expandedSections || settingsQuery.isNotBlank()) {
            SettingsSwitch(stringResource(R.string.set_crop_thumbnails), conf.cropThumbnails) { conf.cropThumbnails = it }
            // Compose grids consume the observable overlay state; use the same persisted flags as
            // the view screens so this global setting has one source of truth everywhere.
            SettingsSwitch(stringResource(R.string.show_video_duration), conf.showVideoDurationOnThumbnails) {
                conf.showVideoDurationOnThumbnails = it
                org.fossify.gallery.compose.util.ThumbnailOverlayState.showVideoDuration = it
            }
            SettingsSwitch(stringResource(R.string.set_show_file_type), conf.showThumbnailFileTypes) { conf.showThumbnailFileTypes = it }
            SettingsSwitch(stringResource(R.string.set_mark_favorites), conf.markFavoriteItems) { conf.markFavoriteItems = it }
            SettingsSwitch(stringResource(R.string.show_rating), conf.showRatingOnThumbnails) {
                conf.showRatingOnThumbnails = it
                org.fossify.gallery.compose.util.ThumbnailOverlayState.showRating = it
            }
            SettingsSwitch(stringResource(R.string.blur_all_media), BlurState.enabled) {
                conf.blurAllMedia = it
                BlurState.enabled = it
            }
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                Text(
                    stringResource(R.string.blur_all_media_legacy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }
            SettingsDivider()
            }

            SettingsCategory(stringResource(R.string.nav_recycle_bin), settingsQuery, "bin" in expandedSections) { toggleSection("bin") }
            if ("bin" in expandedSections || settingsQuery.isNotBlank()) {
            SettingsSwitch(stringResource(R.string.set_use_recycle_bin), conf.useRecycleBin) { conf.useRecycleBin = it }
            SettingsDivider()
            }

            SettingsCategory(stringResource(R.string.set_files), settingsQuery, "files" in expandedSections) { toggleSection("files") }
            if ("files" in expandedSections || settingsQuery.isNotBlank()) {
            SettingsSwitch(stringResource(R.string.set_skip_delete_confirm), conf.skipDeleteConfirmation) { conf.skipDeleteConfirmation = it }
            SettingsDivider()
            }

            SettingsCategory(stringResource(R.string.set_extended_details), settingsQuery, "details" in expandedSections) { toggleSection("details") }
            if ("details" in expandedSections || settingsQuery.isNotBlank()) {
            SettingsSwitch(stringResource(R.string.set_show_details), conf.showExtendedDetails) { conf.showExtendedDetails = it }
            SettingsSwitch(stringResource(R.string.set_hide_details), conf.hideExtendedDetails, enabled = conf.showExtendedDetails) { conf.hideExtendedDetails = it }
            SettingsNav(stringResource(R.string.set_manage_details), getExtendedDetailsSummary(ctx, conf.extendedDetails)) { showExtendedDialog = true }
            SettingsDivider()
            }

            SettingsCategory(stringResource(R.string.set_security), settingsQuery, "security" in expandedSections) { toggleSection("security") }
            if ("security" in expandedSections || settingsQuery.isNotBlank()) {
            // conf reads aren't reactive, so bump this after each toggle so the On/Off labels below
            // refresh immediately instead of showing stale state until the screen is re-entered.
            var securityTick by remember { mutableIntStateOf(0) }
            SecurityNav(stringResource(R.string.set_app_password), securityTick.let { conf.isAppPasswordProtectionOn }) {
                a?.let { act ->
                    SecurityDialog(act, conf.appPasswordHash, if (conf.isAppPasswordProtectionOn) conf.appProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                        if (success) {
                            val wasOn = conf.isAppPasswordProtectionOn
                            conf.isAppPasswordProtectionOn = !wasOn
                            conf.appPasswordHash = if (wasOn) "" else hash
                            conf.appProtectionType = type
                            securityTick++
                        }
                    }
                }
            }
            SecurityNav(stringResource(R.string.set_hidden_password), securityTick.let { conf.isHiddenPasswordProtectionOn }) {
                a?.let { act ->
                    SecurityDialog(act, conf.hiddenPasswordHash, if (conf.isHiddenPasswordProtectionOn) conf.hiddenProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                        if (success) {
                            val wasOn = conf.isHiddenPasswordProtectionOn
                            conf.isHiddenPasswordProtectionOn = !wasOn
                            conf.hiddenPasswordHash = if (wasOn) "" else hash
                            conf.hiddenProtectionType = type
                            securityTick++
                        }
                    }
                }
            }
            SecurityNav(stringResource(R.string.set_delete_password), securityTick.let { conf.isDeletePasswordProtectionOn }) {
                a?.let { act ->
                    SecurityDialog(act, conf.deletePasswordHash, if (conf.isDeletePasswordProtectionOn) conf.deleteProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                        if (success) {
                            val wasOn = conf.isDeletePasswordProtectionOn
                            conf.isDeletePasswordProtectionOn = !wasOn
                            conf.deletePasswordHash = if (wasOn) "" else hash
                            conf.deleteProtectionType = type
                            securityTick++
                        }
                    }
                }
            }
            SettingsDivider()
            }

            SettingsCategory(stringResource(R.string.set_tags_ratings), settingsQuery, "metadata" in expandedSections) { toggleSection("metadata") }
            if ("metadata" in expandedSections || settingsQuery.isNotBlank()) {
            SettingsNav(stringResource(R.string.set_read_metadata)) {
                MetadataSyncWorker.scheduleFullScan(ctx)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = res.getString(R.string.scan_started_notification),
                        actionLabel = res.getString(R.string.cancel),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        MetadataSyncWorker.cancel(ctx)
                    }
                }
            }
            var showAdvancedScan by remember { mutableStateOf(false) }
            SettingsNav(stringResource(R.string.advanced_metadata_scan), stringResource(R.string.advanced_metadata_scan_subtitle)) { showAdvancedScan = true }
            if (showAdvancedScan) {
                AdvancedScanDialog(onDismiss = { showAdvancedScan = false }) { folder, start, end, inc, charging ->
                    MetadataSyncWorker.scheduleAdvancedScan(ctx, folder, start, end, inc, charging)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = res.getString(R.string.advanced_scan_scheduled),
                            actionLabel = res.getString(R.string.cancel),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            MetadataSyncWorker.cancel(ctx)
                        }
                    }
                }
            }
            if (isMetadataScanRunning) {
                SettingsNav(stringResource(R.string.set_cancel_scan)) {
                    MetadataSyncWorker.cancel(ctx)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = res.getString(R.string.set_scan_cancelled),
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            }
            SettingsDivider()
            }

            SettingsCategory(stringResource(R.string.set_manage_data), settingsQuery, "data" in expandedSections) { toggleSection("data") }
            if ("data" in expandedSections || settingsQuery.isNotBlank()) {
            FavoritesExportNav(ctx, conf, scope)
            FavoritesImportNav(ctx, conf, scope)
            FullBackupExportNav(ctx, scope)
            FullBackupImportNav(ctx, scope)
            LibraryHealthNav(ctx, scope)
            ClearCacheNav(ctx, scope)
            SettingsDivider()
            }

            SettingsCategory(stringResource(R.string.set_information), settingsQuery, "information" in expandedSections) { toggleSection("information") }
            if ("information" in expandedSections || settingsQuery.isNotBlank()) {
            SettingsNav(stringResource(R.string.set_about), onClick = onNavigateToAbout)
            CrashLogNav(ctx)
            }

            if (settingsQuery.isNotBlank() && matchTracker.matchedCount == 0) {
                Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showExtendedDialog) {
        ExtendedDetailsDialog(
            current = conf.extendedDetails,
            onDismiss = { showExtendedDialog = false },
            onSave = { conf.extendedDetails = it }
        )
    }

    if (showThemeModeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeModeDialog = false },
            title = { Text(stringResource(R.string.theme_mode_label)) },
            text = {
                Column {
                    listOf(
                        stringResource(R.string.theme_mode_light) to 1,
                        stringResource(R.string.theme_mode_dark) to 2,
                        stringResource(R.string.theme_mode_system) to 0,
                    ).forEach { (label, mode) ->
                        val selected = when (mode) {
                            1 -> conf.forceLightMode
                            2 -> conf.forceDarkMode
                            else -> !conf.forceLightMode && !conf.forceDarkMode
                        }
                        TextButton(
                            onClick = {
                                conf.forceLightMode = mode == 1
                                conf.forceDarkMode = mode == 2
                                org.fossify.gallery.compose.theme.ThemePrefsBus.invalidate()
                                showThemeModeDialog = false
                                settingsVersion++
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeModeDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showFolderTypeDialog) {
        SettingsChoiceDialog(
            title = stringResource(R.string.set_folder_type),
            selectedValue = conf.viewTypeFolders,
            options = listOf(
                VIEW_TYPE_GRID to stringResource(R.string.view_type_grid),
                VIEW_TYPE_LIST to stringResource(R.string.view_type_list),
            ),
            onSelected = { value -> conf.viewTypeFolders = value; settingsVersion++; showFolderTypeDialog = false },
            onDismiss = { showFolderTypeDialog = false },
        )
    }

    manageFoldersMode?.let { mode ->
        ManageFoldersDialog(
            isExcluded = mode == "excluded",
            onChanged = { settingsVersion++ },
            onDismiss = { manageFoldersMode = null },
        )
    }
}

@Composable
internal fun CrashLogNav(ctx: Context) {
    var logs by remember { mutableStateOf(emptyList<java.io.File>()) }
    LaunchedEffect(Unit) { logs = withContext(Dispatchers.IO) { CrashLogger.logs(ctx) } }
    var showDialog by remember { mutableStateOf(false) }

    val latest = logs.firstOrNull()
    SettingsNav(
        stringResource(R.string.set_crash_log),
        latest?.name?.removePrefix("crash_")?.removeSuffix(".txt")?.replace('_', ' ')
            ?: stringResource(R.string.crash_log_empty),
    ) {
        if (latest != null) showDialog = true
    }

    if (showDialog && latest != null) {
        val text = remember(latest) { runCatching { latest.readText() }.getOrDefault("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.set_crash_log)) },
            text = {
                Box(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, latest.name)
                        .putExtra(Intent.EXTRA_TEXT, text)
                    ctx.startActivity(Intent.createChooser(intent, null))
                }) { Text(stringResource(R.string.action_share)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLogger.clear(ctx)
                    logs = emptyList()
                    showDialog = false
                }) { Text(stringResource(org.fossify.commons.R.string.delete)) }
            },
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    if (LocalSettingsQuery.current.isNotBlank()) return
    LibrarySectionHeader(
        title = text,
        showDivider = false,
        topPadding = 20.dp,
        bottomPadding = 8.dp,
    )
}

/** Collapsed by default to keep the settings landing page scannable. A text search deliberately
 * reveals every matching category, so a setting never becomes harder to find. */
@Composable
private fun SettingsCategory(
    title: String,
    query: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    if (query.isNotBlank()) {
        SectionLabel(title)
        return
    }
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    if (LocalSettingsQuery.current.isBlank()) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }
}

@Composable
internal fun SettingsSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    if (!settingsMatches(label)) return
    var internalChecked by remember(checked) { mutableStateOf(checked) }
    val toggle: (Boolean) -> Unit = { value -> internalChecked = value; onChange(value) }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .toggleable(value = internalChecked, enabled = enabled, role = Role.Switch, onValueChange = toggle),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.weight(1f))
            Switch(checked = internalChecked, enabled = enabled, onCheckedChange = null)
        }
    }
}

/** Like [SettingsSwitch] but stateless - the caller owns [checked], so it can veto/revert a toggle
 * (e.g. when a password prompt behind the switch is cancelled). */
@Composable
internal fun SettingsSwitchControlled(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    if (!settingsMatches(label)) return
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
internal fun SettingsNav(label: String, onClick: () -> Unit) {
    if (!settingsMatches(label)) return
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick), shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsNav(label: String, subtitle: String, onClick: () -> Unit) {
    if (!settingsMatches(label, subtitle)) return
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick), shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SecurityNav(label: String, enabled: Boolean, onClick: () -> Unit) {
    if (!settingsMatches(label)) return
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick), shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(if (enabled) stringResource(R.string.set_on) else stringResource(R.string.set_off), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun settingsMatches(label: String, subtitle: String = ""): Boolean {
    val query = LocalSettingsQuery.current
    val matches = query.isBlank() || label.contains(query, ignoreCase = true) || subtitle.contains(query, ignoreCase = true)
    if (matches && query.isNotBlank()) {
        LocalSettingsMatchTracker.current?.let { it.matchedCount++ }
    }
    return matches
}
