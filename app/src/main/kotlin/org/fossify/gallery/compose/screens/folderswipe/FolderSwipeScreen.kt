package org.fossify.gallery.compose.screens.folderswipe

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.AllFilesAccessSheet
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.rememberAllFilesAccessRequester
import org.fossify.gallery.compose.theme.LocalMediaRepository
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.compose.util.rememberMediaStoreConsent
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.helpers.MoveConflicts
import org.fossify.gallery.models.BatchJobItem
import org.fossify.gallery.models.Medium
import org.fossify.gallery.helpers.IntentSecurity
import org.fossify.gallery.workers.BatchOperation
import org.fossify.gallery.workers.MediaBatchWorker
import java.io.File

/**
 * Folder-local Tinder-style triage. Left archives the current medium in a configurable child
 * folder (default localized "Archive"/"Archiv"); right simply advances to the next medium. The
 * buttons are intentionally kept next to the gesture so the same flow remains fully usable with
 * TalkBack, a mouse, or a stylus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSwipeScreen(
    folderPath: String,
    startPath: String = "",
    onBack: () -> Unit,
    onNavigateToViewer: ((paths: List<String>, startIndex: Int) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()
    val consent = rememberMediaStoreConsent()
    val defaultArchiveName = stringResource(R.string.swipe_archive_folder_default)
    var archiveFolderName by remember {
        mutableStateOf(
            ctx.config.swipeArchiveFolderName.trim().ifBlank { defaultArchiveName }
        )
    }
    var showArchiveFolderDialog by remember { mutableStateOf(false) }
    var items by remember(folderPath) { mutableStateOf<List<Medium>?>(null) }
    var currentIndex by rememberSaveable(folderPath) { mutableIntStateOf(0) }
    var isMoving by remember(folderPath) { mutableStateOf(false) }
    var errorMessage by remember(folderPath) { mutableStateOf<String?>(null) }
    var showAllFilesSheet by remember(folderPath) { mutableStateOf(false) }
    var swipeVideosMuted by rememberSaveable { mutableStateOf(true) }

    suspend fun load(preserveIndex: Boolean = false) {
        val loaded = withContext(Dispatchers.IO) { repo.getMediaFromPath(folderPath) }
        items = loaded
        currentIndex = if (preserveIndex) {
            currentIndex.coerceIn(0, loaded.lastIndex.coerceAtLeast(0))
        } else {
            loaded.indexOfFirst { it.path == startPath }.takeIf { it >= 0 } ?: 0
        }
    }

    LaunchedEffect(folderPath, startPath) { load(preserveIndex = false) }
    LaunchedEffect(folderPath) {
        org.fossify.gallery.helpers.RefreshBus.pathEvents.collect {
            if (!isMoving) load(preserveIndex = true)
        }
    }

    fun skip() {
        val current = items ?: return
        if (isMoving) return
        currentIndex = (currentIndex + 1).coerceAtMost(current.size)
    }

    fun archive() {
        val current = items?.getOrNull(currentIndex) ?: return
        if (isMoving) return
        scope.launch {
            isMoving = true
            errorMessage = null
            try {
                val sourcePath = current.path
                val safeArchiveName = IntentSecurity.sanitizeFilePrefix(archiveFolderName)
                val targetPath = withContext(Dispatchers.IO) {
                    val archiveDir = File(sourcePath).parentFile?.resolve(safeArchiveName)
                        ?: error("Missing source folder")
                    MoveConflicts.uniquePath(File(archiveDir, File(sourcePath).name).absolutePath)
                }
                if (!MediaStoreOps.hasAllFilesAccess(ctx) &&
                    MediaStoreOps.requiresAllFilesAccessForTarget(
                        File(targetPath).parent ?: "",
                        MediaStoreOps.isVideoPath(sourcePath),
                    )
                ) {
                    showAllFilesSheet = true
                    return@launch
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !MediaStoreOps.hasAllFilesAccess(ctx)) {
                    val sourceUri = withContext(Dispatchers.IO) { MediaStoreOps.uriForPath(ctx, sourcePath) }
                    if (sourceUri == null) {
                        errorMessage = res.getString(R.string.swipe_mode_move_failed)
                        return@launch
                    }
                    val granted = consent.request(MediaStoreOps.writeRequest(ctx, listOf(sourceUri)))
                    if (!granted) {
                        errorMessage = res.getString(R.string.swipe_mode_cancelled)
                        return@launch
                    }
                }
                val item = BatchJobItem(jobId = "", sourcePath = sourcePath, targetPath = targetPath)
                val operation = if (MediaStoreOps.sameVolume(sourcePath, targetPath)) {
                    BatchOperation.MOVE_FAST
                } else {
                    BatchOperation.MOVE_COPY_DELETE
                }
                val jobId = MediaBatchWorker.enqueue(ctx, operation, listOf(item))
                val info = WorkManager.getInstance(ctx).getWorkInfosByTagFlow(jobId).first { infos ->
                    infos.any { it.state.isFinished }
                }.firstOrNull { it.state.isFinished }
                val succeeded = info?.state == WorkInfo.State.SUCCEEDED && info.outputData.getInt("failed", 0) == 0
                if (!succeeded) {
                    errorMessage = res.getString(R.string.swipe_mode_move_failed)
                    return@launch
                }
                val remaining = items.orEmpty().toMutableList().apply {
                    removeAll { it.path == sourcePath }
                }
                items = remaining
                currentIndex = if (remaining.isEmpty()) 0 else currentIndex.coerceAtMost(remaining.lastIndex)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = res.getString(R.string.swipe_mode_move_failed)
                Toast.makeText(ctx, e.message ?: errorMessage, Toast.LENGTH_SHORT).show()
            } finally {
                isMoving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.swipe_mode_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            "${File(folderPath).name} → $archiveFolderName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showArchiveFolderDialog = true }) {
                        Icon(Icons.Default.Edit, stringResource(R.string.swipe_archive_folder_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = { org.fossify.gallery.compose.components.UndoBar(Modifier.navigationBarsPadding()) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val loaded = items) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                else -> {
                    val current = loaded.getOrNull(currentIndex)
                    if (current == null) {
                        SwipeFinished(onBack = onBack)
                    } else {
                        key(current.path) {
                            SwipeDecisionSurface(
                                leftLabel = stringResource(R.string.swipe_mode_archive),
                                rightLabel = stringResource(R.string.swipe_mode_skip),
                                enabled = !isMoving,
                                onLeft = ::archive,
                                onRight = ::skip,
                            ) {
                                Column(Modifier.fillMaxSize()) {
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Card(
                                            onClick = { onNavigateToViewer?.invoke(loaded.map { it.path }, currentIndex) },
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(Radius.lg),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                        ) {
                                            Box(Modifier.fillMaxSize()) {
                                                if (current.isVideo()) {
                                                    FolderSwipeVideoPreview(
                                                        path = current.path,
                                                        name = current.name,
                                                        durationSec = current.videoDuration,
                                                        muted = swipeVideosMuted,
                                                        onToggleMute = { swipeVideosMuted = !swipeVideosMuted },
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                } else {
                                                    GalleryImage(
                                                        path = current.path,
                                                        contentDescription = current.name,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Fit,
                                                        thumbnailSize = 1536,
                                                        backgroundColor =
                                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Card(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)),
                                        shape = RoundedCornerShape(Radius.md),
                                    ) {
                                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                            Text(
                                                current.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val posStyle = MaterialTheme.typography.labelSmall.copy(
                                                fontFeatureSettings = "tnum",
                                            )
                                            Text(
                                                stringResource(R.string.swipe_mode_position, currentIndex + 1, loaded.size),
                                                style = posStyle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (errorMessage != null) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    errorMessage!!,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        stringResource(R.string.swipe_mode_hint),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Button(
                                            onClick = ::archive,
                                            enabled = !isMoving,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            ),
                                        ) { Text(stringResource(R.string.swipe_mode_archive)) }
                                        OutlinedButton(
                                            onClick = ::skip,
                                            enabled = !isMoving,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        ) { Text(stringResource(R.string.swipe_mode_skip)) }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (isMoving) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(Radius.md),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 4.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.swipe_mode_moving))
                    }
                }
            }
        }
    }

    if (showArchiveFolderDialog) {
        var draft by remember(showArchiveFolderDialog) { mutableStateOf(archiveFolderName) }
        AlertDialog(
            onDismissRequest = { showArchiveFolderDialog = false },
            title = { Text(stringResource(R.string.swipe_archive_folder_title)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.swipe_archive_folder_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        val sanitized = IntentSecurity.sanitizeFilePrefix(draft)
                        archiveFolderName = sanitized
                        ctx.config.swipeArchiveFolderName = sanitized
                        showArchiveFolderDialog = false
                    },
                ) { Text(stringResource(org.fossify.commons.R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveFolderDialog = false }) {
                    Text(stringResource(org.fossify.commons.R.string.cancel))
                }
            },
        )
    }

    val requestAllFilesAccess = rememberAllFilesAccessRequester { granted ->
        showAllFilesSheet = false
        if (granted) archive()
    }
    if (showAllFilesSheet) {
        AllFilesAccessSheet(
            onAllow = { requestAllFilesAccess() },
            onLater = { showAllFilesSheet = false },
        )
    }
}

@Composable
private fun SwipeFinished(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.swipe_mode_finished), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.swipe_mode_finished_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_done))
        }
    }
}

@Composable
fun SwipeDecisionSurface(
    leftLabel: String,
    rightLabel: String,
    enabled: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { 110.dp.toPx() }
    var decided by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(enabled, onLeft, onRight) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (!enabled || decided) return@detectHorizontalDragGestures
                        val value = offsetX.value
                        scope.launch {
                            when {
                                value < -thresholdPx -> {
                                    decided = true
                                    offsetX.animateTo(-size.width * 1.2f, tween(180))
                                    onLeft()
                                }
                                value > thresholdPx -> {
                                    decided = true
                                    offsetX.animateTo(size.width * 1.2f, tween(180))
                                    onRight()
                                }
                                else -> offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                    onHorizontalDrag = { change, delta ->
                        if (enabled && !decided) {
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + delta) }
                        }
                    },
                )
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / 60f).coerceIn(-5f, 5f)
            },
        ) { content() }

        SwipeBadge(
            text = rightLabel,
            container = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp).graphicsLayer {
                alpha = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
                rotationZ = -8f
            },
        )
        SwipeBadge(
            text = leftLabel,
            container = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).graphicsLayer {
                alpha = (-offsetX.value / thresholdPx).coerceIn(0f, 1f)
                rotationZ = 8f
            },
        )
    }
}

@Composable
private fun SwipeBadge(text: String, container: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(Radius.md), color = container) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.material3.contentColorFor(container),
        )
    }
}
