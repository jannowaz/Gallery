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
import org.fossify.gallery.helpers.MEDIA_EXTENSIONS
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.models.Medium
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

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

    LaunchedEffect(folderPath) {
        if (mediaItems != null) return@LaunchedEffect
        mediaItems = withContext(Dispatchers.IO) {
            val deleted = repo.getDeletedPaths()
            scanFolderMedia(folderPath, deleted).also { folderMediaCache[folderPath] = it }
        }
    }
    LaunchedEffect(Unit) {
        org.fossify.gallery.helpers.RefreshBus.events.collect {
            mediaItems = withContext(Dispatchers.IO) {
                val deleted = repo.getDeletedPaths()
                scanFolderMedia(folderPath, deleted).also { folderMediaCache[folderPath] = it }
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
                    MediaSkeleton(columns = tabSettings.folderMedia.columnCount)
                } else {
                    MediaScreen(
                        viewSettings = tabSettings.folderMedia,
                        mediaOverride = items ?: emptyList(),
                        onNavigateToViewer = onNavigateToViewer,
                    )
                }
            }
        }
    }

    if (showViewSettings) {
        ViewSettingsSheet(
            settings = tabSettings.folderMedia,
            showDisplayMode = false,
            onSettingsChange = { s -> viewSettingsVM.updateFolderMedia(s) },
            onDismiss = { showViewSettings = false }
        )
    }
}

private fun scanFolderMedia(path: String, deletedPaths: Set<String>): List<Medium> {
    val result = mutableListOf<Medium>()
    try {
        Files.newDirectoryStream(Paths.get(path)).use { stream ->
            for (entry in stream) {
                val name = entry.fileName.toString()
                if (name.startsWith(".")) continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in MEDIA_EXTENSIONS) {
                    val fPath = entry.toString()
                    if (fPath in deletedPaths) continue
                    result.add(Medium(
                        id = null, name = name, path = fPath, parentPath = path,
                        modified = Files.getLastModifiedTime(entry).toMillis(),
                        taken = Files.getLastModifiedTime(entry).toMillis(),
                        size = Files.size(entry),
                        type = if (ext in VIDEO_EXTENSIONS) 2 else 1,
                        videoDuration = 0, isFavorite = false, deletedTS = 0L, mediaStoreId = 0, rating = 0,
                    ))
                }
            }
        }
    } catch (_: Exception) { }
    return result.sortedByDescending { it.modified }
}
