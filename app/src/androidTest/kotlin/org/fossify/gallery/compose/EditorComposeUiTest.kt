package org.fossify.gallery.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.editor.EditScreen
import org.fossify.gallery.compose.screens.editor.EditorCallbacks
import org.fossify.gallery.compose.screens.editor.EditorPrimaryMode
import org.fossify.gallery.compose.screens.editor.EditorResizeDialog
import org.fossify.gallery.compose.screens.editor.EditorSaveAsDialog
import org.fossify.gallery.compose.screens.editor.EditorUiState
import org.fossify.gallery.compose.screens.settings.SettingsScreen
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.helpers.ASPECT_RATIO_ONE_ONE
import org.fossify.gallery.helpers.MediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorComposeUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editScreen_switchesPrimaryModes_andInvokesCropAndSaveCallbacks() {
        val ctx = composeRule.activity
        var state by mutableStateOf(EditorUiState(hasImage = true))
        var rotateCount = 0
        var saveClicked = false

        val callbacks = stubCallbacks(
            onSelectMode = { state = state.copy(primaryMode = it) },
            onSelectAspectRatio = { state = state.copy(aspectRatio = it) },
            onRotate = { rotateCount++ },
            onSave = { saveClicked = true },
        )

        composeRule.setContent {
            GalleryTheme(dynamicColor = false) {
                EditScreen(
                    state = state,
                    callbacks = callbacks,
                    allowFolderPicker = false,
                ) { modifier -> Box(modifier) }
            }
        }

        composeRule.onNodeWithText(ctx.getString(R.string.basic_editor))
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription(ctx.getString(R.string.rotate))
            .performClick()
        composeRule.onNodeWithText("1:1")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, rotateCount)
            assertEquals(ASPECT_RATIO_ONE_ONE, state.aspectRatio)
        }

        composeRule.onNodeWithText(ctx.getString(R.string.draw))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(EditorPrimaryMode.DRAW, state.primaryMode)
        }

        composeRule.onNodeWithContentDescription(ctx.getString(org.fossify.commons.R.string.save_as))
            .performClick()
        composeRule.runOnIdle {
            assertTrue(saveClicked)
        }
    }

    @Test
    fun editorResizeDialog_confirmsInitialDimensions() {
        val ctx = composeRule.activity
        var confirmed: Pair<Int, Int>? = null

        composeRule.setContent {
            GalleryTheme(dynamicColor = false) {
                EditorResizeDialog(
                    originalWidth = 1920,
                    originalHeight = 1080,
                    onDismiss = {},
                    onConfirm = { w, h -> confirmed = w to h },
                )
            }
        }

        composeRule.onNodeWithText(ctx.getString(R.string.resize_and_save))
            .assertIsDisplayed()
        composeRule.onNodeWithText(ctx.getString(org.fossify.commons.R.string.ok))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1920 to 1080, confirmed)
        }
    }

    @Test
    fun editorSaveAsDialog_confirmsDefaultPathAndFilename() {
        val ctx = composeRule.activity
        var result: Triple<String, String, String>? = null

        composeRule.setContent {
            GalleryTheme(dynamicColor = false) {
                EditorSaveAsDialog(
                    initialFolder = "/storage/emulated/0/Pictures",
                    initialFilename = "photo_1",
                    initialExtension = "jpg",
                    allowFolderPicker = false,
                    onDismiss = {},
                    onConfirm = { folder, name, ext -> result = Triple(folder, name, ext) },
                )
            }
        }

        composeRule.onNodeWithText(ctx.getString(org.fossify.commons.R.string.save_as))
            .assertIsDisplayed()
        composeRule.onNodeWithText(ctx.getString(org.fossify.commons.R.string.ok))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(Triple("/storage/emulated/0/Pictures", "photo_1", "jpg"), result)
        }
    }

    @Test
    fun settingsScreen_wideLayout_switchesDetailPaneSection() {
        val ctx = composeRule.activity
        val repo = MediaRepository(ctx.applicationContext)

        composeRule.setContent {
            GalleryTheme(dynamicColor = false) {
                AppProviders(repo) {
                    Box(Modifier.requiredWidth(960.dp)) {
                        SettingsScreen(onBack = {})
                    }
                }
            }
        }

        composeRule.onNodeWithText(ctx.getString(R.string.set_video))
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithText(ctx.getString(R.string.set_autoplay))
            .assertIsDisplayed()
    }

    private fun stubCallbacks(
        onSelectMode: (EditorPrimaryMode) -> Unit,
        onSelectAspectRatio: (Int) -> Unit,
        onRotate: () -> Unit,
        onSave: () -> Unit,
    ) = EditorCallbacks(
        onBack = {},
        onSaveClick = onSave,
        onOverwriteOriginalClick = {},
        onAdjustColorsClick = {},
        onEditWithClick = {},
        onShareClick = {},
        onSelectPrimaryMode = onSelectMode,
        onRotateRight = onRotate,
        onFlipHorizontal = {},
        onFlipVertical = {},
        onToggleAspectRatioStrip = {},
        onResizeClick = {},
        onSelectAspectRatio = onSelectAspectRatio,
        onOpenOtherAspectRatio = {},
        onSelectFilter = {},
        onOpenBrushColorPicker = {},
        onBrushSizeChange = {},
        onUndoDraw = {},
        onDismissDialog = {},
        onConfirmSaveAs = { _, _, _, _ -> },
        onConfirmOverwriteOriginal = {},
        onConfirmOverwriteFile = { _, _ -> },
        onConfirmResize = { _, _ -> },
        onConfirmOtherAspectRatio = {},
        onOpenCustomAspectRatio = {},
        onConfirmCustomAspectRatio = {},
        onPreviewColorAdjustments = {},
        onConfirmColorAdjustments = {},
        onCancelColorAdjustments = {},
        onConfirmBrushColor = {},
    )
}
