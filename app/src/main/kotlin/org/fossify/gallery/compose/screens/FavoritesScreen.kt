package org.fossify.gallery.compose.screens

import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.LibraryAlbumGrid
import org.fossify.gallery.compose.components.AlbumGridItem
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.extensions.config
import org.fossify.gallery.R
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File

@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
    onNavigateToViewer: ((paths: List<String>, startIndex: Int) -> Unit)? = null,
    onFolderClick: (String) -> Unit = {},
    tabIndex: Int? = null,
) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    var pinTarget by remember { mutableStateOf<AlbumGridItem?>(null) }
    // Seed from the repo-level cache instead of empty lists - the whole FavoritesScreen composable is
    // disposed and recreated whenever the user opens a photo (navigates to Viewer) and comes back, and
    // without this the favorites/dirs queries reran from scratch on every single round trip.
    val cached = repo.getFavoritesCached()
    var favoriteMedia by remember { mutableStateOf(cached?.first ?: emptyList()) }
    var favoriteDirs by remember { mutableStateOf(cached?.second ?: emptyList()) }
    // Only true before the very first load ever completes (cache still null) - otherwise a stale
    // "no favorites" EmptyState would flash for a moment before the real, populated grid replaces it.
    var isLoading by remember { mutableStateOf(cached == null) }

    suspend fun reload() {
        val (media, dirs) = repo.refreshFavoritesCache()
        favoriteMedia = media
        favoriteDirs = dirs
        isLoading = false
    }

    LaunchedEffect(Unit) {
        if (repo.getFavoritesCached() == null) reload()
    }

    LaunchedEffect(Unit) {
        org.fossify.gallery.helpers.RefreshBus.events.collect { reload() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val contentState = when {
            isLoading -> "loading"
            favoriteMedia.isEmpty() && favoriteDirs.isEmpty() -> "empty"
            else -> "content"
        }
        Crossfade(targetState = contentState, label = "favoritesContent") { s ->
            when (s) {
                "loading" -> MediaSkeleton(columns = viewSettings.columnCount)
                "empty" -> EmptyState(Icons.Default.Star, stringResource(R.string.no_favorites), subtitle = stringResource(R.string.no_favorites_hint))
                else -> Column(Modifier.fillMaxSize().padding(8.dp)) {
                    if (favoriteDirs.isNotEmpty()) {
                        Text(stringResource(R.string.folders), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        val favoriteDirItems = remember(favoriteDirs) { favoriteDirs.map { AlbumGridItem(key = it.path, name = it.name, thumbnailPath = it.tmb, count = it.mediaCnt) } }
                        LibraryAlbumGrid(
                            items = favoriteDirItems,
                            viewSettings = viewSettings,
                            onClick = { onFolderClick(it.key) },
                            onLongClick = { pinTarget = it },
                            modifier = if (favoriteMedia.isEmpty()) Modifier.weight(1f) else Modifier.heightIn(max = 320.dp),
                            tabIndex = tabIndex,
                        )
                        if (favoriteMedia.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                    if (favoriteMedia.isNotEmpty()) {
                        Text(stringResource(R.string.media), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        MediaScreen(modifier = Modifier.weight(1f), viewSettings = viewSettings, mediaOverride = favoriteMedia, onNavigateToViewer = onNavigateToViewer, tabIndex = tabIndex)
                    }
                }
            }
        }
    }
    pinTarget?.let { item ->
        val pinned = item.key in ctx.config.pinnedFavoriteFolders
        AlertDialog(
            onDismissRequest = { pinTarget = null },
            title = { Text(item.name) },
            text = { Text(if (pinned) stringResource(R.string.unpin_favorite_q) else stringResource(R.string.pin_favorite_q)) },
            confirmButton = {
                TextButton(onClick = {
                    val cur = ctx.config.pinnedFavoriteFolders
                    ctx.config.pinnedFavoriteFolders = if (pinned) cur - item.key else cur + item.key
                    org.fossify.gallery.helpers.RefreshBus.trigger()
                    pinTarget = null
                }) { Text(if (pinned) stringResource(R.string.unpin_action) else stringResource(R.string.pin_action)) }
            },
            dismissButton = { TextButton(onClick = { pinTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
