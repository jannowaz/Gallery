package org.fossify.gallery.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import org.fossify.gallery.compose.screens.about.AboutScreen
import org.fossify.gallery.compose.theme.GalleryTheme
import org.fossify.gallery.extensions.config

class ComposeAboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GalleryTheme(darkTheme = config.forceDarkMode || isSystemInDarkTheme(), dynamicColor = config.useDynamicColors) {
                AboutScreen(onBack = { finish() })
            }
        }
    }
}
