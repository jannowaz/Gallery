package org.fossify.gallery.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.FilterSheetContent
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.screens.about.AboutScreen
import org.fossify.gallery.compose.screens.folderswipe.FolderSwipeScreen
import org.fossify.gallery.compose.screens.settings.SettingsScreen
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * Compose UI smoke tests for critical interactive surfaces: [UndoBar], [FilterSheetContent],
 * [SettingsScreen], and [FolderSwipeScreen].
 */
@RunWith(AndroidJUnit4::class)
class GalleryComposeUiSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        UndoManager.clear()
        UndoManager.unregisterHandler(UndoType.DELETE)
    }

    @Test
    fun undoBar_displaysDeleteActionAndInvokesRegisteredHandler() {
        val ctx = composeRule.activity
        val restoredAction = AtomicReference<UndoAction?>(null)
        UndoManager.registerHandler(UndoType.DELETE) { action ->
            restoredAction.set(action)
        }

        composeRule.setContent {
            GalleryTheme(dynamicColor = false) {
                UndoBar()
            }
        }

        val deletedPath = "/storage/emulated/0/DCIM/Camera/smoke_test.jpg"
        UndoManager.push(UndoAction(paths = setOf(deletedPath), type = UndoType.DELETE))

        composeRule.onNodeWithText(ctx.getString(R.string.undo_deleted_count, 1))
            .assertIsDisplayed()
        composeRule.onNodeWithText(ctx.getString(R.string.undo))
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) { restoredAction.get() != null }
        assertEquals(setOf(deletedPath), restoredAction.get()?.paths)
    }

    @Test
    fun filterSheetContent_togglesRatingTypeAndClearAllCallbacks() {
        val ctx = composeRule.activity
        val repo = MediaRepository(ctx.applicationContext)
        var rating by mutableIntStateOf(0)
        var type by mutableIntStateOf(0)
        var minSize by mutableLongStateOf(0L)
        var dateRange by mutableIntStateOf(0)
        var clearAllInvoked by mutableStateOf(false)

        composeRule.setContent {
            GalleryTheme(dynamicColor = false) {
                AppProviders(repo) {
                    FilterSheetContent(
                        ratingFilter = rating,
                        onRatingChange = { rating = it },
                        selectedTagNames = emptySet(),
                        onToggleTag = {},
                        minSizeFilter = minSize,
                        onMinSizeChange = { minSize = it },
                        dateRangeFilter = dateRange,
                        onDateRangeChange = { dateRange = it },
                        typeFilter = type,
                        onTypeFilterChange = { type = it },
                        hasActiveFilter = rating > 0 || type != 0,
                        onClearAll = {
                            clearAllInvoked = true
                            rating = 0
                            type = 0
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(ctx.getString(R.string.cd_rating_star, 4))
            .performClick()
        composeRule.runOnIdle { assertEquals(4, rating) }

        composeRule.onNodeWithText(ctx.getString(R.string.images))
            .performClick()
        composeRule.runOnIdle { assertEquals(1, type) }

        composeRule.onNodeWithText(ctx.getString(R.string.clear_all_filters))
            .performClick()
        composeRule.runOnIdle {
            assertTrue(clearAllInvoked)
            assertEquals(0, rating)
            assertEquals(0, type)
        }
    }

    @Test
    fun settingsScreen_showsTopLevelSecurityAndManageData_andEmptySearchState() {
        val ctx = composeRule.activity
        val repo = MediaRepository(ctx.applicationContext)

        composeRule.setContent {
            GalleryTheme(dynamicColor = false) {
                AppProviders(repo) {
                    SettingsScreen(onBack = {})
                }
            }
        }

        composeRule.onNodeWithText(ctx.getString(R.string.set_security))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(ctx.getString(R.string.set_manage_data))
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNode(hasSetTextAction())
            .performScrollTo()
            .performTextInput("zzzz_nonexistent_setting_12345")

        composeRule.onNodeWithText(ctx.getString(R.string.no_results))
            .assertIsDisplayed()
    }

    @Test
    fun folderSwipeScreen_showsConfiguredArchiveFolder_andOpensRenameDialog() {
        val ctx = composeRule.activity
        val repo = MediaRepository(ctx.applicationContext)
        val previousArchiveName = ctx.config.swipeArchiveFolderName
        ctx.config.swipeArchiveFolderName = "InboxArchive"

        try {
            composeRule.setContent {
                GalleryTheme(dynamicColor = false) {
                    AppProviders(repo) {
                        FolderSwipeScreen(
                            folderPath = "/storage/emulated/0/DCIM/Camera",
                            onBack = {},
                        )
                    }
                }
            }

            composeRule.onNodeWithText("Camera → InboxArchive")
                .assertIsDisplayed()

            composeRule.onNodeWithContentDescription(ctx.getString(R.string.swipe_archive_folder_title))
                .performClick()

            composeRule.onNodeWithText(ctx.getString(R.string.swipe_archive_folder_hint))
                .assertIsDisplayed()
            composeRule.onNodeWithText(ctx.getString(org.fossify.commons.R.string.save))
                .assertIsDisplayed()
        } finally {
            ctx.config.swipeArchiveFolderName = previousArchiveName
        }
    }

    @Test
    fun aboutScreen_showsPrivacyAndExpandsLibrariesAndFaq() {
        val ctx = composeRule.activity

        composeRule.setContent {
            GalleryTheme(dynamicColor = false) {
                AboutScreen(onBack = {})
            }
        }

        composeRule.onNodeWithText(ctx.getString(R.string.about_privacy_title))
            .assertIsDisplayed()

        composeRule.onNodeWithText(ctx.getString(R.string.about_libraries_title))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Coil (Compose / Video / SVG / GIF)")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("AndroidX Media3 (ExoPlayer & Transformer)")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText(ctx.getString(R.string.about_faq_title))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(ctx.getString(R.string.faq_5_title))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
