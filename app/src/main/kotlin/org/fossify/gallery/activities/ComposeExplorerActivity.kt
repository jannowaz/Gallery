package org.fossify.gallery.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.navigation.NavHostController
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
import org.fossify.gallery.compose.screens.TabViewSettings
import org.fossify.gallery.compose.screens.ViewSettings
import org.fossify.gallery.compose.screens.ViewSettingsSheet
import org.fossify.gallery.compose.screens.ViewSettingsViewModel
import org.fossify.gallery.compose.screens.VideoThumbnail
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.navigation.GalleryNavHost
import org.fossify.gallery.navigation.ManageCollections
import org.fossify.gallery.navigation.Settings
import org.fossify.gallery.navigation.StorageAnalysis
import org.fossify.gallery.navigation.TagBrowser
import org.fossify.gallery.navigation.Viewer
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.XmpWriter
import org.fossify.gallery.viewmodels.AlbumsViewModel
import org.fossify.gallery.viewmodels.ExplorerViewModel
import org.fossify.gallery.viewmodels.ExplorerUiState
import org.fossify.gallery.workers.RecycleBinCleanupWorker
import java.io.File

private enum class ActiveSheet { MORE_MENU, VIEW_SETTINGS }

class ComposeExplorerActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            recreate()
        }
    }

    private val mediaObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (uri != null) {
                RefreshBus.trigger()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        contentResolver.registerContentObserver(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
        )
        contentResolver.registerContentObserver(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
        )

        if (hasMediaPermissions()) {
            RecycleBinCleanupWorker.schedule(this)
            setContent { GalleryNavHost() }
        } else {
            requestPermissionLauncher.launch(getMediaPermissionStrings())
            RecycleBinCleanupWorker.schedule(this)
            setContent { GalleryNavHost() }
        }
    }

    private fun hasMediaPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(mediaObserver)
    }

    private fun getMediaPermissionStrings(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TagBrowserSheet(
    ctx: android.content.Context,
    mainVM: ExplorerViewModel,
    onDismiss: () -> Unit,
) {
    var allTags by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var scanning by remember { mutableStateOf(false) }
    var deleteConfirmTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mergeTargetTag by remember { mutableStateOf<String?>(null) }
    var pendingParentAssign by remember { mutableStateOf<Set<String>?>(null) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tagSearchQuery by remember { mutableStateOf("") }
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
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tags (${allTags.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Schließen") }
            }
            OutlinedTextField(
                value = tagSearchQuery,
                onValueChange = { tagSearchQuery = it },
                placeholder = { Text("Tag suchen") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, "Suchen", modifier = Modifier.size(18.dp)) },
                trailingIcon = { if (tagSearchQuery.isNotEmpty()) IconButton(onClick = { tagSearchQuery = "" }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Leeren", modifier = Modifier.size(16.dp)) } },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (scanning) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (allTags.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Keine Tags gefunden", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val filteredTags = if (tagSearchQuery.isBlank()) allTags.entries.toList() else allTags.entries.filter { (tag, _) -> tag.contains(tagSearchQuery, ignoreCase = true) }.sortedByDescending { it.value.size }
                LazyColumn(Modifier.heightIn(max = if (tagSearchQuery.isNotBlank()) 600.dp else 480.dp)) {
                    items(filteredTags, key = { it.key }) { (tag, paths) ->
                        val thumbPath = paths.firstOrNull()
                        val isVideo = thumbPath?.let { it.substringAfterLast('.', "").lowercase() in org.fossify.gallery.helpers.VIDEO_EXTENSIONS } ?: false
                        val isSelected = tag in selectedTags
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
                                onClick = {
                                    onDismiss()
                                    mainVM.setPreFilterTab(1)
                                    mainVM.setTagFilter(paths.toSet(), tag)
                                    mainVM.setRatingFilter(0)
                                    mainVM.setPathFilter(null)
                                    mainVM.setSelectedTab(0)
                                },
                                onLongClick = { selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)) {
                                    if (thumbPath != null && File(thumbPath).exists()) {
                                        if (isVideo) {
                                            VideoThumbnail(videoPath = thumbPath, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            GalleryImage(path = thumbPath, contentDescription = tag, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 16.dp)
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
                                }
                            }
                        }
                    }
                }
                if (selectedTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(onClick = {
                            val tagPaths = selectedTags.flatMap { allTags[it] ?: emptyList() }.toSet()
                            onDismiss()
                            mainVM.setTagFilter(tagPaths, selectedTags.joinToString(", "))
                            mainVM.setRatingFilter(0)
                            mainVM.setPathFilter(null)
                            mainVM.setSelectedTab(0)
                        }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text("Filtern", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary) }
                        }
                        Surface(onClick = { deleteConfirmTags = selectedTags }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) { Text("Löschen", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (deleteConfirmTags.isNotEmpty()) {
        val tagsToDelete = deleteConfirmTags
        val totalFiles = tagsToDelete.flatMap { allTags[it] ?: emptyList() }.distinct().size
        AlertDialog(
            onDismissRequest = { deleteConfirmTags = emptySet() },
            title = { Text(if (tagsToDelete.size == 1) "Tag entfernen" else "Tags entfernen") },
            text = {
                if (tagsToDelete.size == 1) Text("Tag \"${tagsToDelete.first()}\" aus $totalFiles Dateien entfernen? Die Dateien bleiben erhalten.")
                else Text("${tagsToDelete.size} Tags aus $totalFiles Dateien entfernen? Die Dateien bleiben erhalten.")
            },
            confirmButton = {
                val repo = LocalMediaRepository.current
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        tagsToDelete.forEach { tag ->
                            val pathsForTag = allTags[tag] ?: return@forEach
                            pathsForTag.forEach { p -> repo.removeTag(p, tag) }
                        }
                        try {
                            val cached = ctx.mediaCacheDB.getAllTagged().filter { mc -> tagsToDelete.any { mc.tags.contains(it) } }
                            cached.forEach { mc ->
                                var newTags = mc.tags
                                tagsToDelete.forEach { tag -> newTags = newTags.split(",").filter { it.trim() != tag }.joinToString(",") }
                                ctx.mediaCacheDB.upsertAll(listOf(mc.copy(tags = newTags)))
                            }
                        } catch (_: Exception) { }
                        withContext(Dispatchers.Main) {
                            ctx.toast("${tagsToDelete.size} Tag(s) entfernt", Toast.LENGTH_SHORT)
                            deleteConfirmTags = emptySet(); refreshTrigger++; selectedTags = emptySet()
                        }
                    }
                }) { Text("Entfernen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmTags = emptySet() }) { Text("Abbrechen") } }
        )
    }
}

private data class NavTab(val index: Int, val label: String, val icon: ImageVector)

private val navTabs = listOf(
    NavTab(0, "Medien", Icons.Default.Image),
    NavTab(1, "Alben", Icons.Default.Folder),
    NavTab(2, "Pfad", Icons.Default.Search),
    NavTab(3, "Sammlung", Icons.Default.CollectionsBookmark),
    NavTab(4, "Favoriten", Icons.Default.Star)
)

@Composable
fun MainScreen(navController: NavHostController, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val mainVM: ExplorerViewModel = viewModel()
    val uiState by mainVM.state.collectAsState()
    val viewSettingsVM: ViewSettingsViewModel = viewModel()
    val tabSettings by viewSettingsVM.settings.collectAsState()
    val settingsMode by viewSettingsVM.settingsMode.collectAsState()
    val albumsViewModel: AlbumsViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var showOmniSearch by remember { mutableStateOf(false) }
    var showRatingBrowser by remember { mutableStateOf(false) }
    var showTagBrowser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        mainVM.initializeDatabase { mainVM.triggerMediaRefresh() }
    }

    BackHandler(enabled = uiState.activeRatingFilter > 0 || uiState.activeTagFilter != null || uiState.activePathFilter != null || showTagBrowser || showOmniSearch || uiState.selectedTab != 1) {
        when {
            showTagBrowser -> showTagBrowser = false
            showOmniSearch -> showOmniSearch = false
            uiState.activeTagFilter != null -> { showTagBrowser = true; mainVM.setSelectedTab(1) }
            uiState.activePathFilter != null -> {
                val backTab = if (uiState.preFilterTab >= 0) uiState.preFilterTab else 1
                mainVM.clearFilters()
                mainVM.setSelectedTab(backTab)
            }
            uiState.activeRatingFilter > 0 -> { mainVM.setRatingFilter(0); mainVM.setSelectedTab(1) }
            uiState.selectedTab != 1 -> mainVM.setSelectedTab(1)
            else -> onFinish()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { activeSheet = ActiveSheet.MORE_MENU }) {
                Icon(Icons.Default.MoreVert, "Mehr")
            }
        },
        bottomBar = {
            MainBottomBar(
                selectedTab = uiState.selectedTab,
                onTabSelected = { mainVM.setSelectedTab(it) },
                onSwipeUp = { showOmniSearch = true },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MainTabContent(
                state = uiState,
                tabSettings = tabSettings,
                settingsMode = settingsMode,
                albumsViewModel = albumsViewModel,
                mainVM = mainVM,
                ctx = ctx,
                scope = scope,
                navController = navController,
            )
        }
    }

    MainSheets(
        activeSheet = activeSheet,
        selectedTab = uiState.selectedTab,
        tabSettings = tabSettings,
        settingsMode = settingsMode,
        viewSettingsVM = viewSettingsVM,
        navController = navController,
        onDismissSheet = { activeSheet = null },
        onSelectSheet = { activeSheet = it },
        onShowRatingBrowser = { showRatingBrowser = true },
    )

    if (showOmniSearch) {
        OmniSearchSheet(
            onDismiss = { showOmniSearch = false },
            storagePath = android.os.Environment.getExternalStorageDirectory().absolutePath,
            onNavigate = { path -> mainVM.setExplorerPath(path); showOmniSearch = false; mainVM.setSelectedTab(2) },
            onFilterChanged = { textPaths, rating, tagPaths, tagName, _, _ ->
                mainVM.setRatingFilter(rating)
                mainVM.setPathFilter(textPaths)
                mainVM.setTagFilter(tagPaths, tagName)
                if (rating > 0 || tagPaths != null || textPaths != null) mainVM.setSelectedTab(0)
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
                    mainVM.setPreFilterTab(1)
                    mainVM.setRatingFilter(ratingFilter)
                    mainVM.setTagFilter(null, null)
                    mainVM.setPathFilter(null)
                    mainVM.setSelectedTab(0)
                }
            }) { Text("Filtern") } },
            dismissButton = { TextButton(onClick = { showRatingBrowser = false }) { Text("Schließen") } }
        )
    }

    if (showTagBrowser) {
        TagBrowserSheet(
            ctx = ctx,
            mainVM = mainVM,
            onDismiss = { showTagBrowser = false },
        )
    }
}

@Composable
private fun MainBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit, onSwipeUp: () -> Unit) {
    Box(Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(onVerticalDrag = { _, drag -> if (drag < -50f) onSwipeUp() })
    }) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.height(56.dp)
        ) {
            navTabs.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab.index,
                    onClick = { onTabSelected(tab.index) },
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

@Composable
private fun MainTabContent(
    state: ExplorerUiState,
    tabSettings: TabViewSettings,
    settingsMode: SettingsMode,
    albumsViewModel: AlbumsViewModel,
    mainVM: ExplorerViewModel,
    ctx: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    navController: NavHostController,
) {
    when (state.selectedTab) {
        0 -> MediaScreen(
            viewSettings = tabSettings.media,
            ratingFilter = state.activeRatingFilter,
            tagFilterPaths = state.activeTagFilter,
            pathFilter = state.activePathFilter,
            activeTagName = state.activeTagName,
            refreshTrigger = state.mediaRefreshTrigger,
            onClearFilter = { mainVM.clearFilters() },
            onNavigateToViewer = { paths, startIndex -> navController.navigate(Viewer(paths, startIndex)) },
        )
        1 -> AlbumsScreen(
            viewModel = albumsViewModel,
            onFolderClick = { dir ->
                ctx.startActivity(Intent(ctx, ComposeFolderActivity::class.java).apply {
                    putExtra("FOLDER_PATH", dir.path)
                })
            },
            viewSettings = tabSettings.albums,
        )
        2 -> ExplorerScreen(
            internalStoragePath = state.explorerPath,
            folderSettings = tabSettings.explorerAlbums,
            mediaSettings = tabSettings.explorerMedia,
        )
        3 -> CollectionsScreen(
            onCollectionClick = { coll ->
                mainVM.setPreFilterTab(3)
                mainVM.setRatingFilter(coll.ratingFilter)
                if (coll.tagFilter.isNotBlank()) {
                    scope.launch(Dispatchers.IO) {
                        val tagNames = coll.tagFilter.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val tagPaths = mutableSetOf<String>()
                        try {
                            ctx.mediaCacheDB.getAllTagged()
                                .filter { mc -> tagNames.any { mc.tags.contains(it) } }
                                .forEach { tagPaths.add(it.fullPath) }
                        } catch (_: Exception) { }
                        withContext(Dispatchers.Main) {
                            mainVM.setTagFilter(if (tagPaths.isNotEmpty()) tagPaths else null, coll.tagFilter.takeIf { it.isNotBlank() })
                        }
                    }
                } else {
                    mainVM.setTagFilter(null, null)
                }
                val included = coll.getIncludedPaths()
                val excluded = coll.getExcludedPaths()
                val incPaths = included.mapNotNull { it.removePrefix("content:").takeIf(String::isNotEmpty) ?: it }
                    .filter { it.isNotEmpty() }.toSet()
                val excPaths = excluded.mapNotNull { it.removePrefix("content:").takeIf(String::isNotEmpty) ?: it }
                    .filter { it.isNotEmpty() }.toSet()
                mainVM.setPathFilter(when {
                    incPaths.isNotEmpty() && excPaths.isNotEmpty() -> incPaths - excPaths
                    incPaths.isNotEmpty() -> incPaths
                    excPaths.isNotEmpty() -> {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val allPaths = ctx.mediaDB.getNewestMedia(5000).map { it.path }.toSet()
                                withContext(Dispatchers.Main) { mainVM.setPathFilter(allPaths - excPaths) }
                            } catch (_: Exception) { }
                        }
                        null
                    }
                    else -> null
                })
                mainVM.setSelectedTab(0)
            },
        )
        4 -> FavoritesScreen(viewSettings = tabSettings.favorites, onNavigateToViewer = { paths, startIndex -> navController.navigate(Viewer(paths, startIndex)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSheets(
    activeSheet: ActiveSheet?,
    selectedTab: Int,
    tabSettings: TabViewSettings,
    settingsMode: SettingsMode,
    viewSettingsVM: ViewSettingsViewModel,
    navController: NavHostController,
    onDismissSheet: () -> Unit,
    onSelectSheet: (ActiveSheet) -> Unit,
    onShowRatingBrowser: () -> Unit,
) {
    if (activeSheet == ActiveSheet.MORE_MENU) {
        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Mehr", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                if (selectedTab in listOf(0, 1, 2, 4)) {
                    MenuRow(Icons.Default.GridView, "Ansicht") { onSelectSheet(ActiveSheet.VIEW_SETTINGS) }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                MenuRow(Icons.Default.Star, "Nach Bewertung") { onDismissSheet(); onShowRatingBrowser() }
                MenuRow(Icons.AutoMirrored.Filled.Label, "Nach Tags") { onDismissSheet(); navController.navigate(TagBrowser) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                MenuRow(Icons.Default.Settings, "Einstellungen") { onDismissSheet(); navController.navigate(Settings) }
                MenuRow(Icons.Default.CollectionsBookmark, "Sammlungen verwalten") { onDismissSheet(); navController.navigate(ManageCollections) }
                MenuRow(Icons.Default.Delete, "Speicher-Analyse") { onDismissSheet(); navController.navigate(StorageAnalysis) }
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
            onDismiss = onDismissSheet,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OmniSearchSheet(
    onDismiss: () -> Unit,
    storagePath: String,
    onNavigate: (String) -> Unit,
    onFilterChanged: (filterPaths: Set<String>?, rating: Int, tagPaths: Set<String>?, tagName: String?, fileType: Int, dateRange: Int) -> Unit,
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
    var folderResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val searchCache = remember { mutableMapOf<String, Set<String>>() }
    var tagResults by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var fileTypeFilter by remember { mutableIntStateOf(0) } // 0=Alle, 1=Bilder, 2=Videos
    var dateFilter by remember { mutableIntStateOf(0) }     // 0=Alle, 1=Heute, 2=7 Tage, 3=30 Tage

    // Load tags on demand (only when user clicks "Tags laden") - catch ALL errors
    LaunchedEffect(showTags) {
        if (!showTags) return@LaunchedEffect
        try {
            val cached = withContext(Dispatchers.IO) {
                try { ctx.mediaCacheDB.getAllTagged() } catch (e: Exception) { emptyList() }
            }
            val tags = mutableMapOf<String, MutableSet<String>>()
            cached.forEach { mc ->
                kotlin.runCatching {
                    mc.tags.split(",").filter { it.isNotBlank() }.forEach { t ->
                        tags.getOrPut(t.trim()) { mutableSetOf() }.add(mc.fullPath)
                    }
                }
            }
            allTags = if (tags.isEmpty()) emptyMap() else tags
        } catch (e: Throwable) {
            android.util.Log.e("OmniSearch", "Tag load failed", e)
        }
    }

    fun triggerSearch() {
        searchTrigger++
    }

    // Text search: fuzzy match on filename + full path (manual trigger + live debounce)
    LaunchedEffect(searchTrigger) {
        if (query.length < 2) { textMatchPaths = null; return@LaunchedEffect }
        isSearching = true
        // Yield so the spinner can render
        kotlinx.coroutines.delay(50)
        val qParts = query.lowercase().split(" ").filter { it.isNotBlank() }
        if (qParts.isEmpty()) { textMatchPaths = null; isSearching = false; return@LaunchedEffect }

        withContext(Dispatchers.IO) {
            val cacheKey = "${query}_${fileTypeFilter}_${dateFilter}"
            searchCache[cacheKey]?.let { textMatchPaths = it; return@withContext }
            if (searchCache.size > 50) searchCache.clear()

            val matched = mutableSetOf<String>()
            val folders = mutableListOf<Pair<String, String>>()
            val tags = mutableListOf<Pair<String, Int>>()

            // 1. Search media via MediaStore (with file type + date filters)
            try {
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val proj = arrayOf(android.provider.MediaStore.MediaColumns.DATA, android.provider.MediaStore.MediaColumns.DISPLAY_NAME, android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                val selParts = mutableListOf<String>()
                val argsList = mutableListOf<String>()
                when (fileTypeFilter) {
                    1 -> { selParts.add("${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"); argsList.add(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()) }
                    2 -> { selParts.add("${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"); argsList.add(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()) }
                    else -> { selParts.add("(${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"); argsList.addAll(arrayOf(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())) }
                }
                when (dateFilter) {
                    1 -> { val t = System.currentTimeMillis() / 1000 - (System.currentTimeMillis() % 86400000) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) }
                    2 -> { val t = (System.currentTimeMillis() - 7 * 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) }
                    3 -> { val t = (System.currentTimeMillis() - 30 * 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) }
                    4 -> { val t = (System.currentTimeMillis() - 365 * 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) }
                }
                ctx.contentResolver.query(uri, proj, selParts.joinToString(" AND "), argsList.toTypedArray(), null)?.use { c ->
                    val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                    val nameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    val dateCol = if (dateFilter > 0) c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_MODIFIED) else -1
                    while (c.moveToNext()) {
                        val path = c.getString(dataCol) ?: continue
                        val name = c.getString(nameCol) ?: ""
                        val lowerFull = "$name ${path.lowercase()}"
                        if (qParts.all { it in lowerFull }) matched.add(path)
                    }
                }
            } catch (_: Exception) { }

            // 2. Search folders (scan known roots 1 level deep)
            try {
                val roots = listOf(
                    storagePath, "/storage/emulated/0/DCIM", "/storage/emulated/0/Download",
                    "/storage/emulated/0/Pictures", "/storage/emulated/0/Movies"
                ).distinct().filter { java.io.File(it).isDirectory }
                roots.forEach { root ->
                    java.io.File(root).listFiles()?.forEach { f ->
                        if (f.isDirectory && !f.name.startsWith(".")) {
                            if (qParts.all { it in f.name.lowercase() }) {
                                folders.add(f.name to f.absolutePath)
                            }
                        }
                    }
                }
            } catch (_: Exception) { }

            // 3. Search tags (from already-loaded allTags)
            if (allTags.isNotEmpty()) {
                qParts.forEach { qp ->
                    allTags.entries.forEach { (tag, paths) ->
                        if (tag.lowercase().contains(qp) && tags.none { it.first == tag }) {
                            tags.add(tag to paths.size)
                        }
                    }
                }
            }

            textMatchPaths = matched
            if (matched.isNotEmpty()) searchCache[cacheKey] = matched
            folderResults = folders.sortedBy { it.first }.take(20)
            tagResults = tags.sortedByDescending { it.second }.take(20)
            isSearching = false
        }
    }

    // Live debounced search (300ms after last keystroke)
    LaunchedEffect(query) {
        if (query.length < 2) { textMatchPaths = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(400)
        triggerSearch()
    }

    var combinedPaths by remember { mutableStateOf<Set<String>?>(null) }

    // Compute combined filter when criteria change (NOT on every keystroke)
    LaunchedEffect(ratingFilter, selectedTags, textMatchPaths) {
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
                onFilterChanged(result, ratingFilter, selectedTags.let { if (it.isEmpty()) null else allTags.filterKeys { t -> t in it }.values.flatten().toSet() }, selectedTags.takeIf { it.isNotEmpty() }?.joinToString(", "), fileTypeFilter, dateFilter)
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

            // File type + Date filter chips
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Typ:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("Alle" to 0, "Bilder" to 1, "Videos" to 2).forEach { (label, v) ->
                    Surface(onClick = { fileTypeFilter = v }, shape = RoundedCornerShape(12.dp), color = if (fileTypeFilter == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = if (fileTypeFilter == v) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Text("Datum:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("Alle" to 0, "Heute" to 1, "7 Tage" to 2, "30 Tage" to 3, "Dieses Jahr" to 4).forEach { (label, v) ->
                    Surface(onClick = { dateFilter = v }, shape = RoundedCornerShape(12.dp), color = if (dateFilter == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = if (dateFilter == v) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            // Tag chips (lazy-loaded on demand)
            if (allTags.isNotEmpty()) {
                Text("Tags:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            } else if (showTags && allTags.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
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

            // Search results (grouped)
            val hasResults = (textMatchPaths != null) || folderResults.isNotEmpty() || tagResults.isNotEmpty()
            if (hasResults && !isSearching) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    if (folderResults.isNotEmpty()) {
                        item { Text("Ordner (${folderResults.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) }
                        items(folderResults.take(5), key = { it.second }) { (name, path) ->
                            Surface(modifier = Modifier.fillMaxWidth().clickable {
                                onNavigate(path)
                            }, color = Color.Transparent, shape = RoundedCornerShape(8.dp)) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (folderResults.size > 5) {
                            item { Text("+ ${folderResults.size - 5} weitere", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp)) }
                        }
                    }
                    if (tagResults.isNotEmpty()) {
                        item { Text("Tags (${tagResults.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) }
                        items(tagResults.take(10), key = { it.first }) { (tag, cnt) ->
                            Surface(modifier = Modifier.fillMaxWidth().clickable {
                                onFilterChanged(null, 0, allTags[tag]?.toSet(), tag, 0, 0)
                                onDismiss()
                            }, color = Color.Transparent, shape = RoundedCornerShape(8.dp)) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(tag, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text("$cnt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (textMatchPaths != null) {
                        val mc = textMatchPaths!!.size
                        item { Text("Medien ($mc)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
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
