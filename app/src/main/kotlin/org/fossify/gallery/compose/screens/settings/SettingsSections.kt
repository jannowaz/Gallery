package org.fossify.gallery.compose.screens.settings

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.ThemePrefsBus
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.ThumbnailOverlayState
import org.fossify.gallery.helpers.ColorModeHelper
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.workers.MediaSyncWorker
import org.fossify.gallery.workers.MetadataSyncWorker

internal enum class SettingsSection(
    val key: String,
    @param:StringRes val titleRes: Int,
) {
    GENERAL("general", R.string.set_general),
    VIDEO("video", R.string.set_video),
    FULLSCREEN("fullscreen", R.string.set_fullscreen),
    THUMBNAILS("thumbnails", R.string.set_thumbnails),
    RECYCLE_BIN("bin", R.string.nav_recycle_bin),
    FILES("files", R.string.set_files),
    EXTENDED_DETAILS("details", R.string.set_extended_details),
    SECURITY("security", R.string.set_security),
    METADATA("metadata", R.string.set_tags_ratings),
    DATA("data", R.string.set_manage_data),
    INFORMATION("information", R.string.set_information),
}

internal data class SettingsSectionCallbacks(
    val onOpenThemeMode: () -> Unit,
    val onOpenFolderType: () -> Unit,
    val onManageFolders: (String) -> Unit,
    val onOpenExtendedDetails: () -> Unit,
    val onNavigateToAbout: () -> Unit,
)

@Composable
internal fun SettingsSectionContent(
    section: SettingsSection,
    ctx: Context,
    activity: Activity?,
    conf: Config,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    isMetadataScanRunning: Boolean,
    callbacks: SettingsSectionCallbacks,
) {
    when (section) {
        SettingsSection.GENERAL -> GeneralSettingsContent(ctx, activity, conf, callbacks)
        SettingsSection.VIDEO -> VideoSettingsContent(conf)
        SettingsSection.FULLSCREEN -> FullscreenSettingsContent(conf)
        SettingsSection.THUMBNAILS -> ThumbnailSettingsContent(conf)
        SettingsSection.RECYCLE_BIN -> SettingsSwitch(
            stringResource(R.string.set_use_recycle_bin),
            conf.useRecycleBin,
        ) { conf.useRecycleBin = it }
        SettingsSection.FILES -> SettingsSwitch(
            stringResource(R.string.set_skip_delete_confirm),
            conf.skipDeleteConfirmation,
        ) { conf.skipDeleteConfirmation = it }
        SettingsSection.EXTENDED_DETAILS -> ExtendedDetailsSettingsContent(ctx, conf, callbacks.onOpenExtendedDetails)
        SettingsSection.SECURITY -> SecuritySettingsContent(activity, conf)
        SettingsSection.METADATA -> MetadataSettingsContent(ctx, scope, snackbarHostState, isMetadataScanRunning)
        SettingsSection.DATA -> DataSettingsContent(ctx, conf, scope)
        SettingsSection.INFORMATION -> {
            SettingsNav(stringResource(R.string.set_about), onClick = callbacks.onNavigateToAbout)
            CrashLogNav(ctx)
        }
    }
}

