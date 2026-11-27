package org.fossify.gallery.compose.screens.viewer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.text.format.Formatter
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.LibrarySectionHeader
import org.fossify.gallery.compose.components.LibrarySheetHeader
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.theme.RatingStarColor
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getDecodedImageDimensions
import org.fossify.gallery.helpers.EXT_CAMERA_MODEL
import org.fossify.gallery.helpers.EXT_DATE_TAKEN
import org.fossify.gallery.helpers.EXT_EXIF_PROPERTIES
import org.fossify.gallery.helpers.EXT_GPS
import org.fossify.gallery.helpers.EXT_LAST_MODIFIED
import org.fossify.gallery.helpers.EXT_NAME
import org.fossify.gallery.helpers.EXT_PATH
import org.fossify.gallery.helpers.EXT_RESOLUTION
import org.fossify.gallery.helpers.EXT_SIZE
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val EXIF_DATE_PREFIX_LEN = 10
private const val ROTATION_QUARTER = 90
private const val ROTATION_THREE_QUARTERS = 270

/** Parses EXIF date headers (`yyyy:MM:dd ...` or `yyyy-MM-dd ...`) into a localized short date. */
internal fun formatExifDate(rawExifDate: String?, locale: Locale = Locale.getDefault()): String? {
    val prefix = rawExifDate?.trim()?.take(EXIF_DATE_PREFIX_LEN)?.takeIf { it.isNotEmpty() } ?: return null
    val patterns = listOf("yyyy:MM:dd", "yyyy-MM-dd")
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(prefix)
        }.getOrNull()
        if (parsed != null) {
            return DateFormat.getDateInstance(DateFormat.SHORT, locale).format(parsed)
        }
    }
    return prefix
}

@Composable
internal fun SheetSectionLabel(text: String) {
    LibrarySectionHeader(
        title = text,
        showDivider = false,
        topPadding = 6.dp,
        bottomPadding = 6.dp,
    )
}

