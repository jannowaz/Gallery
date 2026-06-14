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
import org.fossify.commons.FossifyApp

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
    }
}
