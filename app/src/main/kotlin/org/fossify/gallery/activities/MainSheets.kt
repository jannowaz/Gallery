package org.fossify.gallery.activities

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.FilterSheetContent
import org.fossify.gallery.compose.screens.SettingsMode
import org.fossify.gallery.compose.screens.TabViewSettings
import org.fossify.gallery.compose.screens.ViewSettings
import org.fossify.gallery.compose.screens.ViewSettingsContent
import org.fossify.gallery.compose.screens.ViewSettingsPanel
import org.fossify.gallery.compose.screens.ViewSettingsViewModel
import org.fossify.gallery.models.MediaReviewFilter

/** One focused task per sheet: filtering never shares a navigation layer with view settings. */
internal enum class ActiveSheet { FILTER, VIEW_SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainSheets(
    activeSheet: ActiveSheet?,
    selectedTab: Int,
    collectionActive: Boolean,
    tabSettings: TabViewSettings,
    settingsMode: SettingsMode,
    navController: NavHostController,
    currentScanFolder: String = "",
    viewSettingsVM: ViewSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onDismissSheet: () -> Unit,
    ratingFilter: Int,
    onRatingFilterChange: (Int) -> Unit,
    tagFilterNamesRaw: Set<String>,
    onToggleTag: (String) -> Unit,
    tagMatchAll: Boolean,
    onTagMatchAllChange: (Boolean) -> Unit,
    minSizeFilter: Long,
    onMinSizeFilterChange: (Long) -> Unit,
    dateRangeFilter: Int,
    onDateRangeFilterChange: (Int) -> Unit,
    typeFilter: Int,
    onTypeFilterChange: (Int) -> Unit,
    reviewFilter: MediaReviewFilter,
    onReviewFilterChange: (MediaReviewFilter) -> Unit,
    hasActiveFilter: Boolean,
    onClearAll: () -> Unit,
) {
    if (activeSheet == ActiveSheet.VIEW_SETTINGS || activeSheet == ActiveSheet.FILTER) {
        val isExplorerTab = selectedTab == 2
        val supportsFilter = selectedTab in setOf(0, 2, 4)
        val explorerPath = currentScanFolder
        val s = when (selectedTab) {
            0 -> if (collectionActive) tabSettings.collectionMedia else tabSettings.media
            1 -> tabSettings.albums
            2 -> if (settingsMode == SettingsMode.ALBUMS) tabSettings.explorerAlbums else viewSettingsVM.getExplorerMediaSettingsForPath(explorerPath)
            3 -> tabSettings.collections
            4 -> tabSettings.favorites
            5 -> tabSettings.tags
            else -> ViewSettings()
        }
        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            if (activeSheet == ActiveSheet.FILTER && supportsFilter) {
                FilterSheetContent(
                    ratingFilter = ratingFilter,
                    onRatingChange = onRatingFilterChange,
                    selectedTagNames = tagFilterNamesRaw,
                    onToggleTag = onToggleTag,
                    tagMatchAll = tagMatchAll,
                    onTagMatchAllChange = onTagMatchAllChange,
                    minSizeFilter = minSizeFilter,
                    onMinSizeChange = onMinSizeFilterChange,
                    dateRangeFilter = dateRangeFilter,
                    onDateRangeChange = onDateRangeFilterChange,
                    typeFilter = typeFilter,
                    onTypeFilterChange = onTypeFilterChange,
                    reviewFilter = reviewFilter,
                    onReviewFilterChange = onReviewFilterChange,
                    hasActiveFilter = hasActiveFilter,
                    onClearAll = onClearAll,
                    onDismiss = onDismissSheet,
                )
            } else {
                ViewSettingsContent(
                    settings = s,
                    panel = ViewSettingsPanel.ALL,
                    showDisplayMode = selectedTab !in setOf(3, 5),
                    isAlbumMode = selectedTab == 1 || (isExplorerTab && settingsMode == SettingsMode.ALBUMS),
                    supportsTagGrouping = selectedTab != 0 || collectionActive,
                    isContainerMode = selectedTab == 3 || selectedTab == 5,
                    supportsRecentTagSorting = selectedTab == 5,
                    supportsSorting = true,
                    supportsAnchorBottom = !isExplorerTab,
                    showApplyGloballyToggle = isExplorerTab && settingsMode == SettingsMode.MEDIA,
                    initialApplyGlobally = !viewSettingsVM.hasCustomExplorerMediaSettings(explorerPath),
                    onSettingsChange = { v, applyGlobally ->
                        when (selectedTab) {
                            0 -> if (collectionActive) viewSettingsVM.updateCollectionMedia(v) else viewSettingsVM.updateMedia(v)
                            1 -> viewSettingsVM.updateAlbums(v)
                            2 -> if (settingsMode == SettingsMode.ALBUMS) viewSettingsVM.updateExplorerAlbums(v) else viewSettingsVM.updateExplorerMediaForPath(explorerPath, v, applyGlobally)
                            3 -> viewSettingsVM.updateCollections(v)
                            4 -> viewSettingsVM.updateFavorites(v)
                            5 -> viewSettingsVM.updateTags(v)
                        }
                    },
                    onDismiss = onDismissSheet,
                    // Scope switching (Folders vs. Media) belongs only where both coexist on screen.
                    modeTitle = if (isExplorerTab) {
                        val scopeRes = if (settingsMode == SettingsMode.ALBUMS) {
                            R.string.settings_scope_folders
                        } else {
                            R.string.settings_scope_media
                        }
                        stringResource(scopeRes)
                    } else null,
                    modeOptions = if (isExplorerTab) {
                        listOf(
                            stringResource(R.string.settings_scope_folders),
                            stringResource(R.string.settings_scope_media),
                        )
                    } else null,
                    scopeDescription = if (isExplorerTab) {
                        stringResource(R.string.view_settings_scope_description)
                    } else null,
                    onToggleMode = if (isExplorerTab) {
                        { viewSettingsVM.setSettingsMode(if (settingsMode == SettingsMode.ALBUMS) SettingsMode.MEDIA else SettingsMode.ALBUMS) }
                    } else null,
                )
            }
        }
    }
}
