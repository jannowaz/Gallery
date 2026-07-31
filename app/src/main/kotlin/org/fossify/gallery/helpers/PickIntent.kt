package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Intent
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R

/**
 * Support for acting as a system image/video picker.
 *
 * The manifest advertises ComposeExplorerActivity for ACTION_GET_CONTENT and ACTION_PICK (image and
 * video mime types), so Gallery shows up in other apps' "select a photo" chooser. But the Compose UI never
 * handled the pick flow - it just opened the normal browsing gallery, tapping a photo opened the
 * internal viewer, and the calling app got RESULT_CANCELED. This restores the flow the legacy
 * MediaActivity used to provide: in pick mode a media tap returns that item's content URI to the
 * caller instead of opening the viewer.
 */
object PickIntent {

    /** Whether [intent] launched us as a picker (so a media tap should return a result, not view). */
    fun isPick(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_GET_CONTENT || intent?.action == Intent.ACTION_PICK

    /** Whether the caller allowed selecting more than one item (EXTRA_ALLOW_MULTIPLE). */
    fun allowsMultiple(intent: Intent?): Boolean =
        intent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) == true

    /** The media kind the caller asked for: 1 = images only, 2 = videos only, 0 = either. Derived
     * from the intent type and EXTRA_MIME_TYPES, so a "select an image" pick can't return a video. */
    fun requiredKind(intent: Intent?): Int {
        intent ?: return 0
        val types = buildList {
            intent.type?.let { add(it) }
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.let { addAll(it) }
        }
        val hasImage = types.any { it.startsWith("image/") }
        val hasVideo = types.any { it.startsWith("video/") }
        return when {
            hasImage && !hasVideo -> 1
            hasVideo && !hasImage -> 2
            else -> 0
        }
    }

    private fun matchesRequiredKind(intent: Intent?, path: String): Boolean {
        val kind = requiredKind(intent)
        if (kind == 0) return true
        val isVideo = path.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
        return if (kind == 2) isVideo else !isVideo
    }

    /**
     * Returns [path] to the caller as a readable content:// URI and finishes. FLAG_GRANT_READ_URI_
     * PERMISSION hands the calling package temporary read access to that MediaStore item. No-op with
     * a toast if the file has no resolvable MediaStore URI (e.g. not yet indexed).
     */
    fun returnResult(activity: Activity, path: String?) {
        val uri = path?.let { MediaStoreOps.uriForPath(activity, it) }
        if (uri == null) {
            activity.toast(activity.getString(org.fossify.commons.R.string.unknown_error_occurred))
            return
        }
        val data = Intent().apply {
            setDataAndType(uri, activity.contentResolver.getType(uri) ?: "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.setResult(Activity.RESULT_OK, data)
        activity.finish()
    }

    /**
     * Returns several [paths] to the caller as a ClipData of readable content:// URIs and finishes -
     * the EXTRA_ALLOW_MULTIPLE result shape. Silently drops any path that doesn't match the requested
     * kind or has no resolvable URI; no-op with a toast if nothing valid remains.
     */
    fun returnMultiple(activity: Activity, paths: List<String>) {
        val uris = paths
            .filter { matchesRequiredKind(activity.intent, it) }
            .mapNotNull { MediaStoreOps.uriForPath(activity, it) }
        if (uris.isEmpty()) {
            activity.toast(activity.getString(org.fossify.commons.R.string.unknown_error_occurred))
            return
        }
        val clip = android.content.ClipData.newUri(activity.contentResolver, "media", uris.first())
        uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
        val data = Intent().apply {
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.setResult(Activity.RESULT_OK, data)
        activity.finish()
    }

    /** Convenience for the onNavigateToViewer callbacks: pick the tapped item in pick mode, else run
     * [openViewer]. [paths]/[index] mirror the viewer callback - the tapped item is paths[index]. */
    fun handleMediaTap(activity: Activity?, paths: List<String>, index: Int, openViewer: () -> Unit) {
        if (activity != null && isPick(activity.intent)) {
            val path = paths.getOrNull(index)
            if (path != null && !matchesRequiredKind(activity.intent, path)) {
                activity.toast(activity.getString(R.string.pick_unsupported_type))
                return
            }
            returnResult(activity, path)
        } else openViewer()
    }
}
