@file:OptIn(ExperimentalSharedTransitionApi::class)
package org.fossify.gallery.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.activities.MainScreen
import org.fossify.gallery.compose.screens.folderscreen.FolderMediaScreen
import org.fossify.gallery.compose.screens.folderswipe.FolderSwipeScreen
import org.fossify.gallery.compose.screens.settings.SettingsScreen
import org.fossify.gallery.compose.screens.collections.ManageCollectionsScreen
import org.fossify.gallery.compose.screens.about.AboutScreen
import org.fossify.gallery.compose.screens.tagbrowser.TagBrowserScreen
import org.fossify.gallery.compose.screens.analysis.StorageAnalysisScreen
import org.fossify.gallery.compose.screens.analysis.DuplicateFinderScreen
import org.fossify.gallery.compose.screens.analysis.CleanupHubScreen
import org.fossify.gallery.compose.screens.analysis.CompressionReviewScreen
import org.fossify.gallery.compose.screens.analysis.PersonReviewScreen
import org.fossify.gallery.compose.screens.RecycleBinScreen
import org.fossify.gallery.compose.screens.FoldersMoverScreen
import org.fossify.gallery.compose.screens.viewer.ViewerScreen
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.resolveDarkTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.models.MediaTag
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.compose.util.PrivacyPauseScrim
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment

