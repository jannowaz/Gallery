package org.fossify.gallery.compose.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.util.rememberMediaStoreConsent
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.helpers.RefreshBus
import java.io.File

@Composable
fun RenameDialog(paths: List<String>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var counter by remember { mutableIntStateOf(1) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val consent = rememberMediaStoreConsent()
    val modes = listOf("Präfix" to "Vor den Dateinamen", "Suffix" to "Vor die Dateiendung", "Neuer Name" to "Mit Nummerierung")

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${paths.size} Dateien umbenennen") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    modes.forEachIndexed { idx, (title, _) ->
                        Surface(
                            onClick = { mode = idx },
                            shape = RoundedCornerShape(8.dp),
                            color = if (mode == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(title, modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = if (mode == idx) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(when (mode) { 0 -> "z.B. Urlaub_"; 1 -> "_edited"; 2 -> "z.B. Foto"; else -> "" }, style = MaterialTheme.typography.bodySmall) },
                    label = { Text(modes[mode].second) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                )
                if (mode == 2) {
                    Spacer(Modifier.height(4.dp))
                    Text("Start-Nummer: $counter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                if (text.isNotBlank()) {
                    val preview1 = generatePreview(paths.firstOrNull() ?: "", text, mode, counter)
                    val preview2 = generatePreview(paths.lastOrNull() ?: "", text, mode, counter + paths.size - 1)
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp)) {
                            Text("${File(paths.firstOrNull() ?: "").name} → $preview1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${File(paths.lastOrNull() ?: "").name} → $preview2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$counter–${counter + paths.size - 1} von ${paths.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) return@TextButton
                scope.launch {
                    val db = ctx.mediaDB
                    data class Job(val path: String, val uri: android.net.Uri, val newName: String)
                    val jobs = withContext(Dispatchers.IO) {
                        paths.mapIndexedNotNull { idx, path ->
                            val file = File(path)
                            val ext = file.extension
                            val newName = when (mode) {
                                0 -> "$text${file.name}"
                                1 -> "${file.nameWithoutExtension}$text.$ext"
                                2 -> "${text}_${counter + idx}.$ext"
                                else -> file.name
                            }
                            val uri = MediaStoreOps.uriForPath(ctx, path) ?: return@mapIndexedNotNull null
                            Job(path, uri, newName)
                        }
                    }
                    if (jobs.isEmpty()) {
                        ctx.toast("Keine Dateien gefunden", Toast.LENGTH_SHORT); onDismiss(); return@launch
                    }
                    val granted = try {
                        consent.request(MediaStoreOps.writeRequest(ctx, jobs.map { it.uri }))
                    } catch (_: Exception) { false }
                    if (!granted) { ctx.toast("Abgebrochen", Toast.LENGTH_SHORT); return@launch }
                    val renamed = withContext(Dispatchers.IO) {
                        var n = 0
                        jobs.forEach { job ->
                            if (MediaStoreOps.rename(ctx, job.uri, job.newName)) {
                                val parent = File(job.path).parent ?: ""
                                val newPath = File(parent, job.newName).absolutePath
                                try { db.updateMedium(job.path, parent, job.newName, newPath) } catch (_: Exception) { }
                                n++
                            }
                        }
                        n
                    }
                    RefreshBus.trigger()
                    ctx.toast("$renamed Dateien umbenannt", Toast.LENGTH_SHORT)
                    onDismiss()
                }
            }) { Text("Umbenennen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

private fun generatePreview(path: String, text: String, mode: Int, counter: Int): String {
    val file = File(path)
    return when (mode) {
        0 -> "$text${file.name}"
        1 -> "${file.nameWithoutExtension}$text.${file.extension}"
        2 -> "${text}_$counter.${file.extension}"
        else -> file.name
    }
}
