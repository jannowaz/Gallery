package org.fossify.gallery.helpers

/**
 * Returns the directory chain needed to browse from [root] to [target].
 *
 * A saved Explorer path can outlive the Composable that originally built the stack, so callers can
 * reconstruct its parents from the path alone and keep Back semantics stable.
 */
fun explorerAncestorChain(root: String, target: String): List<String> {
    if (target == root || !target.startsWith("$root/")) return listOf(target)

    val chain = ArrayList<String>()
    chain.add(root)
    var current = root
    for (part in target.removePrefix("$root/").split('/')) {
        if (part.isEmpty()) continue
        current = "$current/$part"
        chain.add(current)
    }
    return chain
}
