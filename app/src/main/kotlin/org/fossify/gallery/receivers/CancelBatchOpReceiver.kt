package org.fossify.gallery.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

class CancelBatchOpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(jobId)
    }

    companion object {
        const val ACTION = "org.fossify.gallery.CANCEL_BATCH_OP"
        const val EXTRA_JOB_ID = "job_id"
    }
}
