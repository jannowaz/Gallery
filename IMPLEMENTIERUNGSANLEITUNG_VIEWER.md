# Unified Media Viewer — Implementierungsanleitung

## Ziel

Ein einheitlicher Viewer für Bilder **und** Videos mit konsistenter UX. Navigation zwischen allen Medien (egal ob Bild oder Video), identisches Bottom-Sheet-Menü, Video-Scrubbing mit Frame-Vorschau, Zoom auf beiden Medientypen. Keine getrennten Codepfade mehr.

---

## 1. Gesten-System (Konfliktfreie Zonen)

Das Kernproblem: horizontales Swipen muss sowohl den Pager (zwischen Medien) als auch das Video-Scrubbing steuern. Die Lösung: **Pager hat immer Vorrang, Scrubbing nur via Slider + Frame-Vorschau**.

```
┌─────────────────────────┐
│  ←── Pager Swipe ──→   │  Ein Finger horizontal = nächstes/vorheriges Medium
│                         │
│    ┌─────────────┐      │
│    │  Pinch Zoom │      │  Zwei Finger = Zoomen (Bild & Video)
│    └─────────────┘      │
│                         │
│  ↕ Dismiss (hoch/runter)│  Ein Finger vertikal = Schließen
│                         │
│  👆 Tap = UI toggeln    │
│  👆👆 DoubleTap = Zoom  │  Wechselt Fit ↔ Crop
├─────────────────────────┤
│  ════●═══════ Slider ═══│  Seek mit Frame-Vorschau
│  00:12 ───────── 02:34  │
└─────────────────────────┘
```

### 1.1 Warum kein horizontales Drag-Scrubbing?

Würde mit dem Pager kollidieren. Google Photos, iOS Photos und TikTok lösen das identisch: Pager per Swipe, Seek per Slider. Die Slider-Interaktion wird durch eine Frame-Vorschau (Keyframe-Extraktion während des Ziehens) aufgewertet.

### 1.2 Gesten-Priorität im Code

```kotlin
// ImagePage.kt / VideoPage.kt
Box {
    // 1. DoubleTap + Dismiss UNTER dem Content rendern
    //    → bekommen Events erst, wenn Content sie nicht konsumiert
    GestureLayer(doubleTap, dismissVertical)
    
    // 2. Content OBEN — bekommt Events zuerst
    AsyncImage / AndroidView(videoPlayer)
        .pointerInput(pinchZoom)
        .pointerInput(singleFingerPanWhenZoomed)
}
```

---

## 2. Architektur: Ein Viewer, zwei Rendering-Pfade

Statt `ComposeViewerActivity` + `ViewerScreen.kt` doppelt zu pflegen, wird der Viewer als **eine** Composable-Funktion mit type-basiertem Rendering implementiert:

```kotlin
@Composable
fun UnifiedViewerPage(
    path: String,
    onClose: () -> Unit,
) {
    val isVideo = remember(path) { path.isVideo() }
    
    if (isVideo) {
        VideoPageContent(path, onClose)
    } else {
        ImagePageContent(path, onClose)
    }
}
```

### 2.1 Gemeinsamer Container

```kotlin
@Composable
fun MediaViewerScreen(paths: List<String>, startIndex: Int, onClose: () -> Unit) {
    val pagerState = rememberPagerState(startIndex) { paths.size }
    
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Pager: immer scrollbar (kein userScrollEnabled-Flag mehr)
        HorizontalPager(state = pagerState) { page ->
            UnifiedViewerPage(paths[page], onClose)
        }
        
        // Top-Bar (Close, EXIF, Seitenzähler) — identisch für Bild/Video
        ViewerTopBar(pagerState, paths.size, onClose)
        
        // Bottom-Sheet-Menü (Share, Copy, Move, Delete, Rate, Tag) — identisch
        ViewerActionSheet(paths[pagerState.currentPage])
        
        // Persistente Rating-Bar (wenn aktiviert)
        RatingOverlay(paths[pagerState.currentPage])
    }
}
```

---

## 3. Video-Scrubbing mit Frame-Vorschau

### 3.1 Ansatz: Slider + MediaMetadataRetriever

