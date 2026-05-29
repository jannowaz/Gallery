package org.fossify.gallery.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.models.MediaCache

class ComposeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GalleryTheme { SettingsScreen(onBack = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val conf = ctx.config
    val scope = rememberCoroutineScope()
    var showScanDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Einstellungen", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zuruck") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {

            SectionLabel("Allgemein")
            SettingsSwitch("Dunkelmodus erzwingen", conf.forceDarkMode) { conf.forceDarkMode = it }
            SettingsSwitch("Versteckte Dateien anzeigen", conf.shouldShowHidden) { conf.shouldShowHidden = it }
            SettingsSwitch("Animierte GIFs abspielen", conf.animateGifs) { conf.animateGifs = it }
            SettingsSwitch("Maximale Helligkeit", conf.maxBrightness) { conf.maxBrightness = it }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Video")
            SettingsSwitch("Automatisch abspielen", conf.autoplayVideos) { conf.autoplayVideos = it }
            SettingsSwitch("Videos wiederholen", conf.loopVideos) { conf.loopVideos = it }
            SettingsSwitch("Video stumm schalten", conf.muteVideos) { conf.muteVideos = it }
            SettingsSwitch("Getrennter Videoplayer", conf.gestureVideoPlayer) { conf.gestureVideoPlayer = it }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Thumbnails")
            SettingsSwitch("Thumbnails zuschneiden", conf.cropThumbnails) { conf.cropThumbnails = it }
            SettingsSwitch("Video-Dauer anzeigen", conf.showThumbnailVideoDuration) { conf.showThumbnailVideoDuration = it }
            SettingsSwitch("Dateityp anzeigen", conf.showThumbnailFileTypes) { conf.showThumbnailFileTypes = it }
            SettingsSwitch("Favoriten markieren", conf.markFavoriteItems) { conf.markFavoriteItems = it }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Papierkorb")
            SettingsSwitch("In Papierkorb verschieben", conf.useRecycleBin) { conf.useRecycleBin = it }
            SettingsSwitch("Bei Ordnern anzeigen", conf.showRecycleBinAtFolders) { conf.showRecycleBinAtFolders = it }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Erweiterte Einstellungen")
            SettingsSwitch("Schwarzer Hintergrund", conf.blackBackground) { conf.blackBackground = it }
            SettingsSwitch("Dateinamen anzeigen", conf.displayFileNames) { conf.displayFileNames = it }
            SettingsSwitch("Bildschirmrotation", conf.screenRotation == 1) { conf.screenRotation = if (it) 1 else 0 }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Tags & Bewertungen")
            SettingsNav("Tags & Bewertungen aus Dateien lesen") {
                showScanDialog = true
                scope.launch(Dispatchers.IO) {
                    var foundTags = 0; var foundRatings = 0; var total = 0; var totalAll = 0
                    val batch = mutableListOf<MediaCache>()
                    try {
                        val uri = MediaStore.Files.getContentUri("external")
                        val proj = arrayOf(MediaStore.MediaColumns.DATA)
                        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
                        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
                        ctx.contentResolver.query(uri, proj, sel, args, null)?.use { c ->
                            totalAll = c.count
                            val col = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                            while (c.moveToNext()) {
                                total++; val p = c.getString(col) ?: continue
                                try {
                                    val tags = org.fossify.gallery.helpers.TagWriter.readTags(p)
                                    val r = org.fossify.gallery.helpers.TagWriter.readRatingFromXmp(p)
                                    if (tags.isNotEmpty()) foundTags++
                                    if (r > 0) foundRatings++
                                    batch.add(MediaCache(fullPath = p, tags = tags.joinToString(","), rating = r, lastScanned = System.currentTimeMillis()))
                                    if (batch.size >= 500) { ctx.mediaCacheDB.upsertAll(batch); batch.clear() }
                                } catch (_: Exception) { }
                            }
                        }
                        if (batch.isNotEmpty()) ctx.mediaCacheDB.upsertAll(batch)
                    } catch (_: Exception) { }
                    withContext(Dispatchers.Main) {
                        showScanDialog = false
                        Toast.makeText(ctx, "$total Dateien: $foundTags mit Tags, $foundRatings bewertet", Toast.LENGTH_LONG).show()
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            SectionLabel("Information")
            SettingsNav("Uber diese App") {
                ctx.startActivity(Intent(ctx, ComposeAboutActivity::class.java))
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showScanDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { },
            title = { Text("Scanne...") },
            text = { Text("Durchsuche Dateien nach Tags und Bewertungen") },
            confirmButton = { }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    var internalChecked by remember { mutableStateOf(checked) }
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = internalChecked, onCheckedChange = { internalChecked = it; onChange(it) })
        }
    }
}

@Composable
private fun SettingsNav(label: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}