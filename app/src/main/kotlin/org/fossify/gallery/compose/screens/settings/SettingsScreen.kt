package org.fossify.gallery.compose.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import org.fossify.gallery.workers.MetadataSyncWorker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.gallery.activities.IncludedFoldersActivity
import org.fossify.gallery.activities.ExcludedFoldersActivity
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.extensions.collectionDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.getFavoriteFromPath
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.BOTTOM_ACTION_CHANGE_ORIENTATION
import org.fossify.gallery.helpers.BOTTOM_ACTION_COPY
import org.fossify.gallery.helpers.BOTTOM_ACTION_DELETE
import org.fossify.gallery.helpers.BOTTOM_ACTION_EDIT
import org.fossify.gallery.helpers.BOTTOM_ACTION_MOVE
import org.fossify.gallery.helpers.BOTTOM_ACTION_PROPERTIES
import org.fossify.gallery.helpers.BOTTOM_ACTION_RATING
import org.fossify.gallery.helpers.BOTTOM_ACTION_RENAME
import org.fossify.gallery.helpers.BOTTOM_ACTION_RESIZE
import org.fossify.gallery.helpers.BOTTOM_ACTION_ROTATE
import org.fossify.gallery.helpers.BOTTOM_ACTION_SET_AS
import org.fossify.gallery.helpers.BOTTOM_ACTION_SHARE
import org.fossify.gallery.helpers.BOTTOM_ACTION_SHOW_ON_MAP
import org.fossify.gallery.helpers.BOTTOM_ACTION_SLIDESHOW
import org.fossify.gallery.helpers.BOTTOM_ACTION_TOGGLE_FAVORITE
import org.fossify.gallery.helpers.BOTTOM_ACTION_TOGGLE_VISIBILITY
import org.fossify.gallery.helpers.EXT_CAMERA_MODEL
import org.fossify.gallery.helpers.EXT_DATE_TAKEN
import org.fossify.gallery.helpers.EXT_EXIF_PROPERTIES
import org.fossify.gallery.helpers.EXT_GPS
import org.fossify.gallery.helpers.EXT_LAST_MODIFIED
import org.fossify.gallery.helpers.EXT_NAME
import org.fossify.gallery.helpers.EXT_PATH
import org.fossify.gallery.helpers.EXT_RESOLUTION
import org.fossify.gallery.helpers.EXT_SIZE
import org.fossify.gallery.helpers.ROTATE_BY_ASPECT_RATIO
import org.fossify.gallery.helpers.ROTATE_BY_DEVICE_ROTATION
import org.fossify.gallery.helpers.ROTATE_BY_SYSTEM_SETTING
import org.fossify.gallery.models.MediaCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigateToAbout: () -> Unit = {}) {
    val ctx = LocalContext.current
    val a = ctx as? Activity
    val conf = ctx.config
    val scope = rememberCoroutineScope()
    var showScanDialog by remember { mutableStateOf(false) }
    var showExtendedDialog by remember { mutableStateOf(false) }
    var showBottomActionsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Einstellungen", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {

            SectionLabel("Allgemein")
            SettingsSwitch("Dunkelmodus erzwingen", conf.forceDarkMode) { conf.forceDarkMode = it }
            SettingsSwitch("Versteckte Dateien anzeigen", conf.showHiddenMedia) { conf.showHiddenMedia = it }
            SettingsSwitch("Animierte GIFs abspielen", conf.animateGifs) { conf.animateGifs = it }
            SettingsSwitch("Maximale Helligkeit", conf.maxBrightness) { conf.maxBrightness = it }
            SettingsSwitch("Standardmäßig Dateisuche", conf.searchAllFilesByDefault) { conf.searchAllFilesByDefault = it }
            SettingsSwitch("Horizontal scrollen", conf.scrollHorizontally) { conf.scrollHorizontally = it }
            SettingsSwitch("Pull to Refresh", conf.enablePullToRefresh) { conf.enablePullToRefresh = it }
            SettingsNav("Ordnertyp", getViewTypeLabel(conf.viewTypeFolders)) { conf.viewTypeFolders = if (conf.viewTypeFolders == VIEW_TYPE_GRID) VIEW_TYPE_LIST else VIEW_TYPE_GRID }
            SettingsNav("Eingeschlossene Ordner") { a?.startActivity(Intent(a, IncludedFoldersActivity::class.java)) }
            SettingsNav("Ausgeschlossene Ordner") { a?.startActivity(Intent(a, ExcludedFoldersActivity::class.java)) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Video")
            SettingsSwitch("Automatisch abspielen", conf.autoplayVideos) { conf.autoplayVideos = it }
            SettingsSwitch("Videos wiederholen", conf.loopVideos) { conf.loopVideos = it }
            SettingsSwitch("Video stumm schalten", conf.muteVideos) { conf.muteVideos = it }
            SettingsSwitch("Getrennter Videoplayer", conf.gestureVideoPlayer) { conf.gestureVideoPlayer = it }
            SettingsSwitch("Position merken", conf.rememberLastVideoPosition) { conf.rememberLastVideoPosition = it }
            SettingsSwitch("Video-Gesten", conf.allowVideoGestures) { conf.allowVideoGestures = it }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Vollbild & Anzeige")
            SettingsSwitch("Schwarzer Hintergrund", conf.blackBackground) { conf.blackBackground = it }
            SettingsSwitch("System-UI ausblenden", conf.hideSystemUI) { conf.hideSystemUI = it }
            SettingsSwitch("Bildschirm an lassen", conf.keepScreenOn) { conf.keepScreenOn = it }
            SettingsSwitch("Notch zeigen", conf.showNotch) { conf.showNotch = it }
            SettingsSwitch("Wischen zum Schließen", conf.allowDownGesture) { conf.allowDownGesture = it }
            SettingsSwitch("Sofort wechseln", conf.allowInstantChange) { conf.allowInstantChange = it }
            SettingsNav("Bildschirmrotation", getRotationLabel(conf.screenRotation)) {
                conf.screenRotation = when (conf.screenRotation) {
                    ROTATE_BY_SYSTEM_SETTING -> ROTATE_BY_DEVICE_ROTATION
                    ROTATE_BY_DEVICE_ROTATION -> ROTATE_BY_ASPECT_RATIO
                    else -> ROTATE_BY_SYSTEM_SETTING
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Thumbnails")
            SettingsSwitch("Thumbnails zuschneiden", conf.cropThumbnails) { conf.cropThumbnails = it }
            SettingsSwitch("Video-Dauer anzeigen", conf.showThumbnailVideoDuration) { conf.showThumbnailVideoDuration = it }
            SettingsSwitch("Dateityp anzeigen", conf.showThumbnailFileTypes) { conf.showThumbnailFileTypes = it }
            SettingsSwitch("Favoriten markieren", conf.markFavoriteItems) { conf.markFavoriteItems = it }
            SettingsSwitch("Bewertung anzeigen", conf.showRatingOnThumbnails) { conf.showRatingOnThumbnails = it }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Papierkorb")
            SettingsSwitch("In Papierkorb verschieben", conf.useRecycleBin) { conf.useRecycleBin = it }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Dateien")
            SettingsSwitch("Leere Ordner löschen", conf.deleteEmptyFolders) { conf.deleteEmptyFolders = it }
            SettingsSwitch("Letzte Änderung bewahren", conf.keepLastModified) { conf.keepLastModified = it }
            SettingsSwitch("Löschbestätigung überspringen", conf.skipDeleteConfirmation) { conf.skipDeleteConfirmation = it }
            SettingsNav("Dateien laden", getFileLoadingLabel(conf.fileLoadingPriority)) {
                conf.fileLoadingPriority = (conf.fileLoadingPriority + 1) % 3
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Erweiterte Details")
            SettingsSwitch("Details anzeigen", conf.showExtendedDetails) { conf.showExtendedDetails = it }
            SettingsSwitch("Details ausblenden (Vollbild)", conf.hideExtendedDetails) { conf.hideExtendedDetails = it }
            SettingsNav("Detail-Elemente verwalten", getExtendedDetailsSummary(conf.extendedDetails)) { showExtendedDialog = true }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Bildschirmaktionen")
            SettingsSwitch("Aktionen anzeigen", conf.bottomActions) { conf.bottomActions = it }
            SettingsNav("Aktionen verwalten", getBottomActionsSummary(conf.visibleBottomActions)) { showBottomActionsDialog = true }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Sicherheit")
            SecurityNav("App-Passwortschutz", conf.isAppPasswordProtectionOn) {
                SecurityDialog(a!!, conf.appPasswordHash, if (conf.isAppPasswordProtectionOn) conf.appProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                    if (success) {
                        val wasOn = conf.isAppPasswordProtectionOn
                        conf.isAppPasswordProtectionOn = !wasOn
                        conf.appPasswordHash = if (wasOn) "" else hash
                        conf.appProtectionType = type
                    }
                }
            }
            SecurityNav("Versteckte Passwort", conf.isHiddenPasswordProtectionOn) {
                SecurityDialog(a!!, conf.hiddenPasswordHash, if (conf.isHiddenPasswordProtectionOn) conf.hiddenProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                    if (success) {
                        val wasOn = conf.isHiddenPasswordProtectionOn
                        conf.isHiddenPasswordProtectionOn = !wasOn
                        conf.hiddenPasswordHash = if (wasOn) "" else hash
                        conf.hiddenProtectionType = type
                    }
                }
            }
            SecurityNav("Ausgeschlossene Passwort", conf.isExcludedPasswordProtectionOn) {
                SecurityDialog(a!!, conf.excludedPasswordHash, if (conf.isExcludedPasswordProtectionOn) conf.excludedProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                    if (success) {
                        val wasOn = conf.isExcludedPasswordProtectionOn
                        conf.isExcludedPasswordProtectionOn = !wasOn
                        conf.excludedPasswordHash = if (wasOn) "" else hash
                        conf.excludedProtectionType = type
                    }
                }
            }
            SecurityNav("Löschen Passwortschutz", conf.isDeletePasswordProtectionOn) {
                SecurityDialog(a!!, conf.deletePasswordHash, if (conf.isDeletePasswordProtectionOn) conf.deleteProtectionType else SHOW_ALL_TABS) { hash, type, success ->
                    if (success) {
                        val wasOn = conf.isDeletePasswordProtectionOn
                        conf.isDeletePasswordProtectionOn = !wasOn
                        conf.deletePasswordHash = if (wasOn) "" else hash
                        conf.deleteProtectionType = type
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Tags & Bewertungen")
            SettingsNav("Tags & Bewertungen aus Dateien lesen") { MetadataSyncWorker.scheduleFullScan(ctx); Toast.makeText(ctx, "Scan gestartet – Fortschritt in der Benachrichtigung", Toast.LENGTH_SHORT).show() }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Daten verwalten")
            FavoritesExportNav(ctx, conf, scope)
            FavoritesImportNav(ctx, conf, scope)
            SettingsNav("Einstellungen exportieren") { a?.startActivity(Intent(a, SettingsActivity::class.java).putExtra("open_section", "export_settings")) }
            SettingsNav("Einstellungen importieren") { a?.startActivity(Intent(a, SettingsActivity::class.java).putExtra("open_section", "import_settings")) }
            ClearCacheNav(ctx, scope)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SectionLabel("Information")
            SettingsNav("Über diese App", onClick = onNavigateToAbout)

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("Scanne...") },
            text = { Text("Durchsuche Dateien nach Tags und Bewertungen") },
            confirmButton = { TextButton(onClick = { showScanDialog = false }) { Text("Abbrechen") } }
        )
    }

    if (showExtendedDialog) {
        ExtendedDetailsDialog(
            current = conf.extendedDetails,
            onDismiss = { showExtendedDialog = false },
            onSave = { conf.extendedDetails = it }
        )
    }

    if (showBottomActionsDialog) {
        BottomActionsDialog(
            current = conf.visibleBottomActions,
            onDismiss = { showBottomActionsDialog = false },
            onSave = { conf.visibleBottomActions = it }
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
internal fun SettingsSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    var internalChecked by remember(checked) { mutableStateOf(checked) }
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = internalChecked, onCheckedChange = { internalChecked = it; onChange(it) })
        }
    }
}

@Composable
internal fun SettingsNav(label: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsNav(label: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SecurityNav(label: String, enabled: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick), shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(if (enabled) "Ein" else "Aus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ExtendedDetailsDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var result by remember { mutableStateOf(current) }
    val items = listOf(
        EXT_NAME to "Name", EXT_PATH to "Pfad", EXT_SIZE to "Größe",
        EXT_RESOLUTION to "Auflösung", EXT_LAST_MODIFIED to "Letzte Änderung",
        EXT_DATE_TAKEN to "Aufnahmedatum", EXT_CAMERA_MODEL to "Kamera",
        EXT_EXIF_PROPERTIES to "EXIF", EXT_GPS to "GPS"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detail-Elemente") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                items.forEach { (flag, label) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = result and flag != 0, onCheckedChange = { if (it) result += flag else result -= flag })
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { if (result and flag == 0) result += flag else result -= flag })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(result); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
internal fun BottomActionsDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var result by remember { mutableStateOf(current) }
    val items = listOf(
        BOTTOM_ACTION_TOGGLE_FAVORITE to "Favorit", BOTTOM_ACTION_EDIT to "Bearbeiten",
        BOTTOM_ACTION_SHARE to "Teilen", BOTTOM_ACTION_DELETE to "Löschen",
        BOTTOM_ACTION_ROTATE to "Drehen", BOTTOM_ACTION_PROPERTIES to "Eigenschaften",
        BOTTOM_ACTION_CHANGE_ORIENTATION to "Ausrichtung", BOTTOM_ACTION_SLIDESHOW to "Diashow",
        BOTTOM_ACTION_SHOW_ON_MAP to "Karte", BOTTOM_ACTION_TOGGLE_VISIBILITY to "Sichtbarkeit",
        BOTTOM_ACTION_RENAME to "Umbenennen", BOTTOM_ACTION_SET_AS to "Setzen als",
        BOTTOM_ACTION_COPY to "Kopieren", BOTTOM_ACTION_MOVE to "Verschieben",
        BOTTOM_ACTION_RESIZE to "Größe ändern", BOTTOM_ACTION_RATING to "Bewertung"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bildschirmaktionen") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                items.forEach { (flag, label) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = result and flag != 0, onCheckedChange = { if (it) result += flag else result -= flag })
                        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { if (result and flag == 0) result += flag else result -= flag })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(result); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
internal fun ClearCacheNav(ctx: Context, scope: CoroutineScope) {
    val a = ctx as? Activity
    var cacheSize by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { cacheSize = withContext(Dispatchers.IO) { a?.cacheDir?.let { dir -> if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }.let { bytes -> if (bytes > 1_000_000) "${bytes / 1_000_000} MB" else "${bytes / 1_000} KB" } else "" } ?: "" } }
    SettingsNav("Cache leeren", cacheSize.ifEmpty { "0 KB" }) {
        scope.launch(Dispatchers.IO) { a?.cacheDir?.deleteRecursively(); withContext(Dispatchers.Main) { cacheSize = "0 KB" } }
    }
}

@Composable
internal fun FavoritesExportNav(ctx: Context, conf: org.fossify.gallery.helpers.Config, scope: CoroutineScope) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                val paths = ctx.favoritesDB.getValidFavoritePaths()
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w -> paths.forEach { w.write("$it\n") } }
                withContext(Dispatchers.Main) { Toast.makeText(ctx, "${paths.size} Favoriten exportiert", Toast.LENGTH_SHORT).show() }
            } catch (_: Exception) { }
        }
    }
    SettingsNav("Favoriten exportieren") { exportLauncher.launch("gallery-favorites_${java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US).format(java.util.Date())}.txt") }
}

