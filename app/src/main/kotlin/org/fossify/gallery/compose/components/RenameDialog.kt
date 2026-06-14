package org.fossify.gallery.compose.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.mediaDB
import java.io.File

enum class RenameMode { PREFIX, SUFFIX, NEW_NAME }

@Composable
fun RenameDialog(
    paths: List<String>,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(0) } // 0=prefix, 1=suffix, 2=new
    var text by remember { mutableStateOf("") }
    var counter by remember { mutableIntStateOf(1) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${paths.size} Dateien umbenennen") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Surface(onClick = { mode = 0 }, color = androidx.compose.ui.graphics.Color.Transparent) {
                        Row { RadioButton(selected = mode == 0, onClick = { mode = 0 }); Text("Präfix", Modifier.width(70.dp)) }
                    }
                    Surface(onClick = { mode = 1 }, color = androidx.compose.ui.graphics.Color.Transparent) {
                        Row { RadioButton(selected = mode == 1, onClick = { mode = 1 }); Text("Suffix", Modifier.width(70.dp)) }
                    }
                    Surface(onClick = { mode = 2 }, color = androidx.compose.ui.graphics.Color.Transparent) {
                        Row { RadioButton(selected = mode == 2, onClick = { mode = 2 }); Text("Neuer Name") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(when (mode) { 0 -> "Präfix"; 1 -> "Suffix (vor Dateiendung)"; 2 -> "Neuer Name"; else -> "" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    shape = RoundedCornerShape(12.dp),
                )
                if (mode in listOf(0, 2)) {
                    Spacer(Modifier.height(4.dp))
                    val preview1 = generatePreview(paths.firstOrNull() ?: "", text, mode, counter)
                    val preview2 = generatePreview(paths.lastOrNull() ?: "", text, mode, counter + paths.size - 1)
                    Text("$counter–${counter+paths.size-1}: $preview1 … $preview2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) return@TextButton
                scope.launch(Dispatchers.IO) {
                    var renamed = 0
                    val db = ctx.mediaDB
                    paths.forEachIndexed { idx, path ->
                        val file = File(path)
                        if (!file.exists()) return@forEachIndexed
                        val ext = file.extension
                        val newName = when (mode) {
                            0 -> "${text}${file.name}"
                            1 -> "${file.nameWithoutExtension}${text}.${ext}"
                            2 -> "${text}_${counter + idx}.${ext}"
                            else -> file.name
                        }
                        val newFile = File(file.parent, newName)
                        if (file.renameTo(newFile)) {
                            try { db.updateMedium(path, file.parent ?: "", newName, newFile.absolutePath) } catch (_: Exception) { }
                            renamed++
                        }
                    }
                    withContext(Dispatchers.Main) {
                        ctx.toast("$renamed Dateien umbenannt", Toast.LENGTH_SHORT)
                        onDismiss()
                    }
                }
            }) { Text("Umbenennen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

private fun generatePreview(path: String, text: String, mode: Int, counter: Int): String {
    val file = File(path)
    val ext = file.extension
    return when (mode) {
        0 -> "${text}${file.name}"
        1 -> "${file.nameWithoutExtension}${text}.${ext}"
        2 -> "${text}_${counter}.${ext}"
        else -> file.name
    }
}
