package org.fossify.gallery.activities

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.handleAppPasswordProtection
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.viewer.ViewerScreen
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.resolveDarkTheme
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.PipState
import org.fossify.gallery.compose.util.PrivacyPauseScrim
import org.fossify.gallery.compose.util.XmpBatch
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.helpers.VIDEO_EXTENSIONS
import org.fossify.gallery.helpers.resolveContentUriToPath
import org.fossify.gallery.navigation.LocalAnimatedVisibilityScope
import org.fossify.gallery.navigation.LocalSharedTransitionScope
import java.io.File
import java.util.Locale

private const val EXTERNAL_VIEWER_CACHE_DIR = "external_viewer"
private const val EXTERNAL_VIEWER_STALE_MS = 24L * 60L * 60L * 1000L
private const val MAX_SIBLING_FILES = 500
private val EXTERNAL_VIEWER_IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp", "svg", "apng", "jxl",
    "dng", "cr2", "nef", "arw", "orf", "rw2",
)

internal fun resolveExternalViewerExtension(displayName: String?, mimeType: String?): String {
    val fromName = displayName
        ?.substringAfterLast('.', "")
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() && it.length <= 8 && it.all { ch -> ch.isLetterOrDigit() } }
    if (fromName != null) return fromName

    val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    val explicit = when (normalizedMime) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "image/avif" -> "avif"
        "image/jxl" -> "jxl"
        "image/svg+xml" -> "svg"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/x-matroska" -> "mkv"
        "video/quicktime" -> "mov"
        "video/3gpp" -> "3gp"
        else -> null
    }
    if (explicit != null) return explicit

    val fromMimeMap = normalizedMime?.let {
        runCatching { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }.getOrNull()
    }?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
    if (fromMimeMap != null) return fromMimeMap

    return if (normalizedMime?.startsWith("video/") == true) "mp4" else "jpg"
}

internal fun sanitizeExternalViewerFilename(displayName: String?, fallbackExtension: String): String {
    val rawBase = displayName
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.substringBeforeLast('.')
        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        ?.trim('.', '_')
        ?.take(64)
        .orEmpty()
    val base = rawBase.ifEmpty { "external_media" }
    val ext = fallbackExtension.trimStart('.').lowercase(Locale.ROOT).ifEmpty { "jpg" }
    return "$base.$ext"
}

private data class ExternalViewerResolution(
    val paths: List<String>,
    val startIndex: Int,
    val tempFile: File? = null,
)

/**
 * Handles external `ACTION_VIEW` and `ACTION_REVIEW` intents directly in the Compose [ViewerScreen],
 * supporting both on-disk/MediaStore paths (with sibling browsing) and opaque `content://` streams.
 */
class ComposeExternalViewerActivity : FragmentActivity() {

    private var activeIntent by mutableStateOf<Intent?>(null)
    private var appUnlocked by mutableStateOf(false)
    private var sessionTempFile: File? = null

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        activeIntent = intent
        appUnlocked = !config.isAppPasswordProtectionOn
        updateSecurityState()

