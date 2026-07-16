package org.fossify.gallery.compose.screens
import org.fossify.gallery.compose.theme.Radius

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.components.EmptyState
import org.fossify.gallery.compose.components.ConfirmDestructive
import org.fossify.gallery.compose.theme.LocalMediaRepository
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.util.rememberMediaStoreConsent
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.models.Medium
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = LocalMediaRepository.current
    val scope = rememberCoroutineScope()
    val consent = rememberMediaStoreConsent()
    var items by remember { mutableStateOf<List<Medium>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Medium?>(null) }
    // Non-null while a permanent delete runs (done/total) - drives the progress bar and disables
    // every delete entry point. Emptying a large bin takes real time; without this the screen
    // looked completely frozen ("nothing happens") until everything was gone.
    var deleteProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(refresh) {
        items = withContext(Dispatchers.IO) { repo.getDeletedMedia() }
    }

    // Permanently deletes the given paths, asking for the OS delete-consent dialog so the files are
    // actually removed and storage freed (raw File.delete is blocked under scoped storage). The
    // list is refreshed only after the operation completes (avoids the stale-read race).
    fun permanentlyDelete(paths: List<String>) {
        if (paths.isEmpty() || deleteProgress != null) return
        scope.launch {
            val uris = withContext(Dispatchers.IO) { MediaStoreOps.urisForPaths(ctx, paths).map { it.second } }
            if (uris.isNotEmpty()) {
                val granted = try { consent.request(MediaStoreOps.deleteRequest(ctx, uris)) } catch (_: Exception) { false }
                if (!granted) { ctx.toast(ctx.getString(R.string.cancelled)); return@launch }
            }
            deleteProgress = 0 to paths.size
            val failed = withContext(Dispatchers.IO) {
                repo.deleteMediaBatch(paths) { done, total -> deleteProgress = done to total }
            }
            deleteProgress = null
            refresh++
            if (failed > 0) {
                ctx.toast(ctx.resources.getQuantityString(R.plurals.delete_failed_count, failed, failed))
            } else {
                ctx.toast(ctx.resources.getQuantityString(R.plurals.deleted_forever_count, paths.size, paths.size))
            }
        }
    }

    BackHandler(enabled = showEmptyConfirm || pendingDelete != null) { showEmptyConfirm = false; pendingDelete = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_recycle_bin), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    if (items.isNotEmpty()) TextButton(onClick = { showEmptyConfirm = true }, enabled = deleteProgress == null) { Text(stringResource(R.string.action_empty), color = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(Icons.Default.DeleteForever, stringResource(R.string.recycle_bin_empty), modifier = Modifier.padding(padding))
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
            deleteProgress?.let { (done, total) ->
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (total > 0) done / total.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.deleting_forever_progress, done, total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                items(items, key = { it.path }) { m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(Radius.sm))) {
                            GalleryImage(path = m.path, contentDescription = m.name, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(File(m.path).parent ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            scope.launch { withContext(Dispatchers.IO) { repo.restoreFromRecycleBin(m.path) }; RefreshBus.trigger(); refresh++ }
                        }, enabled = deleteProgress == null) { Icon(Icons.Default.Restore, stringResource(R.string.action_restore), tint = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = {
                            pendingDelete = m
                        }, enabled = deleteProgress == null) { Icon(Icons.Default.DeleteForever, stringResource(R.string.action_delete_forever), tint = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
            }
        }
    }

    if (showEmptyConfirm) {
        val count = items.size
        ConfirmDestructive(
            title = stringResource(R.string.empty_recycle_bin_title),
            text = stringResource(R.string.empty_recycle_bin_confirm, count),
            confirmLabel = stringResource(R.string.action_delete_forever),
            onConfirm = {
                showEmptyConfirm = false
                permanentlyDelete(items.map { it.path })
            },
            onDismiss = { showEmptyConfirm = false },
        )
    }

    pendingDelete?.let { m ->
        ConfirmDestructive(
            title = stringResource(R.string.action_delete_forever),
            text = stringResource(R.string.delete_forever_single_confirm, m.name),
            confirmLabel = stringResource(R.string.action_delete_forever),
            onConfirm = {
                pendingDelete = null
                permanentlyDelete(listOf(m.path))
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
