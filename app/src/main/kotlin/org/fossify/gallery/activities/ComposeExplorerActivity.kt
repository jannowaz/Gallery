package org.fossify.gallery.activities
import org.fossify.gallery.compose.theme.Radius

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import org.fossify.gallery.compose.components.FilterSheetContent
import org.fossify.gallery.compose.screens.ViewSettingsContent
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.helpers.MyWidgetProvider
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
import org.fossify.gallery.compose.components.BottomSearchField
import org.fossify.gallery.compose.components.AllFilesAccessSheet
import org.fossify.gallery.compose.screens.tagbrowser.TagBrowserScreen
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.navigation.GalleryNavHost
import org.fossify.gallery.navigation.ManageCollections
import org.fossify.gallery.navigation.Settings
import org.fossify.gallery.navigation.StorageAnalysis
import org.fossify.gallery.navigation.FoldersMover
import org.fossify.gallery.navigation.RecycleBin
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.MediaCollection
import org.fossify.gallery.R
import org.fossify.gallery.navigation.DuplicateFinder
import org.fossify.gallery.navigation.Folder
import org.fossify.gallery.navigation.TagBrowser
import org.fossify.gallery.navigation.Viewer
import org.fossify.gallery.navigation.ViewerArgs
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.batchJobItemDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.expandTagsWithDescendants
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.XmpWriter
import org.fossify.gallery.helpers.resolveContentUriToPath
import org.fossify.gallery.viewmodels.AlbumsViewModel
import org.fossify.gallery.viewmodels.ExplorerViewModel
import org.fossify.gallery.viewmodels.ExplorerUiState
import org.fossify.gallery.viewmodels.hasActiveFilter
import org.fossify.gallery.workers.RecycleBinCleanupWorker
import org.fossify.gallery.workers.MediaSyncWorker
import org.fossify.gallery.workers.MetadataSyncWorker
import java.io.File

private enum class ActiveSheet { VIEW_SETTINGS }

/** Which section the combined Filter/Ansicht sheet opens on - only meaningful on tabs where
 * filtering applies (Media/Favorites, see MainSheets); other tabs always render VIEW regardless. */
private enum class SheetSection { FILTER, VIEW }

class ComposeExplorerActivity : ComponentActivity() {

    /** Pre-Android-12 fallback for auto-PiP: 12+ uses setAutoEnterEnabled (see VideoPage), older
     * versions only get this hint when the user leaves via Home while a viewer video plays. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val aspect = org.fossify.gallery.compose.util.PipState.activeVideoAspect
        if (aspect != null && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            try {
                enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(aspect).build())
            } catch (e: Exception) {
                android.util.Log.e("Explorer", "enterPictureInPictureMode failed", e)
            }
        }
    }

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

    // Reached when this Activity is already running and gets re-launched (e.g. the Quick Mover
    // widget's "set up folder pairs" button, see MoverWidgetProvider) - FLAG_ACTIVITY_CLEAR_TOP on
    // an already-top instance delivers here instead of a fresh onCreate(). setIntent() alone isn't
    // enough to reach the NavHost (it's already composed and won't re-run a one-shot LaunchedEffect
    // just because the Activity's own intent field changed), so this also publishes onto
    // NavigateBus, which GalleryNavHost's effect collects from in addition to the cold-start check.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(org.fossify.gallery.helpers.MoverWidgetProvider.EXTRA_NAVIGATE_TO)?.let {
            org.fossify.gallery.compose.util.NavigateBus.trigger(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        addOnPictureInPictureModeChangedListener { info ->
            org.fossify.gallery.compose.util.PipState.inPip = info.isInPictureInPictureMode
        }

        // SharedPreferences (config + the legacy default-named prefs) are now warmed from
        // App.onCreate() instead - it runs well before this Activity and gives that warm-up a
        // real head start, unlike doing it here mere microseconds before setContent() below
        // triggers the same reads on the main thread (ViewSettingsViewModel.loadFromConfig(),
        // ExplorerViewModel's init).

        if (hasMediaPermissions()) {
            setContent { GalleryNavHost() }
            // Fire-and-forget scheduling/cleanup: none of it needs to complete before the first
            // frame, and each call already hands its actual work off to WorkManager's/Room's own
            // background executor - running the WorkManager.getInstance()+enqueue setup itself off
            // the main thread here keeps all of it off the path to first frame, not just the part
            // each call was already deferring internally.
            lifecycleScope.launch(Dispatchers.IO) {
                RecycleBinCleanupWorker.schedule(this@ComposeExplorerActivity)
                MediaSyncWorker.schedule(this@ComposeExplorerActivity)
                MediaSyncWorker.scheduleInitialSync(this@ComposeExplorerActivity)
                // Self-clearing after one successful run; see MediaSyncWorker.scheduleGapRepair.
                MediaSyncWorker.scheduleGapRepair(this@ComposeExplorerActivity)
                MetadataSyncWorker.cancelAutomatic(this@ComposeExplorerActivity)
                MetadataSyncWorker.cancel(this@ComposeExplorerActivity)
                // Sweep batch_job_items left behind by a MediaBatchWorker job that was interrupted
                // and never retried (e.g. app force-stopped mid-batch).
                try { batchJobItemDB.deleteStale(System.currentTimeMillis() - 24 * 60 * 60 * 1000L) } catch (e: Exception) { android.util.Log.e("Explorer", "Stale batch-job cleanup failed", e) }
            }
        } else {
            requestPermissionLauncher.launch(getMediaPermissionStrings())
            setContent { GalleryNavHost() }
            lifecycleScope.launch(Dispatchers.IO) { RecycleBinCleanupWorker.schedule(this@ComposeExplorerActivity) }
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

    // Registered/unregistered here rather than onCreate/onDestroy - this Activity's process (and
    // therefore this observer) stays alive across a home-press/task-switch, so binding to
    // onCreate/onDestroy meant every MediaStore write from *any* app (camera, chat auto-download,
    // screenshots, background sync) kept triggering a RefreshBus fan-out + incremental sync while
    // Gallery itself sat in the background doing nothing the user could see - a steady background
    // battery drain with no benefit, since none of that refreshed state is visible again until the
    // user actually returns to onStart().
    override fun onStart() {
        super.onStart()
        contentResolver.registerContentObserver(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
        )
        contentResolver.registerContentObserver(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver
        )
        // Catch up on everything MediaStore gained while we were stopped. Registering the observer
        // above only covers changes from here on - it does not fire for what already happened, and
        // this observer was (until now) the single caller of scheduleIncrementalSync. So photos
        // taken while Gallery sat in the background were never synced into the media/directories
        // tables on return; they only appeared after a cold start, via scheduleInitialSync.
        // Reproduced on an emulator: 3 images added while backgrounded stayed at 0 rows across a
        // foreground round trip, then all 3 landed after a force-stop + relaunch.
        //
        // Cheap to do unconditionally: syncNewMediaFromStore() is watermark-filtered
        // (DATE_MODIFIED > lastSyncTimestamp), so an onStart with nothing new is a single empty
        // query, and the enqueue itself is KEEP-policy so a still-pending job is not restarted.
        // Measured on a 163k-item library: 0 CPU ticks per foreground return.
        MediaSyncWorker.scheduleIncrementalSync(this)
    }

    override fun onStop() {
        super.onStop()
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

private data class NavTab(val index: Int, @androidx.annotation.StringRes val labelRes: Int, val icon: ImageVector)

/** A pinned quick-nav preview shown in the navigation drawer. */
data class DrawerPin(val key: String, val name: String, val thumb: String)

/** Applies a collection's include/exclude/tag/rating filters and jumps to the media tab. Shared by
 * the Collections tab and the drawer quick-nav previews. */
private fun applyCollection(
    coll: MediaCollection,
    mainVM: ExplorerViewModel,
    ctx: android.content.Context,
) {
    mainVM.setPreFilterTab(3)
    mainVM.setCollectionName(coll.name)
    mainVM.setRatingFilter(coll.ratingFilter)
    if (coll.tagFilter.isNotBlank()) {
        val tagNames = coll.tagFilter.split(",").map { it.trim() }.filter { it.isNotBlank() }
        // Expand each filter tag to include its descendants, so a Collection filtered on a parent
        // tag like "Places" also picks up files only tagged with a nested child like "Berlin". The
        // expanded name set is passed straight through - MediaViewModel resolves tag names to files
        // in SQL now, no DB round trip needed here.
        val expandedTagNames = expandTagsWithDescendants(tagNames, ctx.config.tagHierarchy)
        mainVM.setTagFilter(expandedTagNames.ifEmpty { null }, coll.tagFilter.takeIf { it.isNotBlank() })
    } else {
        mainVM.setTagFilter(null, null)
    }
    val included = coll.getIncludedPaths()
    val excluded = coll.getExcludedPaths()
    val incPaths = included.mapNotNull { resolveContentUriToPath(it) }.filter { it.isNotEmpty() }.toSet()
    val excPaths = excluded.mapNotNull { resolveContentUriToPath(it) }.filter { it.isNotEmpty() }.toSet()
    // Include/exclude are now plain filter data resolved in SQL (MediaRepository.getMediaPagedFiltered)
    // instead of a getNewestMedia(5000)-bounded diff computed here - a Collection touching a larger
    // library no longer silently drops eligible files past that cap.
    mainVM.setPathFilter(incPaths.ifEmpty { null })
    mainVM.setExcludePathFilter(excPaths.ifEmpty { null })
    mainVM.setSelectedTab(0)
}

@Composable
private fun PinnedPreviewRow(pins: List<DrawerPin>, onOpen: (String) -> Unit) {
    if (pins.isEmpty()) return
    val s = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 28.dp, end = s.md, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(s.sm),
    ) {
        pins.take(8).forEach { pin ->
            Column(
                Modifier.width(64.dp).clickable { onOpen(pin.key) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(Radius.md))) {
                    if (pin.thumb.isNotEmpty()) {
                        GalleryImage(path = pin.thumb, contentDescription = pin.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, placeholderIconSize = 16.dp)
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
                Text(
                    pin.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp).width(64.dp),
                )
            }
        }
    }
}

