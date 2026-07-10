package org.fossify.gallery.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.gallery.workers.BootScanWorker

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Enqueueing is a fast, non-blocking call - the actual scan runs in BootScanWorker under
        // WorkManager's own constraints/scheduling instead of directly on an unconstrained
        // background thread during the boot storm (see BootScanWorker's doc comment).
        BootScanWorker.schedule(context)
    }
}