@Composable
internal fun ActionChip(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.md),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .padding(4.dp)
            .heightIn(min = 64.dp)
            .widthIn(min = 64.dp)
            .semantics {
                role = Role.Button
                if (selected) this.selected = true
            },
    ) {
        val resolvedTint = tint ?: LocalContentColor.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(icon, label, tint = resolvedTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = resolvedTint, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerActionSheet(
    currentPath: String,
    currentIsVideo: Boolean,
    currentRating: Int,
    showRatingOverlay: Boolean,
    slideshowActive: Boolean,
    canRunSlideshow: Boolean,
    videoPlaybackSpeed: Float,
    onRate: (Int) -> Unit,
    onToggleRatingOverlay: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onInfo: () -> Unit,
    onTrimVideo: () -> Unit,
    onVideoDisplaySettings: () -> Unit,
    onCycleVideoPlaybackSpeed: () -> Unit,
    onEnterVideoPip: () -> Unit,
    onSaveFrame: () -> Unit,
    onOpenFrameGrabber: () -> Unit,
    onEditImage: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onOpenTags: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val labelDate = stringResource(R.string.sort_date)
    val labelResolution = stringResource(R.string.exif_resolution)
    val labelDuration = stringResource(R.string.media_info_duration)
    val labelCamera = stringResource(R.string.exif_camera)
    val labelFocal = stringResource(R.string.exif_focal_length)
    val labelAperture = stringResource(R.string.exif_aperture)
    val labelExposure = stringResource(R.string.exif_exposure)
    val labelSize = stringResource(R.string.sort_size)
    val labels = remember(labelDate, labelResolution, labelDuration, labelCamera, labelFocal, labelAperture, labelExposure, labelSize) {
        SheetMetadataLabels(
            date = labelDate,
            resolution = labelResolution,
            duration = labelDuration,
            camera = labelCamera,
            focal = labelFocal,
            aperture = labelAperture,
            exposure = labelExposure,
            size = labelSize,
        )
    }
    var exifLines by remember(currentPath) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(currentPath, currentIsVideo, labels) {
        exifLines = withContext(Dispatchers.IO) {
            if (currentIsVideo) {
                loadVideoSheetMetadata(ctx, currentPath, labels)
            } else {
                loadImageSheetMetadata(ctx, currentPath, labels)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            LibrarySheetHeader(
                title = File(currentPath).name,
                onDismiss = onDismiss,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (exifLines.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    exifLines.take(8).forEach { (label, value) ->
                        Surface(
                            shape = RoundedCornerShape(Radius.sm),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    value,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SheetSectionLabel(stringResource(R.string.rating_title))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                for (i in 1..5) {
                    IconButton(onClick = { onRate(i) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (i <= currentRating) Icons.Default.Star else Icons.Default.StarBorder,
                            stringResource(R.string.cd_rating_star, i),
                            tint = if (i <= currentRating) RatingStarColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SheetSectionLabel(stringResource(R.string.viewer_sheet_section_file))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionChip(Icons.Default.DriveFileRenameOutline, stringResource(R.string.action_rename), onClick = onRename)
                ActionChip(Icons.Default.ContentCopy, stringResource(R.string.action_copy), onClick = onCopy)
                ActionChip(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.action_move), onClick = onMove)
                ActionChip(Icons.Default.Info, stringResource(R.string.action_info), onClick = onInfo)
            }
            Spacer(Modifier.height(10.dp))
            SheetSectionLabel(stringResource(R.string.viewer_sheet_section_edit))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionChip(
                    Icons.AutoMirrored.Filled.Label,
                    stringResource(R.string.action_tags),
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onOpenTags,
                )
                if (currentIsVideo) {
                    ActionChip(Icons.Default.ContentCut, stringResource(R.string.trim_video), onClick = onTrimVideo)
                    ActionChip(Icons.Default.PhotoCamera, stringResource(R.string.action_save_frame), onClick = onSaveFrame)
                    ActionChip(Icons.Default.BurstMode, stringResource(R.string.grab_frames), onClick = onOpenFrameGrabber)
                } else {
                    ActionChip(Icons.Default.Edit, stringResource(R.string.edit), onClick = onEditImage)
                }
            }
            Spacer(Modifier.height(10.dp))
            SheetSectionLabel(stringResource(R.string.viewer_sheet_section_view))
            ViewerActionSheetViewSection(
                currentIsVideo = currentIsVideo,
                showRatingOverlay = showRatingOverlay,
                slideshowActive = slideshowActive,
                canRunSlideshow = canRunSlideshow,
                videoPlaybackSpeed = videoPlaybackSpeed,
                onToggleRatingOverlay = onToggleRatingOverlay,
                onToggleSlideshow = onToggleSlideshow,
                onCycleVideoPlaybackSpeed = onCycleVideoPlaybackSpeed,
                onEnterVideoPip = onEnterVideoPip,
                onVideoDisplaySettings = onVideoDisplaySettings,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

private data class SheetMetadataLabels(
    val date: String,
    val resolution: String,
    val duration: String,
    val camera: String,
    val focal: String,
    val aperture: String,
    val exposure: String,
    val size: String,
)

private fun loadImageSheetMetadata(
    ctx: Context,
    path: String,
    labels: SheetMetadataLabels,
): List<Pair<String, String>> {
    val lines = mutableListOf<Pair<String, String>>()
    val file = File(path)
    runCatching {
        val exif = ExifInterface(path)
        formatExifDate(exif.getAttribute(ExifInterface.TAG_DATETIME))?.let { lines.add(labels.date to it) }
        val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
        val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
        val dims = if (w > 0 && h > 0) w to h else path.getDecodedImageDimensions()
        dims?.let { (width, height) -> lines.add(labels.resolution to "$width × $height") }
        val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty()
        val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty()
        val camera = "$make $model".trim()
        if (camera.isNotBlank()) lines.add(labels.camera to camera)
        exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { lines.add(labels.focal to it) }
        exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { lines.add(labels.aperture to "f/$it") }
        exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { lines.add(labels.exposure to "${it}s") }
        exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { lines.add("ISO" to it) }
        if (file.exists()) lines.add(labels.size to Formatter.formatShortFileSize(ctx, file.length()))
    }
    return lines
}

private fun loadVideoSheetMetadata(
    ctx: Context,
    path: String,
    labels: SheetMetadataLabels,
): List<Pair<String, String>> {
    val lines = mutableListOf<Pair<String, String>>()
    val file = File(path)
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val (w, h) = if (rot == ROTATION_QUARTER || rot == ROTATION_THREE_QUARTERS) {
            rawH to rawW
        } else {
            rawW to rawH
        }
        if (w > 0 && h > 0) lines.add(labels.resolution to "$w × $h")
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        if (durationMs > 0L) {
            lines.add(labels.duration to formatTrimTimestamp(durationMs))
        }
    } catch (_: Exception) {
    } finally {
        runCatching { retriever.release() }
    }
    if (file.exists()) {
        if (file.lastModified() > 0L) {
            lines.add(labels.date to DateFormat.getDateInstance(DateFormat.SHORT).format(Date(file.lastModified())))
        }
        lines.add(labels.size to Formatter.formatShortFileSize(ctx, file.length()))
    }
    return lines
}

@Composable
private fun ViewerActionSheetViewSection(
    currentIsVideo: Boolean,
    showRatingOverlay: Boolean,
    slideshowActive: Boolean,
    canRunSlideshow: Boolean,
    videoPlaybackSpeed: Float,
    onToggleRatingOverlay: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onCycleVideoPlaybackSpeed: () -> Unit,
    onEnterVideoPip: () -> Unit,
    onVideoDisplaySettings: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!currentIsVideo) {
            ActionChip(
                Icons.Default.Star,
                stringResource(R.string.viewer_toggle_rating_bar),
                selected = showRatingOverlay,
                onClick = onToggleRatingOverlay,
            )
        }
        if (canRunSlideshow) {
            ActionChip(
                Icons.Default.Slideshow,
                stringResource(R.string.action_slideshow),
                selected = slideshowActive,
                onClick = onToggleSlideshow,
            )
        }
        if (currentIsVideo) {
            ActionChip(
                Icons.Default.Speed,
                formatPlaybackSpeed(videoPlaybackSpeed),
                selected = videoPlaybackSpeed != 1f,
                onClick = onCycleVideoPlaybackSpeed,
            )
            ActionChip(
                Icons.Default.PictureInPictureAlt,
                stringResource(R.string.cd_pip),
                onClick = onEnterVideoPip,
            )
            ActionChip(
                Icons.Default.AspectRatio,
                stringResource(R.string.video_display),
                onClick = onVideoDisplaySettings,
            )
        }
    }
}

@Composable
internal fun ViewerSlideshowDialog(
    onStart: (intervalSec: Int, loop: Boolean, random: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var intervalSec by remember { mutableIntStateOf(ctx.config.slideshowInterval.coerceIn(1, 60)) }
    var loopEnabled by remember { mutableStateOf(ctx.config.loopSlideshow) }
    var randomEnabled by remember { mutableStateOf(ctx.config.slideshowRandomOrder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.slideshow)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 5, 10).forEach { seconds ->
                        FilterChip(
                            selected = intervalSec == seconds,
                            onClick = { intervalSec = seconds },
                            label = {
                                Text(
                                    "${seconds}s",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                )
                            },
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = loopEnabled, role = Role.Switch, onValueChange = { loopEnabled = it })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.loop_slideshow), modifier = Modifier.weight(1f))
                    Switch(checked = loopEnabled, onCheckedChange = null)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = randomEnabled, role = Role.Switch, onValueChange = { randomEnabled = it })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.random_order), modifier = Modifier.weight(1f))
                    Switch(checked = randomEnabled, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(intervalSec, loopEnabled, randomEnabled) }) {
                Text(stringResource(org.fossify.commons.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(org.fossify.commons.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun ViewerVideoScalingDialog(
    currentMode: Int,
    onSelectMode: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.display_mode)) },
        text = {
            Column {
                listOf(
                    stringResource(R.string.video_scale_fit) to SCALING_FIT,
                    stringResource(R.string.video_scale_zoom) to SCALING_ZOOM,
                    stringResource(R.string.video_scale_stretch) to SCALING_FILL_WIDTH,
                ).forEach { (label, mode) ->
                    val isSelected = currentMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelectMode(mode); onDismiss() },
                            )
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cd_close)) } },
    )
}

/** Builds the on-screen extended-details overlay text per [org.fossify.gallery.helpers.Config.extendedDetails]'s
 * bitmask - a persistent-on-photo readout, distinct from the action sheet's tap-to-open EXIF strip. */
internal fun buildExtendedDetailsText(ctx: Context, path: String, isVideo: Boolean): String {
    val file = File(path)
    if (!file.exists()) return ""
    val flags = ctx.config.extendedDetails
    val lines = mutableListOf<String>()
    if (flags and EXT_NAME != 0) lines.add(file.name)
    if (flags and EXT_PATH != 0) lines.add("${file.parent?.trimEnd('/') ?: ""}/")
    if (flags and EXT_SIZE != 0) lines.add(Formatter.formatShortFileSize(ctx, file.length()))
    if (!isVideo) {
        val exif = try { ExifInterface(path) } catch (_: Exception) { null }
        if (flags and EXT_RESOLUTION != 0) {
            val exifDimensions = exif?.let { metadata ->
                val width = metadata.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                val height = metadata.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                if (width > 0 && height > 0) width to height else null
            }
            val dimensions = exifDimensions ?: path.getDecodedImageDimensions()
            dimensions?.let { (width, height) -> lines.add("$width × $height") }
        }
        if (flags and EXT_LAST_MODIFIED != 0) lines.add(DateFormat.getDateInstance().format(Date(file.lastModified())))
        if (flags and EXT_DATE_TAKEN != 0) {
            formatExifDate(exif?.getAttribute(ExifInterface.TAG_DATETIME))?.let { lines.add(it) }
        }
        if (flags and EXT_CAMERA_MODEL != 0) {
            val make = exif?.getAttribute(ExifInterface.TAG_MAKE) ?: ""
            val model = exif?.getAttribute(ExifInterface.TAG_MODEL) ?: ""
            if (make.isNotBlank() || model.isNotBlank()) lines.add("$make $model".trim())
        }
        if (flags and EXT_EXIF_PROPERTIES != 0) {
            val props = mutableListOf<String>()
            exif?.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { props.add(it) }
            exif?.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { props.add("f/$it") }
            exif?.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { props.add("${it}s") }
            exif?.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { props.add("ISO$it") }
            if (props.isNotEmpty()) lines.add(props.joinToString("  "))
        }
        if (flags and EXT_GPS != 0) {
            exif?.latLong?.let { latLon -> lines.add("${latLon[0]}, ${latLon[1]}") }
        }
    } else if (flags and EXT_LAST_MODIFIED != 0) {
        lines.add(DateFormat.getDateInstance().format(Date(file.lastModified())))
    }
    return lines.joinToString("\n")
}
