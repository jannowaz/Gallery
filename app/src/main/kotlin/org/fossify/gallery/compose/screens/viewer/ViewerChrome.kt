package org.fossify.gallery.compose.screens.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.UndoBar
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.FavoriteColor
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.compose.theme.Scrim
import org.fossify.gallery.helpers.RefreshBus
import java.io.File
import java.io.IOException
import kotlin.math.abs

private const val MAX_STAR_RATING = 5
private const val DRAG_FADE_DISTANCE_PX = 160f
private const val HANDLE_ALPHA = 0.58f
private const val UNSELECTED_ICON_ALPHA = 0.72f
private const val DELETE_TINT_ARGB = 0xFFFFB4AB
private const val FRAME_JPEG_QUALITY = 95
private const val VIDEO_BOTTOM_OFFSET_DP = 64
private const val FILMSTRIP_UNDO_CLEARANCE_DP = 56
private const val RATING_UNDO_CLEARANCE_DP = 128
private const val COMPACT_UNDO_CLEARANCE_DP = 72
private const val VIDEO_HINT_OFFSET_Y_DP = 100
private const val BOTTOM_SCRIM_HEIGHT_DP = 200

@Composable
internal fun BoxScope.ViewerGestureHints(
    showGestureHint: Boolean,
    showVideoGestureHintNow: Boolean,
    onDismissGestureHint: () -> Unit,
) {
    AnimatedVisibility(
        visible = showGestureHint,
        enter = fadeIn(AppMotion.medium),
        exit = fadeOut(AppMotion.medium),
        modifier = Modifier.align(Alignment.Center),
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = Scrim.a60,
            modifier = Modifier.clickable(onClick = onDismissGestureHint),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.viewer_gesture_hint_up),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.viewer_gesture_hint_down),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.viewer_gesture_hint_tap),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showVideoGestureHintNow,
        enter = fadeIn(AppMotion.medium),
        exit = fadeOut(AppMotion.medium),
        modifier = Modifier
            .align(Alignment.Center)
            .offset(y = VIDEO_HINT_OFFSET_Y_DP.dp)
            .padding(horizontal = 24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(Radius.lg), color = Scrim.a60) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Default.BrightnessMedium,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.viewer_gesture_hint_brightness),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Surface(shape = RoundedCornerShape(Radius.lg), color = Scrim.a60) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.viewer_gesture_hint_volume),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.ViewerBottomChrome(
    showUI: Boolean,
    inPip: Boolean,
    currentIsVideo: Boolean,
    dragOffset: Float,
    items: List<String>,
    currentPage: Int,
    showRatingOverlay: Boolean,
    currentRating: Int,
    isFavorite: Boolean,
    onSelectPage: (Int) -> Unit,
    onRate: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpenTags: () -> Unit,
    onDelete: () -> Unit,
    onOpenMore: () -> Unit,
) {
    AnimatedVisibility(
        visible = showUI && !inPip,
        enter = fadeIn(AppMotion.medium),
        exit = fadeOut(AppMotion.medium),
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BOTTOM_SCRIM_HEIGHT_DP.dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Scrim.a60)),
        )
    }

    val bottomGroupOffset = if (currentIsVideo) VIDEO_BOTTOM_OFFSET_DP.dp else 0.dp
    val showFilmstrip = showUI && !inPip && items.size > 1
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = (1f - (abs(dragOffset) / DRAG_FADE_DISTANCE_PX)).coerceIn(0f, 1f)
            }
            .navigationBarsPadding()
            .padding(bottom = bottomGroupOffset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showUI && !inPip) {
            Box(
                Modifier
                    .padding(bottom = 2.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = HANDLE_ALPHA)),
            )
        }
        AnimatedVisibility(
            visible = showFilmstrip,
            enter = fadeIn(AppMotion.medium),
            exit = fadeOut(AppMotion.medium),
        ) {
            ViewerFilmstrip(
                items = items,
                currentPage = currentPage,
                onSelectPage = onSelectPage,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        AnimatedVisibility(
            visible = showRatingOverlay && showUI && !inPip,
            enter = fadeIn(AppMotion.medium),
            exit = fadeOut(AppMotion.medium),
        ) {
            ViewerRatingRail(
                currentRating = currentRating,
                isFavorite = isFavorite,
                onRate = onRate,
                onToggleFavorite = onToggleFavorite,
            )
        }
        AnimatedVisibility(
            visible = showUI && !inPip,
            enter = fadeIn(AppMotion.medium),
            exit = fadeOut(AppMotion.medium),
        ) {
            ViewerActionBarRow(
                showFavoriteButton = !showRatingOverlay,
                isFavorite = isFavorite,
                onShare = onShare,
                onOpenTags = onOpenTags,
                onToggleFavorite = onToggleFavorite,
                onDelete = onDelete,
                onOpenMore = onOpenMore,
            )
        }
    }

    if (!inPip) {
        val filmstripClearance = if (showFilmstrip) FILMSTRIP_UNDO_CLEARANCE_DP.dp else 0.dp
        val baseClearance = if (showRatingOverlay) {
            RATING_UNDO_CLEARANCE_DP.dp
        } else {
            COMPACT_UNDO_CLEARANCE_DP.dp
        }
        val undoClearance = if (showUI) baseClearance + filmstripClearance else 0.dp
        UndoBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = bottomGroupOffset + undoClearance),
        )
    }
}