@Composable
private fun GeneralSettingsContent(
    ctx: Context,
    activity: Activity?,
    conf: Config,
    callbacks: SettingsSectionCallbacks,
) {
    val themeModeLabel = when {
        conf.forceDarkMode -> stringResource(R.string.theme_mode_dark)
        conf.forceLightMode -> stringResource(R.string.theme_mode_light)
        else -> stringResource(R.string.theme_mode_system)
    }
    SettingsNav(stringResource(R.string.theme_mode_label), themeModeLabel, callbacks.onOpenThemeMode)
    SettingsSwitch(stringResource(R.string.set_amoled_background), conf.useAmoledBackground) {
        conf.useAmoledBackground = it
        ThemePrefsBus.invalidate()
    }
    SettingsSwitch(stringResource(R.string.set_dynamic_colors), conf.useDynamicColors) {
        conf.useDynamicColors = it
        ThemePrefsBus.invalidate()
    }
    var showHiddenChecked by remember { mutableStateOf(conf.showHiddenMedia) }
    fun applyShowHidden(enabled: Boolean) {
        showHiddenChecked = enabled
        conf.showHiddenMedia = enabled
        RefreshBus.trigger()
        MediaSyncWorker.scheduleIncrementalSync(ctx)
    }
    SettingsSwitchControlled(stringResource(R.string.set_show_hidden), showHiddenChecked) { want ->
        when {
            !want -> applyShowHidden(false)
            activity != null && conf.isHiddenPasswordProtectionOn ->
                activity.handleHiddenFolderPasswordProtection { applyShowHidden(true) }
            else -> applyShowHidden(true)
        }
    }
    SettingsSwitch(stringResource(R.string.set_animate_gifs), conf.animateGifs) {
        conf.animateGifs = it
        ThumbnailOverlayState.animateGifs = it
    }
    SettingsSwitch(stringResource(R.string.set_max_brightness), conf.maxBrightness) { conf.maxBrightness = it }
    SettingsSwitch(stringResource(R.string.set_pull_refresh), conf.enablePullToRefresh) {
        conf.enablePullToRefresh = it
    }
    SettingsNav(
        stringResource(R.string.set_folder_type),
        getViewTypeLabel(ctx, conf.viewTypeFolders),
        callbacks.onOpenFolderType,
    )
    val includedCount = conf.includedFolders.size
    val excludedCount = conf.excludedFolders.size
    SettingsNav(
        stringResource(R.string.set_included_folders),
        if (includedCount > 0) includedCount.toString() else "",
    ) { callbacks.onManageFolders("included") }
    SettingsNav(
        stringResource(R.string.set_excluded_folders),
        if (excludedCount > 0) excludedCount.toString() else "",
    ) {
        if (activity != null && conf.isExcludedPasswordProtectionOn) {
            SecurityDialog(activity, conf.excludedPasswordHash, conf.excludedProtectionType) { _, _, success ->
                if (success) callbacks.onManageFolders("excluded")
            }
        } else {
            callbacks.onManageFolders("excluded")
        }
    }
}

@Composable
private fun VideoSettingsContent(conf: Config) {
    SettingsSwitch(stringResource(R.string.set_autoplay), conf.autoplayVideos) { conf.autoplayVideos = it }
    SettingsSwitch(stringResource(R.string.set_loop_videos), conf.loopVideos) { conf.loopVideos = it }
    SettingsSwitch(stringResource(R.string.set_mute_videos), conf.muteVideos) { conf.muteVideos = it }
    SettingsSwitch(stringResource(R.string.set_remember_position), conf.rememberLastVideoPosition) {
        conf.rememberLastVideoPosition = it
    }
    SettingsSwitch(stringResource(R.string.set_video_gestures), conf.allowVideoGestures) {
        conf.allowVideoGestures = it
    }
}

@Composable
private fun FullscreenSettingsContent(conf: Config) {
    SettingsSwitch(stringResource(R.string.set_black_bg), conf.blackBackground) { conf.blackBackground = it }
    SettingsSwitch(stringResource(R.string.set_hide_system_ui), conf.hideSystemUI) { conf.hideSystemUI = it }
    SettingsSwitch(stringResource(R.string.set_keep_screen_on), conf.keepScreenOn) { conf.keepScreenOn = it }
    SettingsSwitch(stringResource(R.string.set_show_notch), conf.showNotch) { conf.showNotch = it }
    SettingsSwitch(stringResource(R.string.set_swipe_close), conf.allowDownGesture) { conf.allowDownGesture = it }
    if (ColorModeHelper.isGainmapSupported()) {
        SettingsSwitch(stringResource(R.string.render_ultra_hdr_photos), conf.ultraHdrRendering) {
            conf.ultraHdrRendering = it
        }
    }
}

