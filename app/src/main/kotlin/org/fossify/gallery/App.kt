package org.fossify.gallery

import android.graphics.Bitmap
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
import org.fossify.gallery.extensions.config

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
            }
            .bitmapConfig(Bitmap.Config.RGB_565)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
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