@Composable
private fun AppNavigationDrawer(
    selectedTab: Int,
    duplicateScanFolder: String,
    pinnedFavorites: List<DrawerPin>,
    pinnedCollections: List<DrawerPin>,
    onSelectTab: (Int) -> Unit,
    onNavigate: (Any) -> Unit,
    onOpenPinnedFavorite: (String) -> Unit,
    onOpenPinnedCollection: (String) -> Unit,
    onOpenViewSettings: () -> Unit,
    onFilterByRating: () -> Unit,
    onRescanMetadata: () -> Unit,
) {
    ModalDrawerSheet {
        val s = LocalSpacing.current
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(s.md))
            Text(
                stringResource(R.string.nav_library),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = s.md),
            )
            // Favorites itself is a bottom-nav tab now; this is just quick-jump shortcuts to
            // individual folders pinned from within that tab, so it only needs a caption here,
            // not another navigation entry duplicating the tab.
            if (pinnedFavorites.isNotEmpty()) {
                Text(
                    stringResource(R.string.nav_favorites),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = s.md),
                )
                PinnedPreviewRow(pinnedFavorites, onOpenPinnedFavorite)
            }
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_collections)) },
                icon = { Icon(Icons.Default.CollectionsBookmark, null) },
                selected = selectedTab == 3,
                onClick = { onSelectTab(3) },
                modifier = Modifier.padding(horizontal = s.md),
            )
            PinnedPreviewRow(pinnedCollections, onOpenPinnedCollection)
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_tags)) },
                icon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                selected = selectedTab == 5,
                onClick = { onSelectTab(5) },
                modifier = Modifier.padding(horizontal = s.md),
            )
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = s.sm))
            Text(
                stringResource(R.string.view_and_filter),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = s.md),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.view_label)) },
                icon = { Icon(Icons.Default.GridView, null) },
                selected = false,
                onClick = onOpenViewSettings,
                modifier = Modifier.padding(horizontal = s.md),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.by_rating)) },
                icon = { Icon(Icons.Default.Star, null) },
                selected = false,
                onClick = onFilterByRating,
                modifier = Modifier.padding(horizontal = s.md),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.rescan)) },
                icon = { Icon(Icons.Default.Search, null) },
                selected = false,
                onClick = onRescanMetadata,
                modifier = Modifier.padding(horizontal = s.md),
            )
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = s.sm))
            Text(
                stringResource(R.string.nav_more),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = s.md),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_manage_collections)) },
                icon = { Icon(Icons.Default.CollectionsBookmark, null) },
                selected = false,
                onClick = { onNavigate(ManageCollections) },
                modifier = Modifier.padding(horizontal = s.md),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_storage_analysis)) },
                icon = { Icon(Icons.Default.Storage, null) },
                selected = false,
                onClick = { onNavigate(StorageAnalysis) },
                modifier = Modifier.padding(horizontal = s.md),
            )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_find_duplicates)) },
            icon = { Icon(Icons.Default.ContentCopy, null) },
            selected = false,
            onClick = { onNavigate(DuplicateFinder(duplicateScanFolder)) },
            modifier = Modifier.padding(horizontal = s.md),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_mover)) },
            icon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
            selected = false,
            onClick = { onNavigate(FoldersMover) },
            modifier = Modifier.padding(horizontal = s.md),
        )
        NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_recycle_bin)) },
                icon = { Icon(Icons.Default.Delete, null) },
                selected = false,
                onClick = { onNavigate(RecycleBin) },
                modifier = Modifier.padding(horizontal = s.md),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.nav_settings)) },
                icon = { Icon(Icons.Default.Settings, null) },
                selected = false,
                onClick = { onNavigate(Settings) },
                modifier = Modifier.padding(horizontal = s.md),
            )
            Spacer(Modifier.height(s.lg))
        }
    }
}

private val navTabs = listOf(
    NavTab(0, R.string.media, Icons.Default.Image),
    NavTab(1, R.string.albums, Icons.Default.Folder),
    NavTab(2, R.string.explorer, Icons.Default.FolderOpen),
    NavTab(3, R.string.nav_collections, Icons.Default.CollectionsBookmark),
    NavTab(4, R.string.nav_favorites, Icons.Default.Star),
    NavTab(5, R.string.nav_tags, Icons.AutoMirrored.Filled.Label)
)

