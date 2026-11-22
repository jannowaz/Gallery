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
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventState
import org.fossify.commons.extensions.handleAppPasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import org.fossify.gallery.compose.components.ActiveFilterChip
import org.fossify.gallery.compose.components.TagFilterChip
import org.fossify.gallery.compose.screens.ViewSettingsContent
import org.fossify.gallery.compose.screens.ViewSettingsPanel
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
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.compose.util.ExplorerNavigationBus
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
import org.fossify.gallery.navigation.Settings
import org.fossify.gallery.navigation.CleanupHub
import org.fossify.gallery.navigation.FoldersMover
import org.fossify.gallery.navigation.RecycleBin
import org.fossify.gallery.models.MediaCollection
import org.fossify.gallery.R
import org.fossify.gallery.navigation.Folder
import org.fossify.gallery.navigation.FolderSwipe
import org.fossify.gallery.navigation.PersonReview
import org.fossify.gallery.navigation.TagBrowser
import org.fossify.gallery.navigation.Viewer
import org.fossify.gallery.navigation.navigateToViewer
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.theme.LocalSpacing
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.resolveDarkTheme
import org.fossify.gallery.extensions.batchJobItemDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.expandTagsWithDescendants
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.LibrarySyncPhase
import org.fossify.gallery.helpers.LibrarySyncStatus
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.XmpWriter
import org.fossify.gallery.helpers.resolveContentUriToPath
import org.fossify.gallery.viewmodels.AlbumsViewModel
import org.fossify.gallery.viewmodels.ExplorerViewModel
import org.fossify.gallery.viewmodels.ExplorerUiState
import org.fossify.gallery.viewmodels.hasActiveFilter
import org.fossify.gallery.viewmodels.filterReturnTab
import org.fossify.gallery.viewmodels.LibraryFilterActions
import org.fossify.gallery.viewmodels.toLibraryFilterState
import org.fossify.gallery.models.MediaReviewFilter
import org.fossify.gallery.workers.RecycleBinCleanupWorker
import org.fossify.gallery.workers.MediaSyncWorker
import org.fossify.gallery.workers.MetadataSyncWorker
import java.io.File

class ComposeExplorerActivity : FragmentActivity() {

