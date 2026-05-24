# Gallery 2026: Innovations-Blueprint & Implementierungsleitfaden

Dieses Dokument beschreibt die technische Umsetzung der UX/UI-Revolution für die Gallery App.

## Modul 1: Fluid Interaction (Radial- & Gestenmenü)
### Ziel: Aktionen durchführen, ohne das Bild zu öffnen.
- **Implementierung:** 
    - Integration eines `OnLongClickListener` im `MediaAdapter`.
    - Anzeige eines schwebenden Overlays (`ContextualPopup`), das via Drag-Geste Rating und Favoriten setzt.
    - Animation: Nutze `SpringAnimation` für ein physikalisch korrektes Gefühl.

## Modul 2: Smart Cleaning Hub
### Ziel: Proaktives Aufräumen statt passives Speichern.
- **Implementierung:**
    - Neuer Tab `nav_clean`.
    - Algorithmen (Lokal):
        - **Duplikate:** MD5-Hashing für exakte Kopien.
        - **Screenshots:** Filter für `Screenshots` Ordner > 30 Tage.
        - **Blur Detection:** Nutzung von `Laplacian Variance` (via OpenCV oder simpler Bitmap-Analyse).
- **UI:** Nutze Material 3 Cards mit Fortschrittsbalken für den "gesundheitszustand" der Galerie.

## Modul 3: Immersive Image Viewer 2.0
### Ziel: Bilder fühlen sich "greifbar" an.
- **Implementierung:**
    - **Swipe-to-Dismiss:** Implementierung einer vertikalen Drag-Geste, die das Bild verkleinert und den Hintergrund transparent macht.
    - **Shared Element Transition:** Weicher Übergang vom Thumbnail zum Viewer ohne "Jump".
    - **Dynamic Glass:** Die Rating-Bar nutzt `RenderEffect` (Android 12+) für realzeitlichen Blur des Bildes im Hintergrund.

## Modul 4: Collection Stacks
### Ziel: Sammlungen visuell von Ordnern unterscheiden.
- **Implementierung:**
    - Custom View: `StackImageView`. Zeichnet 3 Bilder leicht versetzt übereinander.
    - Dynamische Filter: Sammlungen basieren auf SQL-Queries (z.B. `rating >= 4`), nicht nur auf Pfaden.

---

# Aktuelle Implementierungs-Schritte:
1. [ ] **Navigation-Update:** Hinzufügen des "Clean"-Tabs.
2. [ ] **Clean Activity/Fragment:** Basis-Struktur für das Aufräumen.
3. [ ] **Radial-Aktionen:** Quick-Rating im Grid.
4. [ ] **Fluid-Gesten:** Swipe-to-Dismiss im ViewPager.
