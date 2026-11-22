package org.fossify.gallery.compose.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    state: EditorUiState,
    callbacks: EditorCallbacks,
    modifier: Modifier = Modifier,
    allowFolderPicker: Boolean = true,
    canvasContent: @Composable (Modifier) -> Unit,
) {
    val actionsEnabled = state.hasImage && !state.isSaving

    Scaffold(
        modifier = modifier,
        topBar = {
            EditorTopBar(
                isCropIntent = state.isCropIntent,
                actionsEnabled = actionsEnabled,
                backEnabled = !state.isSaving,
                callbacks = callbacks,
            )
        },
        bottomBar = {
            EditorBottomPanel(
                state = state,
                callbacks = callbacks,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            canvasContent(Modifier.fillMaxSize())
            if (state.isSaving) {
                EditorSavingOverlay()
            }
        }
    }

    EditorDialogsHost(
        dialog = state.activeDialog,
        callbacks = callbacks,
        allowFolderPicker = allowFolderPicker,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    isCropIntent: Boolean,
    actionsEnabled: Boolean,
    backEnabled: Boolean,
    callbacks: EditorCallbacks,
) {
    var overflowOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.basic_editor),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onBack, enabled = backEnabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        },
        actions = {
            IconButton(onClick = callbacks.onShareClick, enabled = actionsEnabled) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(org.fossify.commons.R.string.share),
                )
            }
            IconButton(onClick = callbacks.onSaveClick, enabled = actionsEnabled) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(org.fossify.commons.R.string.save_as),
                )
            }
            Box {
                IconButton(onClick = { overflowOpen = true }, enabled = actionsEnabled) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                    )
                }
                DropdownMenu(
                    expanded = overflowOpen,
                    onDismissRequest = { overflowOpen = false },
                ) {
                    if (!isCropIntent) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.overwrite_original)) },
                            onClick = {
                                overflowOpen = false
                                callbacks.onOverwriteOriginalClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.adjust_colors)) },
                            onClick = {
                                overflowOpen = false
                                callbacks.onAdjustColorsClick()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(org.fossify.commons.R.string.edit_with)) },
                        onClick = {
                            overflowOpen = false
                            callbacks.onEditWithClick()
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun EditorSavingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Scrim.a50),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(org.fossify.commons.R.string.saving),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EditScreenCropPreview() {
    GalleryTheme(darkTheme = true, dynamicColor = false) {
        EditScreen(
            state = EditorUiState(hasImage = true, primaryMode = EditorPrimaryMode.CROP_ROTATE),
            callbacks = previewEditorCallbacks(),
            allowFolderPicker = false,
        ) { modifier ->
            Box(
                modifier = modifier.background(Color(0xFF181A1C)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.basic_editor),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EditScreenDrawPreview() {
    GalleryTheme(darkTheme = true, dynamicColor = false) {
        EditScreen(
            state = EditorUiState(
                hasImage = true,
                primaryMode = EditorPrimaryMode.DRAW,
                canUndoDraw = true,
            ),
            callbacks = previewEditorCallbacks(),
            allowFolderPicker = false,
        ) { modifier ->
            Box(modifier = modifier.background(Color(0xFF181A1C)))
        }
    }
}

private fun previewEditorCallbacks() = EditorCallbacks(
    onBack = {},
    onSaveClick = {},
    onOverwriteOriginalClick = {},
    onAdjustColorsClick = {},
    onEditWithClick = {},
    onShareClick = {},
    onSelectPrimaryMode = {},
    onRotateRight = {},
    onFlipHorizontal = {},
    onFlipVertical = {},
    onToggleAspectRatioStrip = {},
    onResizeClick = {},
    onSelectAspectRatio = {},
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
