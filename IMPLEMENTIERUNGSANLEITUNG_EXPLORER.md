# Implementierungsanleitung: Explorer Mode

## Basis: fossify-gallery-neu (GitHub: FossifyOrg/Gallery)

Der bestehende Code hat bereits den Rohbau für eine hierarchische Ordneransicht (`groupDirectSubfolders`). Der Explorer Mode baut darauf auf, statt neu zu beginnen.

---

## Phase 1: Basis-Navigation reparieren (1–2h)

### Ziel
`groupDirectSubfolders` zeigt aktuell nur Blatt-Ordner an, weil leere Zwischenebenen fehlen. Beispiel: `/Download/Apps/Fotos/` → nur `Fotos` sichtbar, kein Drill-Down möglich.

### Änderungen

#### 1.1 Neue Methode in `Context.kt` → `walkUpCreateMissingAncestors()`

```kotlin
// app/src/main/kotlin/org/fossify/gallery/extensions/Context.kt
// Einfügen vor fillWithSharedDirectParents() (ca. Zeile 343)

fun Context.walkUpCreateMissingAncestors(
    dirs: ArrayList<Directory>,
    rootPath: String
): ArrayList<Directory> {
    val result = ArrayList<Directory>(dirs)
    val existingPaths = dirs.map { it.path.lowercase() }.toHashSet()
    val rootLower = rootPath.lowercase()

    for (dir in dirs) {
        var parent = File(dir.path).parent ?: continue
        while (true) {
            if (parent.isNullOrEmpty() || parent.lowercase() == rootLower) break
            if (!existingPaths.contains(parent.lowercase())) {
                addParentWithoutMediaFiles(result, parent)
                existingPaths.add(parent.lowercase())
            }
            parent = File(parent).parent
        }
    }
    return result
}
```

#### 1.2 `getDirsToShow()` erweitern

```kotlin
// In getDirsToShow(), den groupDirectSubfolders-Zweig ändern:

if (config.groupDirectSubfolders) {
    dirs.forEach {
        it.subfoldersCount = 0
        it.subfoldersMediaCount = it.mediaCnt
    }

    // NEU: Fehlende Zwischenebenen ergänzen
    val filledDirs = walkUpCreateMissingAncestors(
        dirs = dirs,
        rootPath = Environment.getExternalStorageDirectory().absolutePath
    )

    // Bestehende Logik mit gefüllten Dirs
    val withSharedParents = fillWithSharedDirectParents(filledDirs)
    val parentDirs = getDirectParentSubfolders(withSharedParents, currentPathPrefix)
    updateSubfolderCounts(withSharedParents, parentDirs)
    return getSortedDirectories(parentDirs)
}
```

#### 1.3 `fillWithSharedDirectParents()` reparieren (Zeile 354)

```kotlin
// Zeile 354:  filter { dir -> dir.value > 1 ... }
// Ändern zu:
.filter { dir -> dir.value >= 1 && dirs.none { it.path.equals(dir.key, true) } }
```

Grund: Auch bei nur einem Kindordner muss der Parent erzeugt werden.

### Test
- `/Download/Apps/Fotos/` → es erscheint `Download` → Tippen → `Apps` → Tippen → `Fotos`
- Zurück-Button navigiert korrekt nach oben

---

## Phase 2: Breadcrumb Bar aktivieren (30min)

### Ziel
Beim Drill-Down zeigt eine Breadcrumb-Leiste den aktuellen Pfad. Tippen auf ein Segment springt direkt dorthin.

### Änderungen

#### 2.1 Layout hinzufügen

`app/src/main/res/layout/breadcrumb_bar.xml` (existiert bereits im Original-Projekt, muss kopiert werden):

```xml
<?xml version="1.0" encoding="utf-8"?>
<HorizontalScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/breadcrumb_scroll"
    android:layout_width="match_parent"
    android:layout_height="40dp"
    android:scrollbars="none"
    android:background="@android:color/black"
    android:visibility="gone">

    <LinearLayout
        android:id="@+id/breadcrumb_container"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="8dp"
        android:paddingEnd="8dp" />
</HorizontalScrollView>
```

#### 2.2 In `activity_main.xml` einbinden

```xml
<!-- Im RelativeLayout (directories_holder), vor directories_refresh_layout: -->
<include
    android:id="@+id/breadcrumb_bar"
    layout="@layout/breadcrumb_bar" />
```

#### 2.3 Breadcrumb-Logik in `MainActivity.kt`

