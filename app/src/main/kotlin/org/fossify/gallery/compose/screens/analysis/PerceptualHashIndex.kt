package org.fossify.gallery.compose.screens.analysis

/**
 * Exact-radius lookup for perceptual hashes.
 *
 * A straight pairwise comparison makes a 10,000-image similarity scan perform roughly fifty
 * million Hamming-distance checks. A BK-tree applies the triangle inequality of Hamming distance
 * and visits only branches that can still contain a result within the requested radius.
 */
internal class PerceptualHashIndex(
    private val hashes: LongArray,
    valid: BooleanArray,
) {
    private class Node(val index: Int) {
        val children = HashMap<Int, Node>()
    }

    private var root: Node? = null

    init {
        valid.indices.filter { valid[it] }.forEach(::insert)
    }

    private fun insert(index: Int) {
        val hash = hashes[index]
        val initialNode = root
        if (initialNode == null) {
            root = Node(index)
            return
        }
        var node: Node = requireNotNull(initialNode)
        while (true) {
            val distance = hamming(hash, hashes[node.index])
            val child = node.children[distance]
            if (child == null) {
                node.children[distance] = Node(index)
                return
            }
            node = child
        }
    }

    /** Returns every indexed hash within [maxDistance] of the hash at [anchorIndex]. */
    fun within(anchorIndex: Int, maxDistance: Int): List<Int> {
        val anchor = hashes[anchorIndex]
        val result = ArrayList<Int>()
        val pending = ArrayDeque<Node>()
        root?.let(pending::addLast) ?: return result
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            val distance = hamming(anchor, hashes[node.index])
            if (distance <= maxDistance) result += node.index
            val lower = distance - maxDistance
            val upper = distance + maxDistance
            node.children.forEach { (edgeDistance, child) ->
                if (edgeDistance in lower..upper) pending.addLast(child)
            }
        }
        return result
    }

    private fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
