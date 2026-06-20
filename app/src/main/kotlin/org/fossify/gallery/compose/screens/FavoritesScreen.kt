package org.fossify.gallery.compose.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.activities.ComposeFolderActivity
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.LibraryAlbumGrid
import org.fossify.gallery.compose.components.AlbumGridItem
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File

@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
    onNavigateToViewer: ((paths: List<String>, startIndex: Int) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    var favoriteMedia by remember { mutableStateOf<List<Medium>>(emptyList()) }
    var favoriteDirs by remember { mutableStateOf<List<Directory>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        org.fossify.gallery.helpers.RefreshBus.events.collect { refreshTrigger++ }
    }

    LaunchedEffect(refreshTrigger) {
        favoriteMedia = withContext(Dispatchers.IO) {
            try { ctx.mediaDB.getFavorites() } catch (_: Exception) { emptyList() }
        }
        favoriteDirs = withContext(Dispatchers.IO) {
            try {
                val paths = ctx.config.favoriteFolders.toList()
                if (paths.isEmpty()) emptyList()
                else ctx.directoryDB.getAll().filter { it.path in paths }
            } catch (_: Exception) { emptyList() }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (favoriteMedia.isEmpty() && favoriteDirs.isEmpty()) {
            EmptyState(Icons.Default.Star, "Keine Favoriten", subtitle = "Tippe auf den Stern bei Medien oder Ordnern")
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                if (favoriteDirs.isNotEmpty()) {
                    Text("Ordner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    LibraryAlbumGrid(
                        items = favoriteDirs.map { AlbumGridItem(key = it.path, name = it.name, thumbnailPath = it.tmb, count = it.mediaCnt) },
                        viewSettings = viewSettings,
                        onClick = { ctx.startActivity(Intent(ctx, ComposeFolderActivity::class.java).apply { putExtra("FOLDER_PATH", it.key) }) },
                        modifier = if (favoriteMedia.isEmpty()) Modifier.weight(1f) else Modifier.heightIn(max = 320.dp),
                    )
                    if (favoriteMedia.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                if (favoriteMedia.isNotEmpty()) {
                    Text("Medien", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    MediaScreen(modifier = Modifier.weight(1f), viewSettings = viewSettings, mediaOverride = favoriteMedia, onNavigateToViewer = onNavigateToViewer)
                }
            }
        }
    }
}
