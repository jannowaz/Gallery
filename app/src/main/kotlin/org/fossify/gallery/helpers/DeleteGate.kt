package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import org.fossify.commons.extensions.handleDeletePasswordProtection

/**
 * Runs [onProceed] only after the delete-password check passes. commons
 * [handleDeletePasswordProtection] shows the SecurityDialog (PIN/pattern/fingerprint) when the user
 * enabled "Delete password protection" and invokes the callback only on success; when the protection
 * is off it proceeds immediately, so this is a cheap pass-through in the common case.
 *
 * The "Delete password" setting was fully configurable but never consulted by any Compose delete
 * path - deletes ran straight through. Wrapping the destructive step (not the confirm-dialog dismiss)
 * in this gate is what actually enforces it. Falls back to running the action directly if [this]
 * isn't an Activity, since there's no Activity to host the prompt then.
 */
fun Context.withDeletePasswordCheck(onProceed: () -> Unit) {
    (this as? Activity)?.handleDeletePasswordProtection { onProceed() } ?: onProceed()
}
