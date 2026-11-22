package org.fossify.gallery.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.toast
import org.fossify.gallery.compose.screens.editor.EditScreen
import org.fossify.gallery.compose.screens.editor.EditorCanvasContent
import org.fossify.gallery.compose.screens.editor.EditorSessionController
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.resolveDarkTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.ColorModeHelper
import org.fossify.gallery.helpers.MediaRepository
import java.io.File

class EditActivity : ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("NativeImageProcessor")
        }
    }

    private val mediaRepository by lazy { MediaRepository(applicationContext) }
    private lateinit var controller: EditorSessionController
    private var isEditingWithThirdParty = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted || hasStorageReadAccess()) {
            if (!controller.initializeFromIntent(intent)) finish()
        } else {
            toast(org.fossify.commons.R.string.no_storage_permissions)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        updateSecureWindowFlag()

        if (checkAppSideloading()) return

        controller = EditorSessionController(
            activity = this,
            scope = lifecycleScope,
            onOpenThirdPartyEditor = { isEditingWithThirdParty = true },
            onSaved = ::finishWithSavedPath,
        )
        controller.configureInitialExtras(intent)

        setContent {
            AppProviders(repository = mediaRepository) {
                GalleryTheme(
                    darkTheme = resolveDarkTheme(config.forceDarkMode, config.forceLightMode),
                    dynamicColor = config.useDynamicColors,
                    amoledBlack = config.useAmoledBackground,
                ) {
                    val callbacks = remember { controller.buildCallbacks() }
                    EditScreen(
                        state = controller.uiState,
                        callbacks = callbacks,
                    ) { canvasModifier ->
                        EditorCanvasContent(
                            state = controller.uiState,
                            imageUri = controller.imageUri,
                            previewBitmap = controller.previewFilteredBitmap,
                            drawBackgroundBitmap = controller.drawBackgroundBitmap,
                            onDrawBackgroundReady = { controller.drawBackgroundBitmap = it },
                            onCropViewBind = { controller.cropImageView = it },
                            onDrawViewBind = { controller.drawCanvasView = it },
                            onDrawHistoryChange = controller::updateDrawHistory,
                            modifier = canvasModifier,
                        )
                    }
                }
            }
        }

        if (hasStorageReadAccess()) {
            if (!controller.initializeFromIntent(intent)) finish()
        } else {
            requestPermissionLauncher.launch(requiredReadPermission())
        }
    }

    override fun onResume() {
        super.onResume()
        isEditingWithThirdParty = false
        updateSecureWindowFlag()
    }

    override fun onStop() {
        super.onStop()
        if (isEditingWithThirdParty) finish()
    }

    override fun onDestroy() {
        ColorModeHelper.resetColorMode(this)
        if (::controller.isInitialized) {
            controller.cropImageView = null
            controller.drawCanvasView = null
        }
        super.onDestroy()
    }

    private fun updateSecureWindowFlag() {
        if (config.isAppPasswordProtectionOn || config.blurAllMedia) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun requiredReadPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

    private fun hasStorageReadAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return true
        }
        return ContextCompat.checkSelfPermission(this, requiredReadPermission()) == PackageManager.PERMISSION_GRANTED
    }

    private fun finishWithSavedPath(path: String) {
        val resultUri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.provider", File(path))
        }.getOrNull()
        if (resultUri != null) {
            val result = Intent().apply {
                data = resultUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setResult(RESULT_OK, result)
        } else {
            setResult(RESULT_OK)
        }
        finish()
    }
}
