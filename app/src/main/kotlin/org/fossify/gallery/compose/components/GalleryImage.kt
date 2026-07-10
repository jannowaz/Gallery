package org.fossify.gallery.compose.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.privacyBlur
import java.io.File

@Composable
fun GalleryImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIconSize: Dp = 24.dp,
    thumbnailSize: Int? = 384,
    // Fit-scaled images (see MediaTile's cropThumbnails=false mode) don't cover the full box, so
    // this shows through as the letterbox/pillarbox color - callers that want solid black bars
    // instead of the default theme surface pass Color.Black here.
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val ctx = LocalContext.current
    val file = File(path)
    var imageState by remember(path) { mutableStateOf<AsyncImagePainter.State?>(null) }

    var fileExists by remember(path) { mutableStateOf(true) }
    LaunchedEffect(path) {
        fileExists = withContext(Dispatchers.IO) { file.exists() }
    }

    Box(modifier.background(backgroundColor)) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(if (fileExists) Uri.fromFile(file) else null)
                .crossfade(true)
                .apply { if (thumbnailSize != null) size(thumbnailSize, thumbnailSize) }
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().privacyBlur(BlurRadius.thumbnail, BlurState.enabled),
            contentScale = contentScale,
            onSuccess = { imageState = it },
            onError = { imageState = it },
            onLoading = { imageState = it },
        )

        if (imageState is AsyncImagePainter.State.Error || imageState == null && !fileExists) {
            Box(Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.BrokenImage,
                    contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(placeholderIconSize),
                )
            }
        } else if (imageState !is AsyncImagePainter.State.Success) {
            Box(Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Image,
                    contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(placeholderIconSize),
                )
            }
        }
    }
}