@Composable
internal fun FavoritesImportNav(ctx: Context, conf: org.fossify.gallery.helpers.Config, scope: CoroutineScope) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                val lines = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines() ?: emptyList()
                var imported = 0
                lines.forEach { line ->
                    if (java.io.File(line).exists()) { ctx.favoritesDB.insert(ctx.getFavoriteFromPath(line)); imported++ }
                }
                withContext(Dispatchers.Main) { Toast.makeText(ctx, "$imported Favoriten importiert", Toast.LENGTH_SHORT).show() }
            } catch (_: Exception) { }
        }
    }
    SettingsNav("Favoriten importieren") { importLauncher.launch(arrayOf("text/plain")) }
}

internal fun startTagScan(ctx: Context, scope: CoroutineScope, showDialog: (Boolean) -> Unit) {
    showDialog(true)
    scope.launch(Dispatchers.IO) {
        var foundTags = 0; var foundRatings = 0; var total = 0
        val batch = mutableListOf<MediaCache>()
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val proj = arrayOf(MediaStore.MediaColumns.DATA)
            val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            ctx.contentResolver.query(uri, proj, sel, args, null)?.use { c ->
                val col = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                while (c.moveToNext()) {
                    total++; val p = c.getString(col) ?: continue
                    try {
                        val xmp = org.fossify.gallery.helpers.XmpWriter.read(p)
                        if (xmp.tags.isNotEmpty()) foundTags++
                        if (xmp.rating > 0) foundRatings++
                        batch.add(MediaCache(fullPath = p, tags = xmp.tags.joinToString(","), rating = xmp.rating, lastScanned = System.currentTimeMillis()))
                        if (xmp.rating > 0) try { ctx.mediaDB.updateRating(p, xmp.rating) } catch (_: Exception) { }
                        if (batch.size >= 500) { ctx.mediaCacheDB.upsertAll(batch); batch.clear() }
                    } catch (_: Exception) { }
                }
            }
            if (batch.isNotEmpty()) ctx.mediaCacheDB.upsertAll(batch)
        } catch (_: Exception) { }
        withContext(Dispatchers.Main) {
            showDialog(false)
            Toast.makeText(ctx, "$total Dateien: $foundTags mit Tags, $foundRatings bewertet", Toast.LENGTH_LONG).show()
        }
    }
}

