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
            } catch (_: Exception) { }
        }
    }

    GalleryTheme(
        darkTheme = resolveDarkTheme(conf.forceDarkMode, conf.forceLightMode),
        dynamicColor = conf.useDynamicColors,
        amoledBlack = conf.useAmoledBackground,
    ) {
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
                    popEnterTransition = { fadeIn(AppMotion.medium) },
                    popExitTransition = { fadeOut(AppMotion.medium) + scaleOut(targetScale = 0.92f, animationSpec = AppMotion.medium) },
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
                        popEnterTransition = { fadeIn(AppMotion.medium) },
                        popExitTransition = { fadeOut(AppMotion.medium) },
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
}
}