val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope> {
    error("No AnimatedVisibilityScope provided")
}

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope provided")
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    onContentReady: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext }
    val repo = remember(appCtx) { MediaRepository(appCtx) }
    val conf = appCtx.config

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (!destination.route.orEmpty().startsWith(Viewer::class.qualifiedName.orEmpty())) {
                ViewerNavigationGate.release(navController)
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
            ViewerNavigationGate.release(navController)
        }
    }

    LaunchedEffect(repo) {
        UndoManager.registerHandler(UndoType.DELETE) { action -> action.paths.forEach { repo.restoreFromRecycleBin(it) }; org.fossify.gallery.helpers.RefreshBus.trigger() }
        UndoManager.registerHandler(UndoType.TAG_ADD) {
            val action = it
            org.fossify.gallery.compose.util.XmpBatch.run(appCtx, action.paths.toList()) { repo.removeTag(it, action.extra["tag"] ?: "") }
        }
        UndoManager.registerHandler(UndoType.TAG_REMOVE) {
            val action = it
            org.fossify.gallery.compose.util.XmpBatch.run(appCtx, action.paths.toList()) { repo.addTag(it, action.extra["tag"] ?: "") }
        }
        UndoManager.registerHandler(UndoType.TAG_RESTORE_SNAPSHOT) { action ->
            val paths = action.tagSnapshots.keys.toList()
            // Restore the hierarchy first so the exact tag snapshot is serialized with the old
            // ancestry rather than briefly writing it with the post-edit parent map.
            action.hierarchyBefore?.let { appCtx.config.tagHierarchy = it.toMutableMap() }
            org.fossify.gallery.compose.util.XmpBatch.run(appCtx, paths) { path -> repo.replaceTags(path, action.tagSnapshots[path].orEmpty()) }
            org.fossify.gallery.helpers.RefreshBus.trigger(pathsChanged = false, collectionsChanged = false)
        }
        UndoManager.registerHandler(UndoType.TAG_HIERARCHY) { action ->
            action.hierarchyBefore?.let { appCtx.config.tagHierarchy = it.toMutableMap() }
            if (action.paths.isNotEmpty()) repo.rewriteTagHierarchy(action.paths)
            org.fossify.gallery.helpers.RefreshBus.trigger(pathsChanged = false, collectionsChanged = false)
        }
        UndoManager.registerHandler(UndoType.RATING_CHANGE) {
            val action = it
            val snapshots = action.ratingSnapshots
            org.fossify.gallery.compose.util.XmpBatch.run(appCtx, action.paths.toList()) { path -> repo.updateRating(path, snapshots[path] ?: action.extra["oldRating"]?.toIntOrNull() ?: 0) }
            org.fossify.gallery.helpers.RefreshBus.trigger(pathsChanged = false)
        }
        UndoManager.registerHandler(UndoType.COMPRESS_REPLACE) { action ->
            action.paths.forEach { repo.restoreFromRecycleBin(it) }
            action.extra["newPath"]?.let { newPath ->
                runCatching { java.io.File(newPath).delete() }
                runCatching { android.media.MediaScannerConnection.scanFile(appCtx, arrayOf(newPath), null, null) }
            }
            org.fossify.gallery.helpers.RefreshBus.trigger()
        }
        // Batch version of the above for a lossless "Optimize" run: extra maps each original's
        // recycle-bin path (a .trashed path after trashing) to its generated newPath. Restore every
        // original from the recycle bin (which un-trashes it back to its home) and delete every copy.
        UndoManager.registerHandler(UndoType.OPTIMIZE_REPLACE) { action ->
            action.extra.forEach { (binPath, newPath) ->
                repo.restoreFromRecycleBin(binPath)
                runCatching { java.io.File(newPath).delete() }
                runCatching { android.media.MediaScannerConnection.scanFile(appCtx, arrayOf(newPath), null, null) }
            }
            org.fossify.gallery.helpers.RefreshBus.trigger()
        }
        // action.extra maps each moved file's CURRENT path (= action.paths, where it is now) back to
        // where it came from - see MediaBatchWorker's movedPairs. Moving it back is a real file
        // operation (unlike DELETE's undo, which is just flipping deleted_ts), so this re-enqueues the
        // same worker/operation the original move used, in reverse, rather than duplicating its
        // file-move logic here. No consent request first: this app requires MANAGE_EXTERNAL_STORAGE
        // (see MoverWidgetProvider, which enqueues the same way), and a file this app just moved
        // moments ago is already its own to move back.
        UndoManager.registerHandler(UndoType.MOVE) { action ->
            val items = action.extra.map { (currentPath, originalPath) ->
                org.fossify.gallery.models.BatchJobItem(jobId = "", sourcePath = currentPath, targetPath = originalPath)
            }
            if (items.isNotEmpty()) {
                org.fossify.gallery.workers.MediaBatchWorker.enqueue(appCtx, org.fossify.gallery.workers.BatchOperation.MOVE_COPY_DELETE, items)
            }
        }
    }

    // The Quick Mover widget's "set up folder pairs" button (shown when no pairs are configured
    // yet, see MoverWidgetProvider) launches the app with this extra instead of just opening it.
    // Two paths, since a cold start and an already-running task deliver the request differently:
    // - Cold start: read once off the Activity's own launch intent, consumed via removeExtra() so
    //   a later recreation/config change doesn't re-trigger the same navigation.
    // - Already running: ComposeExplorerActivity.onNewIntent() publishes onto NavigateBus instead,
    //   since FLAG_ACTIVITY_CLEAR_TOP on an already-top instance never goes through onCreate()
    //   again, and a plain intent-extra read wouldn't be observed by an already-composed NavHost.
    fun navigateShortcutTarget(target: String?) {
        when (target) {
            org.fossify.gallery.helpers.MoverWidgetProvider.NAVIGATE_TARGET_MOVER ->
                navController.navigate(FoldersMover) { launchSingleTop = true }
            org.fossify.gallery.helpers.AppShortcutsHelper.NAVIGATE_TARGET_CLEANUP ->
                navController.navigate(CleanupHub) { launchSingleTop = true }
            org.fossify.gallery.helpers.AppShortcutsHelper.NAVIGATE_TARGET_RECYCLE_BIN ->
                navController.navigate(RecycleBin) { launchSingleTop = true }
        }
    }
    LaunchedEffect(Unit) {
        val activity = ctx as? android.app.Activity
        val target = activity?.intent?.getStringExtra(org.fossify.gallery.helpers.MoverWidgetProvider.EXTRA_NAVIGATE_TO)
        if (!target.isNullOrBlank()) {
            activity.intent.removeExtra(org.fossify.gallery.helpers.MoverWidgetProvider.EXTRA_NAVIGATE_TO)
            navigateShortcutTarget(target)
        }
        val widgetFolder = activity?.intent?.getStringExtra(org.fossify.gallery.helpers.DIRECTORY)
        if (!widgetFolder.isNullOrBlank()) {
            activity.intent.removeExtra(org.fossify.gallery.helpers.DIRECTORY)
            if (org.fossify.gallery.helpers.IntentSecurity.isSafeExternalPath(ctx, widgetFolder)) {
                navController.navigate(Folder(widgetFolder)) { launchSingleTop = true }
            }
        }
    }
    LaunchedEffect(Unit) {
        org.fossify.gallery.compose.util.NavigateBus.events.collect { target ->
            navigateShortcutTarget(target)
        }
    }
    LaunchedEffect(Unit) {
        org.fossify.gallery.compose.util.NavigateBus.folderEvents.collect { folderPath ->
            navController.navigate(Folder(folderPath)) { launchSingleTop = true }
        }
    }
    // Media details can request "show in Explorer" from routes such as the full-screen viewer.
    // Return to the existing Home entry; MainScreen then consumes the retained folder request and
    // selects Explorer. Keeping the request in a StateFlow avoids losing it during that pop.
    LaunchedEffect(Unit) {
        org.fossify.gallery.compose.util.ExplorerNavigationBus.folderPath.collect { folderPath ->
            if (folderPath != null) navController.popBackStack(Home, inclusive = false)
        }
    }

    // One-time sanitisation of cached tags that may contain UTF-16LE byte dumps (e.g. "100 0 97 0 …").
    LaunchedEffect(Unit) {
        if (conf.tagCacheSanitizeVersion >= 1) return@LaunchedEffect
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                ctx.mediaTagDB.getAllTagPathPairs().forEach { row ->
                    val sanitised = org.fossify.gallery.helpers.XmpWriter.sanitizeTag(row.tag)
                    if (sanitised != row.tag) {
                        ctx.mediaTagDB.delete(row.path, row.tag)
                        if (sanitised.isNotBlank()) ctx.mediaTagDB.insert(MediaTag(mediaPath = row.path, tag = sanitised))
                    }
                }
                conf.tagCacheSanitizeVersion = 1
            } catch (e: Exception) { android.util.Log.e("GalleryNavHost", "Tag sanitize migration failed", e) }
        }
    }

    GalleryTheme(
        darkTheme = resolveDarkTheme(conf.forceDarkMode, conf.forceLightMode),
        dynamicColor = conf.useDynamicColors,
        amoledBlack = conf.useAmoledBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
        AppProviders(repo) {
            SharedTransitionLayout(modifier = modifier) {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = navController,
                    startDestination = Home,
                    // Push: fade+scale-up the new screen in. Pop: just fade the revealed screen back
                    // in (it was already fully rendered a moment ago, scaling it up again read as if
                    // it were new) and fade+scale-down the screen being dismissed, mirroring how it
                    // originally scaled in. Without popEnterTransition/popExitTransition, Navigation
                    // Compose reuses enterTransition/exitTransition for pops too.
                    enterTransition = { fadeIn(AppMotion.medium) + scaleIn(initialScale = 0.92f, animationSpec = AppMotion.medium) },
                    exitTransition = { fadeOut(AppMotion.medium) },
                    // Pop back (e.g. Viewer -> folder) is snappier: the revealed screen was fully
                    // rendered a moment ago, so a quick fade reads as instant, while a 300ms one feels
                    // like a wait when the content is already there.
                    popEnterTransition = { fadeIn(AppMotion.short) },
                    popExitTransition = { fadeOut(AppMotion.short) + scaleOut(targetScale = 0.92f, animationSpec = AppMotion.short) },
                ) {
                    composable<Home> {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            MainScreen(
                                navController = navController,
                                onFinish = { (ctx as? android.app.Activity)?.finish() },
                                onContentReady = onContentReady,
                            )
                        }
                    }
                    composable<Folder> { backStackEntry ->
                        val route = backStackEntry.toRoute<Folder>()
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            FolderMediaScreen(
                                folderPath = route.folderPath,
                                onBack = { navController.popBackStack() },
                                onNavigateToViewer = { paths, startIndex -> org.fossify.gallery.helpers.PickIntent.handleMediaTap(ctx as? android.app.Activity, paths, startIndex) { navController.navigateToViewer(paths, startIndex) } },
                            onOpenSwipeMode = { path, startPath ->
                                navController.navigate(FolderSwipe(path, startPath.orEmpty())) { launchSingleTop = true }
                            },
                            onOpenPersonReview = { path ->
                                navController.navigate(PersonReview(path)) { launchSingleTop = true }
                            },
                        )
                        }
                    }
                    composable<FolderSwipe> { backStackEntry ->
                        val route = backStackEntry.toRoute<FolderSwipe>()
                        FolderSwipeScreen(
                            folderPath = route.folderPath,
                            startPath = route.startPath,
                            onBack = { navController.popBackStack() },
                            onNavigateToViewer = { paths, startIndex ->
                                org.fossify.gallery.helpers.PickIntent.handleMediaTap(ctx as? android.app.Activity, paths, startIndex) {
                                    navController.navigateToViewer(paths, startIndex)
                                }
                            },
                        )
                    }
                    composable<PersonReview> { backStackEntry ->
                        val route = backStackEntry.toRoute<PersonReview>()
                        // PersonReview reuses MediaScreen, whose tiles participate in the same
                        // shared-element transitions as FolderMediaScreen. Provide this route's
                        // AnimatedVisibilityScope just like every other media grid route.
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            PersonReviewScreen(
                                folderPath = route.folderPath,
                                onBack = { navController.popBackStack() },
                                onNavigateToViewer = { paths, startIndex ->
                                    org.fossify.gallery.helpers.PickIntent.handleMediaTap(ctx as? android.app.Activity, paths, startIndex) {
                                        navController.navigateToViewer(paths, startIndex)
                                    }
                                },
                            )
                        }
                    }
                    // Plain fade only (no scale) - the tapped thumbnail already morphs into place via
                    // the shared element transition (see sharedElementKey), so the whole-screen
                    // scaleIn/scaleOut the other destinations use would compete with that morph
                    // instead of complementing it.
                    composable<Viewer>(
                        enterTransition = { fadeIn(AppMotion.medium) },
                        exitTransition = { fadeOut(AppMotion.medium) },
                        popEnterTransition = { fadeIn(AppMotion.short) },
                        // Snappy dismiss back to the folder - see Home's popExit note.
                        popExitTransition = { fadeOut(AppMotion.short) },
                    ) { backStackEntry ->
                        val route = backStackEntry.toRoute<Viewer>()
                        DisposableEffect(route.sessionId) {
                            onDispose { ViewerArgs.cancelResolution(route.sessionId) }
                        }
                        val viewerPathsFlow = remember(route.sessionId) { ViewerArgs.observe(route.sessionId) }
                        val observedPaths by viewerPathsFlow.collectAsStateWithLifecycle()
                        val viewerPaths = observedPaths.ifEmpty {
                            route.startPath.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
                        }
                        LaunchedEffect(route.sessionId) {
                            // Process-death recovery is asynchronous; normal launches already have
                            // the tapped item in the flow and never wait here.
                            ViewerArgs.load(route.sessionId)
                        }
                        if (viewerPaths.isEmpty()) {
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                        } else {
                            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                ViewerScreen(
                                    paths = viewerPaths,
                                    startIndex = route.startIndex,
                                    startPath = route.startPath,
                                    onLoadMore = { direction, anchorPath -> ViewerArgs.loadMore(route.sessionId, anchorPath, direction) },
                                    onClose = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                    composable<Settings> {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToAbout = { navController.navigate(About) { launchSingleTop = true } },
                        )
                    }
                    composable<ManageCollections> {
                        ManageCollectionsScreen(
                            onBack = { navController.popBackStack() },
                            onCollectionClick = { collection ->
                                val singleFolder = collection.getIncludedPaths().singleOrNull()
                                if (singleFolder != null) {
                                    navController.navigate(Folder(singleFolder)) { launchSingleTop = true }
                                } else {
                                    navController.popBackStack()
                                }
                            },
                        )
                    }
                    composable<About> {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                    composable<TagBrowser> {
                        TagBrowserScreen(
                            onBack = { navController.popBackStack() },
                            onTagFilterApplied = { tagPaths, tagName, matchAll ->
                                navController.previousBackStackEntry?.savedStateHandle?.apply {
                                    set("tagFilterPaths", ArrayList(tagPaths))
                                    set("tagFilterName", tagName)
                                    set("tagFilterMatchAll", matchAll)
                                }
                                navController.popBackStack()
                            },
                        )
                    }
                    composable<StorageAnalysis> {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        StorageAnalysisScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToViewer = { path -> navController.navigateToViewer(listOf(path), 0) },
                            onNavigateToCompressionReview = { navController.navigate(CompressionReview) { launchSingleTop = true } },
                            onNavigateToSwipe = { results ->
                                org.fossify.gallery.compose.screens.analysis.CompressionSwipeArgs.save(appCtx, results)
                                navController.navigate(CompressionSwipe) { launchSingleTop = true }
                            },
                        )
                        }
                    }
                    composable<CleanupHub> { backStackEntry ->
                        val route = backStackEntry.toRoute<CleanupHub>()
                        CleanupHubScreen(
                            onBack = { navController.popBackStack() },
                            onOpenStorageAnalysis = { navController.navigate(StorageAnalysis) { launchSingleTop = true } },
                            onOpenDuplicates = { navController.navigate(DuplicateFinder(route.folderPath)) { launchSingleTop = true } },
                            onOpenRecycleBin = { navController.navigate(RecycleBin) { launchSingleTop = true } },
                            onOpenCompressionReview = { navController.navigate(CompressionReview) { launchSingleTop = true } },
                        )
                    }
                    composable<DuplicateFinder> { backStackEntry ->
                        val route = backStackEntry.toRoute<DuplicateFinder>()
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        DuplicateFinderScreen(
                            initialFolder = route.folderPath,
                            onBack = { navController.popBackStack() },
                            onNavigateToViewer = { paths, startIndex -> org.fossify.gallery.helpers.PickIntent.handleMediaTap(ctx as? android.app.Activity, paths, startIndex) { navController.navigateToViewer(paths, startIndex) } },
                        )
                        }
                    }
                    composable<CompressionReview> {
                        CompressionReviewScreen(onBack = { navController.popBackStack() })
                    }
                    composable<CompressionSwipe> {
                        org.fossify.gallery.compose.screens.analysis.CompressionSwipeScreen(onBack = { navController.popBackStack() })
                    }
                    composable<FoldersMover> {
                        FoldersMoverScreen(onBack = { navController.popBackStack() })
                    }
                    composable<RecycleBin> {
                        RecycleBinScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToViewer = { paths, startIndex ->
                                org.fossify.gallery.helpers.PickIntent.handleMediaTap(ctx as? android.app.Activity, paths, startIndex) {
                                    navController.navigateToViewer(paths, startIndex)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
        PrivacyPauseScrim()
        }
    }
}