```kotlin
@Composable
fun VideoSeekBar(player: ExoPlayer, videoPath: String) {
    var scrubPosition by remember { mutableFloatStateOf(-1f) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    
    Row(Modifier.fillMaxWidth()) {
        Text(formatTime(player.currentPosition))
        
        Slider(
            value = if (scrubPosition >= 0) scrubPosition 
                    else player.currentPosition.toFloat() / player.duration,
            onValueChange = { fraction ->
                scrubPosition = fraction
                // Frame extrahieren (IO-Thread, 100ms Throttle)
                scope.launch(Dispatchers.IO) {
                    val ms = (fraction * player.duration).toLong()
                    previewBitmap = extractFrame(videoPath, ms)
                }
            },
            onValueChangeFinished = {
                player.seekTo((scrubPosition * player.duration).toLong())
                scrubPosition = -1f
            }
        )
        
        Text(formatTime(player.duration))
    }
    
    // Frame-Vorschau über dem Slider
    if (scrubPosition >= 0 && previewBitmap != null) {
        FramePreviewOverlay(previewBitmap, scrubPosition)
    }
}
```

### 3.2 Throttled Frame Extraction

```kotlin
private var lastFrameRequest = 0L

suspend fun extractFrame(path: String, timeUs: Long): Bitmap? = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    if (now - lastFrameRequest < 80) return@withContext null // Max 12 fps
    lastFrameRequest = now
    
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        retriever.getFrameAtTime(timeUs * 1000, OPTION_CLOSEST)
    } finally {
        retriever.release()
    }
}
```

### 3.3 Frame-Vorschau UI

```kotlin
@Composable
fun FramePreviewOverlay(bitmap: Bitmap, position: Float) {
    // Kleine Vorschau oberhalb des Sliders
    Box(Modifier.fillMaxWidth().padding(bottom = 60.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.85f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bitmap = bitmap.asImageBitmap(), modifier = Modifier.size(160.dp, 90.dp))
                Text(formatTime((position * player.duration).toLong()), color = Color.White)
            }
        }
    }
}
```

---

## 4. Zoom auf Bildern UND Videos

### 4.1 Gemeinsame Zoom-Logik

Beide Medientypen nutzen denselben Pinch-to-Zoom-Mechanismus:

```kotlin
// In UnifiedViewerPage:
var scale by remember { mutableFloatStateOf(1f) }
var offsetX by remember { mutableFloatStateOf(0f) }
var offsetY by remember { mutableFloatStateOf(0f) }

Modifier
    .graphicsLayer {
        scaleX = scale; scaleY = scale
        translationX = offsetX; translationY = offsetY
    }
    .pointerInput(Unit) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            val newScale = (scale * zoom).coerceIn(0.5f, 5f)
            scale = newScale
            if (newScale > 1f) {
                offsetX += pan.x
                offsetY += pan.y
                // Clamp to bounds
            }
        }
    }
```

Für Videos: `AndroidView(spv)` wird in eine `Box` mit `graphicsLayer` + `clipToBounds()` gewrappt. `detectTransformGestures` kommt auf den `Modifier` der Wrapper-Box.

### 4.2 Double-Tap-Zoom

```kotlin
detectTapGestures(
    onDoubleTap = { tapOffset ->
        if (scale > 1.1f) {
            // Reset zoom
            scale = 1f; offsetX = 0f; offsetY = 0f
        } else {
            // Zoom to 2.5x centered on tap position
            scale = 2.5f
            offsetX = (size.width / 2f - tapOffset.x) * (scale - 1f)
            offsetY = (size.height / 2f - tapOffset.y) * (scale - 1f)
        }
    }
)
```

---

## 5. Video-Player mit Controls

### 5.1 Layout

```
┌─────────────────────────┐
│  ✕             1/5  ⓘ  │  Top Bar (auto-hide)
│                         │
│                         │
│         ▶️ pause        │  Center Play/Pause
│                         │
│         1.5x  🎧        │  Speed + BG Audio
│                         │
│  00:12 ═══●════ 02:34  │  Seek Bar (mit Vorschau)
│  [Start] [Ende] [✂️...] │  Trim Controls (wenn aktiv)
└─────────────────────────┘
```

### 5.2 Auto-Hide

```kotlin
var showControls by remember { mutableStateOf(true) }
LaunchedEffect(showControls) {
    if (showControls && player.isPlaying) {
        delay(3000)
        showControls = false
    }
}
```

Controls erscheinen bei: Tap, Seek, Play/Pause, Double-Tap.

---

## 6. Bottom-Sheet-Menü (identisch für Bild & Video)

