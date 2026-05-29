package org.fossify.gallery.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.screens.MediaScreen
import org.fossify.gallery.compose.screens.ViewSettingsViewModel
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.models.Medium
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
                    Scaffold { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            MediaScreen(
                                viewSettings = tabSettings.media,
                                mediaOverride = mediaItems ?: emptyList()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun scanFolderMedia(path: String): List<Medium> {
    val videoExts = setOf("mp4", "mkv", "mov", "3gp", "wmv", "flv", "avi")
    val mediaExts = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "avif", "bmp", "svg", "apng", "jxl") + videoExts
    val result = mutableListOf<Medium>()
    try {
        Files.newDirectoryStream(Paths.get(path)).use { stream ->
            for (entry in stream) {
                val name = entry.fileName.toString()
                if (name.startsWith(".")) continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in mediaExts) {
                    val fPath = entry.toString()
                    result.add(Medium(
                        id = null, name = name, path = fPath, parentPath = path,
                        modified = Files.getLastModifiedTime(entry).toMillis(),
                        taken = Files.getLastModifiedTime(entry).toMillis(),
                        size = Files.size(entry),
                        type = if (ext in videoExts) 2 else 1,
                        videoDuration = 0, isFavorite = false, deletedTS = 0L, mediaStoreId = 0, rating = 0,
                    ))
                }
            }
        }
    } catch (_: Exception) { }
    return result.sortedByDescending { it.modified }
}
