package org.fossify.gallery.compose.screens.folderscreen
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.screens.MediaScreen
import org.fossify.gallery.compose.screens.MediaSkeleton
import org.fossify.gallery.compose.screens.ViewSettingsSheet
import org.fossify.gallery.compose.screens.ViewSettingsViewModel
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.models.Medium
import java.io.File

// Process-level cache of the last scan per folder, kept alive across this composable's disposal
// (see MediaStoreOps for the analogous full-device cache and why a plain remember{} isn't enough).
// ConcurrentHashMap since it's written from an IO-dispatcher coroutine and read from Compose (main).
private val folderMediaCache = java.util.concurrent.ConcurrentHashMap<String, List<Medium>>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderMediaScreen(
    folderPath: String,
    onBack: () -> Unit,
    onNavigateToViewer: (List<String>, Int) -> Unit = { _, _ -> },
) {
    val viewSettingsVM: ViewSettingsViewModel = viewModel()
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val tabSettings by viewSettingsVM.settings.collectAsState()
    // Seed from the process-level cache instead of null - FolderMediaScreen is `composable<Folder>`,
    // a sibling destination that gets disposed too whenever Viewer is pushed on top of it, and without
    // this the directory-stream scan reran from disk every time the user opened a photo and came back.
    var mediaItems by remember { mutableStateOf(folderMediaCache[folderPath]) }
    var showViewSettings by remember { mutableStateOf(false) }
    // Resolves to this folder's own pinned settings if the user saved one via the "Einstellung
    // global übernehmen" toggle, else falls back to the tab-wide default - re-resolved fresh
    // whenever a *different* folder is opened (composable<Folder> gives each folder its own
    // instance, so folderPath is stable for the screen's lifetime).
    var viewSettings by remember(folderPath) { mutableStateOf(viewSettingsVM.getFolderMediaSettingsForPath(folderPath)) }
    // Keeps tracking the global default live if this folder has no custom override - matches the
    // legacy Views-based getFolderGrouping/getFolderSorting fallback-to-global behavior.
    LaunchedEffect(tabSettings.folderMedia, folderPath) {
        if (!viewSettingsVM.hasCustomFolderMediaSettings(folderPath)) viewSettings = tabSettings.folderMedia
    }

    // Single source of truth: the same `media` DB table the Media tab pages over (getMediaFromPath =
    // WHERE deleted_ts = 0 AND parent_path = folderPath, parent_path-indexed). MediaScreen's override
    // path then sorts it by date_sort_key via applySort, so a drilled-into album shows exactly the
    // same media, in exactly the same order, as the Media tab - instead of the old direct-disk scan
    // that sorted by file mtime and could momentarily diverge from the synced DB.
    LaunchedEffect(folderPath) {
        if (mediaItems != null) return@LaunchedEffect
        mediaItems = withContext(Dispatchers.IO) {
            repo.getMediaFromPath(folderPath).also { folderMediaCache[folderPath] = it }
        }
    }
    LaunchedEffect(Unit) {
        org.fossify.gallery.helpers.RefreshBus.events.collect {
            mediaItems = withContext(Dispatchers.IO) {
                repo.getMediaFromPath(folderPath).also { folderMediaCache[folderPath] = it }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(File(folderPath).name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    IconButton(onClick = { showViewSettings = true }) { Icon(Icons.Default.GridView, stringResource(R.string.view_settings_title)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
            val items = mediaItems
            // Crossfade + shimmer skeleton instead of a bare spinner - this is the same "media grid is
            // loading" moment as Media/Albums/Explorer, so it should look the same, not like a
            // different, less-polished screen.
            androidx.compose.animation.Crossfade(targetState = items == null, label = "folderMediaLoading") { loading ->
                if (loading) {
                    MediaSkeleton(columns = viewSettings.columnCount)
                } else {
                    MediaScreen(
                        viewSettings = viewSettings,
                        mediaOverride = items ?: emptyList(),
                        onNavigateToViewer = onNavigateToViewer,
                    )
                }
            }
        }
    }

    if (showViewSettings) {
        ViewSettingsSheet(
            settings = viewSettings,
            showDisplayMode = false,
            showApplyGloballyToggle = true,
            initialApplyGlobally = !viewSettingsVM.hasCustomFolderMediaSettings(folderPath),
            onSettingsChange = { s, applyGlobally -> viewSettingsVM.updateFolderMediaForPath(folderPath, s, applyGlobally); viewSettings = s },
            onDismiss = { showViewSettings = false }
        )
    }
}
