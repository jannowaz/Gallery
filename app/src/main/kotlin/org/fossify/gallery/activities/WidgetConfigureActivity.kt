package org.fossify.gallery.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.setBackgroundColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.compose.components.GalleryImage
import org.fossify.gallery.compose.screens.FolderPathPickerSheet
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.getFolderNameFromPath
import org.fossify.gallery.extensions.widgetsDB
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.MyWidgetProvider
import org.fossify.gallery.models.Widget

class WidgetConfigureActivity : ComponentActivity() {
    private var mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (config.isAppPasswordProtectionOn || config.blurAllMedia) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        setResult(RESULT_CANCELED)

        mWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repo = MediaRepository(applicationContext)
        setContent {
            GalleryTheme {
                AppProviders(repo) {
                    WidgetConfigureScreen(
                        onCancel = { finish() },
                        onSave = { folderPath, bgColor, textColor, showFolderName ->
                            saveConfig(folderPath, bgColor, textColor, showFolderName)
                        },
                    )
                }
            }
        }
    }

    private fun saveConfig(folderPath: String, bgColor: Int, textColor: Int, showFolderName: Boolean) {
        val views = RemoteViews(packageName, R.layout.widget)
        views.setBackgroundColor(R.id.widget_holder, bgColor)
        AppWidgetManager.getInstance(this)?.updateAppWidget(mWidgetId, views) ?: return

        config.apply {
            showWidgetFolderName = showFolderName
            widgetBgColor = bgColor
            widgetTextColor = textColor
        }

        val widget = Widget(null, mWidgetId, folderPath)
        ensureBackgroundThread {
            widgetsDB.insertOrUpdate(widget)
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, MyWidgetProvider::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(mWidgetId))
                sendBroadcast(this)
            }
        }

        Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }
}

private val WIDGET_BG_PRESETS = listOf(
    0xFF000000.toInt(),
    0xFF1E1E1E.toInt(),
    0xFF263238.toInt(),
    0xFF1B5E20.toInt(),
    0xFF0D47A1.toInt(),
    0xFF4A148C.toInt(),
    0xFFFFFFFF.toInt(),
)

private val WIDGET_TEXT_PRESETS = listOf(
    0xFFFFFFFF.toInt(),
    0xFFE0E0E0.toInt(),
    0xFFFFEB3B.toInt(),
    0xFF81C784.toInt(),
    0xFF64B5F6.toInt(),
    0xFF1E1E1E.toInt(),
    0xFF000000.toInt(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigureScreen(
    onCancel: () -> Unit,
    onSave: (folderPath: String, bgColor: Int, textColor: Int, showFolderName: Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val conf = ctx.config

    val initialBg = conf.widgetBgColor
    var bgRgb by remember {
        mutableIntStateOf(AndroidColor.rgb(AndroidColor.red(initialBg), AndroidColor.green(initialBg), AndroidColor.blue(initialBg)))
    }
    var bgAlpha by remember { mutableFloatStateOf(AndroidColor.alpha(initialBg) / 255f) }
    var textColor by remember { mutableIntStateOf(conf.widgetTextColor) }
    var showFolderName by remember { mutableStateOf(conf.showWidgetFolderName) }
    var folderPath by remember { mutableStateOf("") }
    var thumbnailPath by remember { mutableStateOf<String?>(null) }
    var showFolderPicker by remember { mutableStateOf(false) }

    val effectiveBgColor = bgRgb.adjustAlpha(bgAlpha)

    LaunchedEffect(Unit) {
        val firstDir = withContext(Dispatchers.IO) {
            runCatching { ctx.directoryDB.getAll().firstOrNull() }.getOrNull()
        }
        if (firstDir != null && folderPath.isEmpty()) {
            folderPath = firstDir.path
            thumbnailPath = firstDir.tmb.ifBlank { null }
        }
    }

    LaunchedEffect(folderPath) {
        if (folderPath.isNotEmpty()) {
            thumbnailPath = withContext(Dispatchers.IO) {
                runCatching { ctx.directoryDB.getDirectoryThumbnail(folderPath) }.getOrNull()
            }
        }
    }

    val folderName = remember(folderPath) {
        if (folderPath.isEmpty()) "" else ctx.getFolderNameFromPath(folderPath)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(org.fossify.commons.R.string.folder), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = { onSave(folderPath, effectiveBgColor, textColor, showFolderName) },
                        enabled = folderPath.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    ) {
                        Text(stringResource(org.fossify.commons.R.string.ok))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Live Widget Preview
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(Color(effectiveBgColor))
                    .clickable { showFolderPicker = true }
                    .padding(10.dp),
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!thumbnailPath.isNullOrBlank()) {
                            GalleryImage(
                                path = thumbnailPath!!,
                                contentDescription = folderName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = if (conf.cropThumbnails) ContentScale.Crop else ContentScale.Fit,
                            )
                        } else {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                    if (showFolderName) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = folderName.ifBlank { stringResource(R.string.select_folder) },
                            color = Color(textColor),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Folder selector card
            Surface(
                onClick = { showFolderPicker = true },
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(org.fossify.commons.R.string.folder), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = folderName.ifBlank { stringResource(R.string.select_folder) },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Show folder name toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFolderName = !showFolderName }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.show_folder_name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = showFolderName, onCheckedChange = { showFolderName = it })
            }

            // Background color presets + opacity
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(org.fossify.commons.R.string.background_color), style = MaterialTheme.typography.titleSmall)
                ColorSwatchRow(
                    presets = WIDGET_BG_PRESETS,
                    selectedRgb = bgRgb,
                    onSelect = { bgRgb = it },
                )
                Slider(
                    value = bgAlpha,
                    onValueChange = { bgAlpha = it },
                    valueRange = 0f..1f,
                )
            }

            // Text color presets
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(org.fossify.commons.R.string.text_color), style = MaterialTheme.typography.titleSmall)
                ColorSwatchRow(
                    presets = WIDGET_TEXT_PRESETS,
                    selectedRgb = textColor,
                    onSelect = { textColor = it },
                )
            }
        }
    }

    if (showFolderPicker) {
        FolderPathPickerSheet(
            title = stringResource(R.string.select_folder),
            initialPath = folderPath,
            onDismiss = { showFolderPicker = false },
            onPathSelected = { selected ->
                folderPath = selected
                showFolderPicker = false
            },
        )
    }
}

@Composable
private fun ColorSwatchRow(
    presets: List<Int>,
    selectedRgb: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        presets.forEach { argb ->
            val isSelected = (argb and 0x00FFFFFF) == (selectedRgb and 0x00FFFFFF)
            val isLight = AndroidColor.red(argb) * 0.299 + AndroidColor.green(argb) * 0.587 + AndroidColor.blue(argb) * 0.114 > 160
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isLight) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
