package org.fossify.gallery.compose.util

/**
 * Deliberately separates ordinary multi-selection from an interval operation.  A range must never
 * be inferred from a second tap: that makes a harmless tap turn into an accidental bulk action.
 */
enum class MediaSelectionMode {
    INDIVIDUAL,
    RANGE,
}