```kotlin
// Neue Methode in MainActivity:

private fun setupBreadcrumbs(pathPrefix: String) {
    val container = binding.breadcrumbBar.breadcrumbContainer
    container.removeAllViews()

    if (pathPrefix.isEmpty()) {
        binding.breadcrumbBar.breadcrumbScroll.beGone()
        return
    }

    binding.breadcrumbBar.breadcrumbScroll.beVisible()
    val parts = pathPrefix.trimEnd('/').split("/").filter { it.isNotEmpty() }

    // Root-Segment "📁"
    container.addView(TextView(this).apply {
        text = "📁"
        setTextColor(getProperPrimaryColor())
        setPadding(8, 0, 4, 0)
        setOnClickListener {
            mCurrentPathPrefix = ""
            mOpenedSubfolders.clear()
            mOpenedSubfolders.add("")
            setupAdapter(mDirs)
        }
    })

    var accumulatedPath = ""
    for ((i, part) in parts.withIndex()) {
        // Trennzeichen
        container.addView(TextView(this).apply {
            text = " › "
            setTextColor(Color.GRAY)
        })

        accumulatedPath += "/$part"
        val currentPath = accumulatedPath
        container.addView(TextView(this).apply {
            text = part
            setTextColor(if (i == parts.lastIndex) Color.WHITE else getProperPrimaryColor())
            setPadding(4, 0, 4, 0)
            setOnClickListener {
                mCurrentPathPrefix = currentPath
                mOpenedSubfolders = arrayListOf(currentPath)
                setupAdapter(mDirs)
            }
        })
    }
}
```

#### 2.4 Aufruf in `setupAdapter()`

```kotlin
// Nach dem Erstellen des Adapters in setupAdapter():
setupBreadcrumbs(mCurrentPathPrefix)
```

Damit aktualisiert sich die Breadcrumb automatisch bei jedem Ordnerwechsel.

---

## Phase 3: Explorer Mode Toggle (30min)

### Ziel
Ein Menü-Button schaltet zwischen Ordneransicht und Explorer Mode um. Im Explorer Mode:
- Breadcrumb ist sichtbar (auch auf Root-Ebene)
- `groupDirectSubfolders` ist immer aktiv
- Ordner werden als ausklappbare Hierarchie dargestellt

### Änderungen

#### 3.1 `EXPLORER_MODE` Konstante in `Constants.kt`

```kotlin
const val EXPLORER_MODE = "explorer_mode"
```

#### 3.2 `config.explorerMode` Property in `Config.kt`

```kotlin
var explorerMode: Boolean
    get() = prefs.getBoolean(EXPLORER_MODE, false)
    set(explorerMode) = prefs.edit().putBoolean(EXPLORER_MODE, explorerMode).apply()
```

#### 3.3 `mExplorerRootPath` in `MainActivity.kt`

```kotlin
private var mExplorerRootPath = ""
```

#### 3.4 `toggleExplorerMode()` in `MainActivity.kt`

```kotlin
private fun toggleExplorerMode() {
    config.explorerMode = !config.explorerMode
    if (config.explorerMode) {
        mExplorerRootPath = internalStoragePath
        config.groupDirectSubfolders = true
        mCurrentPathPrefix = mExplorerRootPath
        mOpenedSubfolders.clear()
        mOpenedSubfolders.add(mExplorerRootPath)
    } else {
        mExplorerRootPath = ""
        mCurrentPathPrefix = ""
        mOpenedSubfolders.clear()
        mOpenedSubfolders.add("")
    }
    refreshMenuItems()
    getDirectories()
}
```

#### 3.5 Menü-Icon-Wechsel in `refreshMenuItems()`

```kotlin
findItem(R.id.explorer).apply {
    if (config.explorerMode) {
        setIcon(R.drawable.ic_explore_off_vector)
        setTitle(org.fossify.commons.R.string.folder)
    } else {
        setIcon(R.drawable.ic_explore_vector)
        setTitle(R.string.explorer)
    }
}
```

#### 3.6 Menü-Item in `menu_main.xml`

```xml
<item
    android:id="@+id/explorer"
    android:icon="@drawable/ic_explore_vector"
    android:title="@string/explorer"
    app:showAsAction="always" />
```

#### 3.7 Strings ergänzen

```xml
<string name="explorer">Explorer</string>
<string name="explorer_mode">Explorer mode</string>
```

#### 3.8 Drawables kopieren

- `ic_explore_vector.xml` und `ic_explore_off_vector.xml` aus dem alten Projekt nach `app/src/main/res/drawable/` kopieren

---

## Phase 4: Verbesserte Ordneransicht (1h)

### Ziel
Im Explorer Mode werden Ordner mit Unterordnern visuell hervorgehoben (Indikator, ausklappbar). Statt immer einen neuen Screen zu öffnen, können Unterordner inline expandiert werden.

### Änderungen

#### 4.1 `DirectoryAdapter` – `subfoldersCount` visuell anzeigen

