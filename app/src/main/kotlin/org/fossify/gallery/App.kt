package org.fossify.gallery

import android.os.Build
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.Decoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.size.Precision
import coil.util.DebugLogger
import com.github.ajalt.reprint.core.Reprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.fossify.commons.FossifyApp
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.CrashLogger
import org.fossify.gallery.helpers.RefreshBus

class App : FossifyApp(), ImageLoaderFactory {

    override val isAppLockFeatureAvailable = true

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .components {
                add(coil.decode.VideoFrameDecoder.Factory())
                add(coil.decode.SvgDecoder.Factory())
                // ImageDecoderDecoder needs API 28's platform ImageDecoder; GifDecoder is the
                // movie-based fallback for the API 26/27 devices this app's minSdk still covers.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            // No RGB_565 override - it drops the alpha channel entirely, flattening transparent
            // PNG/WEBP (screenshots, stickers, exported graphics) onto an opaque background. Coil's
            // default (ARGB_8888) is what actually renders transparency correctly; the size cap on
            // individual requests (e.g. ImagePage's 2560px viewer request) already bounds per-image
            // memory regardless of bit depth.
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
        Reprint.initialize(this)

        // Gates RefreshBus on the process being in the foreground. Registered here, before any
        // Activity exists, so the very first onStart is the one that opens the gate - and so a
        // trigger fired by a worker in a headless process is recorded for replay rather than lost.
        RefreshBus.startForegroundTracking()

        // HotThreadSampler is deliberately NOT started here. It costs real CPU of its own
        // (Thread.getAllStackTraces() over ~56 threads twice a second), which distorts exactly the
        // kind of measurement it exists to support - call HotThreadSampler.start() by hand when
        // investigating, don't leave it wired into startup.

        // Seed the Compose-observable blur mirror from the persisted setting - BlurState (not
        // Config directly) is what every blur call site and toggle reads/writes through, since
        // flipping a plain SharedPreferences-backed boolean doesn't recompose anything that
        // already read it. Read eagerly/synchronously (not in the async warm-up block below) so
        // there's no window on cold start where a persisted "on" reads back as off.
        BlurState.enabled = config.blurAllMedia

        // Coil's ImageLoader (see newImageLoader() above) is built lazily on whatever thread
        // first requests an image - building its DiskCache does I/O (creates the cache dir and
        // opens/creates the journal file), which without this warm-up happened synchronously on
        // the main thread during the very first AsyncImage composition, confirmed via a
        // StrictMode DiskReadViolation (~440ms) and two ~780ms dropped frames right after first
        // launch. Dispatched on Dispatchers.IO's already-warm worker pool rather than a freshly
        // spun-up Thread - a brand-new Thread's own startup latency (allocating a stack,
        // registering with the OS scheduler, all while cold-start is already saturating every
        // core with classloading/JIT work) was still frequently losing the race to the main
        // thread's first AsyncImage request. GlobalScope is deliberate here: this warm-up must
        // outlive whatever Activity happens to be first, isn't cancellable, and there is no
        // Application-scoped CoroutineScope available to launch it on instead.
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) { coil.Coil.imageLoader(this@App) }

        // Same idea for the app's own SharedPreferences (config) and the legacy default-named
        // prefs file some ViewModels also read (ViewSettingsViewModel, ExplorerViewModel, ...):
        // the first access to each triggers a synchronous-if-not-yet-loaded disk read on whichever
        // thread makes it. Application.onCreate() runs well before any Activity/ViewModel, giving
        // this a real head start - unlike the previous attempt in ComposeExplorerActivity.onCreate(),
        // which fired the same warm-up mere microseconds before setContent() triggered the same
        // reads on the main thread and lost that race often enough to still show up in StrictMode.
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            config
            android.preference.PreferenceManager.getDefaultSharedPreferences(this@App)
        }
    }
}
