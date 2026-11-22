package org.fossify.gallery.helpers

import org.fossify.gallery.models.Medium

enum class GroupBy(val value: Int) {
    NONE(0), MONTH(1), TAG(2), RATING(3), SIZE(4), ALPHABET(5);

    companion object {
        fun from(value: Int) = entries.find { it.value == value } ?: NONE
    }
}

enum class GroupOrder(val value: Int) {
    ALPHABETICAL(0), COUNT(1);

    companion object {
        fun from(value: Int) = entries.find { it.value == value } ?: ALPHABETICAL
    }
}

/** A flattened, render-ready row for a grouped media grid/list. [SectionHeader] is a full-width
 * header (indented by [depth] for nested tag hierarchies); [Items] is the batch of media directly
 * below the header that emitted it (keyed by [sectionKey] so the same [Medium.path] can safely
 * appear under more than one section - a medium with several tags is meant to show up in each). */
sealed interface GroupRow {
    data class SectionHeader(
        val key: String,
        val label: String,
        val depth: Int,
        val exactCount: Int,
        val totalCount: Int,
        val hasChildren: Boolean,
        val isExpanded: Boolean,
        // Top-level ancestor tag name, only set for GroupBy.TAG headers (null for month/rating/
        // untagged) - lets the UI derive a stable color accent per branch of the hierarchy.
        val rootKey: String? = null,
        // 1..5 for a GroupBy.RATING header, null everywhere else - lets the UI render real star
        // icons instead of parsing/re-rendering the [label] string.
        val ratingValue: Int? = null,
        // All media belonging to this group, including descendants of a collapsed tag group. A
        // long-press on the header can therefore select hidden child rows as well.
        val mediaPaths: Set<String> = emptySet(),
    ) : GroupRow

    data class Items(val sectionKey: String, val media: List<Medium>) : GroupRow
}

/** Stable set of headers that can actually be expanded or collapsed. */
fun collapsibleGroupKeys(rows: List<GroupRow>): Set<String> =
    rows.asSequence()
        .filterIsInstance<GroupRow.SectionHeader>()
        .filter { it.hasChildren }
        .map { it.key }
        .toSet()

fun toggleGroupKey(collapsedKeys: Set<String>, key: String): Set<String> =
    if (key in collapsedKeys) collapsedKeys - key else collapsedKeys + key

/** Applies the same expand/collapse-all semantics to every grouped media surface. */
fun setGroupsExpanded(collapsedKeys: Set<String>, rows: List<GroupRow>, expanded: Boolean): Set<String> {
    val keys = collapsibleGroupKeys(rows)
    return if (expanded) collapsedKeys - keys else collapsedKeys + keys
}

/**
 * Applies a collapsed state to an already-built, fully expanded row model.
 *
 * Building tag rows is the expensive part for large libraries because it walks the hierarchy and
 * creates path sets. A header tap changes only which rows are visible, so it should not repeat
 * that work. The depth stack skips items and descendants below a collapsed header while preserving
 * the header itself and all of its counts.
 */
fun applyCollapsedGroups(rows: List<GroupRow>, collapsedKeys: Set<String>): List<GroupRow> {
    if (rows.isEmpty() || collapsedKeys.isEmpty()) return rows

    val result = ArrayList<GroupRow>(rows.size)
    val collapsedDepths = ArrayDeque<Int>()
    rows.forEach { row ->
        when (row) {
            is GroupRow.SectionHeader -> {
                while (collapsedDepths.lastOrNull()?.let { it >= row.depth } == true) {
                    collapsedDepths.removeLast()
                }
                if (collapsedDepths.isNotEmpty()) return@forEach

                val expanded = row.key !in collapsedKeys
                result += row.copy(isExpanded = expanded)
                if (!expanded && row.hasChildren) collapsedDepths.addLast(row.depth)
            }

            is GroupRow.Items -> {
                if (collapsedDepths.isEmpty()) result += row
            }
        }
    }
    return result
}

