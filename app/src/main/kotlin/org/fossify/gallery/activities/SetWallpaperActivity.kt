package org.fossify.gallery.activities

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.compose.screens.wallpaper.SetWallpaperScreen
import org.fossify.gallery.compose.screens.wallpaper.WallpaperAspectRatio
import org.fossify.gallery.compose.screens.wallpaper.WallpaperTarget
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.resolveDarkTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.IntentSecurity
import java.io.IOException

class SetWallpaperActivity : ComponentActivity() {
    companion object {
        private const val TAG = "SetWallpaperActivity"
        private const val ROTATE_RIGHT_DEGREES = 90
    }

    private var imageUri by mutableStateOf<Uri?>(null)
    private var aspectRatio by mutableStateOf(WallpaperAspectRatio.PORTRAIT)
    private var isApplying by mutableStateOf(false)
    private var showTargetDialog by mutableStateOf(false)

    private var cropImageView: CropImageView? = null
    private var lastLoadedUri: Uri? = null
    private var applyJob: Job? = null

    private val wallpaperManager: WallpaperManager by lazy {
        WallpaperManager.getInstance(applicationContext)
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val resultData = result.data
        if (result.resultCode == RESULT_OK && resultData != null) {
            handleIncomingIntent(resultData)
        } else {
            finish()
        }
    }

    private fun updateSecureWindowFlag() {
        if (config.isAppPasswordProtectionOn || config.blurAllMedia) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        updateSecureWindowFlag()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        updateSecureWindowFlag()

        if (intent.data == null) {
            val pickIntent = Intent(applicationContext, ComposeExplorerActivity::class.java).apply {
                action = Intent.ACTION_PICK
                type = "image/*"
            }
            pickImageLauncher.launch(pickIntent)
        } else {
            handleIncomingIntent(intent)
        }

        setContent {
            GalleryTheme(
                darkTheme = resolveDarkTheme(config.forceDarkMode, config.forceLightMode),
                dynamicColor = config.useDynamicColors,
                amoledBlack = config.useAmoledBackground,
            ) {
                SetWallpaperScreen(
                    hasImage = imageUri != null,
                    aspectRatio = aspectRatio,
                    isApplying = isApplying,
                    showTargetDialog = showTargetDialog,
                    onBack = { finish() },
                    onCycleAspectRatio = {
                        aspectRatio = aspectRatio.nextPreset()
                        cropImageView?.let(::applyAspectRatio)
                    },
                    onAllowFreeAspectRatio = {
                        aspectRatio = WallpaperAspectRatio.FREE
                        cropImageView?.clearAspectRatio()
                    },
                    onRotateRight = {
                        cropImageView?.rotateImage(ROTATE_RIGHT_DEGREES)
                    },
                    onSaveClick = {
                        showTargetDialog = true
                    },
                    onDismissTargetDialog = {
                        showTargetDialog = false
                    },
                    onConfirmTarget = { target ->
                        showTargetDialog = false
                        applyWallpaper(target)
                    },
                ) { modifier ->
                    val currentUri = imageUri
                    AndroidView(
                        modifier = modifier,
                        factory = { ctx ->
                            CropImageView(ctx).apply {
                                setImageCropOptions(
                                    CropImageOptions(
                                        backgroundColor = ContextCompat.getColor(
                                            ctx,
                                            R.color.crop_image_view_background,
                                        ),
                                        initialCropWindowPaddingRatio = 0f,
                                    ),
                                )
                                cropImageView = this
                            }
                        },
                        update = { view ->
                            cropImageView = view
                            if (currentUri != null && currentUri != lastLoadedUri) {
                                lastLoadedUri = currentUri
                                view.setImageUriAsync(currentUri)
                                applyAspectRatio(view)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        applyJob?.cancel()
        cropImageView = null
        super.onDestroy()
    }

    private fun handleIncomingIntent(sourceIntent: Intent) {
        val incomingUri = sourceIntent.data
        if (incomingUri == null || !IntentSecurity.isSafeIncomingUri(this, incomingUri)) {
            toast(R.string.unknown_file_location)
            finish()
            return
        }
        imageUri = incomingUri
    }

    private fun applyAspectRatio(view: CropImageView) {
        if (aspectRatio == WallpaperAspectRatio.FREE) {
            view.clearAspectRatio()
            return
        }

        val heightToUse = wallpaperManager.desiredMinimumHeight.coerceAtLeast(1)
        var widthToUse = wallpaperManager.desiredMinimumWidth.coerceAtLeast(1)
        if (widthToUse == heightToUse) {
            widthToUse = (widthToUse / 2).coerceAtLeast(1)
        }

        when (aspectRatio) {
            WallpaperAspectRatio.PORTRAIT -> view.setAspectRatio(heightToUse, widthToUse)
            WallpaperAspectRatio.LANDSCAPE -> view.setAspectRatio(widthToUse, heightToUse)
            WallpaperAspectRatio.SQUARE -> view.setAspectRatio(widthToUse, widthToUse)
            WallpaperAspectRatio.FREE -> view.clearAspectRatio()
        }
    }

    private fun applyWallpaper(target: WallpaperTarget) {
        val cropView = cropImageView ?: return
        isApplying = true
        applyJob?.cancel()
        applyJob = lifecycleScope.launch {
            val bitmap = try {
                withContext(Dispatchers.Default) {
                    cropView.getCroppedImage()
                }
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "Cropping wallpaper failed with OOM", oom)
                isApplying = false
                toast(org.fossify.commons.R.string.out_of_memory_error)
                return@launch
            }

            if (isFinishing || isDestroyed) return@launch
            if (bitmap == null) {
                isApplying = false
                toast(R.string.set_as_wallpaper_failed)
                return@launch
            }

            val applied = withContext(Dispatchers.IO) {
                setScaledWallpaper(bitmap, target)
            }

            if (isFinishing || isDestroyed) return@launch
            isApplying = false
            if (applied) {
                toast(R.string.wallpaper_set_successfully)
                setResult(RESULT_OK)
                finish()
            } else {
                setResult(RESULT_CANCELED)
            }
        }
    }

    private fun setScaledWallpaper(
        bitmap: android.graphics.Bitmap,
        target: WallpaperTarget,
    ): Boolean {
        return try {
            val wantedHeight = wallpaperManager.desiredMinimumHeight.coerceAtLeast(1)
            val ratio = wantedHeight / bitmap.height.coerceAtLeast(1).toFloat()
            val wantedWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val scaledBitmap = bitmap.scale(wantedWidth, wantedHeight)
            wallpaperManager.setBitmap(scaledBitmap, null, true, target.flag)
            true
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Scaling wallpaper failed with OOM", oom)
            toast(org.fossify.commons.R.string.out_of_memory_error)
            false
        } catch (ioe: IOException) {
            Log.e(TAG, "Failed to apply wallpaper", ioe)
            toast(R.string.set_as_wallpaper_failed)
            false
        } catch (se: SecurityException) {
            Log.e(TAG, "Permission denied while setting wallpaper", se)
            toast(R.string.set_as_wallpaper_failed)
            false
        }
    }
}
