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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
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
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.TagWriter
import org.fossify.gallery.viewmodels.AlbumsViewModel
import java.io.File

class ComposeExplorerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repo = remember { MediaRepository(this@ComposeExplorerActivity) }
            GalleryTheme {
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
    var showMoreMenu by remember { mutableStateOf(false) }
    var showViewSettings by remember { mutableStateOf(false) }
    var showOmniSearch by remember { mutableStateOf(false) }
    var showRatingBrowser by remember { mutableStateOf(false) }
    var showTagBrowser by remember { mutableStateOf(false) }
    var explorerPath by remember { mutableStateOf(ctx.config.internalStoragePath) }
    var activeRatingFilter by remember { mutableIntStateOf(0) }
    var activeTagFilter by remember { mutableStateOf<Set<String>?>(null) }
    var activeTagName by remember { mutableStateOf<String?>(null) }
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
                        Box(
                            Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
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
                                        if (tab.index == 5) { showMoreMenu = true; return@NavigationBarItem }
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
                0 -> MediaScreen(viewSettings = tabSettings.media, ratingFilter = activeRatingFilter, tagFilterPaths = activeTagFilter, activeTagName = activeTagName, onClearFilter = { activeRatingFilter = 0; activeTagFilter = null; activeTagName = null })
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

    if (showMoreMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Mehr", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                if (selectedTab in listOf(0, 1, 2, 4)) {
                    MenuRow(Icons.Default.GridView, "Ansicht") { showMoreMenu = false; showViewSettings = true }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                MenuRow(Icons.Default.Star, "Nach Bewertung") { showMoreMenu = false; showRatingBrowser = true }
                MenuRow(Icons.AutoMirrored.Filled.Label, "Nach Tags") { showMoreMenu = false; showTagBrowser = true }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                MenuRow(Icons.Default.Settings, "Einstellungen") { showMoreMenu = false; ctx.startActivity(Intent(ctx, ComposeSettingsActivity::class.java)) }
            }
        }
    }

    if (showViewSettings) {
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
            onDismiss = { showViewSettings = false },
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
            onNavigate = { path -> explorerPath = path; showOmniSearch = false; selectedTab = 2 }
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
                    selectedTab = 0
                }
            }) { Text("Filtern") } },
            dismissButton = { TextButton(onClick = { showRatingBrowser = false }) { Text("Schließen") } }
        )
    }

    if (showTagBrowser) {
        var allTags by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
        var scanning by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            scanning = true
            withContext(Dispatchers.IO) {
                val tags = mutableMapOf<String, MutableList<String>>()
                try {
                    val uri = android.provider.MediaStore.Files.getContentUri("external")
                    val proj = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
                    val sel = "${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
                    val args = arrayOf(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
                    ctx.contentResolver.query(uri, proj, sel, args, null)?.use { c ->
                        val col = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                        while (c.moveToNext()) {
                            val p = c.getString(col) ?: continue
                            TagWriter.readTags(p).forEach { tag ->
                                tags.getOrPut(tag) { mutableListOf() }.add(p)
                            }
                        }
                    }
                } catch (_: Exception) { }
                withContext(Dispatchers.Main) { allTags = tags.entries.sortedByDescending { it.value.size }.associate { it.key to it.value }; scanning = false }
            }
        }
        AlertDialog(
            onDismissRequest = { showTagBrowser = false },
            title = { Text(if (scanning) "Scanne..." else "Tags (${allTags.size})") },
            text = {
                if (scanning) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.padding(16.dp)) } }
                else if (allTags.isEmpty()) { Text("Keine Tags gefunden") }
                else {
                    LazyColumn(Modifier.height(300.dp)) {
                        items(allTags.entries.toList(), key = { it.key }) { (tag, paths) ->
                            Surface(Modifier.fillMaxWidth().clickable {
                                showTagBrowser = false
                                activeTagFilter = paths.toSet()
                                activeTagName = tag
                                activeRatingFilter = 0
                                selectedTab = 0
                            }, color = Color.Transparent) {
                                Row(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(tag, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text("${paths.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTagBrowser = false }) { Text("Schließen") } }
        )
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

private fun String.fuzzyScore(query: String): Int {
    val qParts = query.lowercase().split(" ").filter { it.isNotBlank() }
    val target = this.lowercase()
    if (qParts.isEmpty()) return 0
    var score = 0
    for (part in qParts) {
        val idx = target.indexOf(part)
        if (idx < 0) return 0
        score += maxOf(0, 100 - idx * 2)
    }
    return score + (if (target.startsWith(qParts.first())) 50 else 0) + (if (target == qParts.first()) 100 else 0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OmniSearchSheet(onDismiss: () -> Unit, storagePath: String, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<Map<String, List<ResultItem>>>(emptyMap()) }
    var isSearching by remember { mutableStateOf(false) }
    var ratingFilter by remember { mutableIntStateOf(0) }

    LaunchedEffect(query, ratingFilter) {
        if (query.length < 2) { results = emptyMap(); return@LaunchedEffect }
        isSearching = true
        kotlinx.coroutines.delay(300)
        if (query.isBlank()) { results = emptyMap(); isSearching = false; return@LaunchedEffect }
        val folders = mutableListOf<ResultItem>()
        val media = mutableListOf<ResultItem>()
        try {
            val root = File(storagePath)
            if (root.isDirectory) {
                root.listFiles()?.forEach { dir ->
                    if (dir.isDirectory && !dir.name.startsWith(".")) {
                        val score = dir.name.fuzzyScore(query)
                        if (score > 0) folders.add(ResultItem(dir.name, dir.absolutePath, ResultType.FOLDER, score))
                    }
                }
            }
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val proj = arrayOf(android.provider.MediaStore.MediaColumns.DATA, android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            val sel = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${android.provider.MediaStore.MediaColumns.DATA} LIKE ?"
            val selArgs = arrayOf("%$query%", "%$query%")
            try {
                ctx.contentResolver.query(uri, proj, sel, selArgs, null)?.use { c ->
                    val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                    val nameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val path = c.getString(dataCol) ?: continue
                        val name = c.getString(nameCol) ?: ""
                        val score = name.fuzzyScore(query)
                        if (score > 0) media.add(ResultItem(name, path, ResultType.MEDIA, score))
                    }
                }
            } catch (_: Exception) { }
        } catch (_: Exception) { }
        val allResults = mutableListOf<Pair<String, List<ResultItem>>>()
        if (folders.isNotEmpty()) allResults.add("Ordner" to folders.sortedByDescending { it.score })
        if (media.isNotEmpty()) allResults.add("Medien" to media.sortedByDescending { it.score })
        results = allResults.associate { it.first to it.second }
        isSearching = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Dateien, Ordner durchsuchen...") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, "Suchen") },
                trailingIcon = { if (isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Leeren") } },
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            )
            Spacer(Modifier.height(8.dp))
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
            if (results.isNotEmpty()) {
                var totalItems = 0; results.values.forEach { totalItems += it.size }
                Text("$totalItems Ergebnisse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    results.entries.forEach { (category, items) ->
                        item { Text(category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                        items(items.size) { idx ->
                            val item = items[idx]
                            Surface(modifier = Modifier.fillMaxWidth().clickable {
                                if (item.type == ResultType.FOLDER) onNavigate(item.path)
                                else {
                                    onDismiss()
                                    ctx.startActivity(android.content.Intent(ctx, ComposeViewerActivity::class.java).apply {
                                        putStringArrayListExtra("PATHS", arrayListOf(item.path))
                                        putExtra("START_INDEX", 0)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                }
                            }, color = Color.Transparent) {
                                Row(Modifier.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (item.type == ResultType.FOLDER) Icons.Default.Folder else Icons.Default.Image, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text(item.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(item.path.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (idx < items.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 36.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            } else if (query.length >= 2 && !isSearching) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Keine Ergebnisse fur \"$query\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private enum class ResultType { FOLDER, MEDIA }
private data class ResultItem(val label: String, val path: String, val type: ResultType, val score: Int = 0, val rating: Int = 0)

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
