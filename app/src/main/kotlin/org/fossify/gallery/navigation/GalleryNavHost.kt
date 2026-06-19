@file:OptIn(ExperimentalSharedTransitionApi::class)
package org.fossify.gallery.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
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
import org.fossify.gallery.activities.MainScreen
import org.fossify.gallery.compose.screens.folderscreen.FolderMediaScreen
import org.fossify.gallery.compose.screens.settings.SettingsScreen
import org.fossify.gallery.compose.screens.collections.ManageCollectionsScreen
import org.fossify.gallery.compose.screens.about.AboutScreen
import org.fossify.gallery.compose.screens.tagbrowser.TagBrowserScreen
import org.fossify.gallery.compose.screens.analysis.StorageAnalysisScreen
import org.fossify.gallery.compose.screens.analysis.DuplicateFinderScreen
import org.fossify.gallery.compose.screens.viewer.ViewerScreen
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.config
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
        UndoManager.registerHandler(UndoType.DELETE) { action -> action.paths.forEach { repo.restoreFromRecycleBin(it) } }
        UndoManager.registerHandler(UndoType.TAG_ADD) { action -> action.paths.forEach { repo.removeTag(it, action.extra["tag"] ?: "") } }
        UndoManager.registerHandler(UndoType.TAG_REMOVE) { action -> action.paths.forEach { repo.addTag(it, action.extra["tag"] ?: "") } }
        UndoManager.registerHandler(UndoType.RATING_CHANGE) { action -> action.paths.forEach { repo.updateRating(it, action.extra["oldRating"]?.toIntOrNull() ?: 0) } }
    }

    GalleryTheme(darkTheme = conf.forceDarkMode || isSystemInDarkTheme()) {
        AppProviders(repo) {
            SharedTransitionLayout(modifier = modifier) {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = navController,
                    startDestination = Home,
                    enterTransition = { fadeIn(tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300)) },
                    exitTransition = { fadeOut(tween(300)) },
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
                        FolderMediaScreen(
                            folderPath = route.folderPath,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<Viewer> { backStackEntry ->
                        val route = backStackEntry.toRoute<Viewer>()
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            ViewerScreen(
                                paths = route.paths,
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
                        StorageAnalysisScreen(onBack = { navController.popBackStack() })
                    }
                    composable<DuplicateFinder> {
                        DuplicateFinderScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
}
