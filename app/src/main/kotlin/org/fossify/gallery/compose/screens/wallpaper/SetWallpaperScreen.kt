package org.fossify.gallery.compose.screens.wallpaper

import android.app.WallpaperManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.Scrim

enum class WallpaperAspectRatio {
    PORTRAIT,
    LANDSCAPE,
    SQUARE,
    FREE;

    fun nextPreset(): WallpaperAspectRatio = when (this) {
        PORTRAIT -> LANDSCAPE
        LANDSCAPE -> SQUARE
        SQUARE -> PORTRAIT
        FREE -> PORTRAIT
    }
}

enum class WallpaperTarget(
    val flag: Int,
    @get:StringRes val labelRes: Int,
) {
    HOME(WallpaperManager.FLAG_SYSTEM, R.string.home_screen),
    LOCK(WallpaperManager.FLAG_LOCK, R.string.lock_screen),
    BOTH(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK, R.string.home_and_lock_screen),
}

@Composable
private fun aspectRatioLabel(ratio: WallpaperAspectRatio): String = when (ratio) {
    WallpaperAspectRatio.PORTRAIT -> stringResource(R.string.portrait_aspect_ratio)
    WallpaperAspectRatio.LANDSCAPE -> stringResource(R.string.landscape_aspect_ratio)
    WallpaperAspectRatio.SQUARE -> "1:1"
    WallpaperAspectRatio.FREE -> stringResource(R.string.free_aspect_ratio)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetWallpaperScreen(
    hasImage: Boolean,
    aspectRatio: WallpaperAspectRatio,
    isApplying: Boolean,
    showTargetDialog: Boolean,
    onBack: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onAllowFreeAspectRatio: () -> Unit,
    onRotateRight: () -> Unit,
    onSaveClick: () -> Unit,
    onDismissTargetDialog: () -> Unit,
    onConfirmTarget: (WallpaperTarget) -> Unit,
    modifier: Modifier = Modifier,
    cropContent: @Composable (Modifier) -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.wallpaper),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isApplying) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSaveClick,
                        enabled = hasImage && !isApplying,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(org.fossify.commons.R.string.save),
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { overflowOpen = true },
                            enabled = hasImage && !isApplying,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_actions),
                            )
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.allow_changing_aspect_ratio))
                                },
                                trailingIcon = if (aspectRatio == WallpaperAspectRatio.FREE) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    overflowOpen = false
                                    onAllowFreeAspectRatio()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            WallpaperBottomBar(
                aspectRatio = aspectRatio,
                enabled = hasImage && !isApplying,
                onCycleAspectRatio = onCycleAspectRatio,
                onRotateRight = onRotateRight,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            cropContent(Modifier.fillMaxSize())

            if (isApplying) {
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
                                text = stringResource(R.string.setting_wallpaper),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTargetDialog) {
        WallpaperTargetDialog(
            onDismiss = onDismissTargetDialog,
            onConfirm = onConfirmTarget,
        )
    }
}

@Composable
private fun WallpaperBottomBar(
    aspectRatio: WallpaperAspectRatio,
    enabled: Boolean,
    onCycleAspectRatio: () -> Unit,
    onRotateRight: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onCycleAspectRatio,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = aspectRatioLabel(aspectRatio),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(
                onClick = onRotateRight,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.rotate),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun WallpaperTargetDialog(
    onDismiss: () -> Unit,
    onConfirm: (WallpaperTarget) -> Unit,
) {
    var selectedTarget by rememberSaveable { mutableStateOf(WallpaperTarget.BOTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.set_as_wallpaper))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WallpaperTarget.entries.forEach { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.RadioButton) { selectedTarget = target }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedTarget == target,
                            onClick = null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(target.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedTarget) }) {
                Text(stringResource(org.fossify.commons.R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun SetWallpaperScreenPreview() {
    GalleryTheme(dynamicColor = false) {
        SetWallpaperScreen(
            hasImage = true,
            aspectRatio = WallpaperAspectRatio.PORTRAIT,
            isApplying = false,
            showTargetDialog = false,
            onBack = {},
            onCycleAspectRatio = {},
            onAllowFreeAspectRatio = {},
            onRotateRight = {},
            onSaveClick = {},
            onDismissTargetDialog = {},
            onConfirmTarget = {},
        ) { modifier ->
            Box(
                modifier = modifier.background(Color(0xFF1A1C1E)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.wallpaper),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.portrait_aspect_ratio),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