@Composable
private fun ThumbnailSettingsContent(conf: Config) {
    SettingsSwitch(stringResource(R.string.set_crop_thumbnails), conf.cropThumbnails) { conf.cropThumbnails = it }
    SettingsSwitch(stringResource(R.string.show_video_duration), conf.showVideoDurationOnThumbnails) {
        conf.showVideoDurationOnThumbnails = it
        ThumbnailOverlayState.showVideoDuration = it
    }
    SettingsSwitch(stringResource(R.string.set_show_file_type), conf.showThumbnailFileTypes) {
        conf.showThumbnailFileTypes = it
    }
    SettingsSwitch(stringResource(R.string.set_mark_favorites), conf.markFavoriteItems) {
        conf.markFavoriteItems = it
    }
    SettingsSwitch(stringResource(R.string.show_rating), conf.showRatingOnThumbnails) {
        conf.showRatingOnThumbnails = it
        ThumbnailOverlayState.showRating = it
    }
    SettingsSwitch(stringResource(R.string.blur_all_media), BlurState.enabled) {
        conf.blurAllMedia = it
        BlurState.enabled = it
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Text(
            stringResource(R.string.blur_all_media_legacy_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ExtendedDetailsSettingsContent(
    ctx: Context,
    conf: Config,
    onOpenExtendedDetails: () -> Unit,
) {
    SettingsSwitch(stringResource(R.string.set_show_details), conf.showExtendedDetails) {
        conf.showExtendedDetails = it
    }
    SettingsSwitch(
        stringResource(R.string.set_hide_details),
        conf.hideExtendedDetails,
        enabled = conf.showExtendedDetails,
    ) { conf.hideExtendedDetails = it }
    SettingsNav(
        stringResource(R.string.set_manage_details),
        getExtendedDetailsSummary(ctx, conf.extendedDetails),
        onOpenExtendedDetails,
    )
}

@Composable
private fun SecuritySettingsContent(activity: Activity?, conf: Config) {
    var securityTick by remember { mutableIntStateOf(0) }
    SecurityNav(stringResource(R.string.set_app_password), securityTick.let { conf.isAppPasswordProtectionOn }) {
        promptSecurityToggle(
            activity = activity,
            isEnabled = conf.isAppPasswordProtectionOn,
            currentHash = conf.appPasswordHash,
            currentType = conf.appProtectionType,
        ) { enabled, hash, type ->
            conf.isAppPasswordProtectionOn = enabled
            conf.appPasswordHash = hash
            conf.appProtectionType = type
            securityTick++
        }
    }
    SecurityNav(
        stringResource(R.string.set_hidden_password),
        securityTick.let { conf.isHiddenPasswordProtectionOn },
    ) {
        promptSecurityToggle(
            activity = activity,
            isEnabled = conf.isHiddenPasswordProtectionOn,
            currentHash = conf.hiddenPasswordHash,
            currentType = conf.hiddenProtectionType,
        ) { enabled, hash, type ->
            conf.isHiddenPasswordProtectionOn = enabled
            conf.hiddenPasswordHash = hash
            conf.hiddenProtectionType = type
            securityTick++
        }
    }
    SecurityNav(
        stringResource(R.string.set_delete_password),
        securityTick.let { conf.isDeletePasswordProtectionOn },
    ) {
        promptSecurityToggle(
            activity = activity,
            isEnabled = conf.isDeletePasswordProtectionOn,
            currentHash = conf.deletePasswordHash,
            currentType = conf.deleteProtectionType,
        ) { enabled, hash, type ->
            conf.isDeletePasswordProtectionOn = enabled
            conf.deletePasswordHash = hash
            conf.deleteProtectionType = type
            securityTick++
        }
    }
}

private fun promptSecurityToggle(
    activity: Activity?,
    isEnabled: Boolean,
    currentHash: String,
    currentType: Int,
    onResult: (enabled: Boolean, hash: String, type: Int) -> Unit,
) {
    val act = activity ?: return
    val tab = if (isEnabled) currentType else SHOW_ALL_TABS
    SecurityDialog(act, currentHash, tab) { hash, chosenType, success ->
        if (success) {
            val nextEnabled = !isEnabled
            onResult(nextEnabled, if (nextEnabled) hash else "", chosenType)
        }
    }
}

@Composable
private fun MetadataSettingsContent(
    ctx: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    isMetadataScanRunning: Boolean,
) {
    val res = LocalResources.current
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
    SettingsNav(
        stringResource(R.string.advanced_metadata_scan),
        stringResource(R.string.advanced_metadata_scan_subtitle),
    ) { showAdvancedScan = true }
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
}

@Composable
private fun DataSettingsContent(ctx: Context, conf: Config, scope: CoroutineScope) {
    FavoritesExportNav(ctx, conf, scope)
    FavoritesImportNav(ctx, conf, scope)
    FullBackupExportNav(ctx, scope)
    FullBackupImportNav(ctx, scope)
    LibraryHealthNav(ctx, scope)
    ClearCacheNav(ctx, scope)
}
