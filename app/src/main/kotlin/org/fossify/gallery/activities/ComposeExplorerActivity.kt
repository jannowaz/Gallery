package org.fossify.gallery.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.filled.ContentCopy
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
import org.fossify.gallery.compose.screens.tagbrowser.TagBrowserScreen
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.navigation.GalleryNavHost
import org.fossify.gallery.navigation.ManageCollections
import org.fossify.gallery.navigation.Settings
import org.fossify.gallery.navigation.StorageAnalysis
import org.fossify.gallery.navigation.RecycleBin
import org.fossify.gallery.navigation.DuplicateFinder
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
import org.fossify.gallery.workers.MediaSyncWorker
import org.fossify.gallery.workers.MetadataSyncWorker
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

    @Volatile private var lastObserverMs = 0L
    private val mediaObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            val now = System.currentTimeMillis()
            if (now - lastObserverMs < 1500) return
            lastObserverMs = now
            RefreshBus.trigger()
            MediaSyncWorker.scheduleIncrementalSync(this@ComposeExplorerActivity)
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
            MediaSyncWorker.schedule(this)
            MediaSyncWorker.scheduleInitialSync(this)
            MetadataSyncWorker.schedule(this)
            MetadataSyncWorker.scheduleNow(this)
            setContent { GalleryNavHost() }
        } else {
            requestPermissionLauncher.launch(getMediaPermissionStrings())
            RecycleBinCleanupWorker.schedule(this)
            setContent { GalleryNavHost() }
        }
    }

    private fun hasMediaPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(mediaObserver)
    }

    private fun getMediaPermissionStrings(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        return perms.toTypedArray()
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
    NavTab(4, "Favoriten", Icons.Default.Star),
    NavTab(5, "Tags", Icons.AutoMirrored.Filled.Label)
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
    var isMediaSelectionActive by remember { mutableStateOf(false) }
    var showAllFilesPrompt by remember { mutableStateOf(false) }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showAllFilesPrompt = !hasAllFilesAccess(ctx)
    }
    LaunchedEffect(Unit) { if (!hasAllFilesAccess(ctx)) showAllFilesPrompt = true }

    LaunchedEffect(Unit) {
        mainVM.initializeDatabase { mainVM.triggerMediaRefresh() }
    }

    BackHandler(enabled = uiState.activeRatingFilter > 0 || uiState.activeTagFilter != null || uiState.activePathFilter != null || showTagBrowser || showOmniSearch || (uiState.selectedTab != 1 && !isMediaSelectionActive)) {
        when {
            showTagBrowser -> showTagBrowser = false
            showOmniSearch -> showOmniSearch = false
            uiState.activeTagFilter != null -> { val backTab = if (uiState.preFilterTab >= 0) uiState.preFilterTab else 1; mainVM.setTagFilter(null, null); mainVM.setSelectedTab(backTab) }
            uiState.activePathFilter != null -> {
                val backTab = if (uiState.preFilterTab >= 0) uiState.preFilterTab else 1
                mainVM.clearFilters()
                mainVM.setSelectedTab(backTab)
            }
            uiState.activeRatingFilter > 0 -> { val backTab = if (uiState.preFilterTab >= 0) uiState.preFilterTab else 1; mainVM.setRatingFilter(0); mainVM.setSelectedTab(backTab) }
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
                onMediaSelectionChanged = { isMediaSelectionActive = it },
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
        currentScanFolder = if (uiState.selectedTab == 2) uiState.explorerPath else "",
        onDismissSheet = { activeSheet = null },
        onSelectSheet = { activeSheet = it },
        onShowRatingBrowser = { showRatingBrowser = true },
    )

    if (showAllFilesPrompt) {
        AlertDialog(
            onDismissRequest = { showAllFilesPrompt = false },
            title = { Text("Zugriff auf alle Dateien") },
            text = { Text("Zum Verschieben, Kopieren, Löschen, Bearbeiten und Taggen von Medien benötigt die App den Zugriff auf alle Dateien. Lesen/Anzeigen funktioniert auch ohne.") },
            confirmButton = {
                TextButton(onClick = {
                    showAllFilesPrompt = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            allFilesLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        } catch (_: Exception) {
                            try { allFilesLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (_: Exception) { }
                        }
                    }
                }) { Text("Erlauben") }
            },
            dismissButton = { TextButton(onClick = { showAllFilesPrompt = false }) { Text("Später") } },
        )
    }

    if (showOmniSearch) {
        OmniSearchSheet(
            onDismiss = { showOmniSearch = false },
            storagePath = android.os.Environment.getExternalStorageDirectory().absolutePath,
            onNavigate = { path -> mainVM.setExplorerPath(path); showOmniSearch = false; mainVM.setSelectedTab(2) },
            onFilterChanged = { textPaths, rating, tagPaths, tagName, _, _ ->
                mainVM.setRatingFilter(rating)
                mainVM.setPathFilter(textPaths, if (textPaths != null) "Suche" else null)
                mainVM.setCollectionName(null)
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
    onMediaSelectionChanged: (Boolean) -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = state.selectedTab, pageCount = { navTabs.size })
    LaunchedEffect(pagerState.settledPage) { mainVM.setSelectedTab(pagerState.settledPage) }
    LaunchedEffect(state.selectedTab) {
        if (state.selectedTab != pagerState.currentPage && state.selectedTab != pagerState.targetPage) {
            pagerState.animateScrollToPage(state.selectedTab)
        }
    }

    HorizontalPager(state = pagerState, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { tab ->
        when (tab) {
        0 -> MediaScreen(
            viewSettings = tabSettings.media,
            ratingFilter = state.activeRatingFilter,
            tagFilterPaths = state.activeTagFilter,
            pathFilter = state.activePathFilter,
            activeTagName = state.activeTagName,
            activePathName = state.activePathName,
            activeCollectionName = state.activeCollectionName,
            refreshTrigger = state.mediaRefreshTrigger,
            onClearFilter = { mainVM.clearFilters() },
            onClearRatingFilter = { mainVM.setRatingFilter(0) },
            onClearTagFilter = { mainVM.setTagFilter(null, null) },
            onClearPathFilter = { mainVM.setPathFilter(null); mainVM.setCollectionName(null) },
            onNavigateToViewer = { paths, startIndex -> navController.navigate(Viewer(paths, startIndex)) },
            scrollToPath = state.lastViewedPath,
            onClearScrollToPath = { mainVM.clearLastViewedPath() },
            onSelectionActiveChanged = onMediaSelectionChanged,
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
            onPathChange = { mainVM.setExplorerPath(it) },
        )
        3 -> CollectionsScreen(
            onCollectionClick = { coll ->
                mainVM.setPreFilterTab(3)
                mainVM.setCollectionName(coll.name)
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
                val incPaths = included.mapNotNull { resolveContentUriToPath(it) }
                    .filter { it.isNotEmpty() }.toSet()
                val excPaths = excluded.mapNotNull { resolveContentUriToPath(it) }
                    .filter { it.isNotEmpty() }.toSet()
                mainVM.setPathFilter(when {
                    incPaths.isNotEmpty() && excPaths.isNotEmpty() -> {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val allFiles = ctx.mediaDB.getNewestMedia(5000).map { it.path }.toSet()
                                val excDirs = excPaths.filter { File(it).isDirectory }.toSet()
                                val incDirs = incPaths.filter { File(it).isDirectory }.toSet()
                                val result = allFiles.filter { path ->
                                    path in incPaths || incDirs.any { path.startsWith("$it/") }
                                }.filter { path ->
                                    path !in excPaths && excDirs.none { path.startsWith("$it/") }
                                }.toSet()
                                withContext(Dispatchers.Main) { mainVM.setPathFilter(result) }
                            } catch (_: Exception) { }
                        }
                        null
                    }
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
        5 -> TagBrowserScreen(
            onBack = {},
            onTagFilterApplied = { tagPaths, tagName ->
                mainVM.setPreFilterTab(5)
                mainVM.setRatingFilter(0)
                mainVM.setTagFilter(tagPaths, tagName)
                mainVM.setPathFilter(null)
                mainVM.setSelectedTab(0)
            },
        )
    }
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
    currentScanFolder: String = "",
    onDismissSheet: () -> Unit,
    onSelectSheet: (ActiveSheet) -> Unit,
    onShowRatingBrowser: () -> Unit,
) {
    if (activeSheet == ActiveSheet.MORE_MENU) {
        val sheetCtx = LocalContext.current
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
                MenuRow(Icons.Default.Search, "Tags & Bewertungen neu scannen") { onDismissSheet(); MetadataSyncWorker.scheduleFullScan(sheetCtx) }
                MenuRow(Icons.Default.Settings, "Einstellungen") { onDismissSheet(); navController.navigate(Settings) }
                MenuRow(Icons.Default.CollectionsBookmark, "Sammlungen verwalten") { onDismissSheet(); navController.navigate(ManageCollections) }
                MenuRow(Icons.Default.Delete, "Speicher-Analyse") { onDismissSheet(); navController.navigate(StorageAnalysis) }
                MenuRow(Icons.Default.ContentCopy, "Duplikate finden") { onDismissSheet(); navController.navigate(DuplicateFinder(currentScanFolder)) }
                MenuRow(Icons.Default.Delete, "Papierkorb") { onDismissSheet(); navController.navigate(RecycleBin) }
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
            isAlbumMode = (selectedTab == 1 || selectedTab == 2) && settingsMode == SettingsMode.ALBUMS,
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
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var ratingFilter by remember { mutableIntStateOf(0) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allTags by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var isSearching by remember { mutableStateOf(false) }
    var textMatchPaths by remember { mutableStateOf<Set<String>?>(null) }
    var folderResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var tagResults by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var fileTypeFilter by remember { mutableIntStateOf(0) }
    var dateFilter by remember { mutableIntStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    val searchCache = remember { mutableMapOf<String, Set<String>>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val cached = ctx.mediaCacheDB.getAllTagged()
                val tags = mutableMapOf<String, MutableSet<String>>()
                cached.forEach { mc -> mc.tags.split(",").filter { it.isNotBlank() }.forEach { t -> tags.getOrPut(t.trim()) { mutableSetOf() }.add(mc.fullPath) } }
                withContext(Dispatchers.Main) { allTags = tags.takeIf { it.isNotEmpty() } ?: emptyMap() }
            } catch (_: Exception) { }
        }
    }

    fun performSearch() {
        if (query.length < 2) { textMatchPaths = null; return }
        isSearching = true
        val qParts = query.lowercase().split(" ").filter { it.isNotBlank() }
        if (qParts.isEmpty()) { textMatchPaths = null; isSearching = false; return }
        scope.launch(Dispatchers.IO) {
            val cacheKey = "${query}_${fileTypeFilter}_${dateFilter}"
            searchCache[cacheKey]?.let { c -> textMatchPaths = c.filter { java.io.File(it).exists() }.toSet(); isSearching = false; return@launch }
            if (searchCache.size > 30) searchCache.clear()
            val matched = mutableSetOf<String>()
            val folders = mutableListOf<Pair<String, String>>()
            val tags = mutableListOf<Pair<String, Int>>()
            try {
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val proj = arrayOf(android.provider.MediaStore.MediaColumns.DATA, android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                val selParts = mutableListOf<String>(); val argsList = mutableListOf<String>()
                when (fileTypeFilter) { 1 -> { selParts.add("${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"); argsList.add(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()) } 2 -> { selParts.add("${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"); argsList.add(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()) } else -> { selParts.add("(${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"); argsList.addAll(arrayOf(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())) } }
                when (dateFilter) { 1 -> { val t = (System.currentTimeMillis() - 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) } 2 -> { val t = (System.currentTimeMillis() - 7 * 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) } 3 -> { val t = (System.currentTimeMillis() - 30 * 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) } 4 -> { val t = (System.currentTimeMillis() - 365 * 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) } }
                qParts.forEach { qp -> selParts.add("(${android.provider.MediaStore.MediaColumns.DATA} LIKE ? OR ${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?)"); argsList.add("%$qp%"); argsList.add("%$qp%") }
                ctx.contentResolver.query(uri, proj, selParts.joinToString(" AND "), argsList.toTypedArray(), null)?.use { c ->
                    val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA); val nameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    while (c.moveToNext()) { val path = c.getString(dataCol) ?: continue; val name = c.getString(nameCol) ?: ""; if (qParts.all { it in "${name} ${path}".lowercase() } && java.io.File(path).exists()) matched.add(path) }
                }
            } catch (_: Exception) { }
            try { ctx.directoryDB.getAll().forEach { d -> val lp = d.path.lowercase(); if (qParts.all { it in lp }) folders.add(d.name to d.path) } } catch (_: Exception) { }
            if (allTags.isNotEmpty()) qParts.forEach { qp -> allTags.entries.forEach { (tag, paths) -> if (tag.lowercase().contains(qp) && tags.none { it.first == tag }) tags.add(tag to paths.size) } }
            withContext(Dispatchers.Main) { textMatchPaths = matched.takeIf { it.isNotEmpty() }?.also { searchCache[cacheKey] = it }; folderResults = folders.sortedBy { it.first }.take(15); tagResults = tags.sortedByDescending { it.second }.take(15); isSearching = false }
        }
    }

    LaunchedEffect(query) { kotlinx.coroutines.delay(300); performSearch() }
    LaunchedEffect(fileTypeFilter, dateFilter) { if (query.length >= 2) performSearch() }

    val hasAnyFilter = ratingFilter > 0 || selectedTags.isNotEmpty() || fileTypeFilter > 0 || dateFilter > 0
    val hasResults = textMatchPaths != null && textMatchPaths!!.isNotEmpty()
    val mc = textMatchPaths?.size ?: 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(max = 560.dp)) {
            // Search bar
            OutlinedTextField(value = query, onValueChange = { query = it; textMatchPaths = null },
                placeholder = { Text("Dateiname, Ordner oder Tag\u2026") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, "Suchen", modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; textMatchPaths = null; folderResults = emptyList(); tagResults = emptyList() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Leeren", Modifier.size(16.dp)) }
                        if (isSearching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(6.dp))

            // Filter toggle bar
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = { showFilters = !showFilters }, shape = RoundedCornerShape(12.dp), color = if (showFilters || hasAnyFilter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = if (showFilters || hasAnyFilter) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (hasAnyFilter) {
                            val parts = mutableListOf<String>()
                            if (ratingFilter > 0) parts.add("★$ratingFilter")
                            if (selectedTags.isNotEmpty()) parts.add(selectedTags.joinToString(","))
                            Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
                        } else Text("Filter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (hasAnyFilter) {
                    Spacer(Modifier.width(4.dp))
                    Surface(onClick = { ratingFilter = 0; selectedTags = emptySet(); fileTypeFilter = 0; dateFilter = 0 }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text("Zurücksetzen", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Expandable filter panel
            if (showFilters) {
                Spacer(Modifier.height(6.dp))
                // Rating
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("★", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (i in 1..5) IconButton(onClick = { ratingFilter = if (ratingFilter == i) 0 else i }, modifier = Modifier.size(32.dp)) { Icon(if (i <= ratingFilter) Icons.Default.Star else Icons.Default.StarBorder, "$i", tint = if (i <= ratingFilter) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(4.dp))
                    listOf("Alles" to 0, "Bilder" to 1, "Videos" to 2).forEach { (l, v) -> Surface(onClick = { fileTypeFilter = v }, shape = RoundedCornerShape(10.dp), color = if (fileTypeFilter == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) { Text(l, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = if (fileTypeFilter == v) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) } }
                    Spacer(Modifier.width(4.dp))
                    listOf("Alle" to 0, "Heute" to 1, "7d" to 2, "30d" to 3).forEach { (l, v) -> Surface(onClick = { dateFilter = v }, shape = RoundedCornerShape(10.dp), color = if (dateFilter == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) { Text(l, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = if (dateFilter == v) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) } }
                }
                // Tags
                if (allTags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        allTags.entries.sortedByDescending { it.value.size }.take(15).forEach { (tag, _) ->
                            val sel = tag in selectedTags
                            Surface(onClick = { selectedTags = if (sel) selectedTags - tag else selectedTags + tag }, shape = RoundedCornerShape(12.dp), color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                                Text(tag, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Results
            if (isSearching) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            } else if (query.length >= 2 && !hasResults && folderResults.isEmpty() && tagResults.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("Keine Ergebnisse", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (hasResults || folderResults.isNotEmpty() || tagResults.isNotEmpty()) {
                Spacer(Modifier.height(4.dp)); HorizontalDivider(); Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    if (folderResults.isNotEmpty()) {
                        item { Text("Ordner", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(folderResults.take(5), key = { it.second }) { (name, path) ->
                            Surface(modifier = Modifier.fillMaxWidth().clickable { onNavigate(path) }, color = Color.Transparent, shape = RoundedCornerShape(8.dp)) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (folderResults.size > 5) item { Text("+${folderResults.size - 5} weitere", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)) }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (tagResults.isNotEmpty()) {
                        item { Text("Tags", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(tagResults.take(8), key = { it.first }) { (tag, cnt) ->
                            Surface(modifier = Modifier.fillMaxWidth().clickable { onFilterChanged(null, 0, allTags[tag]?.toSet(), tag, 0, 0); onDismiss() }, color = Color.Transparent, shape = RoundedCornerShape(8.dp)) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(tag, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); Text("$cnt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (hasResults) {
                        item { Text("Medien ($mc)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (hasResults || folderResults.isNotEmpty() || tagResults.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                        Surface(onClick = {
                            onFilterChanged(textMatchPaths, ratingFilter,
                                selectedTags.let { if (it.isEmpty()) null else allTags.filterKeys { t -> t in it }.values.flatten().toSet() },
                                selectedTags.takeIf { it.isNotEmpty() }?.joinToString(", "), fileTypeFilter, dateFilter)
                            onDismiss()
                        }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary) {
                            Text("${mc + folderResults.size} Ergebnisse anzeigen", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

private fun hasAllFilesAccess(ctx: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

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