Im bestehenden `DirectoryAdapter` (ca. Zeile 500, `setupDirectory()` oder ähnlich):

```kotlin
// Für Ordner mit subfoldersCount > 0:
// Icon oder Chevron anzeigen, das auf Ausklappbarkeit hinweist
if (directory.subfoldersCount > 0 && config.explorerMode) {
    binding.dirHasChildren.beVisible()
    binding.dirHasChildren.text = "+${directory.subfoldersCount}"
} else {
    binding.dirHasChildren.beGone()
}
```

Dafür braucht `item_directory.xml` ein neues TextView (z.B. `dir_has_children`).

#### 4.2 Inline-Expand im Explorer Mode

```kotlin
// In setupAdapter(), beim Klick-Handler:
if (config.explorerMode && clickedDir.subfoldersCount > 1) {
    // Inline expandieren (wie gehabt via mCurrentPathPrefix)
    mCurrentPathPrefix = path
    mOpenedSubfolders.add(path)
    setupAdapter(mDirs, "")
} else {
    // Normal öffnen
    itemClicked(path)
}
```

Diese Logik existiert bereits – der Explorer Mode aktiviert sie nur zusätzlich zur bestehenden `groupDirectSubfolders`-Logik.

---

## Phase 5: Performance & UX-Feinschliff (1h)

### 5.1 Smooth Scrolling & Lazy Loading

Die `MyRecyclerView` + `MyGridLayoutManager` unterstützen bereits Recycling. Zusätzlich:

```kotlin
// In setupAdapter(), MyRecyclerView konfigurieren:
binding.directoriesGrid.apply {
    setHasFixedSize(true)
    setItemViewCacheSize(20)  // Mehr Views cachen für flüssiges Scrollen
}

// Lazy Loading der Thumbnails via Glide (bereits implementiert):
// Glide mit thumbnail() + DiskCacheStrategy.RESOURCE
```

### 5.2 Pinch-to-Zoom Spaltenanzahl

Existiert bereits via `MyRecyclerView.MyZoomListener` in `initZoomListener()`. Funktioniert sowohl in Grid- als auch in Explorer-Ansicht.

### 5.3 SwipeRefresh im Explorer Mode aktiv halten

```kotlin
// In setupAdapter() immer sicherstellen:
binding.directoriesRefreshLayout.isEnabled = config.enablePullToRefresh
```

---

## Ergebnis: Vollständiger Explorer Mode

| Feature | Status |
|---------|--------|
| Hierarchische Ordner-Navigation | Phase 1 |
| Breadcrumb Bar | Phase 2 |
| Explorer Mode Toggle (Menü-Button) | Phase 3 |
| Inline-Expand für Unterordner | Phase 4 |
| Pinch-to-Zoom Grid | Bestehend |
| Smooth Scroll + Lazy Loading | Bestehend (optimiert in 5) |
| SwipeRefresh | Bestehend |
| Favoriten-Integration | Bestehend (über `FAVORITES`) |
| Sammlungen-Integration | Bestehend (über Tab-Navigation) |

## Was NICHT Teil dieser Anleitung ist

- **Jetpack Compose** – würde einen kompletten Rewrite erfordern. Die bestehende ViewBinding-Architektur wird weiterverwendet.
- **AI-basierte Suche / Duplikaterkennung** – benötigt serverseitige ML-Modelle, nicht realistisch für eine Offline-Galerie-App.
- **Cloud-Sources** – die App ist für lokale Dateien ausgelegt; Cloud-Integration bräuchte komplett neue Datenquellen-Architektur.
- **Timeline / Map / Mood Explorer** – wären separate Feature-Entwicklungen, die auf Phase 1–4 aufbauen könnten.

## Dateien, die geändert werden (keine neuen Dateien nötig)

| Datei | Änderung |
|-------|----------|
| `Context.kt` | `walkUpCreateMissingAncestors()` hinzufügen, `getDirsToShow()` anpassen, `fillWithSharedDirectParents()`-Filter lockern |
| `MainActivity.kt` | `toggleExplorerMode()`, `setupBreadcrumbs()`, `mExplorerRootPath`, Aufrufe |
| `activity_main.xml` | Breadcrumb-Bar einbinden |
| `breadcrumb_bar.xml` | Aus Originalprojekt kopieren |
| `Constants.kt` | `EXPLORER_MODE` Konstante |
| `Config.kt` | `explorerMode` Property |
| `menu_main.xml` | Explorer Menü-Item |
| `strings.xml` | `explorer`, `explorer_mode` Strings |
| `item_directory.xml` | Optional: `dir_has_children` TextView |
| `drawable/` | `ic_explore_vector.xml`, `ic_explore_off_vector.xml` kopieren |
