package org.fossify.gallery.activities

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.ReportDrawn
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.handleAppPasswordProtection
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.resolveDarkTheme
import org.fossify.gallery.compose.util.NavigateBus
import org.fossify.gallery.compose.util.PipState
import org.fossify.gallery.extensions.batchJobItemDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppShortcutsHelper
import org.fossify.gallery.helpers.DIRECTORY
import org.fossify.gallery.helpers.IntentSecurity
import org.fossify.gallery.navigation.GalleryNavHost
import org.fossify.gallery.workers.MediaSyncWorker
import org.fossify.gallery.workers.MetadataSyncWorker
import org.fossify.gallery.workers.RecycleBinCleanupWorker

private const val MEDIA_OBSERVER_DEBOUNCE_MS = 1500L
private const val STALE_BATCH_JOB_MAX_AGE_MS = 24 * 60 * 60 * 1000L
private const val LOCK_ICON_ALPHA = 0.5f

class ComposeExplorerActivity : FragmentActivity() {

    private var mediaPermissionState by mutableStateOf(MediaPermissionState.NONE)
    private var permissionRequestAttempted = false

    /** Pre-Android-12 fallback for auto-PiP: 12+ uses setAutoEnterEnabled (see VideoPage), older
     * versions only get this hint when the user leaves via Home while a viewer video plays. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val aspect = PipState.activeVideoAspect
        if (aspect != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(aspect).build(),
                )
            } catch (e: Exception) {
                Log.e("Explorer", "enterPictureInPictureMode failed", e)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRequestAttempted = true
        mediaPermissionState = readMediaPermissionState()
    }

    // --- App password lock ---------------------------------------------------------------------
    // Gates the whole content behind the commons SecurityDialog on launch and again after every
    // real backgrounding. Compose-observable so the opaque cover hides the gallery (and its
    // recents thumbnail) while the PIN/pattern/fingerprint prompt is up.
    private val showLockCover = mutableStateOf(false)
    private var isAppUnlocked = false
    private var lockPromptInFlight = false

    private fun updateSecureWindowFlag() {
        if (config.isAppPasswordProtectionOn || config.blurAllMedia) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun maybeLockApp() {
        updateSecureWindowFlag()
        if (!config.isAppPasswordProtectionOn) {
            isAppUnlocked = true
            showLockCover.value = false
            return
        }
        if (isAppUnlocked || lockPromptInFlight) return
        showLockCover.value = true
        lockPromptInFlight = true
        handleAppPasswordProtection { success ->
            lockPromptInFlight = false
            if (success) {
                isAppUnlocked = true
                showLockCover.value = false
            } else {
                finish()
            }
        }
    }

    @Volatile private var lastObserverMs = 0L
    private val mediaObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            val now = System.currentTimeMillis()
            if (now - lastObserverMs < MEDIA_OBSERVER_DEBOUNCE_MS) return
            lastObserverMs = now
            // Only schedule incremental sync; MediaSyncWorker emits the refresh event after commit.
            MediaSyncWorker.scheduleIncrementalSync(this@ComposeExplorerActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(AppShortcutsHelper.EXTRA_NAVIGATE_TO)?.let {
            NavigateBus.trigger(it)
        }
        intent.getStringExtra(DIRECTORY)
            ?.takeIf { IntentSecurity.isSafeExternalPath(this, it) }
            ?.let { NavigateBus.triggerFolder(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        updateSecureWindowFlag()
        mediaPermissionState = readMediaPermissionState()
        showLockCover.value = config.isAppPasswordProtectionOn
        addOnPictureInPictureModeChangedListener { info ->
            PipState.inPip = info.isInPictureInPictureMode
        }

        setContent {
            val isLocked = showLockCover.value
            @OptIn(ExperimentalComposeUiApi::class)
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                if (!isLocked) {
                    when (mediaPermissionState) {
                        MediaPermissionState.FULL, MediaPermissionState.SELECTED -> GalleryNavHost()
                        MediaPermissionState.NONE, MediaPermissionState.SETTINGS -> GalleryTheme(
                            darkTheme = resolveDarkTheme(config.forceDarkMode, config.forceLightMode),
                            dynamicColor = config.useDynamicColors,
                            amoledBlack = config.useAmoledBackground,
                        ) {
                            MediaPermissionGate(
                                state = mediaPermissionState,
                                onRequest = {
                                    permissionRequestAttempted = true
                                    requestPermissionLauncher.launch(getMediaPermissionStrings())
                                },
                                onOpenSettings = {
                                    startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:$packageName")
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                if (isLocked || !mediaPermissionState.hasMediaAccess) {
                    ReportDrawn()
                }
                LockCover()
            }
        }
        scheduleStartupWorkers()
    }

    private fun scheduleStartupWorkers() {
        if (mediaPermissionState.hasMediaAccess) {
            lifecycleScope.launch(Dispatchers.IO) {
                RecycleBinCleanupWorker.schedule(this@ComposeExplorerActivity)
                MediaSyncWorker.schedule(this@ComposeExplorerActivity)
                MediaSyncWorker.scheduleInitialSync(this@ComposeExplorerActivity)
                MediaSyncWorker.scheduleGapRepair(this@ComposeExplorerActivity)
                MetadataSyncWorker.cancelAutomatic(this@ComposeExplorerActivity)
                MetadataSyncWorker.cancel(this@ComposeExplorerActivity)
                try {
                    batchJobItemDB.deleteStale(System.currentTimeMillis() - STALE_BATCH_JOB_MAX_AGE_MS)
                } catch (e: Exception) {
                    Log.e("Explorer", "Stale batch-job cleanup failed", e)
                }
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                RecycleBinCleanupWorker.schedule(this@ComposeExplorerActivity)
            }
        }
    }

    private fun readMediaPermissionState(): MediaPermissionState {
        val imagesGranted: Boolean
        val videosGranted: Boolean
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            imagesGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES,
            ) == PackageManager.PERMISSION_GRANTED
            videosGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VIDEO,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            imagesGranted = granted
            videosGranted = granted
        }
        if (imagesGranted || videosGranted) return MediaPermissionState.FULL

        val visualSelectedGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == PackageManager.PERMISSION_GRANTED
        if (visualSelectedGranted) {
            return MediaPermissionState.SELECTED
        }

        val shouldUseSettings = permissionRequestAttempted && !shouldShowMediaPermissionRationale()
        return if (shouldUseSettings) MediaPermissionState.SETTINGS else MediaPermissionState.NONE
    }

    private fun shouldShowMediaPermissionRationale(): Boolean {
        val permissions = getMediaPermissionStrings()
        return permissions.any { shouldShowRequestPermissionRationale(it) }
    }

    override fun onStart() {
        super.onStart()
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver,
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver,
        )
        if (mediaPermissionState.hasMediaAccess) {
            lifecycleScope.launch(Dispatchers.IO) {
                MediaSyncWorker.scheduleIncrementalSync(this@ComposeExplorerActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mediaPermissionState = readMediaPermissionState()
        maybeLockApp()
    }

    override fun onStop() {
        super.onStop()
        contentResolver.unregisterContentObserver(mediaObserver)
        if (!isChangingConfigurations && config.isAppPasswordProtectionOn) {
            isAppUnlocked = false
            showLockCover.value = true
        }
    }

    @Composable
    private fun LockCover() {
        if (!showLockCover.value) return
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    enabled = true,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {},
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = LOCK_ICON_ALPHA),
                modifier = Modifier.size(48.dp),
            )
        }
    }

    private fun getMediaPermissionStrings(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                perms.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return perms.toTypedArray()
    }
}