internal fun getViewTypeLabel(viewType: Int): String = when {
    viewType == VIEW_TYPE_GRID -> "Kacheln"
    viewType == 2 -> "Mosaik"
    else -> "Liste"
}
internal fun getRotationLabel(rotation: Int): String = when (rotation) {
    ROTATE_BY_SYSTEM_SETTING -> "Systemeinstellung"
    ROTATE_BY_DEVICE_ROTATION -> "Geräterotation"
    ROTATE_BY_ASPECT_RATIO -> "Seitenverhältnis"
    else -> "Systemeinstellung"
}
internal fun getFileLoadingLabel(priority: Int): String = when (priority) {
    0 -> "Geschwindigkeit"
    1 -> "Kompromiss"
    else -> "Gültigkeit"
}

internal fun getBottomActionsSummary(actions: Int): String {
    val labels = mutableListOf<String>()
    if (actions and BOTTOM_ACTION_SHARE != 0) labels.add("Teilen")
    if (actions and BOTTOM_ACTION_DELETE != 0) labels.add("Löschen")
    if (actions and BOTTOM_ACTION_EDIT != 0) labels.add("Bearbeiten")
    if (actions and BOTTOM_ACTION_ROTATE != 0) labels.add("Drehen")
    if (actions and BOTTOM_ACTION_TOGGLE_FAVORITE != 0) labels.add("Favorit")
    if (actions and BOTTOM_ACTION_RATING != 0) labels.add("Bewertung")
    return if (labels.isEmpty()) "Keine" else labels.take(3).joinToString(", ") + if (labels.size > 3) "..." else ""
}

internal fun getExtendedDetailsSummary(details: Int): String {
    val labels = mutableListOf<String>()
    if (details and EXT_NAME != 0) labels.add("Name")
    if (details and EXT_PATH != 0) labels.add("Pfad")
    if (details and EXT_SIZE != 0) labels.add("Größe")
    if (details and EXT_RESOLUTION != 0) labels.add("Auflösung")
    if (details and EXT_LAST_MODIFIED != 0) labels.add("Datum")
    if (details and EXT_DATE_TAKEN != 0) labels.add("Aufnahme")
    if (details and EXT_CAMERA_MODEL != 0) labels.add("Kamera")
    if (details and EXT_EXIF_PROPERTIES != 0) labels.add("EXIF")
    if (details and EXT_GPS != 0) labels.add("GPS")
    return if (labels.isEmpty()) "Standard" else labels.take(3).joinToString(", ") + if (labels.size > 3) "..." else ""
}