        setContent {
            val conf = config
            val appCtx = remember { applicationContext }
            val repo = remember(appCtx) { MediaRepository(appCtx) }

            LaunchedEffect(repo) {
                registerViewerUndoHandlers(appCtx, repo)
            }

            GalleryTheme(
                darkTheme = resolveDarkTheme(conf.forceDarkMode, conf.forceLightMode),
                dynamicColor = conf.useDynamicColors,
                amoledBlack = conf.useAmoledBackground,
            ) {
                if (!appUnlocked) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    ExternalViewerHost(
                        intent = activeIntent,
                        repo = repo,
                        onTempFileCreated = { file ->
                            sessionTempFile?.let { previous -> if (previous != file) previous.delete() }
                            sessionTempFile = file
                        },
                        onClose = { finish() },
                    )
                }
            }
        }

        if (config.isAppPasswordProtectionOn) {
            handleAppPasswordProtection { success ->
                if (success) {
                    appUnlocked = true
                } else {
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeIntent = intent
    }

    override fun onResume() {
        super.onResume()
        updateSecurityState()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val aspect = PipState.activeVideoAspect
        if (aspect != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(aspect).build())
            }.onFailure { Log.e("ExternalViewer", "enterPictureInPictureMode failed", it) }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            sessionTempFile?.let { runCatching { it.delete() } }
            sessionTempFile = null
        }
        super.onDestroy()
    }

    private fun updateSecurityState() {
        BlurState.enabled = config.blurAllMedia
        if (config.isAppPasswordProtectionOn || config.blurAllMedia) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ExternalViewerHost(
    intent: Intent?,
    repo: MediaRepository,
    onTempFileCreated: (File) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var resolution by remember(intent) { mutableStateOf<ExternalViewerResolution?>(null) }
    var isLoading by remember(intent) { mutableStateOf(true) }

    LaunchedEffect(intent) {
        isLoading = true
        val uri = intent?.data
        resolution = if (uri == null) {
            null
        } else {
            resolveExternalViewerTarget(ctx, uri, intent.type, repo)
        }
        resolution?.tempFile?.let(onTempFileCreated)
        isLoading = false
    }

    AppProviders(repo) {
        Box(Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                resolution == null || resolution!!.paths.isEmpty() -> {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(org.fossify.commons.R.string.unknown_error_occurred),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onClose) {
                                Text(stringResource(R.string.cd_close))
                            }
                        }
                    }
                }
                else -> {
                    val resolved = resolution!!
                    SharedTransitionLayout(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                            AnimatedVisibility(visible = true) {
                                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                                    ViewerScreen(
                                        paths = resolved.paths,
                                        startIndex = resolved.startIndex,
                                        startPath = resolved.paths.getOrNull(resolved.startIndex),
                                        onClose = onClose,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PrivacyPauseScrim()
        }
    }
}

private suspend fun resolveExternalViewerTarget(
    context: Context,
    uri: Uri,
    intentMimeType: String?,
    repo: MediaRepository,
): ExternalViewerResolution? = withContext(Dispatchers.IO) {
    cleanupStaleExternalViewerCache(context)

    val directPath = resolveDirectPath(context, uri)
    if (directPath != null) {
        val file = File(directPath)
        if (file.isFile && file.canRead()) {
            val siblings = loadSiblingMediaPaths(file, repo)
            val idx = siblings.indexOfFirst { it.equals(directPath, ignoreCase = true) }
            return@withContext if (idx >= 0) {
                ExternalViewerResolution(paths = siblings, startIndex = idx)
            } else {
                ExternalViewerResolution(paths = listOf(directPath), startIndex = 0)
            }
        }
    }

    val copied = copyContentUriToCache(context, uri, intentMimeType) ?: return@withContext null
    ExternalViewerResolution(paths = listOf(copied.absolutePath), startIndex = 0, tempFile = copied)
}

private fun resolveDirectPath(context: Context, uri: Uri): String? {
    if (uri.scheme.equals("file", ignoreCase = true)) {
        return uri.path?.takeIf { it.isNotBlank() }
    }
    if (uri.scheme.equals("content", ignoreCase = true)) {
        runCatching { context.getRealPathFromURI(uri) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        resolveContentUriToPath(uri.toString())
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    return null
}

private fun loadSiblingMediaPaths(targetFile: File, repo: MediaRepository): List<String> {
    val parent = targetFile.parentFile ?: return listOf(targetFile.absolutePath)
    val dbMedia = runCatching { repo.getMediaFromPath(parent.absolutePath) }.getOrDefault(emptyList())
    if (dbMedia.isNotEmpty()) {
        val sorted = dbMedia.sortedByDescending { it.dateSortKey.takeIf { k -> k > 0L } ?: it.modified }
            .map { it.path }
            .take(MAX_SIBLING_FILES)
        if (sorted.any { it.equals(targetFile.absolutePath, ignoreCase = true) }) {
            return sorted
        }
    }
    val allExts = EXTERNAL_VIEWER_IMAGE_EXTENSIONS + VIDEO_EXTENSIONS
    val diskSiblings = runCatching {
        parent.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase(Locale.ROOT) in allExts }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.absolutePath }
            ?.take(MAX_SIBLING_FILES)
            ?.toList()
    }.getOrNull().orEmpty()
    return if (diskSiblings.any { it.equals(targetFile.absolutePath, ignoreCase = true) }) {
        diskSiblings
    } else {
        listOf(targetFile.absolutePath)
    }
}

private fun copyContentUriToCache(context: Context, uri: Uri, intentMimeType: String?): File? {
    return try {
        val displayName = queryDisplayName(context, uri)
        val mimeType = context.contentResolver.getType(uri) ?: intentMimeType
        val ext = resolveExternalViewerExtension(displayName, mimeType)
        val safeName = sanitizeExternalViewerFilename(displayName, ext)
        val dir = File(context.cacheDir, EXTERNAL_VIEWER_CACHE_DIR).apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (target.length() <= 0L) {
            target.delete()
            null
        } else {
            target
        }
    } catch (e: Exception) {
        Log.e("ExternalViewer", "Failed to copy external content URI $uri", e)
        null
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

private fun cleanupStaleExternalViewerCache(context: Context) {
    runCatching {
        val dir = File(context.cacheDir, EXTERNAL_VIEWER_CACHE_DIR)
        if (!dir.isDirectory) return
        val cutoff = System.currentTimeMillis() - EXTERNAL_VIEWER_STALE_MS
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }
}

private fun registerViewerUndoHandlers(appCtx: Context, repo: MediaRepository) {
    UndoManager.registerHandler(UndoType.DELETE) { action ->
        action.paths.forEach { repo.restoreFromRecycleBin(it) }
        RefreshBus.trigger()
    }
    UndoManager.registerHandler(UndoType.TAG_ADD) { action ->
        XmpBatch.run(appCtx, action.paths.toList()) { repo.removeTag(it, action.extra["tag"] ?: "") }
    }
    UndoManager.registerHandler(UndoType.TAG_REMOVE) { action ->
        XmpBatch.run(appCtx, action.paths.toList()) { repo.addTag(it, action.extra["tag"] ?: "") }
    }
    UndoManager.registerHandler(UndoType.RATING_CHANGE) { action ->
        val snapshots = action.ratingSnapshots
        XmpBatch.run(appCtx, action.paths.toList()) { path ->
            repo.updateRating(path, snapshots[path] ?: action.extra["oldRating"]?.toIntOrNull() ?: 0)
        }
        RefreshBus.trigger(pathsChanged = false)
    }
}
