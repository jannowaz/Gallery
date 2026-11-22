package org.fossify.gallery.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.fossify.gallery.compose.screens.about.AboutScreen
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.resolveDarkTheme
import org.fossify.gallery.extensions.config

class ComposeAboutActivity : ComponentActivity() {
    private fun updateSecureWindowFlag() {
        if (config.isAppPasswordProtectionOn || config.blurAllMedia) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        updateSecureWindowFlag()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateSecureWindowFlag()
        setContent {
            GalleryTheme(
                darkTheme = resolveDarkTheme(config.forceDarkMode, config.forceLightMode),
                dynamicColor = config.useDynamicColors,
                amoledBlack = config.useAmoledBackground,
            ) {
                AboutScreen(onBack = { finish() })
            }
        }
    }
}
