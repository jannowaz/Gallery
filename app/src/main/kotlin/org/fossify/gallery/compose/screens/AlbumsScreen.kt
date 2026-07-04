package org.fossify.gallery.compose.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.gallery.compose.components.FolderTile
import org.fossify.gallery.compose.components.LibraryAlbumGrid
import org.fossify.gallery.compose.components.AlbumGridItem
import org.fossify.gallery.compose.components.SelectionAppBar
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.extensions.config
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.R
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.models.Directory
import org.fossify.gallery.viewmodels.AlbumsViewModel
import org.fossify.gallery.workers.MetadataSyncWorker
import android.widget.Toast
import org.fossify.commons.extensions.toast
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onFolderClick: (Directory) -> Unit,
    modifier: Modifier = Modifier,
    viewSettings: ViewSettings = ViewSettings(),
    onSelectionActiveChanged: (Boolean) -> Unit = {},
    tabIndex: Int? = null,
) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val state by viewModel.state.collectAsState()
    var favorites by remember { mutableStateOf(ctx.config.favoriteFolders) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    val hasSelection = selectedPaths.isNotEmpty()
    LaunchedEffect(hasSelection) { onSelectionActiveChanged(hasSelection) }
    BackHandler(enabled = hasSelection) { selectedPaths = emptySet() }
    var selectionBarHeightPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Bouncy spring can transiently overshoot past 0 while animating the inset closed, and
    // Modifier.padding() throws on a negative value - coerce here, mirroring MediaScreen.kt.
    val rawContentTopInset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (hasSelection) with(density) { selectionBarHeightPx.toDp() } else 0.dp,
        animationSpec = org.fossify.gallery.compose.theme.AppMotion.insetSpring,
        label = "albumsSelectionInset",
    )
    val contentTopInset = rawContentTopInset.coerceAtLeast(0.dp)

    val sortedDirs = remember(state.directories, viewSettings.sortBy, viewSettings.sortDesc) {
        val sorted = when (viewSettings.sortBy) {
            SortField.NAME -> state.directories.sortedBy { it.name.lowercase() }
            SortField.DATE -> state.directories.sortedBy { it.modified }
            SortField.SIZE -> state.directories.sortedBy { it.size }
            SortField.RATING -> state.directories.sortedBy { it.name.lowercase() }
        }
        if (viewSettings.sortDesc) sorted.reversed() else sorted
    }

    val isGrid = viewSettings.viewType == ViewType.GRID
    val itemSpacing = viewSettings.spacing.dp
    val containerColor = when (viewSettings.displayMode) {
        DisplayMode.COMPACT, DisplayMode.NORMAL -> MaterialTheme.colorScheme.surface
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }

    var albumPreviews by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    LaunchedEffect(sortedDirs, isGrid) {
        if (!isGrid) {
            albumPreviews = withContext(Dispatchers.IO) {
                sortedDirs.associate { dir -> dir.path to repo.getMediaFromPath(dir.path).map { it.path }.take(4) }
            }
        }
    }
    val albumItems = remember(sortedDirs, albumPreviews) {
        sortedDirs.map { AlbumGridItem(key = it.path, name = it.name, thumbnailPath = it.tmb, count = it.mediaCnt, previewPaths = albumPreviews[it.path] ?: emptyList()) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(modifier = modifier.fillMaxSize().padding(top = contentTopInset)) {
            if (state.isLoading) {
                MediaSkeleton(columns = viewSettings.columnCount)
            } else if (state.directories.isEmpty()) {
                EmptyState(Icons.Default.Folder, stringResource(R.string.no_albums))
            } else {
                LibraryAlbumGrid(
                    items = albumItems,
                    viewSettings = viewSettings,
                    onClick = { item ->
                        if (hasSelection) selectedPaths = if (item.key in selectedPaths) selectedPaths - item.key else selectedPaths + item.key
                        else sortedDirs.find { it.path == item.key }?.let(onFolderClick)
                    },
                    onLongClick = { item -> selectedPaths = selectedPaths + item.key },
                    selectedKeys = selectedPaths,
                    modifier = Modifier.weight(1f),
                    tabIndex = tabIndex,
                )
            }
        }

        AnimatedVisibility(
            visible = hasSelection,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val selPath = selectedPaths.firstOrNull() ?: ""
            val isFav = selPath in favorites
            SelectionAppBar(
                modifier = Modifier.onGloballyPositioned { selectionBarHeightPx = it.size.height },
                count = selectedPaths.size,
                onClose = { selectedPaths = emptySet() },
                actions = {
                    IconButton(onClick = { selectedPaths.firstOrNull()?.let { p -> sortedDirs.find { it.path == p }?.let { d -> onFolderClick(d) } }; selectedPaths = emptySet() }) { Icon(Icons.Default.Folder, stringResource(R.string.action_open)) }
                    IconButton(onClick = { if (isFav) ctx.config.removeFavoriteFolder(selPath) else ctx.config.addFavoriteFolder(selPath); favorites = ctx.config.favoriteFolders; selectedPaths = emptySet() }) { Icon(if (isFav) Icons.Default.Star else Icons.Default.StarBorder, stringResource(if (isFav) R.string.action_unfavorite else R.string.action_favorite)) }
                    IconButton(onClick = { selectedPaths.firstOrNull()?.let { (ctx as? android.app.Activity)?.let { a -> PropertiesDialog(a, it, true) } } }) { Icon(Icons.Default.Info, stringResource(R.string.action_info)) }
                    IconButton(onClick = { selectedPaths.firstOrNull()?.let { path -> MetadataSyncWorker.scheduleFolderScan(ctx, path); ctx.toast(ctx.getString(R.string.scan_started, File(path).name), Toast.LENGTH_SHORT) }; selectedPaths = emptySet() }) { Icon(Icons.AutoMirrored.Filled.Label, stringResource(R.string.action_scan_metadata)) }
                },
            )
        }
    }
}