    private var mediaPermissionState by mutableStateOf(MediaPermissionState.NONE)
    private var permissionRequestAttempted = false
    private var fullyDrawnReported = false

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
        permissionRequestAttempted = true
        mediaPermissionState = readMediaPermissionState()
    }

    // --- App password lock ---------------------------------------------------------------------
    // The "App password protection" setting was fully configurable but never enforced anywhere in the
    // Compose UI: SplashActivity launched straight into this host, so anyone who opened the app saw
    // everything regardless of the toggle - a security setting that quietly did nothing (worse than
    // none, since the user believes they're protected). These three members gate the whole content
    // behind the commons SecurityDialog on launch and again after every real backgrounding.
    // Compose-observable so the opaque cover below can hide the gallery (and its recents thumbnail)
    // while the PIN/pattern/fingerprint prompt is up or before it's been answered.
    private val showLockCover = mutableStateOf(false)
    private var isAppUnlocked = false
    private var lockPromptInFlight = false

    private fun updateSecureWindowFlag() {
        if (config.isAppPasswordProtectionOn || config.blurAllMedia) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun maybeLockApp() {
        updateSecureWindowFlag()
        if (!config.isAppPasswordProtectionOn) { isAppUnlocked = true; showLockCover.value = false; return }
        if (isAppUnlocked || lockPromptInFlight) return
        showLockCover.value = true
        lockPromptInFlight = true
        // Commons handles the SecurityDialog (PIN/pattern) + biometric auto-prompt and the toggle
        // check itself; it calls back true on success / when protection is off, false on cancel.
        handleAppPasswordProtection { success ->
            lockPromptInFlight = false
            if (success) {
                isAppUnlocked = true
                showLockCover.value = false
            } else {
                // Refused/cancelled - don't reveal the gallery. Leave to the launcher.
                finish()
            }
        }
    }

    @Volatile private var lastObserverMs = 0L
    private val mediaObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            val now = System.currentTimeMillis()
            if (now - lastObserverMs < 1500) return
            lastObserverMs = now
            // The observer is only a signal that MediaStore may have changed. Do not broadcast a
            // UI refresh before the worker has imported the rows into Room; that used to make every
            // tab reload against the old snapshot, followed by a second reload after the worker's
            // commit. MediaSyncWorker emits the refresh event after the DB is consistent.
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
        intent.getStringExtra(org.fossify.gallery.helpers.DIRECTORY)?.takeIf { it.isNotBlank() }?.let {
            org.fossify.gallery.compose.util.NavigateBus.triggerFolder(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        updateSecureWindowFlag()
        mediaPermissionState = readMediaPermissionState()
        // Cover the very first frame when a launch lock is armed, so the gallery never flashes before
        // onResume() gets a chance to raise the prompt.
        showLockCover.value = config.isAppPasswordProtectionOn
        // Seed the observable thumbnail-overlay flags from persisted config so the media grids read
        // Compose-observable values (see ThumbnailOverlayState) and pick up toggles from the view
        // sheet instantly instead of only after a restart.
        org.fossify.gallery.compose.util.ThumbnailOverlayState.sync(config)
        addOnPictureInPictureModeChangedListener { info ->
            org.fossify.gallery.compose.util.PipState.inPip = info.isInPictureInPictureMode
        }

        // SharedPreferences (config + the legacy default-named prefs) are now warmed from
        // App.onCreate() instead - it runs well before this Activity and gives that warm-up a
        // real head start, unlike doing it here mere microseconds before setContent() below
        // triggers the same reads on the main thread (ViewSettingsViewModel.loadFromConfig(),
        // ExplorerViewModel's init).

        setContent {
            val isLocked = showLockCover.value
            @OptIn(ExperimentalComposeUiApi::class)
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                if (!isLocked) {
                    when (mediaPermissionState) {
                        MediaPermissionState.FULL, MediaPermissionState.SELECTED -> GalleryNavHost(onContentReady = ::reportContentReady)
                        MediaPermissionState.NONE, MediaPermissionState.SETTINGS -> GalleryTheme(
                            darkTheme = resolveDarkTheme(config.forceDarkMode, config.forceLightMode),
                            dynamicColor = config.useDynamicColors,
                            amoledBlack = config.useAmoledBackground,
                        ) {
                            MediaPermissionGate(
                                state = mediaPermissionState,
                                onRequest = {
                                    permissionRequestAttempted = true
                                    requestPermissionLauncher.launch(getMediaPermissionStrings())
                                },
                                onOpenSettings = {
                                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:$packageName")
                                    })
                                },
                            )
                        }
                    }
                }
                if (isLocked || !mediaPermissionState.hasMediaAccess) {
                    LaunchedEffect(isLocked, mediaPermissionState) {
                        androidx.compose.runtime.withFrameNanos { }
                        reportContentReady()
                    }
                }
                LockCover()
            }
        }
        if (mediaPermissionState.hasMediaAccess) {
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
            lifecycleScope.launch(Dispatchers.IO) { RecycleBinCleanupWorker.schedule(this@ComposeExplorerActivity) }
        }
    }

    private fun readMediaPermissionState(): MediaPermissionState {
        val imagesGranted: Boolean
        val videosGranted: Boolean
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            imagesGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            videosGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            imagesGranted = granted
            videosGranted = granted
        }
        if (imagesGranted || videosGranted) return MediaPermissionState.FULL

        if (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return MediaPermissionState.SELECTED
        }

        val shouldUseSettings = permissionRequestAttempted && !shouldShowMediaPermissionRationale()
        return if (shouldUseSettings) MediaPermissionState.SETTINGS else MediaPermissionState.NONE
    }

    private fun reportContentReady() {
        if (!fullyDrawnReported) {
            fullyDrawnReported = true
            reportFullyDrawn()
        }
    }

    private fun shouldShowMediaPermissionRationale(): Boolean {
        val permissions = getMediaPermissionStrings()
        return permissions.any { shouldShowRequestPermissionRationale(it) }
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

    override fun onResume() {
        super.onResume()
        // Users can change selected/full access in system settings while this Activity remains in
        // the task. Re-read the actual media grants instead of trusting the previous screen state.
        mediaPermissionState = readMediaPermissionState()
        maybeLockApp()
    }

    override fun onStop() {
        super.onStop()
        contentResolver.unregisterContentObserver(mediaObserver)
        // Re-arm the lock for the next foreground - but only on a genuine backgrounding, not a
        // config change (rotation), which also passes through onStop and would otherwise re-prompt
        // for the PIN on every rotate.
        if (!isChangingConfigurations && config.isAppPasswordProtectionOn) {
            isAppUnlocked = false
            showLockCover.value = true
        }
    }

    /** Opaque cover drawn above the whole NavHost while the app is locked - hides the gallery (and
     * its entry in the recents switcher) until the launch/return password prompt is answered. */
    @Composable
    private fun LockCover() {
        if (!showLockCover.value) return
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // Swallow every tap so nothing behind the cover is interactable while locked.
                .clickable(enabled = true, indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        }
    }

    private fun getMediaPermissionStrings(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= 34) perms.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return perms.toTypedArray()
    }
}

