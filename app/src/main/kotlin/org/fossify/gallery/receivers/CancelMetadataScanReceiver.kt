package org.fossify.gallery.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.gallery.workers.MetadataSyncWorker

class CancelMetadataScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MetadataSyncWorker.cancel(context.applicationContext)
    }

    companion object {
        const val ACTION = "org.fossify.gallery.CANCEL_METADATA_SCAN"
    }
}
