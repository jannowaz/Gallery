package org.fossify.gallery.helpers

/** Applies the same select-all/invert semantics to every multi-select surface. */
fun selectionForAll(
    selected: Set<String>,
    available: Collection<String>,
    invert: Boolean,
): Set<String> {
    val availableSet = available.toSet()
    return if (invert) availableSet - selected else availableSet
}
