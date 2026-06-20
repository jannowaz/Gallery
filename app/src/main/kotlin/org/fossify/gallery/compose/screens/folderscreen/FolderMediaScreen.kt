package org.fossify.gallery.compose.screens.folderscreen

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
import org.fossify.gallery.compose.screens.ViewSettingsSheet
import org.fossify.gallery.compose.screens.ViewSettingsViewModel
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MEDIA_EXTENSIONS
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.models.Medium
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderMediaScreen(
    folderPath: String,
    onBack: () -> Unit,
) {
    val viewSettingsVM: ViewSettingsViewModel = viewModel()
    val ctx = LocalContext.current
    val tabSettings by viewSettingsVM.settings.collectAsState()
    var mediaItems by remember { mutableStateOf<List<Medium>?>(null) }
    var showViewSettings by remember { mutableStateOf(false) }

    LaunchedEffect(folderPath) {
        mediaItems = withContext(Dispatchers.IO) {
            val deleted = try { ctx.mediaDB.getDeletedMedia().map { it.path }.toSet() } catch (_: Exception) { emptySet() }
            scanFolderMedia(folderPath, deleted)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(File(folderPath).name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { showViewSettings = true }) { Icon(Icons.Default.GridView, "Ansicht") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MediaScreen(
                viewSettings = tabSettings.folderMedia,
                mediaOverride = mediaItems ?: emptyList()
            )
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
