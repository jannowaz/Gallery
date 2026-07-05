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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