### 6.1 Aktionen

| Aktion | Icon | Beschreibung |
|--------|------|-------------|
| Teilen | Share | Intent.ACTION_SEND |
| Kopieren | ContentCopy | Ordner wählen → kopieren |
| Verschieben | DriveFileMove | Ordner wählen → verschieben |
| Löschen | Delete | In Papierkorb (Undo via UndoBar) |
| Bewerten | Star/StarBorder | Rating 1-5 |
| Tags | Edit | Tag-Dialog |
| Bearbeiten | Edit | Externer Editor |
| Info | Info | EXIF-Details mit Editor |
| Favorit | Favorite/FavoriteBorder | Toggle |

### 6.2 Aufruf

```kotlin
// Wisch nach oben (detectVerticalDrag: drag < -20) zeigt das Sheet
// Oder: Tap auf Bild zeigt Sheet (bestehendes Verhalten)
ModalBottomSheet(onDismissRequest = { ... }) {
    Column {
        ActionGridRow(Share, "Teilen") { shareFile(path) }
        ActionGridRow(Copy, "Kopieren") { showFolderPicker(false) }
        // ...
    }
}
```

---

## 7. EXIF-Info-Sheet (Bild & Video)

Gleiches Sheet für beide Medientypen. Bei Videos: Dauer, Auflösung, Codec statt EXIF-Tags.

```
EXIF-Details                    ✕
─────────────────────────────────
Aufnahmedatum    2024-03-15
Hersteller       Samsung
Kamera           SM-S908B
Breite           4000 px
Höhe             3000 px
Brennweite       5.4 mm
Blende           f/1.8
ISO              200
GPS              48.1234, 11.5678
Dateigröße       4.2 MB
Dateiname        IMG_20240315.jpg
─────────────────────────────────
  [90° CCW]  [90° CW]
  [ Aufnahmedatum ändern ]
```

---

## 8. Dismiss-Animation

```kotlin
val dismissAnim = remember { Animatable(0f) }

// Vertikaler Drag (wenn scale ≈ 1)
detectVerticalDragGestures(
    onVerticalDrag = { _, dragAmount ->
        scope.launch { dismissAnim.snapTo((current + dragAmount).coerceIn(-max, max)) }
    },
    onDragEnd = {
        if (abs(current) > threshold) onClose()
        else scope.launch { dismissAnim.animateTo(0f, spring(dampingRatio = MediumBouncy)) }
    }
)

// Hintergrund-Alpha proportional zur Drag-Distanz
val alpha = 1f - (abs(dismissAnim.value) / maxDrag).coerceIn(0f, 1f)
Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha })
```

---

## 9. Datei-Struktur (nach Refactoring)

```
compose/screens/viewer/
├── MediaViewerScreen.kt    ← Container: Pager + TopBar + ActionSheet
├── UnifiedViewerPage.kt    ← Routing: Bild oder Video?
├── ImagePageContent.kt     ← ImagePage (Zoom + Pan + Dismiss)
├── VideoPageContent.kt     ← VideoPage (Player + Controls + Zoom)
├── VideoSeekBar.kt         ← Slider + FramePreview
├── FrameExtractor.kt       ← MediaMetadataRetriever-Helfer
├── ViewerActionSheet.kt    ← Bottom-Sheet-Menü
├── ViewerTopBar.kt         ← Close + EXIF + PageCounter
├── ExifSheet.kt            ← EXIF-Anzeige + Editor
└── DismissOverlay.kt       ← Swipe-to-Dismiss + Alpha
```

---

## 10. Migrationsplan

| Schritt | Datei | Aktion |
|---------|-------|--------|
| 1 | `VideoPage.kt` | `detectTransformGestures` für Zoom hinzufügen |
| 2 | `VideoPage.kt` | Slider + Frame-Vorschau implementieren |
| 3 | `ImagePage.kt` | `detectTransformGestures` ersetzen (bessere Zoom-Physik) |
| 4 | `ComposeViewerActivity.kt` | `MediaViewerScreen` extrahieren, Duplikate entfernen |
| 5 | `ViewerScreen.kt` | Löschen (durch `MediaViewerScreen` in NavHost ersetzt) |
| 6 | `VideoPage.kt` | Double-Tap-Zoom auf Tap-Position |
| 7 | `VideoPage.kt` | Trim-UI in Slider integrieren (Start/End-Marker) |
