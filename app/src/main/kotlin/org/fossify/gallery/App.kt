package org.fossify.gallery

import android.os.Build
import android.os.StrictMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.addLastModifiedToFileCacheKey
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import com.github.ajalt.reprint.core.Reprint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import org.fossify.commons.FossifyApp
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.ThumbnailOverlayState
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.defaultSharedPrefs
import org.fossify.gallery.helpers.CrashLogger
import org.fossify.gallery.helpers.JxlCoilDecoder
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.navigation.ViewerArgs

class App : FossifyApp(), SingletonImageLoader.Factory {

    override val isAppLockFeatureAvailable = true

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
                add(SvgDecoder.Factory())
                add(JxlCoilDecoder.Factory())
                // AnimatedImageDecoder needs API 28's platform ImageDecoder; GifDecoder is the
                // movie-based fallback for the API 26/27 devices this app's minSdk still covers.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            // No global RGB_565 override - it drops the alpha channel for PNG/WebP graphics.
            // GalleryImage selectively uses RGB_565 only for guaranteed opaque camera thumbnails.
            // Coil's default FileKeyer calls File.lastModified() synchronously on the main thread
            // when building every memory cache key (AsyncImagePainter.onRemembered). On Android 11+
            // FUSE-backed storage that triggers 100-250ms StrictMode DiskReadViolations right in
            // the middle of scroll and startup frames. In-place edits explicitly evict the path
            // from Coil's caches instead (see EditActivity.scanFinalPath).
            .addLastModifiedToFileCacheKey(false)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        // First thing, before super.onCreate() - crashes during app init should be captured too.
        CrashLogger.install(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
        super.onCreate()
        ViewerArgs.configure(this)

        // Gates RefreshBus on the process being in the foreground. Registered here, before any
        // Activity exists, so the very first onStart is the one that opens the gate - and so a
        // trigger fired by a worker in a headless process is recorded for replay rather than lost.
        RefreshBus.startForegroundTracking()

        // Seed the Compose-observable blur mirror from the persisted setting eagerly so there is
        // no cold-start frame where a persisted privacy blur reads back as off.
        BlurState.enabled = config.blurAllMedia
        ThumbnailOverlayState.sync(config)

        // Reprint uses reflection to load the fingerprint module; only block cold-start if app
        // lock is actually enabled, otherwise warm it up off the main thread.
        if (config.isAppPasswordProtectionOn) {
            Reprint.initialize(this)
        } else {
            appScope.launch { Reprint.initialize(this@App) }
        }

        // Warm up Coil's DiskCache journal and secondary SharedPreferences off the main thread
        // before the first Activity composes its grid.
        appScope.launch { SingletonImageLoader.get(this@App) }
        appScope.launch {
            defaultSharedPrefs()
            org.fossify.gallery.helpers.AppShortcutsHelper.publish(this@App)
        }
    }
}
