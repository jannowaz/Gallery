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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.activities.MainScreen
import org.fossify.gallery.compose.screens.folderscreen.FolderMediaScreen
import org.fossify.gallery.compose.screens.settings.SettingsScreen
import org.fossify.gallery.compose.screens.collections.ManageCollectionsScreen
import org.fossify.gallery.compose.screens.about.AboutScreen
import org.fossify.gallery.compose.screens.tagbrowser.TagBrowserScreen
import org.fossify.gallery.compose.screens.analysis.StorageAnalysisScreen
import org.fossify.gallery.compose.screens.analysis.DuplicateFinderScreen
import org.fossify.gallery.compose.screens.analysis.CompressionReviewScreen
import org.fossify.gallery.compose.screens.RecycleBinScreen
import org.fossify.gallery.compose.screens.FoldersMoverScreen
import org.fossify.gallery.compose.screens.viewer.ViewerScreen
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.resolveDarkTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.models.MediaTag
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.compose.util.PrivacyPauseScrim

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
) {
    val ctx = LocalContext.current
    val repo = remember { MediaRepository(ctx) }
    val conf = ctx.config

    LaunchedEffect(repo) {
        UndoManager.registerHandler(UndoType.DELETE) { action -> action.paths.forEach { repo.restoreFromRecycleBin(it) }; org.fossify.gallery.helpers.RefreshBus.trigger() }
        UndoManager.registerHandler(UndoType.TAG_ADD) { action -> action.paths.forEach { repo.removeTag(it, action.extra["tag"] ?: "") } }
        UndoManager.registerHandler(UndoType.TAG_REMOVE) { action -> action.paths.forEach { repo.addTag(it, action.extra["tag"] ?: "") } }
        UndoManager.registerHandler(UndoType.RATING_CHANGE) { action -> action.paths.forEach { repo.updateRating(it, action.extra["oldRating"]?.toIntOrNull() ?: 0) }; org.fossify.gallery.helpers.RefreshBus.trigger() }
        UndoManager.registerHandler(UndoType.COMPRESS_REPLACE) { action ->
            action.paths.forEach { repo.restoreFromRecycleBin(it) }
            action.extra["newPath"]?.let { newPath ->
                runCatching { java.io.File(newPath).delete() }
                runCatching { android.media.MediaScannerConnection.scanFile(ctx, arrayOf(newPath), null, null) }
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
                org.fossify.gallery.workers.MediaBatchWorker.enqueue(ctx, org.fossify.gallery.workers.BatchOperation.MOVE_COPY_DELETE, items)
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
    LaunchedEffect(Unit) {
        val activity = ctx as? android.app.Activity
        if (activity?.intent?.getStringExtra(org.fossify.gallery.helpers.MoverWidgetProvider.EXTRA_NAVIGATE_TO) == org.fossify.gallery.helpers.MoverWidgetProvider.NAVIGATE_TARGET_MOVER) {
            activity.intent.removeExtra(org.fossify.gallery.helpers.MoverWidgetProvider.EXTRA_NAVIGATE_TO)
            navController.navigate(FoldersMover)
        }
    }
    LaunchedEffect(Unit) {
        org.fossify.gallery.compose.util.NavigateBus.events.collect { target ->
            if (target == org.fossify.gallery.helpers.MoverWidgetProvider.NAVIGATE_TARGET_MOVER) {
                navController.navigate(FoldersMover)
            }
        }
    }

    // One-time sanitisation of cached tags that may contain UTF-16LE byte dumps (e.g. "100 0 97 0 …").
    LaunchedEffect(Unit) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                ctx.mediaTagDB.getAllTagPathPairs().forEach { row ->
                    val sanitised = org.fossify.gallery.helpers.XmpWriter.sanitizeTag(row.tag)
                    if (sanitised != row.tag) {
                        ctx.mediaTagDB.delete(row.path, row.tag)
                        if (sanitised.isNotBlank()) ctx.mediaTagDB.insert(MediaTag(mediaPath = row.path, tag = sanitised))
                    }
                }
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
                            )
                        }
                    }
                    composable<Folder> { backStackEntry ->
                        val route = backStackEntry.toRoute<Folder>()
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            FolderMediaScreen(
                                folderPath = route.folderPath,
                                onBack = { navController.popBackStack() },
                                onNavigateToViewer = { paths, startIndex -> ViewerArgs.paths = paths; navController.navigate(Viewer(startIndex)) },
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
                        val viewerPaths = remember { ViewerArgs.paths }
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            ViewerScreen(
                                paths = viewerPaths,
                                startIndex = route.startIndex,
                                onClose = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<Settings> {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToAbout = { navController.navigate(About) },
                        )
                    }
                    composable<ManageCollections> {
                        ManageCollectionsScreen(
                            onBack = { navController.popBackStack() },
                            onCollectionClick = { navController.popBackStack() },
                        )
                    }
                    composable<About> {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                    composable<TagBrowser> {
                        TagBrowserScreen(
                            onBack = { navController.popBackStack() },
                            onTagFilterApplied = { tagPaths, tagName ->
                                navController.previousBackStackEntry?.savedStateHandle?.apply {
                                    set("tagFilterPaths", ArrayList(tagPaths))
                                    set("tagFilterName", tagName)
                                }
                                navController.popBackStack()
                            },
                        )
                    }
                    composable<StorageAnalysis> {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        StorageAnalysisScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToViewer = { path -> ViewerArgs.paths = listOf(path); navController.navigate(Viewer(0)) },
                            onNavigateToCompressionReview = { navController.navigate(CompressionReview) },
                            onNavigateToSwipe = { results ->
                                org.fossify.gallery.compose.screens.analysis.CompressionSwipeArgs.results = results
                                navController.navigate(CompressionSwipe)
                            },
                        )
                        }
                    }
                    composable<DuplicateFinder> { backStackEntry ->
                        val route = backStackEntry.toRoute<DuplicateFinder>()
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        DuplicateFinderScreen(
                            initialFolder = route.folderPath,
                            onBack = { navController.popBackStack() },
                            onNavigateToViewer = { path -> ViewerArgs.paths = listOf(path); navController.navigate(Viewer(0)) },
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
                        RecycleBinScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
        PrivacyPauseScrim()
        }
    }
}