/** Walks backward from [firstVisibleIndex] collecting the nearest header at each shallower depth,
 * i.e. the breadcrumb path ("Urlaub ▸ Kroatien") to whatever section is currently at the top of a
 * scrolled grid/mosaic (which, unlike LazyColumn, has no real sticky header). */
fun currentBreadcrumb(rows: List<GroupRow>, firstVisibleIndex: Int): String? {
    if (rows.isEmpty()) return null
    var i = firstVisibleIndex.coerceIn(0, rows.size - 1)
    var neededDepth = Int.MAX_VALUE
    val labels = mutableListOf<String>()
    while (i >= 0) {
        val r = rows[i]
        if (r is GroupRow.SectionHeader && r.depth < neededDepth) {
            labels.add(0, r.label)
            neededDepth = r.depth
            if (r.depth == 0) break
        }
        i--
    }
    return if (labels.isEmpty()) null else labels.joinToString(" ▸ ")
}

/** Shared with MediaScreen's paged-grid header scan so both label a given rating identically. */
fun ratingLabelFor(rating: Int, unratedLabel: String): String =
    if (rating == 0) unratedLabel else "★".repeat(rating) + "☆".repeat(5 - rating)

fun buildRatingGroupRows(media: List<Medium>, order: GroupOrder, unratedLabel: String): List<GroupRow> {
    if (media.isEmpty()) return emptyList()
    val buckets = LinkedHashMap<Int, MutableList<Medium>>()
    media.forEach { m -> buckets.getOrPut(m.rating) { mutableListOf() }.add(m) }
    // Ratings communicate quality, not a user-defined name/count ordering: a five-star group is
    // always the first one to inspect and the unrated backlog always belongs at the end.
    // Keep [order] in the public signature for persisted settings/call-site compatibility; it is
    // deliberately not applied to rating groups.
    val ratingsPresent = buckets.keys.sortedDescending()
    return ratingsPresent.flatMap { rating ->
        val items = buckets.getValue(rating)
        val label = ratingLabelFor(rating, unratedLabel)
        val key = "rating:$rating"
        listOf(
            GroupRow.SectionHeader(
                key = key, label = label, depth = 0, exactCount = items.size, totalCount = items.size, hasChildren = items.isNotEmpty(), isExpanded = true,
                ratingValue = rating.takeIf { it > 0 },
                mediaPaths = items.mapTo(linkedSetOf()) { it.path },
            ),
            GroupRow.Items(sectionKey = key, media = items),
        )
    }
}

// Bucket boundaries shared between the streaming paged accumulator (MediaScreen's
// PagedRowsAccumulator, one item at a time) and the full-list builder below, so both agree on
// where a group starts. Not translated - "MB"/"KB" read the same in every locale this app ships.
fun sizeLabelFor(sizeBytes: Long): String = when {
    sizeBytes >= 100L * 1024 * 1024 -> "≥ 100 MB"
    sizeBytes >= 10L * 1024 * 1024 -> "10–100 MB"
    sizeBytes >= 1L * 1024 * 1024 -> "1–10 MB"
    sizeBytes >= 100L * 1024 -> "100 KB–1 MB"
    else -> "< 100 KB"
}

fun alphabetLabelFor(name: String): String {
    val c = name.trimStart().firstOrNull()?.uppercaseChar() ?: '#'
    return if (c.isLetter()) c.toString() else "#"
}

/** Buckets [media] by [sizeLabelFor]. Header order follows [desc] (largest-first for the normal
 * descending-size sort this grouping mode is paired with, smallest-first if the user flipped to
 * ascending) - each bucket's own items stay in whatever order [media] already had them in. */