@Composable
private fun ViewerRatingRail(
    currentRating: Int,
    isFavorite: Boolean,
    onRate: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.xl),
        color = Scrim.a40,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            for (i in 1..MAX_STAR_RATING) {
                IconButton(
                    onClick = { onRate(i) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (i <= currentRating) {
                            Icons.Default.Star
                        } else {
                            Icons.Default.StarBorder
                        },
                        contentDescription = stringResource(R.string.cd_rating_star, i),
                        tint = if (i <= currentRating) {
                            RatingStarColor
                        } else {
                            Color.White.copy(alpha = UNSELECTED_ICON_ALPHA)
                        },
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    contentDescription = stringResource(R.string.favorite),
                    tint = if (isFavorite) {
                        FavoriteColor
                    } else {
                        Color.White.copy(alpha = UNSELECTED_ICON_ALPHA)
                    },
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun ViewerActionBarRow(
    showFavoriteButton: Boolean,
    isFavorite: Boolean,
    onShare: () -> Unit,
    onOpenTags: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onOpenMore: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = stringResource(R.string.action_share),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onOpenTags) {
                Icon(
                    Icons.AutoMirrored.Filled.Label,
                    contentDescription = stringResource(R.string.action_tags),
                    tint = Color.White,
                )
            }
            if (showFavoriteButton) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        contentDescription = stringResource(R.string.favorite),
                        tint = if (isFavorite) FavoriteColor else Color.White,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(org.fossify.commons.R.string.delete),
                    tint = Color(DELETE_TINT_ARGB),
                )
            }
            IconButton(onClick = onOpenMore) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_actions),
                    tint = Color.White,
                )
            }
        }
    }
}

internal fun shareViewerMedia(ctx: Context, path: String, isVideo: Boolean) {
    val targetFile = File(path)
    if (!targetFile.exists()) {
        ctx.toast(R.string.no_files_found)
        return
    }
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", targetFile)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, ctx.getString(R.string.action_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    } catch (e: IllegalArgumentException) {
        Log.e("Viewer", "Share failed for $path", e)
        ctx.toast(R.string.viewer_share_failed)
    } catch (e: ActivityNotFoundException) {
        Log.e("Viewer", "Share failed for $path", e)
        ctx.toast(R.string.viewer_share_failed)
    } catch (e: SecurityException) {
        Log.e("Viewer", "Share failed for $path", e)
        ctx.toast(R.string.viewer_share_failed)
    }
}

internal suspend fun saveCapturedVideoFrame(
    ctx: Context,
    sourcePath: String,
    bitmap: Bitmap,
): Result<File> = withContext(Dispatchers.IO) {
    try {
        val sourceFile = File(sourcePath)
        val parent = sourceFile.parentFile ?: error("Video has no parent directory")
        val screensDir = File(parent, "screens")
        if (!screensDir.isDirectory && !screensDir.mkdirs() && !screensDir.isDirectory) {
            error("Could not create ${screensDir.absolutePath}")
        }
        val stem = sourceFile.nameWithoutExtension.ifBlank { "video" }
        var suffix = 0
        var output: File
        do {
            val suffixText = if (suffix == 0) "" else "_$suffix"
            output = File(screensDir, "${stem}_screen_${System.currentTimeMillis()}$suffixText.jpg")
            suffix++
        } while (output.exists())
        output.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, FRAME_JPEG_QUALITY, stream)) {
                "Could not encode screenshot"
            }
        }
        bitmap.recycle()
        runCatching {
            MediaScannerConnection.scanFile(
                ctx,
                arrayOf(output.absolutePath),
                arrayOf("image/jpeg"),
                null,
            )
        }
        RefreshBus.trigger(affectedPaths = listOf(output.absolutePath))
        Result.success(output)
    } catch (e: IOException) {
        runCatching { bitmap.recycle() }
        Result.failure(e)
    } catch (e: IllegalStateException) {
        runCatching { bitmap.recycle() }
        Result.failure(e)
    }
}
