package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.screens.AlbumsScreen
import org.fossify.gallery.compose.screens.CollectionsScreen
import org.fossify.gallery.compose.screens.ExplorerScreen
import org.fossify.gallery.compose.screens.FavoritesScreen
import org.fossify.gallery.compose.screens.MediaScreen
import org.fossify.gallery.compose.screens.SettingsMode
import org.fossify.gallery.compose.screens.ViewSettings
import org.fossify.gallery.compose.screens.ViewSettingsSheet
import org.fossify.gallery.compose.screens.ViewSettingsViewModel
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.XmpWriter
import org.fossify.gallery.viewmodels.AlbumsViewModel
import java.io.File

private enum class ActiveSheet { MORE_MENU, VIEW_SETTINGS }

class ComposeExplorerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repo = remember { MediaRepository(this@ComposeExplorerActivity) }
            val conf = this.config
            GalleryTheme(darkTheme = conf.forceDarkMode || isSystemInDarkTheme()) {
                AppProviders(repo) { MainScreen(onFinish = { finish() }) }
            }
        }
    }
}

private data class NavTab(val index: Int, val label: String, val icon: ImageVector)

private val navTabs = listOf(
    NavTab(0, "Medien", Icons.Default.Image),
    NavTab(1, "Alben", Icons.Default.Folder),
    NavTab(2, "Pfad", Icons.Default.Search),
    NavTab(3, "Sammlung", Icons.Default.CollectionsBookmark),
    NavTab(4, "Favoriten", Icons.Default.Star),
    NavTab(5, "Mehr", Icons.Default.MoreVert),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(1) }
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var showOmniSearch by remember { mutableStateOf(false) }
    var showRatingBrowser by remember { mutableStateOf(false) }
    var showTagBrowser by remember { mutableStateOf(false) }
    var explorerPath by remember { mutableStateOf(ctx.config.internalStoragePath) }
    var activeRatingFilter by remember { mutableIntStateOf(0) }
    var activeTagFilter by remember { mutableStateOf<Set<String>?>(null) }
    var activeTagName by remember { mutableStateOf<String?>(null) }
    var activePathFilter by remember { mutableStateOf<Set<String>?>(null) }
    val viewSettingsVM: ViewSettingsViewModel = viewModel()
    val tabSettings by viewSettingsVM.settings.collectAsState()
    val settingsMode by viewSettingsVM.settingsMode.collectAsState()
    val albumsViewModel: AlbumsViewModel = viewModel()

    // Populate Room DB from MediaStore on first launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val existing = ctx.mediaDB.getNewestMedia(1)
                android.util.Log.e("DBInit", "existing.isEmpty=${existing.isEmpty()}")
                if (existing.isEmpty()) {
                    val mediums = mutableListOf<org.fossify.gallery.models.Medium>()
                    val uri = android.provider.MediaStore.Files.getContentUri("external")
                    val projection = arrayOf(
                        android.provider.MediaStore.Files.FileColumns._ID,
                        android.provider.MediaStore.Files.FileColumns.DATA,
                        android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED,
                        android.provider.MediaStore.Files.FileColumns.DATE_TAKEN,
                        android.provider.MediaStore.Files.FileColumns.SIZE,
                        android.provider.MediaStore.Files.FileColumns.MIME_TYPE,
                        android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE,
                        android.provider.MediaStore.Files.FileColumns.DURATION,
                    )
                    val selection = "${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
                    val selectionArgs = arrayOf(
                        android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                        android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                    )
                    ctx.contentResolver.query(uri, projection, selection, selectionArgs, "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                        android.util.Log.e("DBInit", "MediaStore cursor count=${cursor.count}")
                        val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATA)
                        val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED)
                        val takenCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATE_TAKEN)
                        val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.SIZE)
                        val typeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE)
                        val durCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DURATION)
                        while (cursor.moveToNext()) {
                            val path = cursor.getString(dataCol) ?: continue
                            val name = java.io.File(path).name
                            val parentPath = java.io.File(path).parent ?: ""
                            val modified = cursor.getLong(dateCol) * 1000L
                            val taken = if (!cursor.isNull(takenCol)) cursor.getLong(takenCol) else modified
                            val size = cursor.getLong(sizeCol)
                            val mediaType = cursor.getInt(typeCol)
                            val type = if (mediaType == android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                            val duration = if (!cursor.isNull(durCol)) (cursor.getInt(durCol) / 1000) else 0
                            mediums.add(org.fossify.gallery.models.Medium(
                                id = null, name = name, path = path, parentPath = parentPath,
                                modified = modified, taken = taken, size = size, type = type,
                                videoDuration = duration, isFavorite = false, deletedTS = 0L, mediaStoreId = 0, rating = 0,
                            ))
                        }
                    }
                    android.util.Log.e("DBInit", "Scanned ${mediums.size} media from MediaStore")
                    if (mediums.isNotEmpty()) {
                        ctx.mediaDB.insertAll(mediums)
                        android.util.Log.e("DBInit", "Inserted ${mediums.size} media into DB")
                        // Also populate directories
                        val dirs = mediums.map { it.parentPath }.distinct()
                        dirs.forEach { dirPath ->
                            val dirMedia = mediums.filter { it.parentPath == dirPath }
                            val dirName = java.io.File(dirPath).name
                            val hasImage = dirMedia.any { it.type == 1 }
                            val hasVideo = dirMedia.any { it.type == 2 }
                            val types = if (hasImage && hasVideo) 3 else if (hasVideo) 2 else 1
                            ctx.directoryDB.insertAll(listOf(org.fossify.gallery.models.Directory(
                                id = null, path = dirPath, tmb = dirMedia.maxByOrNull { it.modified }?.path ?: "",
                                name = dirName, mediaCnt = dirMedia.size,
                                modified = dirMedia.maxOf { it.modified },
                                taken = dirMedia.maxOf { it.taken },
                                size = dirMedia.size.toLong(),
                                location = org.fossify.gallery.helpers.LOCATION_INTERNAL, types = types, sortValue = "",
                            )))
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Gallery", "MediaStore scan failed", e)
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (selectedTab != 5) {
                Box(Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(onVerticalDrag = { _, drag -> if (drag < -50f) showOmniSearch = true })
                }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.KeyboardArrowUp, "Hochwischen",
                            modifier = Modifier.size(18.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            modifier = Modifier.height(56.dp)
                        ) {
                            navTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab.index,
                                    onClick = {
                                        if (tab.index == 5) { activeSheet = ActiveSheet.MORE_MENU; return@NavigationBarItem }
                                        selectedTab = tab.index
                                    },
                                    icon = { Icon(tab.icon, tab.label, modifier = Modifier.size(22.dp)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                }
        }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> MediaScreen(viewSettings = tabSettings.media, ratingFilter = activeRatingFilter, tagFilterPaths = activeTagFilter, pathFilter = activePathFilter, activeTagName = activeTagName, onClearFilter = { activeRatingFilter = 0; activeTagFilter = null; activeTagName = null; activePathFilter = null })
                1 -> AlbumsScreen(viewModel = albumsViewModel, onFolderClick = { dir ->
                    ctx.startActivity(Intent(ctx, ComposeFolderActivity::class.java).apply {
                        putExtra("FOLDER_PATH", dir.path)
                    })
                }, viewSettings = tabSettings.albums)
                2 -> ExplorerScreen(internalStoragePath = explorerPath, folderSettings = tabSettings.explorerAlbums, mediaSettings = tabSettings.explorerMedia)
                3 -> CollectionsScreen(onCollectionClick = { coll ->
                    val paths = coll.getIncludedPaths()
                    val fsPath = paths.firstOrNull()?.let { resolveContentUriToPath(it) }
                    if (fsPath != null) {
                        ctx.startActivity(Intent(ctx, ComposeFolderActivity::class.java).apply {
                            putExtra("FOLDER_PATH", fsPath)
                        })
                    }
                })
                4 -> FavoritesScreen(onNavigateToPath = { path -> explorerPath = path; selectedTab = 2 }, viewSettings = tabSettings.favorites)
            }
        }
    }

    if (activeSheet == ActiveSheet.MORE_MENU) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Mehr", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                if (selectedTab in listOf(0, 1, 2, 4)) {
                    MenuRow(Icons.Default.GridView, "Ansicht") { activeSheet = ActiveSheet.VIEW_SETTINGS }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                MenuRow(Icons.Default.Star, "Nach Bewertung") { activeSheet = null; showRatingBrowser = true }
                MenuRow(Icons.AutoMirrored.Filled.Label, "Nach Tags") { activeSheet = null; showTagBrowser = true }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                MenuRow(Icons.Default.Settings, "Einstellungen") { activeSheet = null; ctx.startActivity(Intent(ctx, ComposeSettingsActivity::class.java)) }
            }
        }
    }

    if (activeSheet == ActiveSheet.VIEW_SETTINGS) {
        val isAlbumsTab = selectedTab == 1
        val isExplorerTab = selectedTab == 2
        val s = when (selectedTab) {
            0 -> tabSettings.media
            1 -> if (settingsMode == SettingsMode.ALBUMS) tabSettings.albums else tabSettings.folderMedia
            2 -> if (settingsMode == SettingsMode.ALBUMS) tabSettings.explorerAlbums else tabSettings.explorerMedia
            4 -> tabSettings.favorites
            else -> ViewSettings()
        }
        ViewSettingsSheet(
            settings = s,
            showDisplayMode = ((selectedTab == 1 || selectedTab == 4) && settingsMode == SettingsMode.ALBUMS) || (selectedTab == 2 && settingsMode == SettingsMode.ALBUMS),
            onSettingsChange = { v ->
                when (selectedTab) {
                    0 -> viewSettingsVM.updateMedia(v)
                    1 -> if (settingsMode == SettingsMode.ALBUMS) viewSettingsVM.updateAlbums(v) else viewSettingsVM.updateFolderMedia(v)
                    2 -> if (settingsMode == SettingsMode.ALBUMS) viewSettingsVM.updateExplorerAlbums(v) else viewSettingsVM.updateExplorerMedia(v)
                    4 -> viewSettingsVM.updateFavorites(v)
                }
            },
            onDismiss = { activeSheet = null },
            modeTitle = when {
                selectedTab == 1 -> if (settingsMode == SettingsMode.ALBUMS) "Alben" else "Ordner-Inhalt"
                selectedTab == 2 -> if (settingsMode == SettingsMode.ALBUMS) "Alben" else "Medien"
                else -> null
            },
            modeOptions = when (selectedTab) {
                1 -> listOf("Alben", "Ordner-Inhalt")
                2 -> listOf("Alben", "Medien")
                else -> null
            },
            onToggleMode = if (isAlbumsTab || isExplorerTab) {{ viewSettingsVM.setSettingsMode(if (settingsMode == SettingsMode.ALBUMS) SettingsMode.MEDIA else SettingsMode.ALBUMS) }} else null,
        )
    }
    if (showOmniSearch) {
        OmniSearchSheet(
            onDismiss = { showOmniSearch = false },
            storagePath = android.os.Environment.getExternalStorageDirectory().absolutePath,
            onNavigate = { path -> explorerPath = path; showOmniSearch = false; selectedTab = 2 },
            onFilterChanged = { textPaths, rating, tagPaths, tagName ->
                activeRatingFilter = rating
                activePathFilter = textPaths
                activeTagFilter = tagPaths
                activeTagName = tagName
                selectedTab = 0
            },
        )
    }

    if (showRatingBrowser) {
        var ratingFilter by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showRatingBrowser = false },
            title = { Text("Nach Bewertung filtern") },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Wähle eine Bewertung:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 1..5) {
                            IconButton(onClick = { ratingFilter = i }, modifier = Modifier.size(48.dp)) {
                                Icon(if (i <= ratingFilter) Icons.Default.Star else Icons.Default.StarBorder, "Bewertung $i", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                showRatingBrowser = false
                if (ratingFilter > 0) {
                    activeRatingFilter = ratingFilter
                    activeTagFilter = null
                    activeTagName = null
                    activePathFilter = null
                    selectedTab = 0
                }
            }) { Text("Filtern") } },
            dismissButton = { TextButton(onClick = { showRatingBrowser = false }) { Text("Schließen") } }
        )
    }

     if (showTagBrowser) {
        var allTags by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
        var scanning by remember { mutableStateOf(false) }
        var deleteConfirmTag by remember { mutableStateOf<String?>(null) }
        var mergeTargetTag by remember { mutableStateOf<String?>(null) }
        var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
        var refreshTrigger by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(refreshTrigger) {
            scanning = true
            withContext(Dispatchers.IO) {
                val tags = mutableMapOf<String, MutableList<String>>()
                try {
                    val cached = ctx.mediaCacheDB.getAllTagged()
                    if (cached.isNotEmpty()) {
                        cached.forEach { mc ->
                            mc.tags.split(",").filter { it.isNotBlank() }.forEach { t ->
                                tags.getOrPut(t.trim()) { mutableListOf() }.add(mc.fullPath)
                            }
                        }
                    }
                } catch (_: Exception) { }
                withContext(Dispatchers.Main) { allTags = tags.entries.sortedByDescending { it.value.size }.associate { it.key to it.value }; scanning = false }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showTagBrowser = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tags (${allTags.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showTagBrowser = false }) { Icon(Icons.Default.Close, "Schließen") }
                }
                Spacer(Modifier.height(8.dp))
                if (scanning) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (allTags.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("Keine Tags gefunden", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(allTags.entries.toList(), key = { it.key }) { (tag, paths) ->
                            val thumbPath = paths.firstOrNull()
                            val isVideo = thumbPath?.let { it.substringAfterLast('.', "").lowercase() in org.fossify.gallery.helpers.VIDEO_EXTENSIONS } ?: false
                            val isSelected = tag in selectedTags
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    if (selectedTags.isEmpty()) {
                                        showTagBrowser = false
                                        activeTagFilter = paths.toSet()
                                        activeTagName = tag
                                        activeRatingFilter = 0
                                        activePathFilter = null
                                        selectedTab = 0
                                    } else {
                                        selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)) {
                                        if (thumbPath != null && File(thumbPath).exists()) {
                                            if (isVideo) {
                                                VideoThumbnail(videoPath = thumbPath, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            } else {
                                                coil.compose.AsyncImage(
                                                    model = coil.request.ImageRequest.Builder(ctx).data(android.net.Uri.fromFile(File(thumbPath))).crossfade(true).build(),
                                                    contentDescription = tag, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                                )
                                            }
                                        } else {
                                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                                Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(tag, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${paths.size} Dateien", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Close, "Ausgewählt", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    } else {
                                        IconButton(onClick = { deleteConfirmTag = tag }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Delete, "Tag entfernen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Action bar when tags are selected
                    if (selectedTags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = {
                                    val tagPaths = selectedTags.flatMap { allTags[it] ?: emptyList() }.toSet()
                                    showTagBrowser = false
                                    activeTagFilter = tagPaths
                                    activeTagName = selectedTags.joinToString(", ")
                                    activeRatingFilter = 0
                                    activePathFilter = null
                                    selectedTab = 0
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                                    Text("${selectedTags.size} Tags filtern", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                            if (selectedTags.size >= 2) {
                                Surface(
                                    onClick = {
                                        mergeTargetTag = selectedTags.first()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                                        Text("Zusammenführen", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // Delete confirmation
        if (deleteConfirmTag != null) {
            val tagToDelete = deleteConfirmTag!!
            val pathsForTag = allTags[tagToDelete] ?: emptyList()
            AlertDialog(
                onDismissRequest = { deleteConfirmTag = null },
                title = { Text("Tag entfernen") },
                text = { Text("Tag \"$tagToDelete\" aus ${pathsForTag.size} Dateien entfernen? Die Dateien bleiben erhalten.") },
                confirmButton = {
                    val repo = LocalMediaRepository.current
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            pathsForTag.forEach { p -> repo.removeTag(p, tagToDelete) }
                            try {
                                val cached = ctx.mediaCacheDB.getAllTagged().filter { it.tags.contains(tagToDelete) }
                                cached.forEach { mc ->
                                    val newTags = mc.tags.split(",").filter { it.trim() != tagToDelete }.joinToString(",")
                                    ctx.mediaCacheDB.upsertAll(listOf(mc.copy(tags = newTags)))
                                }
                            } catch (_: Exception) { }
                            withContext(Dispatchers.Main) {
                                ctx.toast("Tag \"$tagToDelete\" aus ${pathsForTag.size} Dateien entfernt", Toast.LENGTH_SHORT)
                                deleteConfirmTag = null; refreshTrigger++; selectedTags = emptySet()
                            }
                        }
                    }) { Text("Entfernen", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleteConfirmTag = null }) { Text("Abbrechen") } }
            )
        }

        // Merge dialog
        if (mergeTargetTag != null) {
            val target = mergeTargetTag!!
            val sources = selectedTags - target
            AlertDialog(
                onDismissRequest = { mergeTargetTag = null },
                title = { Text("Tags zusammenführen") },
                text = { Text("Alle Dateien aus \"${sources.joinToString("\", \"")}\" zum Tag \"$target\" hinzufügen und die Quell-Tags entfernen?") },
                confirmButton = {
                    val repo = LocalMediaRepository.current
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            sources.forEach { srcTag ->
                                val srcPaths = allTags[srcTag] ?: return@forEach
                                srcPaths.forEach { p ->
                                    repo.addTag(p, target)
                                    repo.removeTag(p, srcTag)
                                }
                            }
                            try {
                                val cached = ctx.mediaCacheDB.getAllTagged().filter { it.tags.let { t -> sources.any { s -> t.contains(s) } } }
                                cached.forEach { mc ->
                                    var newTags = mc.tags
                                    sources.forEach { src -> newTags = newTags.split(",").filter { it.trim() != src }.joinToString(",") }
                                    if (target !in newTags.split(",").map { it.trim() }) newTags = "$newTags,$target"
                                    ctx.mediaCacheDB.upsertAll(listOf(mc.copy(tags = newTags)))
                                }
                            } catch (_: Exception) { }
                            withContext(Dispatchers.Main) {
                                ctx.toast("Tags zu \"$target\" zusammengeführt", Toast.LENGTH_SHORT)
                                mergeTargetTag = null; refreshTrigger++; selectedTags = emptySet()
                            }
                        }
                    }) { Text("Zusammenführen") }
                },
                dismissButton = { TextButton(onClick = { mergeTargetTag = null }) { Text("Abbrechen") } }
            )
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OmniSearchSheet(
    onDismiss: () -> Unit,
    storagePath: String,
    onNavigate: (String) -> Unit,
    onFilterChanged: (filterPaths: Set<String>?, rating: Int, tagPaths: Set<String>?, tagName: String?) -> Unit,
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var ratingFilter by remember { mutableIntStateOf(0) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allTags by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var isSearching by remember { mutableStateOf(false) }
    var textMatchPaths by remember { mutableStateOf<Set<String>?>(null) }
    var searchTrigger by remember { mutableIntStateOf(0) }
    var showTags by remember { mutableStateOf(false) }

    // Load tags on demand (only when user clicks "Tags laden")
    LaunchedEffect(showTags) {
        if (!showTags) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val tags = mutableMapOf<String, MutableSet<String>>()
            try {
                ctx.mediaCacheDB.getAllTagged().forEach { mc ->
                    mc.tags.split(",").filter { it.isNotBlank() }.forEach { t ->
                        tags.getOrPut(t.trim()) { mutableSetOf() }.add(mc.fullPath)
                    }
                }
            } catch (_: Exception) { }
            allTags = tags
        }
    }

    fun triggerSearch() {
        searchTrigger++
    }

    // Text search: fuzzy match on filename + full path (only on manual trigger)
    LaunchedEffect(searchTrigger) {
        if (query.length < 2) { textMatchPaths = null; return@LaunchedEffect }
        isSearching = true
        val qParts = query.lowercase().split(" ").filter { it.isNotBlank() }
        if (qParts.isEmpty()) { textMatchPaths = null; isSearching = false; return@LaunchedEffect }

        val matched = mutableSetOf<String>()
        try {
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val proj = arrayOf(android.provider.MediaStore.MediaColumns.DATA, android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            ctx.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                val nameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val path = c.getString(dataCol) ?: continue
                    val name = c.getString(nameCol) ?: ""
                    val lowerFull = "$name ${path.lowercase()}"
                    if (qParts.all { it in lowerFull }) matched.add(path)
                }
            }
        } catch (_: Exception) { }
        textMatchPaths = matched
        isSearching = false
    }

    var combinedPaths by remember { mutableStateOf<Set<String>?>(null) }

    // Compute combined filter + live update on IO
    LaunchedEffect(query, ratingFilter, selectedTags, textMatchPaths) {
        withContext(Dispatchers.IO) {
            val sets = mutableListOf<Set<String>>()
            if (textMatchPaths != null) sets.add(textMatchPaths!!)
            if (ratingFilter > 0) {
                try { sets.add(ctx.mediaDB.getByMinRating(ratingFilter).map { it.path }.toSet()) } catch (_: Exception) { }
            }
            if (selectedTags.isNotEmpty()) {
                val tagPaths = allTags.filterKeys { it in selectedTags }.values.flatten().toSet()
                sets.add(tagPaths)
            }
            val result = when {
                sets.isEmpty() -> null
                sets.size == 1 -> sets.first()
                else -> sets.reduce { a, b -> a.intersect(b) }
            }
            withContext(Dispatchers.Main) {
                combinedPaths = result
                onFilterChanged(result, ratingFilter, selectedTags.let { if (it.isEmpty()) null else allTags.filterKeys { t -> t in it }.values.flatten().toSet() }, selectedTags.takeIf { it.isNotEmpty() }?.joinToString(", "))
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(max = 640.dp)) {
            // Search text field (manual trigger via button)
            OutlinedTextField(value = query, onValueChange = { query = it; textMatchPaths = null },
                placeholder = { Text("Ordner-/Dateiname") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, "Suchen") },
                trailingIcon = {
                    if (isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else if (query.length >= 2) IconButton(onClick = { triggerSearch() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowRight, "Suchen starten", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { triggerSearch() }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            )
            Spacer(Modifier.height(6.dp))

            // Rating filter
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Bewertung:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                IconButton(onClick = { ratingFilter = 0 }, modifier = Modifier.size(28.dp)) { Text("Alle", style = MaterialTheme.typography.labelSmall, color = if (ratingFilter == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                for (i in 1..5) {
                    IconButton(onClick = { ratingFilter = if (ratingFilter == i) 0 else i }, modifier = Modifier.size(28.dp)) {
                        Icon(if (i <= ratingFilter) Icons.Default.Star else Icons.Default.StarBorder, "$i", tint = if (i <= ratingFilter) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            // Tag chips (lazy-loaded on demand)
            if (allTags.isNotEmpty()) {
                Text("Tags:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    allTags.entries.sortedByDescending { it.value.size }.take(20).forEach { (tag, paths) ->
                        val isSelected = tag in selectedTags
                        Surface(
                            onClick = { selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(tag, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(4.dp))
                                Text("${paths.size}", style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            } else {
                Surface(onClick = { showTags = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tags laden", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Filter summary
            val activeCount = listOfNotNull(
                textMatchPaths?.let { "Text" },
                ratingFilter.takeIf { it > 0 }?.let { "★ $it+" },
                selectedTags.takeIf { it.isNotEmpty() }?.let { "${it.size} Tag${if (it.size != 1) "s" else ""}" }
            )
            if (activeCount.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filter: ${activeCount.joinToString(" + ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val resultCount = combinedPaths?.size
                    if (resultCount != null) {
                        Text("$resultCount Ergebnisse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { query = ""; ratingFilter = 0; selectedTags = emptySet() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Filter zurücksetzen", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun resolveContentUriToPath(uriString: String): String? {
    if (uriString.startsWith("/")) return uriString
    val uri = android.net.Uri.parse(uriString)
    val docId = try { android.provider.DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { return null }
    val parts = docId.split(":")
    if (parts.size == 2 && parts[0] == "primary") {
        return "${android.os.Environment.getExternalStorageDirectory().absolutePath}/${parts[1]}"
    }
    return null
}
