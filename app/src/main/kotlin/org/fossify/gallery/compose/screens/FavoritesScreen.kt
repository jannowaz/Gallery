package org.fossify.gallery.compose.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fossify.gallery.extensions.config
import java.io.File

@Composable
fun FavoritesScreen(onNavigateToPath: (String) -> Unit = {}, modifier: Modifier = Modifier, viewSettings: ViewSettings = ViewSettings()) {
    val ctx = LocalContext.current
    val favoritePaths = remember {
        val paths = ctx.config.favoriteFolders.toList()
        val sorted = when (viewSettings.sortBy) {
            SortField.NAME -> paths.sortedBy { File(it).name.lowercase() }
            SortField.DATE -> paths.sortedBy { File(it).lastModified() }
            SortField.SIZE -> paths.sortedBy { File(it).name.lowercase() }
            SortField.RATING -> paths.sortedBy { File(it).name.lowercase() }
        }
        if (viewSettings.sortDesc) sorted.reversed() else sorted
    }

    val cardColor = when (viewSettings.displayMode) {
        DisplayMode.COMPACT -> MaterialTheme.colorScheme.surface
        DisplayMode.NORMAL -> MaterialTheme.colorScheme.surface
        DisplayMode.DARK -> MaterialTheme.colorScheme.surfaceVariant
    }
    val cardPadding = when (viewSettings.displayMode) {
        DisplayMode.COMPACT -> 8.dp
        DisplayMode.NORMAL -> 12.dp
        DisplayMode.DARK -> 12.dp
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (favoritePaths.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Star, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("Keine Favoriten", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Favorisiere Ordner im Alben-Tab", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(favoritePaths, key = { it }) { path ->
                    val file = File(path)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onNavigateToPath(path) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor)
                    ) {
                        Row(Modifier.padding(cardPadding), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
