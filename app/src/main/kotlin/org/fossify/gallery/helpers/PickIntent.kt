package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Intent
import org.fossify.commons.extensions.toast

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

    /** Convenience for the onNavigateToViewer callbacks: pick the tapped item in pick mode, else run
     * [openViewer]. [paths]/[index] mirror the viewer callback - the tapped item is paths[index]. */
    fun handleMediaTap(activity: Activity?, paths: List<String>, index: Int, openViewer: () -> Unit) {
        if (activity != null && isPick(activity.intent)) returnResult(activity, paths.getOrNull(index))
        else openViewer()
    }
}
