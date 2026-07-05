package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import org.fossify.gallery.compose.screens.settings.SettingsScreen
import org.fossify.gallery.compose.theme.AppProviders
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.compose.theme.resolveDarkTheme
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.MediaRepository

class ComposeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repo = remember { MediaRepository(this) }
            GalleryTheme(
                darkTheme = resolveDarkTheme(config.forceDarkMode, config.forceLightMode),
                dynamicColor = config.useDynamicColors,
                amoledBlack = config.useAmoledBackground,
            ) {
                AppProviders(repo) {
                    SettingsScreen(
                        onBack = { finish() },
                        onNavigateToAbout = { startActivity(Intent(this, ComposeAboutActivity::class.java)) },
                    )
                }
            }
        }
    }
}