fun buildSizeGroupRows(media: List<Medium>, desc: Boolean): List<GroupRow> {
    if (media.isEmpty()) return emptyList()
    val buckets = LinkedHashMap<String, MutableList<Medium>>()
    media.forEach { m -> buckets.getOrPut(sizeLabelFor(m.size)) { mutableListOf() }.add(m) }
    val order = listOf("≥ 100 MB", "10–100 MB", "1–10 MB", "100 KB–1 MB", "< 100 KB").let { if (desc) it else it.reversed() }
    return order.filter { it in buckets }.flatMap { label ->
        val items = buckets.getValue(label)
        val key = "size:$label"
        listOf(
            GroupRow.SectionHeader(key = key, label = label, depth = 0, exactCount = items.size, totalCount = items.size, hasChildren = false, isExpanded = true, mediaPaths = items.mapTo(linkedSetOf()) { it.path }),
            GroupRow.Items(sectionKey = key, media = items),
        )
    }
}

/** Buckets [media] by [alphabetLabelFor]. Header order follows [desc] (A-Z, then "#", or reversed). */
fun buildAlphabetGroupRows(media: List<Medium>, desc: Boolean): List<GroupRow> {
    if (media.isEmpty()) return emptyList()
    val buckets = LinkedHashMap<String, MutableList<Medium>>()
    media.forEach { m -> buckets.getOrPut(alphabetLabelFor(m.name)) { mutableListOf() }.add(m) }
    var ordered = buckets.keys.sortedWith(compareBy({ it == "#" }, { it }))
    if (desc) ordered = ordered.reversed()
    return ordered.flatMap { label ->
        val items = buckets.getValue(label)
        val key = "alpha:$label"
        listOf(
            GroupRow.SectionHeader(key = key, label = label, depth = 0, exactCount = items.size, totalCount = items.size, hasChildren = false, isExpanded = true, mediaPaths = items.mapTo(linkedSetOf()) { it.path }),
            GroupRow.Items(sectionKey = key, media = items),
        )
    }
}

/** Builds the nested tag-hierarchy tree (or, with [onlyTopLevelTags], a flat pooled-by-root
 * view) as a pre-flattened row list ready for a LazyGrid/LazyColumn. A medium tagged with both a
 * parent and a child tag intentionally appears under both sections - see the "exactByTag" map
 * below, which indexes every one of a medium's own tags, not just one. */