// Collections got its own bottom-bar tab per explicit request; Tags stays drawer-only since it's
// set-up-once/occasional rather than part of the daily browse loop.
private val bottomNavTabs = listOf(navTabs[0], navTabs[1], navTabs[2], navTabs[3], navTabs[4])

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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val previewRepo = LocalMediaRepository.current
    var pinnedFavDirs by remember { mutableStateOf<List<Directory>>(emptyList()) }
    var pinnedColls by remember { mutableStateOf<List<MediaCollection>>(emptyList()) }
    var pinnedCollThumbs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Only re-fetch when the drawer is actually opening (not closing, which also flips
    // targetValue) and only if the pinned sets themselves changed since the last fetch - otherwise
    // every open/close toggle reran getAllDirectories()/getCollections()/getMediaFromPath() for no
    // reason, since most drawer opens don't touch pinning at all.
    var lastPinnedKey by remember { mutableStateOf<Pair<Set<String>, Set<String>>?>(null) }
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue != DrawerValue.Open) return@LaunchedEffect
        val favPaths = ctx.config.pinnedFavoriteFolders
        val collIds = ctx.config.pinnedCollections
        val key = favPaths to collIds
        if (key == lastPinnedKey) return@LaunchedEffect
        lastPinnedKey = key
        val result = withContext(Dispatchers.IO) {
            val dirs = if (favPaths.isEmpty()) emptyList() else previewRepo.getAllDirectories().filter { it.path in favPaths }
            val colls = if (collIds.isEmpty()) emptyList() else previewRepo.getCollections().filter { it.id.toString() in collIds }
            // Same shape as AlbumsScreen's preview strip: only the first path is ever used, so cap
            // it in SQL rather than loading every row of the folder and discarding all but one.
            val thumbs = colls.associate { c -> c.id.toString() to (c.getIncludedPaths().firstNotNullOfOrNull { p -> previewRepo.getPreviewPathsFromPath(p, 1).firstOrNull() } ?: "") }
            Triple(dirs, colls, thumbs)
        }
        pinnedFavDirs = result.first; pinnedColls = result.second; pinnedCollThumbs = result.third
    }
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var activeSheetSection by remember { mutableStateOf(SheetSection.VIEW) }
    var omniQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val searchFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val searchKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val searchActive = searchFocused || omniQuery.isNotBlank()
    val closeSearch: () -> Unit = { omniQuery = ""; searchFocused = false; searchFocusManager.clearFocus(); searchKeyboard?.hide() }
    var showRatingBrowser by remember { mutableStateOf(false) }
    var isMediaSelectionActive by remember { mutableStateOf(false) }
    // Lets the Explorer tab's own directory-up BackHandler take priority over this screen's
    // tab-switch fallback below - otherwise both handlers are enabled at once while browsing a
    // subfolder and back can non-deterministically switch tabs instead of navigating up one level.
    var explorerCanGoUp by remember { mutableStateOf(false) }
    var showAllFilesPrompt by remember { mutableStateOf(false) }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showAllFilesPrompt = !hasAllFilesAccess(ctx)
    }
    LaunchedEffect(Unit) { if (!hasAllFilesAccess(ctx)) showAllFilesPrompt = true }

    LaunchedEffect(Unit) {
        mainVM.initializeDatabase { mainVM.triggerMediaRefresh() }
    }

    BackHandler(enabled = uiState.activeRatingFilter > 0 || uiState.activeTagFilter != null || uiState.activePathFilter != null || searchActive || (uiState.selectedTab != 1 && !isMediaSelectionActive && !(uiState.selectedTab == 2 && explorerCanGoUp))) {
        when {
            searchActive -> closeSearch()
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isMediaSelectionActive,
        drawerContent = {
            AppNavigationDrawer(
                selectedTab = uiState.selectedTab,
                duplicateScanFolder = if (uiState.selectedTab == 2) uiState.explorerPath else "",
                pinnedFavorites = pinnedFavDirs.map { DrawerPin(it.path, it.name, it.tmb) },
                pinnedCollections = pinnedColls.map { DrawerPin(it.id.toString(), it.name, pinnedCollThumbs[it.id.toString()] ?: "") },
                onSelectTab = { tab -> mainVM.setSelectedTab(tab); scope.launch { drawerState.close() } },
                onNavigate = { route -> scope.launch { drawerState.close() }; navController.navigate(route) },
                onOpenPinnedFavorite = { path -> scope.launch { drawerState.close() }; navController.navigate(Folder(path)) },
                onOpenPinnedCollection = { id -> scope.launch { drawerState.close() }; pinnedColls.find { it.id.toString() == id }?.let { applyCollection(it, mainVM, ctx) } },
                onOpenViewSettings = { scope.launch { drawerState.close() }; activeSheetSection = SheetSection.VIEW; activeSheet = ActiveSheet.VIEW_SETTINGS },
                onFilterByRating = { scope.launch { drawerState.close() }; showRatingBrowser = true },
                onRescanMetadata = { scope.launch { drawerState.close() }; MetadataSyncWorker.scheduleFullScan(ctx) },
            )
        },
    ) {
    Scaffold(
        bottomBar = {
            BottomChrome(
                isMediaSelectionActive = isMediaSelectionActive,
                searchActive = searchActive,
                omniQuery = omniQuery,
                onQueryChange = { omniQuery = it },
                searchFocusRequester = searchFocusRequester,
                onFocusChanged = { searchFocused = it },
                onClear = closeSearch,
                onMenuClick = { scope.launch { drawerState.open() } },
                onSearch = { searchKeyboard?.hide() },
                blurEnabled = BlurState.enabled,
                // Plain persistent toggle - defaults off (see Config.blurAllMedia) and only ever
                // changes on an explicit tap here or in Settings, no auto-revert. A previous
                // "momentary reveal" version snapped back on after 15s (or immediately on
                // backgrounding) regardless of what the user was doing - including mid-selection -
                // which was surprising and is exactly what was asked to be removed.
                onToggleBlur = {
                    val next = !BlurState.enabled
                    BlurState.enabled = next
                    ctx.config.blurAllMedia = next
                    MyWidgetProvider.requestImmediateUpdate(ctx)
                },
                onSwipeUp = { activeSheetSection = SheetSection.VIEW; activeSheet = ActiveSheet.VIEW_SETTINGS },
                onOpenFilterAndView = { activeSheetSection = SheetSection.FILTER; activeSheet = ActiveSheet.VIEW_SETTINGS },
                hasActiveFilter = uiState.hasActiveFilter,
                selectedTab = uiState.selectedTab,
                onTabSelected = { mainVM.setSelectedTab(it) },
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Must come from THIS Box's own maxHeight (already reduced by the bottom bar's
            // Modifier.imePadding() while the keyboard is up), not a keyboard-independent value
            // like LocalConfiguration.screenHeightDp - a height computed against the full screen
            // is taller than the actual space left once the keyboard is showing, and since the
            // panel is bottom-aligned, the overflow pushes its top edge up past the window's own
            // top (confirmed on a real device: the filter row rendered up near the status bar
            // instead of just above the search field). Deriving it from maxHeight guarantees the
            // panel can never be taller than the space actually available, keyboard or not.
            val searchPanelHeight = maxHeight * 0.6f
            MainTabContent(
                state = uiState,
                tabSettings = tabSettings,
                settingsMode = settingsMode,
                viewSettingsVM = viewSettingsVM,
                albumsViewModel = albumsViewModel,
                mainVM = mainVM,
                ctx = ctx,
                scope = scope,
                navController = navController,
                onMediaSelectionChanged = { isMediaSelectionActive = it },
                onExplorerCanGoUpChanged = { explorerCanGoUp = it },
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )
            if (searchActive) {
                Box(Modifier.align(Alignment.BottomCenter).height(searchPanelHeight)) {
                OmniSearchPanel(
                    modifier = Modifier.fillMaxSize(),
                    query = omniQuery,
                    onQueryChange = { omniQuery = it },
                    onDismiss = closeSearch,
                    storagePath = android.os.Environment.getExternalStorageDirectory().absolutePath,
                    onNavigate = { path -> mainVM.setExplorerPath(path); closeSearch(); mainVM.setSelectedTab(2) },
                    ratingFilter = uiState.activeRatingFilter,
                    onRatingFilterChange = { mainVM.setRatingFilter(it) },
                    selectedTagNames = uiState.activeTagFilterRaw,
                    onToggleTag = { tag -> mainVM.toggleTagFilter(tag) },
                    typeFilter = uiState.activeTypeFilter,
                    onTypeFilterChange = { mainVM.setTypeFilter(it) },
                    dateFilter = uiState.activeDateRangeFilter,
                    onDateFilterChange = { mainVM.setDateRangeFilter(it) },
                    onResetFilters = {
                        mainVM.setRatingFilter(0)
                        mainVM.setTypeFilter(0)
                        mainVM.setDateRangeFilter(0)
                        mainVM.setTagFilter(null, null)
                    },
                    onApplyTagOnly = { tag ->
                        mainVM.setRatingFilter(0)
                        mainVM.setTypeFilter(0)
                        mainVM.setDateRangeFilter(0)
                        mainVM.setPathFilter(null)
                        mainVM.setCollectionName(null)
                        mainVM.setTagFilter(expandTagsWithDescendants(setOf(tag), ctx.config.tagHierarchy), tag)
                        mainVM.setSelectedTab(0)
                    },
                    onApplyResults = { paths, textQuery ->
                        mainVM.setPathFilter(paths, textQuery)
                        mainVM.setCollectionName(null)
                        mainVM.setSelectedTab(0)
                    },
                    onOpenCollection = { coll -> applyCollection(coll, mainVM, ctx); closeSearch() },
                    onOpenFavorite = { path -> ViewerArgs.paths = listOf(path); closeSearch(); navController.navigate(Viewer(0)) },
                    onOpenMedia = { path -> ViewerArgs.paths = listOf(path); closeSearch(); navController.navigate(Viewer(0)) },
                )
                }
            }
        }
    }
    }

    MainSheets(
        activeSheet = activeSheet,
        activeSheetSection = activeSheetSection,
        onSheetSectionChange = { activeSheetSection = it },
        selectedTab = uiState.selectedTab,
        tabSettings = tabSettings,
        settingsMode = settingsMode,
        viewSettingsVM = viewSettingsVM,
        navController = navController,
        currentScanFolder = if (uiState.selectedTab == 2) uiState.explorerPath else "",
        onDismissSheet = { activeSheet = null },
        ratingFilter = uiState.activeRatingFilter,
        onRatingFilterChange = { mainVM.setRatingFilter(it) },
        tagFilterNamesRaw = uiState.activeTagFilterRaw,
        onToggleTag = { tag -> mainVM.toggleTagFilter(tag) },
        minSizeFilter = uiState.activeMinSizeFilter,
        onMinSizeFilterChange = { mainVM.setMinSizeFilter(it) },
        dateRangeFilter = uiState.activeDateRangeFilter,
        onDateRangeFilterChange = { mainVM.setDateRangeFilter(it) },
        typeFilter = uiState.activeTypeFilter,
        onTypeFilterChange = { mainVM.setTypeFilter(it) },
    )

    if (showAllFilesPrompt) {
        AllFilesAccessSheet(
            onAllow = {
                showAllFilesPrompt = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        allFilesLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                    } catch (_: Exception) {
                        try { allFilesLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (_: Exception) { }
                    }
                }
            },
            onLater = { showAllFilesPrompt = false },
        )
    }

    if (showRatingBrowser) {
        var ratingFilter by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showRatingBrowser = false },
            title = { Text(stringResource(R.string.filter_by_rating_title)) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.choose_a_rating), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 1..5) {
                            IconButton(onClick = { ratingFilter = i }, modifier = Modifier.size(48.dp)) {
                                Icon(if (i <= ratingFilter) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.cd_rating_star, i), tint = RatingStarColor, modifier = Modifier.size(36.dp))
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
            }) { Text(stringResource(R.string.filter_action)) } },
            dismissButton = { TextButton(onClick = { showRatingBrowser = false }) { Text(stringResource(R.string.cd_close)) } }
        )
    }
}

