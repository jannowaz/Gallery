package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import org.fossify.commons.extensions.handleDeletePasswordProtection
import org.fossify.gallery.extensions.config

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

/** Runs a normal user delete (move to the recycle bin). When the user explicitly enabled both
 * the recycle bin and "skip delete confirmation", the recycle bin is the safety net and the
 * additional delete-password prompt is skipped as well. Permanent deletion from the recycle bin
 * deliberately continues to use [withDeletePasswordCheck]. */
fun Context.withRecycleBinDeletePasswordCheck(onProceed: () -> Unit) {
    if (config.useRecycleBin && config.skipDeleteConfirmation) onProceed()
    else withDeletePasswordCheck(onProceed)
}