fun buildTagGroupRows(
    media: List<Medium>,
    tagsByPath: Map<String, List<String>>,
    hierarchy: Map<String, String>,
    order: GroupOrder,
    onlyTopLevelTags: Boolean,
    collapsedKeys: Set<String>,
    untaggedLabel: String,
): List<GroupRow> {
    if (media.isEmpty()) return emptyList()

    val exactByTag = LinkedHashMap<String, MutableList<Medium>>()
    val untagged = mutableListOf<Medium>()
    media.forEach { m ->
        val tags = tagsByPath[m.path].orEmpty()
        if (tags.isEmpty()) untagged.add(m) else tags.forEach { t -> exactByTag.getOrPut(t) { mutableListOf() }.add(m) }
    }

    val rows = mutableListOf<GroupRow>()

    if (onlyTopLevelTags) {
        val rootByTag = HashMap<String, String>()
        val pooled = LinkedHashMap<String, MutableList<Medium>>()
        val pooledPaths = HashMap<String, MutableSet<String>>()
        exactByTag.forEach { (tag, items) ->
            val root = rootByTag.getOrPut(tag) { rootOf(tag, hierarchy) }
            val bucket = pooled.getOrPut(root) { mutableListOf() }
            val paths = pooledPaths.getOrPut(root) { HashSet() }
            items.forEach { m -> if (paths.add(m.path)) bucket.add(m) }
        }
        orderKeys(pooled.keys, order) { pooled.getValue(it).size }.forEach { root ->
            val items = pooled.getValue(root)
            val key = "tag:$root"
            val expanded = key !in collapsedKeys
            rows += GroupRow.SectionHeader(key, root, 0, items.size, items.size, items.isNotEmpty(), expanded, rootKey = root, mediaPaths = items.mapTo(linkedSetOf()) { it.path })
            if (expanded) rows += GroupRow.Items(key, items)
        }
    } else {
        val childrenByParent = childrenByParentMap(hierarchy)
        val tagsWithContent = HashSet<String>()
        exactByTag.keys.forEach { tag ->
            var cur: String? = tag
            val seen = HashSet<String>()
            while (cur != null && seen.add(cur)) {
                if (!tagsWithContent.add(cur)) break
                cur = hierarchy[cur]
            }
        }
        // Group headers need both descendant counts and the complete path set for selection. The
        // previous implementation walked the same subtree once for every header and again for
        // every count-based sort comparison. Memoizing both projections turns repeated hierarchy
        // work into one traversal per reachable tag, which matters for large libraries and deep
        // tag trees.
        val visibleChildrenByParent = childrenByParent.mapValues { (_, children) ->
            children.filter { it in tagsWithContent }
        }
        val totalCountCache = HashMap<String, Int>()
        val groupPathsCache = HashMap<String, Set<String>>()

        fun totalCountFor(tag: String, visiting: MutableSet<String> = HashSet()): Int {
            totalCountCache[tag]?.let { return it }
            if (!visiting.add(tag)) return 0
            var total = exactByTag[tag]?.size ?: 0
            visibleChildrenByParent[tag].orEmpty().forEach { child ->
                total += totalCountFor(child, visiting)
            }
            visiting.remove(tag)
            totalCountCache[tag] = total
            return total
        }

        fun groupPathsFor(tag: String, visiting: MutableSet<String> = HashSet()): Set<String> {
            groupPathsCache[tag]?.let { return it }
            if (!visiting.add(tag)) return emptySet()
            val paths = linkedSetOf<String>()
            exactByTag[tag].orEmpty().forEach { paths += it.path }
            visibleChildrenByParent[tag].orEmpty().forEach { child ->
                paths += groupPathsFor(child, visiting)
            }
            visiting.remove(tag)
            val result = paths.toSet()
            groupPathsCache[tag] = result
            return result
        }

        // A tag counts as a rendering root if walking its parent chain leads back to itself -
        // true both for a plain top-level tag (no parent) and, defensively, for a member of a
        // corrupted/cyclic hierarchy entry (rootOf resolves each cycle member to itself rather
        // than looping forever), so a bad Config.tagHierarchy entry can't hide media from view.
        val roots = orderKeys(tagsWithContent.filter { rootOf(it, hierarchy) == it }.toSet(), order) { totalCountFor(it) }

        fun emit(tag: String, depth: Int, ancestors: Set<String>, rootLabel: String) {
            if (tag in ancestors) return
            val exact = exactByTag[tag].orEmpty()
            val children = visibleChildrenByParent[tag].orEmpty()
            val total = totalCountFor(tag)
            val key = "tag:$tag"
            val expanded = key !in collapsedKeys
            rows += GroupRow.SectionHeader(key, tag, depth, exact.size, total, exact.isNotEmpty() || children.isNotEmpty(), expanded, rootKey = rootLabel, mediaPaths = groupPathsFor(tag))
            if (!expanded) return
            if (exact.isNotEmpty()) rows += GroupRow.Items(key, exact)
            orderKeys(children.toSet(), order) { totalCountFor(it) }.forEach { emit(it, depth + 1, ancestors + tag, rootLabel) }
        }
        roots.forEach { emit(it, 0, emptySet(), rootLabel = it) }
    }

    if (untagged.isNotEmpty()) {
        val key = "untagged"
        val expanded = key !in collapsedKeys
        rows += GroupRow.SectionHeader(key, untaggedLabel, 0, untagged.size, untagged.size, true, expanded, mediaPaths = untagged.mapTo(linkedSetOf()) { it.path })
        if (expanded) rows += GroupRow.Items(key, untagged)
    }
    return rows
}

private fun orderKeys(keys: Set<String>, order: GroupOrder, countOf: (String) -> Int): List<String> = when (order) {
    GroupOrder.ALPHABETICAL -> keys.sortedBy { it.lowercase() }
    GroupOrder.COUNT -> keys.sortedByDescending(countOf)
}