@Composable
fun MainScreen(navController: NavHostController, onFinish: () -> Unit, onContentReady: () -> Unit = {}) {
    val ctx = LocalContext.current
    val mainVM: ExplorerViewModel = viewModel()
    val uiState by mainVM.state.collectAsStateWithLifecycle()
    val pendingExplorerFolder by ExplorerNavigationBus.folderPath.collectAsStateWithLifecycle()
    val viewSettingsVM: ViewSettingsViewModel = viewModel()
    val tabSettings by viewSettingsVM.settings.collectAsStateWithLifecycle()
    val settingsMode by viewSettingsVM.settingsMode.collectAsStateWithLifecycle()
    val albumsViewModel: AlbumsViewModel = viewModel()
    val albumsState by albumsViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var omniQuery by rememberSaveable { mutableStateOf("") }
    var tabSearchQuery by rememberSaveable { mutableStateOf("") }
    var explorerNameFilter by rememberSaveable { mutableStateOf("") }
    var explorerNameFilterPath by rememberSaveable { mutableStateOf(uiState.explorerPath) }
    var searchLocalScope by rememberSaveable(uiState.selectedTab) { mutableStateOf(uiState.selectedTab == 2) }
    var searchFocused by remember { mutableStateOf(false) }
    // Opening a folder result temporarily hides search without discarding it. The originating tab
    // is kept so Back can restore the exact query/results context instead of returning to an empty
    // Album list. -1 means there is no suspended search navigation.
    var suspendedSearchOriginTab by rememberSaveable { mutableIntStateOf(-1) }
    val omniSearchListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val searchFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val searchKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(pendingExplorerFolder) {
        pendingExplorerFolder?.let { folderPath ->
            mainVM.setExplorerPath(folderPath)
            mainVM.setSelectedTab(2)
            ExplorerNavigationBus.consume(folderPath)
        }
    }
    val localScopeLabel: String? = when (uiState.selectedTab) {
        1 -> stringResource(R.string.search_scope_current_tab, stringResource(R.string.albums))
        2 -> stringResource(R.string.search_scope_current_folder)
        3 -> stringResource(R.string.search_scope_current_tab, stringResource(R.string.nav_collections))
        4 -> stringResource(R.string.search_scope_current_tab, stringResource(R.string.nav_favorites))
        5 -> stringResource(R.string.search_scope_current_tab, stringResource(R.string.nav_tags))
        else -> null
    }
    val effectiveLocalScope = localScopeLabel != null && searchLocalScope
    val activeSearchQuery = when {
        effectiveLocalScope && uiState.selectedTab == 2 -> explorerNameFilter
        effectiveLocalScope -> tabSearchQuery
        else -> omniQuery
    }
    val searchActive = suspendedSearchOriginTab < 0 && (searchFocused || activeSearchQuery.isNotBlank())
    var focusedSearchTab by remember { mutableIntStateOf(uiState.selectedTab) }
    LaunchedEffect(uiState.selectedTab) {
        if (focusedSearchTab != uiState.selectedTab) {
            // Horizontal pager swipes can change tabs while the keyboard still owns focus. Keep
            // each tab's query, but never reinterpret that focus as a search on the new tab.
            focusedSearchTab = uiState.selectedTab
            tabSearchQuery = ""
            searchFocused = false
            searchFocusManager.clearFocus()
            searchKeyboard?.hide()
        }
    }
    LaunchedEffect(uiState.explorerPath) {
        // A name filter describes exactly one Explorer directory. Carrying it into a child or an
        // externally opened search result makes matching content disappear for no visible reason.
        // Keep it across Viewer round-trips to the same path, but clear it on an actual path change.
        if (explorerNameFilterPath != uiState.explorerPath) {
            explorerNameFilter = ""
            explorerNameFilterPath = uiState.explorerPath
        }
    }
    val switchSearchScope: (Boolean) -> Unit = { toLocal ->
        if (toLocal != searchLocalScope) {
            if (toLocal) {
                if (uiState.selectedTab == 2) {
                    explorerNameFilterPath = uiState.explorerPath
                    explorerNameFilter = omniQuery
                } else {
                    tabSearchQuery = omniQuery
                }
                omniQuery = ""
            } else {
                val carry = if (uiState.selectedTab == 2) explorerNameFilter else tabSearchQuery
                omniQuery = carry
                if (uiState.selectedTab == 2) explorerNameFilter = "" else tabSearchQuery = ""
            }
            searchLocalScope = toLocal
        }
    }
    val closeSearch: () -> Unit = {
        when {
            effectiveLocalScope && uiState.selectedTab == 2 -> explorerNameFilter = ""
            effectiveLocalScope -> tabSearchQuery = ""
            else -> omniQuery = ""
        }
        searchFocused = false
        suspendedSearchOriginTab = -1
        searchFocusManager.clearFocus()
        searchKeyboard?.hide()
    }
    var recentSearches by remember { mutableStateOf(ctx.config.recentSearches) }
    val rememberSearch: (String) -> Unit = { query ->
        ctx.config.rememberSearch(query)
        recentSearches = ctx.config.recentSearches
    }
    var isMediaSelectionActive by remember { mutableStateOf(false) }
    // Lets the Explorer tab's own directory-up BackHandler take priority over this screen's
    // tab-switch fallback below - otherwise both handlers are enabled at once while browsing a
    // subfolder and back can non-deterministically switch tabs instead of navigating up one level.
    var explorerCanGoUp by remember { mutableStateOf(false) }
    var showAllFilesPrompt by remember { mutableStateOf(false) }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showAllFilesPrompt = !hasAllFilesAccess(ctx)
    }
    val allFilesAccessGranted = hasAllFilesAccess(ctx)
    val isWideWindow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    LaunchedEffect(uiState.dbInitialized, uiState.dbInitError, uiState.selectedTab, albumsState.isLoading) {
        // reportFullyDrawn is intentionally tied to a usable start state: the database has either
        // produced the initial content/empty state or exposed a concrete error, and the default
        // Albums tab is no longer stuck behind its first-load skeleton.
        if ((uiState.dbInitialized || uiState.dbInitError != null) &&
            (uiState.selectedTab != 1 || !albumsState.isLoading)
        ) onContentReady()
    }

    // Picker mode (GET_CONTENT/PICK): constrain the all-media tab to the kind the caller asked for,
    // so an "select a photo" pick only shows images (a video pick only videos) there. The hard
    // guarantee that the wrong kind can never be returned lives in PickIntent.handleMediaTap, which
    // also covers taps inside folders where this grid filter doesn't apply.
    LaunchedEffect(Unit) {
        val act = ctx as? android.app.Activity
        if (act != null && org.fossify.gallery.helpers.PickIntent.isPick(act.intent)) {
            when (org.fossify.gallery.helpers.PickIntent.requiredKind(act.intent)) {
                1 -> mainVM.setTypeFilter(1)
                2 -> mainVM.setTypeFilter(2)
            }
        }
    }

    LaunchedEffect(Unit) {
        mainVM.initializeDatabase {
            mainVM.triggerMediaRefresh()
            // Perform the foreground catch-up after database bootstrap has completed. This gives
            // Media, Albums and Explorer one committed Room snapshot instead of letting the first
            // visible tab race a delayed WorkManager sync.
            mainVM.synchronizeLibraryAfterInitialization()
        }
    }

    val selectTabFromChrome: (Int) -> Unit = { tab ->
        val originTab = suspendedSearchOriginTab
        when {
            originTab < 0 -> mainVM.setSelectedTab(tab)
            tab == originTab -> {
                suspendedSearchOriginTab = -1
                if (!mainVM.navigateBackToPreviousTab()) mainVM.setSelectedTab(tab, addToHistory = false)
            }
            else -> {
                closeSearch()
                mainVM.setSelectedTab(tab)
            }
        }
    }

    val mainBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = mainBackState,
        isBackEnabled = uiState.hasActiveFilter || searchActive || suspendedSearchOriginTab >= 0 || uiState.tabHistory.isNotEmpty() || (uiState.selectedTab != 1 && !isMediaSelectionActive && !(uiState.selectedTab == 2 && explorerCanGoUp)),
        onBackCompleted = {
            when {
                searchActive -> closeSearch()
                suspendedSearchOriginTab >= 0 -> {
                    val originTab = suspendedSearchOriginTab
                    suspendedSearchOriginTab = -1
                    if (!mainVM.navigateBackToPreviousTab()) mainVM.setSelectedTab(originTab, addToHistory = false)
                }
                uiState.hasActiveFilter -> {
                    val backTab = uiState.filterReturnTab()
                    mainVM.clearFilters()
                    mainVM.setSelectedTab(backTab, addToHistory = false)
                }
                uiState.tabHistory.isNotEmpty() -> mainVM.navigateBackToPreviousTab()
                else -> onFinish()
            }
        },
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isMediaSelectionActive,
        drawerContent = {
            AppNavigationDrawer(
                selectedTab = uiState.selectedTab,
                duplicateScanFolder = if (uiState.selectedTab == 2) uiState.explorerPath else "",
                allFilesAccessGranted = allFilesAccessGranted,
                onSelectTab = { tab -> scope.launch { drawerState.close(); selectTabFromChrome(tab) } },
                onNavigate = { route -> scope.launch { drawerState.close(); navController.navigate(route) { launchSingleTop = true } } },
                onOpenFolder = { path -> scope.launch { drawerState.close(); mainVM.setExplorerPath(path); mainVM.setSelectedTab(2) } },
                onOpenCollection = { collection -> scope.launch { drawerState.close(); applyCollection(collection, mainVM, ctx) } },
                onOpenTag = { tag -> scope.launch { drawerState.close(); applyTagFilter(tag, mainVM, ctx, 0) } },
                onOpenRating = { rating -> scope.launch { drawerState.close(); mainVM.clearFilters(); mainVM.setPreFilterTab(0); mainVM.setRatingFilter(rating); mainVM.setSelectedTab(0) } },
                onRequestAllFilesAccess = { scope.launch { drawerState.close(); showAllFilesPrompt = true } },
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
    Row(Modifier.fillMaxSize()) {
    AnimatedVisibility(
        visible = isWideWindow && !isMediaSelectionActive,
        enter = expandHorizontally() + fadeIn(),
        exit = shrinkHorizontally() + fadeOut(),
    ) {
        MainNavigationRail(
            selectedTab = uiState.selectedTab,
            onTabSelected = selectTabFromChrome,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onOpenSettings = { navController.navigate(Settings) { launchSingleTop = true } },
        )
    }
    Scaffold(
        bottomBar = {
            BottomChrome(
                isMediaSelectionActive = isMediaSelectionActive,
                searchActive = searchActive,
                omniQuery = if (suspendedSearchOriginTab >= 0) "" else activeSearchQuery,
                onQueryChange = {
                    if (suspendedSearchOriginTab >= 0) suspendedSearchOriginTab = -1
                    when {
                        effectiveLocalScope && uiState.selectedTab == 2 -> {
                            explorerNameFilterPath = uiState.explorerPath
                            explorerNameFilter = it
                        }
                        effectiveLocalScope -> tabSearchQuery = it
                        else -> omniQuery = it
                    }
                },
                searchFocusRequester = searchFocusRequester,
                onFocusChanged = { focused ->
                    if (focused && suspendedSearchOriginTab >= 0) {
                        suspendedSearchOriginTab = -1
                        omniQuery = ""
                    }
                    searchFocused = focused
                },
                onClear = closeSearch,
                onMenuClick = { scope.launch { drawerState.open() } },
                onSearch = {
                    if (!effectiveLocalScope) rememberSearch(omniQuery)
                    searchKeyboard?.hide()
                },
                onSwipeUp = { activeSheet = ActiveSheet.VIEW_SETTINGS },
                onOpenFilterAndView = {
                    activeSheet = if (
                        uiState.selectedTab == 0 || uiState.selectedTab == 4 ||
                        (uiState.selectedTab == 2 && settingsMode == SettingsMode.MEDIA)
                    ) {
                        ActiveSheet.FILTER
                    } else {
                        ActiveSheet.VIEW_SETTINGS
                    }
                },
                filtersAvailable = uiState.selectedTab == 0 || uiState.selectedTab == 4 ||
                    (uiState.selectedTab == 2 && settingsMode == SettingsMode.MEDIA),
                hasActiveFilter = uiState.hasActiveFilter,
                searchHintRes = if (!effectiveLocalScope) {
                    R.string.search_hint_compact
                } else {
                    when (uiState.selectedTab) {
                        1 -> R.string.search_folders_hint
                        2 -> R.string.explorer_name_filter_hint
                        3 -> R.string.search_collections
                        5 -> R.string.search_tag
                        else -> R.string.search_hint_compact
                    }
                },
                selectedTab = uiState.selectedTab,
                onTabSelected = selectTabFromChrome,
                showNavigation = !isWideWindow,
            )
        }
    ) { padding ->
        // Keep the persistent browse chrome stable while scrolling. Removing the bottom slot from
        // Scaffold during a gesture changes the content height underneath the user's finger, while
        // the media toolbar used to disappear on a separate signal. Both effects felt like a
        // competing pair of animations rather than one coherent surface. Scroll now moves media
        // only; chrome changes are reserved for explicit modes such as search and selection.
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Must come from THIS Box's own maxHeight (already reduced by the bottom bar's
            // Modifier.imePadding() while the keyboard is up), not a keyboard-independent value
            // like LocalConfiguration.screenHeightDp - a height computed against the full screen
            // is taller than the actual space left once the keyboard is showing, and since the
            // panel is bottom-aligned, the overflow pushes its top edge up past the window's own
            // top (confirmed on a real device: the filter row rendered up near the status bar
            // instead of just above the search field). Deriving it from maxHeight guarantees the
            // panel can never be taller than the space actually available, keyboard or not.
            val searchPanelHeight = (maxHeight * 0.75f).coerceAtLeast(minOf(maxHeight, 360.dp))
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
                searchResultBackPending = suspendedSearchOriginTab >= 0,
                explorerNameFilter = explorerNameFilter,
                onExplorerNameFilterChange = {
                    explorerNameFilterPath = uiState.explorerPath
                    explorerNameFilter = it
                },
                tabSearchQuery = if (effectiveLocalScope) tabSearchQuery else "",
                onClearTabSearch = closeSearch,
                explorerSearchBackActive = uiState.selectedTab == 2 && searchActive,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenNewMedia = {
                    val since = ctx.config.newMediaSeenAt
                    mainVM.setPreFilterTab(uiState.selectedTab)
                    mainVM.setNewMediaFilter(since)
                    mainVM.setSelectedTab(0)
                    org.fossify.gallery.compose.util.ScrollToTopBus.trigger(0)
                },
                onMarkNewMediaSeen = {
                    mainVM.markNewMediaSeen()
                    ctx.toast(ctx.getString(R.string.new_media_marked_seen))
                },
                onOpenViewSettings = { activeSheet = ActiveSheet.VIEW_SETTINGS },
            )
            uiState.dbInitError?.let { errorMessage ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.error_generic, errorMessage),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            mainVM.initializeDatabase {
                                mainVM.triggerMediaRefresh()
                                mainVM.synchronizeLibraryAfterInitialization()
                            }
                        }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = searchActive && !effectiveLocalScope,
                modifier = Modifier.align(Alignment.BottomCenter).height(searchPanelHeight),
                enter = slideInVertically(
                    initialOffsetY = { it / 8 },
                    animationSpec = tween(180),
                ) + fadeIn(tween(150)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 8 },
                    animationSpec = tween(140),
                ) + fadeOut(tween(120)),
            ) {
                OmniSearchPanel(
                    modifier = Modifier.fillMaxSize(),
                    query = omniQuery,
                    onQueryChange = { omniQuery = it },
                    resultListState = omniSearchListState,
                    recentSearches = recentSearches,
                    onUseRecentSearch = { omniQuery = it },
                    onClearRecentSearches = { ctx.config.clearRecentSearches(); recentSearches = emptyList() },
                    onDismiss = closeSearch,
                    storagePath = android.os.Environment.getExternalStorageDirectory().absolutePath,
                    onNavigate = { path ->
                        rememberSearch(omniQuery)
                        suspendedSearchOriginTab = uiState.selectedTab
                        searchFocused = false
                        searchFocusManager.clearFocus()
                        searchKeyboard?.hide()
                        mainVM.setExplorerPath(path)
                        mainVM.setSelectedTab(2)
                    },
                    ratingFilter = uiState.activeRatingFilter,
                    onRatingFilterChange = { mainVM.setRatingFilter(it) },
                    selectedTagNames = uiState.activeTagFilterRaw,
                    onToggleTag = { tag -> mainVM.toggleTagFilter(tag) },
                    tagMatchAll = uiState.activeTagMatchAll,
                    onTagMatchAllChange = { mainVM.setTagMatchAll(it) },
                    typeFilter = uiState.activeTypeFilter,
                    onTypeFilterChange = { mainVM.setTypeFilter(it) },
                    dateFilter = uiState.activeDateRangeFilter,
                    onDateFilterChange = { mainVM.setDateRangeFilter(it) },
                    minSizeFilter = uiState.activeMinSizeFilter,
                    onMinSizeFilterChange = { mainVM.setMinSizeFilter(it) },
                    onResetFilters = {
                        // Keep this reset path in lockstep with the persistent filter sheet. The
                        // older field-by-field version forgot the review and new-media inboxes,
                        // leaving the search panel looking reset while the grid was still filtered.
                        mainVM.clearFilters()
                    },
                    onEditFilters = { activeSheet = ActiveSheet.FILTER },
                    onApplyTagOnly = { tag ->
                        rememberSearch(omniQuery)
                        applyTagFilter(tag, mainVM, ctx, uiState.selectedTab)
                    },
                    onApplyResults = { textQuery ->
                        rememberSearch(textQuery)
                        mainVM.clearFilters()
                        mainVM.setPreFilterTab(uiState.selectedTab)
                        mainVM.setTextQueryFilter(textQuery)
                        mainVM.setCollectionName(null)
                        mainVM.setSelectedTab(0)
                    },
                    onOpenCollection = { coll -> applyCollection(coll, mainVM, ctx); closeSearch() },
                    onOpenFavorite = { path -> closeSearch(); navController.navigateToViewer(listOf(path), 0) },
                    onOpenMedia = { path -> closeSearch(); navController.navigateToViewer(listOf(path), 0) },
                    localScopeLabel = localScopeLabel,
                    onSwitchToLocalScope = localScopeLabel?.let { { switchSearchScope(true) } },
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = searchActive && effectiveLocalScope,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(120)),
            ) {
                localScopeLabel?.let { label ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 3.dp,
                    ) {
                        SearchScopeBar(
                            isLocal = true,
                            localLabel = label,
                            onSelectEverywhere = { switchSearchScope(false) },
                            onSelectLocal = {},
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            LibrarySyncIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
            )
        }
    }
    }
    }

    MainSheets(
        activeSheet = activeSheet,
        selectedTab = uiState.selectedTab,
        collectionActive = uiState.activeCollectionName != null,
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
            tagMatchAll = uiState.activeTagMatchAll,
            onTagMatchAllChange = { mainVM.setTagMatchAll(it) },
        minSizeFilter = uiState.activeMinSizeFilter,
        onMinSizeFilterChange = { mainVM.setMinSizeFilter(it) },
        dateRangeFilter = uiState.activeDateRangeFilter,
        onDateRangeFilterChange = { mainVM.setDateRangeFilter(it) },
        typeFilter = uiState.activeTypeFilter,
        onTypeFilterChange = { mainVM.setTypeFilter(it) },
        reviewFilter = uiState.activeReviewFilter,
        onReviewFilterChange = { mainVM.setUndecidedReviewFilter(it == MediaReviewFilter.UNDECIDED) },
        hasActiveFilter = uiState.hasActiveFilter,
        onClearAll = { mainVM.clearFilters() },
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
    searchResultBackPending: Boolean = false,
    explorerNameFilter: String = "",
    onExplorerNameFilterChange: (String) -> Unit = {},
    tabSearchQuery: String = "",
    onClearTabSearch: () -> Unit = {},
    explorerSearchBackActive: Boolean = false,
    onOpenDrawer: () -> Unit = {},
    onOpenNewMedia: () -> Unit = {},
    onMarkNewMediaSeen: () -> Unit = {},
    onOpenViewSettings: () -> Unit = {},
) {
    // Keep each tab's rememberSaveable state in a separate registry even though only one heavy
    // screen tree is composed at a time. This preserves list/grid scroll positions without keeping
    // all thumbnail-heavy tabs alive in memory.
    val tabStateHolder = rememberSaveableStateHolder()
    val sharedFilterState = state.toLibraryFilterState()
    val sharedFilterActions = remember(mainVM) {
        LibraryFilterActions(
            clearAll = { mainVM.clearFilters() },
            clearRating = { mainVM.setRatingFilter(0) },
            clearTags = { mainVM.setTagFilter(null, null) },
            removeTag = mainVM::removeTagFromFilter,
            clearTagMatchAll = { mainVM.setTagMatchAll(false) },
            clearPath = {
                mainVM.setPathFilter(null)
                mainVM.setTextQueryFilter("")
                mainVM.setExcludePathFilter(null)
                mainVM.setCollectionName(null)
                mainVM.setCollectionMediaPaths(emptySet())
                mainVM.setFolderFilterName(null)
            },
            clearSize = { mainVM.setMinSizeFilter(0L) },
            clearDate = { mainVM.setDateRangeFilter(0) },
            clearAddedSince = { mainVM.setNewMediaFilter(0L) },
            clearType = { mainVM.setTypeFilter(0) },
            clearReview = { mainVM.setUndecidedReviewFilter(false) },
        )
    }
    // Keep only one heavy tab tree composed at a time; crossfading two full screens doubled work.
    // HorizontalPager still owns the motion between the three main tabs.
    // animateScrollToPage handles that motion, while switching into/out of/between the non-pager
    // Non-pager tabs are switched as a single tree to avoid duplicate thumbnail work.
    val contentMode = if (state.selectedTab <= 2) "pager" else state.selectedTab.toString()
    if (contentMode == "pager") {
        val pagerState = rememberPagerState(initialPage = state.selectedTab.coerceIn(0, 2), pageCount = { 3 })
        // Keep one adjacent page warm on normal devices so a horizontal tab swipe does not have
        // to compose a complete thumbnail-heavy screen at the exact moment the finger crosses the
        // page boundary. Low-RAM devices keep the old bounded-memory behaviour.
        val adjacentPageCount = remember(ctx) {
            val activityManager = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (activityManager?.isLowRamDevice == true) 0 else 1
        }
        // The first settled-page value is the pager's initial state, not a user navigation event.
        // Writing it back unconditionally races a bottom-tab click while the pager is being
        // recreated after returning from Viewer and can snap Media back to Albums. Only settled
        // values after that initial emission are genuine pager swipes.
        LaunchedEffect(pagerState) {
            var initialValue = true
            snapshotFlow { pagerState.settledPage }.collect { page ->
                if (initialValue) initialValue = false
                else mainVM.setSelectedTab(page)
            }
        }
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
        HorizontalPager(state = pagerState, beyondViewportPageCount = adjacentPageCount, modifier = Modifier.fillMaxSize().nestedScroll(edgeToDrawer)) { tab ->
            val tabDispatcherOwner = rememberNavigationEventDispatcherOwner(enabled = pagerState.currentPage == tab)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides tabDispatcherOwner) {
            tabStateHolder.SaveableStateProvider("main-tab-$tab") {
            when (tab) {
        0 -> MediaScreen(
            viewSettings = if (state.activeCollectionName != null) tabSettings.collectionMedia else tabSettings.media,
            ratingFilter = state.activeRatingFilter,
            tagFilterNames = state.activeTagFilter,
            tagMatchAll = state.activeTagMatchAll,
            pathFilter = state.activePathFilter,
            excludePathFilter = state.activeExcludePathFilter,
            minSizeFilter = state.activeMinSizeFilter,
            dateRangeFilter = state.activeDateRangeFilter,
            addedSince = state.activeAddedSince,
            typeFilter = state.activeTypeFilter,
            reviewFilter = state.activeReviewFilter,
            textQuery = state.activeTextQuery,
            newMediaCount = state.newMediaCount,
            activeTagName = state.activeTagName,
            activePathName = state.activePathName,
            activeCollectionName = state.activeCollectionName,
            additionalMediaPaths = state.activeCollectionMediaPaths,
            activeFolderFilterName = state.activeFolderFilterName,
            onClearFilter = { mainVM.clearFilters() },
            onClearRatingFilter = { mainVM.setRatingFilter(0) },
            onClearTagFilter = { mainVM.setTagFilter(null, null) },
            onRemoveTagFilter = mainVM::removeTagFromFilter,
            onClearTagMatchAll = { mainVM.setTagMatchAll(false) },
            onTagClick = { tag -> applyTagFilter(tag, mainVM, ctx, 0) },
            onClearPathFilter = { mainVM.setPathFilter(null); mainVM.setTextQueryFilter(""); mainVM.setExcludePathFilter(null); mainVM.setCollectionName(null); mainVM.setCollectionMediaPaths(emptySet()); mainVM.setFolderFilterName(null) },
            onClearSizeFilter = { mainVM.setMinSizeFilter(0L) },
            onClearDateFilter = { mainVM.setDateRangeFilter(0) },
            onClearAddedSinceFilter = { mainVM.setNewMediaFilter(0L) },
            onClearTypeFilter = { mainVM.setTypeFilter(0) },
            onClearReviewFilter = { mainVM.setUndecidedReviewFilter(false) },
            showToolbar = true,
            onOpenViewSettings = onOpenViewSettings,
            onOpenNewMedia = onOpenNewMedia,
            onMarkNewMediaSeen = onMarkNewMediaSeen,
            onNavigateToViewer = { paths, startIndex -> org.fossify.gallery.helpers.PickIntent.handleMediaTap(ctx as? android.app.Activity, paths, startIndex) { navController.navigateToViewer(paths, startIndex) } },
            onNavigateToViewerFast = { path, resolveWindow, loadMore ->
                val activity = ctx as? android.app.Activity
                if (activity != null && org.fossify.gallery.helpers.PickIntent.isPick(activity.intent)) {
                    org.fossify.gallery.helpers.PickIntent.handleMediaTap(activity, listOf(path), 0) {}
                } else {
                    navController.navigateToViewer(path, resolveWindow, loadMore)
                }
            },
            scrollToPath = state.lastViewedPath,
            onClearScrollToPath = { mainVM.clearLastViewedPath() },
            onSelectionActiveChanged = onMediaSelectionChanged,
            tabIndex = 0,
            pickMultiple = (ctx as? android.app.Activity)?.let { org.fossify.gallery.helpers.PickIntent.isPick(it.intent) && org.fossify.gallery.helpers.PickIntent.allowsMultiple(it.intent) } ?: false,
            onConfirmPick = { paths -> (ctx as? android.app.Activity)?.let { org.fossify.gallery.helpers.PickIntent.returnMultiple(it, paths) } },
        )
        1 -> AlbumsScreen(
            viewModel = albumsViewModel,
            onFolderClick = { dir -> openFolderChecked(ctx, dir.path) { navController.navigate(Folder(dir.path)) { launchSingleTop = true } } },
            viewSettings = tabSettings.albums,
            externalSearchQuery = tabSearchQuery,
            onClearExternalSearch = onClearTabSearch,
            onOpenViewSettings = onOpenViewSettings,
            onSelectionActiveChanged = onMediaSelectionChanged,
            tabIndex = 1,
        )
        2 -> ExplorerScreen(
            internalStoragePath = state.explorerPath,
            nameFilter = explorerNameFilter,
            onClearNameFilter = { onExplorerNameFilterChange("") },
            folderSettings = tabSettings.explorerAlbums,
            // Resolves to a per-path pin (the "Einstellung global übernehmen" toggle in the
            // Ansicht sheet) if the user saved one for state.explorerPath, else the tab default.
            mediaSettings = viewSettingsVM.getExplorerMediaSettingsForPath(state.explorerPath),
            filterState = sharedFilterState,
            filterActions = sharedFilterActions,
            onPathChange = { mainVM.setExplorerPath(it) },
            onSelectionActiveChanged = onMediaSelectionChanged,
            onCanGoUpChanged = onExplorerCanGoUpChanged,
            externalBackHandlerActive = searchResultBackPending || explorerSearchBackActive,
            onNavigateToViewer = { paths, startIndex -> org.fossify.gallery.helpers.PickIntent.handleMediaTap(ctx as? android.app.Activity, paths, startIndex) { navController.navigateToViewer(paths, startIndex) } },
            initialScrollIndex = state.gridScrollIndex,
            initialScrollOffset = state.gridScrollOffset,
            onScrollPositionChanged = { index, offset -> mainVM.saveScrollPosition(index, offset) },
            // "In Medien öffnen" on one or more selected folders - pathFilter's directory branch
            // already matches recursively (full_path LIKE 'folder/%' in MediaRepository), so passing
            // the selected folder paths straight through already covers every subfolder for free.
            onOpenInMedia = { paths ->
                val name = if (paths.size == 1) java.io.File(paths.first()).name else ctx.getString(R.string.selected_folders_count, paths.size)
                mainVM.clearFilters()
                mainVM.setPreFilterTab(2)
                mainVM.setPathFilter(paths)
                mainVM.setExcludePathFilter(null)
                mainVM.setCollectionName(null)
                mainVM.setFolderFilterName(name)
                mainVM.setSelectedTab(0)
            },
            onRescan = { RefreshBus.trigger() },
            onScanMetadata = {
                MetadataSyncWorker.scheduleFullScan(ctx)
                ctx.toast(ctx.getString(R.string.scan_started_notification), Toast.LENGTH_SHORT)
            },
            onOpenSwipeMode = { path, startPath ->
                navController.navigate(FolderSwipe(path, startPath.orEmpty())) { launchSingleTop = true }
            },
            onOpenPersonReview = { path ->
                navController.navigate(PersonReview(path)) { launchSingleTop = true }
            },
            onOpenViewSettings = onOpenViewSettings,
            onTagClick = { tag -> applyTagFilter(tag, mainVM, ctx, 2) },
            tabIndex = 2,
        )
            }
            }
            }
        }
    } else {
        tabStateHolder.SaveableStateProvider("main-tab-${state.selectedTab}") {
        when (state.selectedTab) {
        3 -> CollectionsScreen(
            viewSettings = tabSettings.collections,
            onCollectionClick = { coll -> applyCollection(coll, mainVM, ctx) },
            externalSearchQuery = tabSearchQuery,
            onClearExternalSearch = onClearTabSearch,
            onOpenViewSettings = onOpenViewSettings,
            tabIndex = 3,
        )
        4 -> FavoritesScreen(
            viewSettings = tabSettings.favorites,
            externalSearchQuery = tabSearchQuery,
            onClearExternalSearch = onClearTabSearch,
            onOpenViewSettings = onOpenViewSettings,
            filterState = sharedFilterState,
            filterActions = sharedFilterActions,
            onNavigateToViewer = { paths, startIndex -> org.fossify.gallery.helpers.PickIntent.handleMediaTap(ctx as? android.app.Activity, paths, startIndex) { navController.navigateToViewer(paths, startIndex) } },
            onFolderClick = { path -> openFolderChecked(ctx, path) { navController.navigate(Folder(path)) { launchSingleTop = true } } },
            onSelectionActiveChanged = onMediaSelectionChanged,
            tabIndex = 4,
        )
        5 -> TagBrowserScreen(
            onBack = {},
            onTagFilterApplied = { tagNames, tagName, matchAll ->
                mainVM.clearFilters()
                mainVM.setPreFilterTab(5)
                mainVM.setRatingFilter(0)
                mainVM.setTagFilter(tagNames, tagName)
                mainVM.setTagMatchAll(matchAll)
                mainVM.setPathFilter(null)
                mainVM.setExcludePathFilter(null)
                mainVM.setSelectedTab(0)
            },
            viewSettings = tabSettings.tags,
            embeddedInMainTabs = true,
            externalSearchQuery = tabSearchQuery,
            onClearExternalSearch = onClearTabSearch,
            onOpenViewSettings = onOpenViewSettings,
        )
    }
    }
    }
}
