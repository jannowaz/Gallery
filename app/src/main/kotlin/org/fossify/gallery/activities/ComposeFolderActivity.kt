package org.fossify.gallery.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.screens.MediaScreen
import org.fossify.gallery.compose.screens.ViewSettings
import org.fossify.gallery.compose.screens.ViewSettingsSheet
import org.fossify.gallery.compose.screens.ViewSettingsViewModel
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.helpers.MEDIA_EXTENSIONS
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.models.Medium
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class ComposeFolderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val folderPath = intent.getStringExtra("FOLDER_PATH") ?: finish().let { return }
        setContent {
            val repo = remember { MediaRepository(this@ComposeFolderActivity) }
            val viewSettingsVM: ViewSettingsViewModel = viewModel()
            val tabSettings by viewSettingsVM.settings.collectAsState()

            var mediaItems by remember { mutableStateOf<List<Medium>?>(null) }

            LaunchedEffect(folderPath) {
                mediaItems = withContext(Dispatchers.IO) { scanFolderMedia(folderPath) }
            }

            GalleryTheme {
                AppProviders(repo) {
                    FolderMediaScreen(folderPath = folderPath, tabSettings = tabSettings, viewSettingsVM = viewSettingsVM, mediaItems = mediaItems ?: emptyList(), onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderMediaScreen(folderPath: String, tabSettings: org.fossify.gallery.compose.screens.TabViewSettings, viewSettingsVM: ViewSettingsViewModel, mediaItems: List<Medium>, onBack: () -> Unit) {
    var showViewSettings by remember { mutableStateOf(false) }

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
                mediaOverride = mediaItems
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

private fun scanFolderMedia(path: String): List<Medium> {
    val result = mutableListOf<Medium>()
    try {
        Files.newDirectoryStream(Paths.get(path)).use { stream ->
            for (entry in stream) {
                val name = entry.fileName.toString()
                if (name.startsWith(".")) continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in MEDIA_EXTENSIONS) {
                    val fPath = entry.toString()
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