/**
 * Search field + nav bar + the swipe-up-to-open-view-settings gesture spanning both, extracted out
 * of [MainScreen] (previously ~60 lines inline in its `bottomBar` slot) so that god-composable
 * doesn't keep absorbing every future bottom-chrome change too.
 */
@Composable
private fun BottomChrome(
    isMediaSelectionActive: Boolean,
    searchActive: Boolean,
    omniQuery: String,
    onQueryChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onMenuClick: () -> Unit,
    onSearch: () -> Unit,
    blurEnabled: Boolean,
    onToggleBlur: () -> Unit,
    onSwipeUp: () -> Unit,
    onOpenFilterAndView: () -> Unit,
    hasActiveFilter: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Column(Modifier.imePadding()) {
        if (!isMediaSelectionActive) {
            val showNavBar = !searchActive
            // Swipe-up-to-open-view-settings spans the whole bottom chrome (search field + nav
            // bar), not just a thin grab-handle strip - Jannik found that strip too small a target
            // to reliably hit. A tap still reaches every button normally here:
            // detectVerticalDragGestures only starts consuming once the touch-slop threshold is
            // exceeded, so a plain tap on a button/tab passes through untouched, and Material's own
            // clickable naturally cancels its press if the pointer moves away instead (covered by
            // the drag) - no separate dedicated gesture row needed, unlike the old strip, which
            // saves that whole row's height too.
            val density = LocalDensity.current
            val swipeThresholdPx = with(density) { 32.dp.toPx() }
            Column(
                modifier = if (showNavBar) {
                    Modifier.pointerInput(Unit) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = { if (totalDrag < -swipeThresholdPx) onSwipeUp() },
                            onDragCancel = { },
                        ) { change, dragAmount ->
                            totalDrag += dragAmount
                            change.consume()
                        }
                    }
                } else {
                    Modifier
                },
            ) {
                val searchField = @Composable {
                    BottomSearchField(
                        value = omniQuery,
                        onValueChange = onQueryChange,
                        focusRequester = searchFocusRequester,
                        onFocusChanged = onFocusChanged,
                        onClear = onClear,
                        onMenuClick = onMenuClick,
                        isActive = searchActive,
                        onSearch = onSearch,
                        blurEnabled = blurEnabled,
                        onToggleBlur = onToggleBlur,
                        onOpenFilterAndView = onOpenFilterAndView,
                        hasActiveFilter = hasActiveFilter,
                    )
                }
                // Landscape: search pill and nav bar side by side instead of stacked - the stacked
                // chrome ate ~40% of the (short) landscape height, leaving about one visible grid
                // row (UX finding 2026-07-10).
                val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                if (isLandscape && showNavBar) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { searchField() }
                        Box(Modifier.weight(1f)) {
                            MainBottomBar(selectedTab = selectedTab, onTabSelected = onTabSelected)
                        }
                    }
                } else {
                    searchField()
                    if (showNavBar) {
                        MainBottomBar(
                            selectedTab = selectedTab,
                            onTabSelected = onTabSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    // ShortNavigationBar (Material3 1.4.0, stable) instead of NavigationBar - 64dp content height
    // vs. NavigationBar's fixed 80dp (both are defaultMinSize-enforced by M3's own token values,
    // not something a wrapping Modifier.height() can safely trim - clamping NavigationBar itself
    // to a shorter fixed height was tried before and squeezed out its own reserved system-bar/
    // gesture-nav inset instead of actually saving space). Same windowInsets handling internally,
    // just a shorter official variant, so this real ~16dp saving doesn't reintroduce that bug.
    //
    // The swipe-up-to-open-view-settings gesture used to live in a dedicated handle strip drawn
    // over this bar - moved to the call site (wraps this + the search field together) so the
    // swipe target is the whole bottom chrome instead of a thin strip; this pill is now purely
    // decorative.
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        ShortNavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            bottomNavTabs.forEach { tab ->
                ShortNavigationBarItem(
                    selected = selectedTab == tab.index,
                    onClick = {
                        if (selectedTab == tab.index) org.fossify.gallery.compose.util.ScrollToTopBus.trigger(tab.index)
                        else onTabSelected(tab.index)
                    },
                    icon = { Icon(tab.icon, stringResource(tab.labelRes), modifier = Modifier.size(22.dp)) },
                    label = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall) },
                    colors = ShortNavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        selectedIndicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun MainTabContent(
    state: ExplorerUiState,
    tabSettings: TabViewSettings,
    settingsMode: SettingsMode,
    viewSettingsVM: ViewSettingsViewModel,
    albumsViewModel: AlbumsViewModel,
    mainVM: ExplorerViewModel,
    ctx: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    navController: NavHostController,
    onMediaSelectionChanged: (Boolean) -> Unit = {},
    onExplorerCanGoUpChanged: (Boolean) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
) {
    // Crossfade keyed on "pager" vs the specific non-pager tab, not on state.selectedTab directly -
    // switching among tabs 0-2 must stay inside the same "pager" branch so HorizontalPager's own
    // animateScrollToPage handles that motion, while switching into/out of/between the non-pager
    // tabs (Sammlung/Favoriten/Tags) now fades instead of hard-cutting like it did before.
    val contentMode = if (state.selectedTab <= 2) "pager" else state.selectedTab.toString()
    Crossfade(targetState = contentMode, label = "mainTabContent") { mode ->
    if (mode == "pager") {
        val pagerState = rememberPagerState(initialPage = state.selectedTab.coerceIn(0, 2), pageCount = { 3 })
        LaunchedEffect(pagerState.settledPage) { mainVM.setSelectedTab(pagerState.settledPage) }
        LaunchedEffect(state.selectedTab) {
            if (state.selectedTab in 0..2 && state.selectedTab != pagerState.currentPage && state.selectedTab != pagerState.targetPage) {
                pagerState.animateScrollToPage(state.selectedTab)
            }
        }
        val latestOpenDrawer by rememberUpdatedState(onOpenDrawer)
        val edgeToDrawer = remember(pagerState) {
            object : NestedScrollConnection {
                var accum = 0f
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.x < 0f) accum = 0f
                    return Offset.Zero
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (pagerState.currentPage == 0 && available.x > 0f) {
                        accum += available.x
                        if (accum > 140f) { accum = 0f; latestOpenDrawer() }
                    }
                    return Offset.Zero
                }
            }
        }
        HorizontalPager(state = pagerState, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize().nestedScroll(edgeToDrawer)) { tab ->
            when (tab) {
        0 -> MediaScreen(
            viewSettings = tabSettings.media,
            ratingFilter = state.activeRatingFilter,
            tagFilterNames = state.activeTagFilter,
            pathFilter = state.activePathFilter,
            excludePathFilter = state.activeExcludePathFilter,
            minSizeFilter = state.activeMinSizeFilter,
            dateRangeFilter = state.activeDateRangeFilter,
            typeFilter = state.activeTypeFilter,
            activeTagName = state.activeTagName,
            activePathName = state.activePathName,
            activeCollectionName = state.activeCollectionName,
            activeFolderFilterName = state.activeFolderFilterName,
            refreshTrigger = state.mediaRefreshTrigger,
            onClearFilter = { mainVM.clearFilters() },
            onClearRatingFilter = { mainVM.setRatingFilter(0) },
            onClearTagFilter = { mainVM.setTagFilter(null, null) },
            onClearPathFilter = { mainVM.setPathFilter(null); mainVM.setExcludePathFilter(null); mainVM.setCollectionName(null); mainVM.setFolderFilterName(null) },
            onClearSizeFilter = { mainVM.setMinSizeFilter(0L) },
            onClearDateFilter = { mainVM.setDateRangeFilter(0) },
            onClearTypeFilter = { mainVM.setTypeFilter(0) },
            onNavigateToViewer = { paths, startIndex -> ViewerArgs.paths = paths; navController.navigate(Viewer(startIndex)) },
            scrollToPath = state.lastViewedPath,
            onClearScrollToPath = { mainVM.clearLastViewedPath() },
            onSelectionActiveChanged = onMediaSelectionChanged,
            tabIndex = 0,
        )
        1 -> AlbumsScreen(
            viewModel = albumsViewModel,
            onFolderClick = { dir -> navController.navigate(Folder(dir.path)) },
            viewSettings = tabSettings.albums,
            onSelectionActiveChanged = onMediaSelectionChanged,
            tabIndex = 1,
        )
        2 -> ExplorerScreen(
            internalStoragePath = state.explorerPath,
            folderSettings = tabSettings.explorerAlbums,
            // Resolves to a per-path pin (the "Einstellung global übernehmen" toggle in the
            // Ansicht sheet) if the user saved one for state.explorerPath, else the tab default.
            mediaSettings = viewSettingsVM.getExplorerMediaSettingsForPath(state.explorerPath),
            onPathChange = { mainVM.setExplorerPath(it) },
            onSelectionActiveChanged = onMediaSelectionChanged,
            onCanGoUpChanged = onExplorerCanGoUpChanged,
            onNavigateToViewer = { paths, startIndex -> ViewerArgs.paths = paths; navController.navigate(Viewer(startIndex)) },
            // "In Medien öffnen" on one or more selected folders - pathFilter's directory branch
            // already matches recursively (full_path LIKE 'folder/%' in MediaRepository), so passing
            // the selected folder paths straight through already covers every subfolder for free.
            onOpenInMedia = { paths ->
                val name = if (paths.size == 1) java.io.File(paths.first()).name else ctx.getString(R.string.selected_folders_count, paths.size)
                mainVM.setPreFilterTab(2)
                mainVM.setPathFilter(paths)
                mainVM.setExcludePathFilter(null)
                mainVM.setCollectionName(null)
                mainVM.setFolderFilterName(name)
                mainVM.setSelectedTab(0)
            },
            tabIndex = 2,
        )
            }
        }
    } else {
        when (state.selectedTab) {
        3 -> CollectionsScreen(
            viewSettings = tabSettings.collections,
            onCollectionClick = { coll -> applyCollection(coll, mainVM, ctx) },
        )
        4 -> FavoritesScreen(viewSettings = tabSettings.favorites, onNavigateToViewer = { paths, startIndex -> ViewerArgs.paths = paths; navController.navigate(Viewer(startIndex)) }, onFolderClick = { navController.navigate(Folder(it)) }, tabIndex = 4)
        5 -> TagBrowserScreen(
            onBack = {},
            onTagFilterApplied = { tagNames, tagName ->
                mainVM.setPreFilterTab(5)
                mainVM.setRatingFilter(0)
                mainVM.setTagFilter(tagNames, tagName)
                mainVM.setPathFilter(null)
                mainVM.setExcludePathFilter(null)
                mainVM.setSelectedTab(0)
            },
            viewSettings = tabSettings.tags,
        )
    }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSheets(
    activeSheet: ActiveSheet?,
    activeSheetSection: SheetSection,
    onSheetSectionChange: (SheetSection) -> Unit,
    selectedTab: Int,
    tabSettings: TabViewSettings,
    settingsMode: SettingsMode,
    viewSettingsVM: ViewSettingsViewModel,
    navController: NavHostController,
    currentScanFolder: String = "",
    onDismissSheet: () -> Unit,
    ratingFilter: Int,
    onRatingFilterChange: (Int) -> Unit,
    tagFilterNamesRaw: Set<String>,
    onToggleTag: (String) -> Unit,
    minSizeFilter: Long,
    onMinSizeFilterChange: (Long) -> Unit,
    dateRangeFilter: Int,
    onDateRangeFilterChange: (Int) -> Unit,
    typeFilter: Int,
    onTypeFilterChange: (Int) -> Unit,
) {
    if (activeSheet == ActiveSheet.VIEW_SETTINGS) {
        val isAlbumsTab = selectedTab == 1
        val isExplorerTab = selectedTab == 2
        // Filtering (rating/tag/size/date) only exists for the two tabs that actually render a
        // media grid with these params wired (Media/Favorites) - see MediaScreen's filter params.
        // Folder/Explorer/Collections/Tags tabs have nothing to filter here, so they never show
        // the Filter/Ansicht toggle and always land straight on view settings.
        val supportsFilter = selectedTab == 0 || selectedTab == 4
        // currentScanFolder doubles as "Explorer's currently browsed path" here (only ever
        // non-empty when selectedTab == 2 - see the call site) - the path the "Einstellung global
        // übernehmen" toggle below pins Explorer-media settings to when unchecked.
        val explorerPath = currentScanFolder
        val s = when (selectedTab) {
            0 -> tabSettings.media
            1 -> if (settingsMode == SettingsMode.ALBUMS) tabSettings.albums else tabSettings.folderMedia
            2 -> if (settingsMode == SettingsMode.ALBUMS) tabSettings.explorerAlbums else viewSettingsVM.getExplorerMediaSettingsForPath(explorerPath)
            3 -> tabSettings.collections
            4 -> tabSettings.favorites
            5 -> tabSettings.tags
            else -> ViewSettings()
        }
        ModalBottomSheet(
            onDismissRequest = onDismissSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            if (supportsFilter) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    listOf(SheetSection.FILTER to stringResource(R.string.filter_label), SheetSection.VIEW to stringResource(R.string.view_settings_title)).forEachIndexed { i, (section, label) ->
                        SegmentedButton(
                            selected = activeSheetSection == section,
                            onClick = { onSheetSectionChange(section) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (supportsFilter && activeSheetSection == SheetSection.FILTER) {
                FilterSheetContent(
                    ratingFilter = ratingFilter,
                    onRatingChange = onRatingFilterChange,
                    selectedTagNames = tagFilterNamesRaw,
                    onToggleTag = onToggleTag,
                    minSizeFilter = minSizeFilter,
                    onMinSizeChange = onMinSizeFilterChange,
                    dateRangeFilter = dateRangeFilter,
                    onDateRangeChange = onDateRangeFilterChange,
                    typeFilter = typeFilter,
                    onTypeFilterChange = onTypeFilterChange,
                )
            } else {
                ViewSettingsContent(
                    settings = s,
                    showDisplayMode = ((selectedTab == 1 || selectedTab == 4) && settingsMode == SettingsMode.ALBUMS) || (selectedTab == 2 && settingsMode == SettingsMode.ALBUMS),
                    isAlbumMode = (selectedTab == 1 || selectedTab == 2) && settingsMode == SettingsMode.ALBUMS,
                    // Tab 0 (Media) is the unbounded paged grid - its section headers are built
                    // incrementally as pages stream in (see MediaScreen's PagedRowsAccumulator),
                    // which requires each item to belong to exactly one contiguous group. Tag
                    // grouping lets one medium appear under several tags at once, which that
                    // streaming model can't represent - every other tab builds its grouped rows
                    // from a fully-loaded list instead, so Tag grouping stays available there.
                    supportsTagGrouping = selectedTab != 0,
                    // Collections and Tags are flat container lists: they sort by name or item count
                    // (CollectionsScreen/TagBrowserScreen apply it) but have no date/size/rating,
                    // grouping, mosaic layout or file names - isContainerMode trims the sheet to just
                    // the applicable controls instead of showing ones that do nothing.
                    isContainerMode = selectedTab == 3 || selectedTab == 5,
                    supportsSorting = true,
                    // Only Explorer's media listing is a "currently browsing one specific path"
                    // screen from this shared sheet's perspective (a drilled-into folder has its
                    // own separate sheet in FolderMediaScreen, with the same toggle).
                    showApplyGloballyToggle = selectedTab == 2 && settingsMode == SettingsMode.MEDIA,
                    initialApplyGlobally = !viewSettingsVM.hasCustomExplorerMediaSettings(explorerPath),
                    onSettingsChange = { v, applyGlobally ->
                        when (selectedTab) {
                            0 -> viewSettingsVM.updateMedia(v)
                            1 -> if (settingsMode == SettingsMode.ALBUMS) viewSettingsVM.updateAlbums(v) else viewSettingsVM.updateFolderMedia(v)
                            2 -> if (settingsMode == SettingsMode.ALBUMS) viewSettingsVM.updateExplorerAlbums(v) else viewSettingsVM.updateExplorerMediaForPath(explorerPath, v, applyGlobally)
                            3 -> viewSettingsVM.updateCollections(v)
                            4 -> viewSettingsVM.updateFavorites(v)
                            5 -> viewSettingsVM.updateTags(v)
                        }
                    },
                    onDismiss = onDismissSheet,
                    modeTitle = when {
                        selectedTab == 1 -> if (settingsMode == SettingsMode.ALBUMS) stringResource(R.string.albums) else stringResource(R.string.settings_mode_folder_content)
                        selectedTab == 2 -> if (settingsMode == SettingsMode.ALBUMS) stringResource(R.string.albums) else stringResource(R.string.media)
                        else -> null
                    },
                    modeOptions = when (selectedTab) {
                        1 -> listOf(stringResource(R.string.albums), stringResource(R.string.settings_mode_folder_content))
                        2 -> listOf(stringResource(R.string.albums), stringResource(R.string.media))
                        else -> null
                    },
                    onToggleMode = if (isAlbumsTab || isExplorerTab) {{ viewSettingsVM.setSettingsMode(if (settingsMode == SettingsMode.ALBUMS) SettingsMode.MEDIA else SettingsMode.ALBUMS) }} else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OmniSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    storagePath: String,
    onNavigate: (String) -> Unit,
    ratingFilter: Int,
    onRatingFilterChange: (Int) -> Unit,
    selectedTagNames: Set<String>,
    onToggleTag: (String) -> Unit,
    typeFilter: Int,
    onTypeFilterChange: (Int) -> Unit,
    dateFilter: Int,
    onDateFilterChange: (Int) -> Unit,
    onResetFilters: () -> Unit,
    onApplyTagOnly: (tag: String) -> Unit,
    onApplyResults: (paths: Set<String>?, textQuery: String?) -> Unit,
    onOpenCollection: (MediaCollection) -> Unit,
    onOpenFavorite: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Seed from the repo-level cache instead of an empty map - this panel is a fresh composable every
    // time the search field gets focus, and without this the full media_tags scan reran from scratch
    // on every single tap into search.
    val repo = LocalMediaRepository.current
    var allTags by remember { mutableStateOf<Map<String, Set<String>>>(repo.getTagsWithPathsCached()?.mapValues { it.value.toSet() } ?: emptyMap()) }
    var isSearching by remember { mutableStateOf(false) }
    var textMatchPaths by remember { mutableStateOf<Set<String>?>(null) }
    var folderResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var tagResults by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var collectionResults by remember { mutableStateOf<List<MediaCollection>>(emptyList()) }
    var favoriteResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showFilters by remember { mutableStateOf(false) }
    val searchCache = remember { mutableMapOf<String, Set<String>>() }
    // Bumped on every new search so slower jobs from a stale query can't clobber a newer query's
    // results after the user has already kept typing (jobs below run concurrently, unordered).
    var searchGeneration by remember { mutableIntStateOf(0) }
    var pendingJobs by remember { mutableIntStateOf(0) }
    // searchGeneration above only gates *writes* to state - it never stopped a superseded search's
    // 5 background jobs from actually running to completion, most importantly the "most expensive
    // source" one (a full MediaStore cursor scan + repo.getActivePaths(), the same ~200k-row full-
    // library query the Viewer/OOM issues trace back to). Typing a query in two bursts more than
    // 300ms apart left the first burst's expensive job still running when the second one started,
    // so their full-library scans ran concurrently - reliably reproduced as an OutOfMemoryError on a
    // real ~200k-media library. Cancelling the previous batch before launching a new one closes that.
    val searchJobs = remember { mutableListOf<kotlinx.coroutines.Job>() }

    LaunchedEffect(Unit) {
        if (repo.getTagsWithPathsCached() != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val tags = try { repo.refreshTagsWithPathsCache().mapValues { it.value.toSet() } } catch (_: Exception) { emptyMap() }
            withContext(Dispatchers.Main) { allTags = tags.takeIf { it.isNotEmpty() } ?: emptyMap() }
        }
    }
    // Without this, a tag added/removed elsewhere while this panel stays mounted (search field kept
    // focus) never invalidated its frozen snapshot of allTags.
    LaunchedEffect(Unit) {
        RefreshBus.events.collect {
            withContext(Dispatchers.IO) {
                val tags = try { repo.refreshTagsWithPathsCache().mapValues { it.value.toSet() } } catch (_: Exception) { emptyMap() }
                withContext(Dispatchers.Main) { allTags = tags }
            }
        }
    }

    // Each source below reports its own result as soon as it's ready instead of all sources being
    // collected into one final state update - the cheap in-memory/small-table sources (tags,
    // folders, collections, favorites) used to be held back behind the expensive MediaStore scan
    // just because they were assigned in the same withContext block at the end of one shared
    // coroutine. Running them as independent jobs and writing straight to state lets the UI
    // populate progressively, fastest first, while the MediaStore query keeps running.
    fun performSearch() {
        searchJobs.forEach { it.cancel() }
        searchJobs.clear()
        val myGen = ++searchGeneration
        if (query.length < 2) {
            textMatchPaths = null; folderResults = emptyList(); tagResults = emptyList()
            collectionResults = emptyList(); favoriteResults = emptyList(); isSearching = false
            return
        }
        val qParts = query.lowercase().split(" ").filter { it.isNotBlank() }
        if (qParts.isEmpty()) {
            textMatchPaths = null; folderResults = emptyList(); tagResults = emptyList()
            collectionResults = emptyList(); favoriteResults = emptyList(); isSearching = false
            return
        }
        pendingJobs = 5
        isSearching = true
        // Guarded by myGen so a job from a superseded (stale) search can't decrement the *new*
        // search's counter and turn the spinner off before the current search actually finished.
        fun jobDone() { if (myGen == searchGeneration) { pendingJobs--; if (pendingJobs <= 0) isSearching = false } }

        // In-memory tag cache - no I/O at all, so this can resolve on the current thread.
        searchJobs += scope.launch(Dispatchers.Default) {
            val tags = mutableListOf<Pair<String, Int>>()
            try {
                if (allTags.isNotEmpty()) qParts.forEach { qp -> allTags.entries.forEach { (tag, paths) -> if (tag.lowercase().contains(qp) && tags.none { it.first == tag }) tags.add(tag to paths.size) } }
            } catch (e: Exception) { android.util.Log.e("Explorer", "Tag search job failed", e) }
            if (myGen == searchGeneration) tagResults = tags.sortedByDescending { it.second }.take(15)
            jobDone()
        }
        // Small Room table (folder count, not file count) - cheap full scan.
        searchJobs += scope.launch(Dispatchers.IO) {
            val folders = try { ctx.directoryDB.getAll().mapNotNull { d -> val ln = d.name.lowercase(); if (qParts.all { it in ln }) d.name to d.path else null } } catch (_: Exception) { emptyList() }
            if (myGen == searchGeneration) folderResults = folders.sortedBy { it.first }.take(15)
            jobDone()
        }
        // Handful of user-defined collections at most.
        searchJobs += scope.launch(Dispatchers.IO) {
            val colls = try { repo.getCollections().filter { c -> val ln = c.name.lowercase(); qParts.all { it in ln } } } catch (_: Exception) { emptyList() }
            if (myGen == searchGeneration) collectionResults = colls.take(10)
            jobDone()
        }
        // Favorites table is a small subset of all media - cheap relative to the full MediaStore scan.
        searchJobs += scope.launch(Dispatchers.IO) {
            val favs = try { repo.getFavorites().mapNotNull { m -> val ln = m.name.lowercase(); if (qParts.all { it in ln }) m.name to m.path else null } } catch (_: Exception) { emptyList() }
            if (myGen == searchGeneration) favoriteResults = favs.take(10)
            jobDone()
        }
        // Most expensive source: full MediaStore cursor scan + per-match filesystem stat.
        searchJobs += scope.launch(Dispatchers.IO) {
            // The raw MediaStore query below sees every file on the volume, including ones the
            // app's own library considers inactive (excluded/hidden folders, not-yet-synced,
            // recycle-binned) - intersecting against the same active-path set MediaScreen's
            // PagingSource ultimately filters against guarantees the count shown here always
            // matches what "Show N results" actually applies, instead of silently dropping some
            // matches only after the filter is applied.
            val activePaths = try { repo.getActivePaths().toSet() } catch (_: Exception) { null }
            fun List<String>.toActiveSet() = (if (activePaths != null) filter { it in activePaths } else this).toSet()
            val cacheKey = "${query}_${typeFilter}_${dateFilter}"
            searchCache[cacheKey]?.let { c ->
                val filtered = c.filter { java.io.File(it).exists() }.toActiveSet()
                if (myGen == searchGeneration) textMatchPaths = filtered
                jobDone(); return@launch
            }
            if (searchCache.size > 30) searchCache.clear()
            val matched = mutableSetOf<String>()
            try {
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val proj = arrayOf(android.provider.MediaStore.MediaColumns.DATA, android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                val selParts = mutableListOf<String>(); val argsList = mutableListOf<String>()
                when (typeFilter) { 1 -> { selParts.add("${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"); argsList.add(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()) } 2 -> { selParts.add("${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"); argsList.add(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()) } else -> { selParts.add("(${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)"); argsList.addAll(arrayOf(android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())) } }
                when (dateFilter) { 1 -> { val t = (System.currentTimeMillis() - 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) } 2 -> { val t = (System.currentTimeMillis() - 7 * 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) } 3 -> { val t = (System.currentTimeMillis() - 30 * 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) } 4 -> { val t = (System.currentTimeMillis() - 365 * 86400000L) / 1000; selParts.add("${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} >= ?"); argsList.add(t.toString()) } }
                // Push the text match into SQL instead of fetching every image/video row on the
                // device (previously up to ~200k rows per keystroke pause on a large library,
                // filtered client-side afterward) - MediaProvider now only has to hand back rows
                // whose DISPLAY_NAME actually contains every typed term, a tiny fraction of the
                // library for a typical query. The client-side qParts.all{...} check below stays as
                // a cheap final safety net (SQLite's LIKE case-folding isn't guaranteed identical to
                // Kotlin's lowercase() for all locales/scripts), just over a far smaller result set now.
                qParts.forEach { part ->
                    val escaped = part.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                    selParts.add("${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'")
                    argsList.add("%$escaped%")
                }
                ctx.contentResolver.query(uri, proj, selParts.joinToString(" AND "), argsList.toTypedArray(), null)?.use { c ->
                    val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA); val nameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    while (c.moveToNext()) { val path = c.getString(dataCol) ?: continue; val name = c.getString(nameCol) ?: ""; if (qParts.all { it in name.lowercase() } && java.io.File(path).exists()) matched.add(path) }
                }
            } catch (e: Exception) { android.util.Log.e("Explorer", "MediaStore search job failed", e) }
            // Cache the pre-intersection match set (independent of which library items happen to be
            // "active" right now) so a later cache hit re-derives against a possibly-updated active set.
            searchCache[cacheKey] = matched
            if (myGen == searchGeneration) textMatchPaths = matched.toList().toActiveSet().takeIf { it.isNotEmpty() }
            jobDone()
        }
    }

    LaunchedEffect(query) { kotlinx.coroutines.delay(300); performSearch() }
    LaunchedEffect(typeFilter, dateFilter) { if (query.length >= 2) performSearch() }

    val hasAnyFilter = ratingFilter > 0 || selectedTagNames.isNotEmpty() || typeFilter > 0 || dateFilter > 0
    val hasResults = textMatchPaths != null && textMatchPaths!!.isNotEmpty()
    val mc = textMatchPaths?.size ?: 0

    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg, bottomEnd = 0.dp, bottomStart = 0.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Filter toggle bar
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = { showFilters = !showFilters }, shape = RoundedCornerShape(Radius.md), color = if (showFilters || hasAnyFilter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FilterList, null, modifier = Modifier.size(14.dp), tint = if (showFilters || hasAnyFilter) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (hasAnyFilter) {
                            val parts = mutableListOf<String>()
                            if (ratingFilter > 0) parts.add("★$ratingFilter")
                            if (selectedTagNames.isNotEmpty()) parts.add(selectedTagNames.joinToString(","))
                            Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
                        } else Text(stringResource(R.string.filter_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (hasAnyFilter) {
                    Spacer(Modifier.width(4.dp))
                    Surface(onClick = onResetFilters, shape = RoundedCornerShape(Radius.md), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(stringResource(R.string.reset), Modifier.padding(horizontal = 8.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Expandable filter panel
            if (showFilters) {
                Spacer(Modifier.height(6.dp))
                // Rating
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("★", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (i in 1..5) IconButton(onClick = { onRatingFilterChange(if (ratingFilter == i) 0 else i) }, modifier = Modifier.size(40.dp)) { Icon(if (i <= ratingFilter) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.cd_rating_star, i), tint = if (i <= ratingFilter) RatingStarColor else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(4.dp))
                    listOf(stringResource(R.string.everything) to 0, stringResource(R.string.images) to 1, stringResource(R.string.videos) to 2).forEach { (l, v) -> Surface(onClick = { onTypeFilterChange(v) }, shape = RoundedCornerShape(Radius.md), color = if (typeFilter == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) { Text(l, Modifier.padding(horizontal = 7.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = if (typeFilter == v) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) } }
                    Spacer(Modifier.width(4.dp))
                    listOf(stringResource(R.string.all_dates) to 0, stringResource(R.string.today) to 1, "7d" to 2, "30d" to 3).forEach { (l, v) -> Surface(onClick = { onDateFilterChange(v) }, shape = RoundedCornerShape(Radius.md), color = if (dateFilter == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) { Text(l, Modifier.padding(horizontal = 7.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = if (dateFilter == v) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) } }
                }
                // Tags
                if (allTags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        allTags.entries.sortedByDescending { it.value.size }.take(15).forEach { (tag, _) ->
                            val sel = tag in selectedTagNames
                            Surface(onClick = { onToggleTag(tag) }, shape = RoundedCornerShape(Radius.md), color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                                Text(tag, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Results
            val hasAnyResults = hasResults || folderResults.isNotEmpty() || tagResults.isNotEmpty() || collectionResults.isNotEmpty() || favoriteResults.isNotEmpty()
            if (isSearching && !hasAnyResults) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            } else if (query.length >= 2 && !isSearching && !hasAnyResults) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (hasAnyResults || isSearching) {
                Spacer(Modifier.height(4.dp)); HorizontalDivider(); Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    if (folderResults.isNotEmpty()) {
                        item { Text(stringResource(R.string.folders), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(folderResults.take(5), key = { it.second }) { (name, path) ->
                            // Parent dir (relative to external storage root) below the name - folders
                            // sharing a basename in different places were otherwise indistinguishable.
                            val parentLabel = remember(path) {
                                val parent = java.io.File(path).parent ?: ""
                                parent.removePrefix(storagePath).trim('/').ifEmpty { "/" }
                            }
                            Surface(modifier = Modifier.fillMaxWidth().clickable { onNavigate(path) }, color = Color.Transparent, shape = RoundedCornerShape(Radius.sm)) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        // Folder name and parent path can themselves be the sensitive part (a
                                        // person's name, a category) - blurring only thumbnails/filenames
                                        // elsewhere but leaving this suggestion's path in plain text would leak
                                        // exactly what Privacy Blur exists to hide.
                                        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                        Text(parentLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                    }
                                    Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (folderResults.size > 5) item { Text(stringResource(R.string.plus_n_more, folderResults.size - 5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)) }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (tagResults.isNotEmpty()) {
                        item { Text(stringResource(R.string.nav_tags), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(tagResults.take(8), key = { it.first }) { (tag, cnt) ->
                            Surface(modifier = Modifier.fillMaxWidth().clickable {
                                // Resets rating/type/date/path and applies just this tag (descendants
                                // included - resolved to files in SQL by MediaViewModel, not here).
                                onApplyTagOnly(tag); onDismiss()
                            }, color = Color.Transparent, shape = RoundedCornerShape(Radius.sm)) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.Label, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(tag, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).privacyBlur(BlurRadius.thumbnail, BlurState.enabled)); Text("$cnt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (collectionResults.isNotEmpty()) {
                        item { Text(stringResource(R.string.nav_collections), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(collectionResults, key = { it.id }) { coll ->
                            Surface(modifier = Modifier.fillMaxWidth().clickable { onOpenCollection(coll); onDismiss() }, color = Color.Transparent, shape = RoundedCornerShape(Radius.sm)) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CollectionsBookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(coll.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                    Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (favoriteResults.isNotEmpty()) {
                        item { Text(stringResource(R.string.nav_favorites), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(favoriteResults.take(8), key = { it.second }) { (name, path) ->
                            Surface(modifier = Modifier.fillMaxWidth().clickable { onOpenFavorite(path); onDismiss() }, color = Color.Transparent, shape = RoundedCornerShape(Radius.sm)) {
                                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).privacyBlur(BlurRadius.thumbnail, BlurState.enabled))
                                }
                            }
                        }
                        if (favoriteResults.size > 8) item { Text(stringResource(R.string.plus_n_more, favoriteResults.size - 8), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)) }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    if (hasResults) {
                        item { Text(stringResource(R.string.media_count_paren, mc), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 4.dp)) }
                        // Without this, a query that only matches media (the common case - most
                        // searches are for a filename, not a folder/tag/collection/favorite name)
                        // rendered nothing but a header and a button, leaving most of the panel's
                        // height blank. A tap opens the file directly in the Viewer, skipping the
                        // "apply as grid filter" step entirely for the single-result case.
                        item {
                            LazyRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(textMatchPaths!!.take(20).toList(), key = { it }) { path ->
                                    Surface(
                                        onClick = { onOpenMedia(path); onDismiss() },
                                        shape = RoundedCornerShape(Radius.sm),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        GalleryImage(
                                            path = path,
                                            contentDescription = java.io.File(path).name,
                                            modifier = Modifier.size(64.dp),
                                            thumbnailSize = 128,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (isSearching) {
                        // The MediaStore file scan is usually the slowest source - if the fast
                        // sources above already rendered, show a small inline spinner instead of
                        // blanking the whole panel while files are still being matched.
                        item {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.searching), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Gated on hasResults (media matches only), not hasAnyResults - the button only
                // ever applies textMatchPaths (see onApplyResults below), so showing it while only
                // folders/tags/collections/favorites matched rendered a confusing "0 Ergebnisse
                // anzeigen" button that would apply an empty media filter if tapped.
                if (hasResults) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                        Surface(onClick = {
                            onApplyResults(textMatchPaths, query.trim().takeIf { it.isNotEmpty() })
                            onDismiss()
                        }, shape = RoundedCornerShape(Radius.xl), color = MaterialTheme.colorScheme.primary) {
                            Text(stringResource(R.string.show_results_count, mc), Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            } else if (!hasAnyFilter) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_min_chars_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun hasAllFilesAccess(ctx: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
