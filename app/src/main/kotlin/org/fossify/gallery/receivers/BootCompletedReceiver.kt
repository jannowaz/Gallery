package org.fossify.gallery.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.gallery.workers.BootScanWorker

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != ACTION_QUICKBOOT_POWERON) {
            return
        }
        // Enqueueing is a fast, non-blocking call - the actual scan runs in BootScanWorker under
        // WorkManager's own constraints/scheduling instead of directly on an unconstrained
        // background thread during the boot storm (see BootScanWorker's doc comment).
        BootScanWorker.schedule(context)
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
