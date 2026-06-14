package org.fossify.gallery.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.MediaRepository

@Composable
fun GalleryNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val ctx = LocalContext.current
    val repo = remember { MediaRepository(ctx) }
    val conf = ctx.config

    GalleryTheme(darkTheme = conf.forceDarkMode || isSystemInDarkTheme()) {
        AppProviders(repo) {
            NavHost(
                navController = navController,
                startDestination = Home,
                modifier = modifier,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable<Home> {
                    MainScreen(
                        onFinish = { (ctx as? android.app.Activity)?.finish() },
                    )
                }
                composable<Folder> { backStackEntry ->
                    val route = backStackEntry.toRoute<Folder>()
                    FolderMediaScreen(
                        folderPath = route.folderPath,
                        onBack = { navController.popBackStack() },
                    )
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
                        onCollectionClick = { coll ->
                            navController.popBackStack()
                        },
                    )
                }
                composable<About> {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                    )
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
                    StorageAnalysisScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
