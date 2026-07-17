package org.fossify.gallery.compose.util

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius

/**
 * Shared runner for per-file XMP batch operations (tagging/rating a selection, tag merge/rename):
 * tracks progress in a globally observable slot ([XmpBatchIndicator] renders it wherever the
 * operation was triggered) and always ends with an honest toast - success count on its own, or
 * "N done, M failed" when individual writes failed. These operations used to be fire-and-forget
 * loops: hundreds of XMP writes over many seconds with no progress, no failure count, and no
 * completion signal.
 */
object XmpBatch {

    /** Non-null while a batch runs (done/total). Single slot - these ops are user-triggered one
     * at a time from a modal surface. */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** Runs [op] per item on IO. Returns the failure count. [successMessage] overrides the
     * default "N files updated" toast on a clean run. */
    suspend fun <T> run(context: Context, items: List<T>, successMessage: String? = null, op: (T) -> Boolean): Int {
        if (items.isEmpty()) return 0
        var failed = 0
        try {
            progress = 0 to items.size
            withContext(Dispatchers.IO) {
                items.forEachIndexed { i, item ->
                    if (!op(item)) failed++
                    progress = (i + 1) to items.size
                }
            }
        } finally {
            progress = null
        }
        withContext(Dispatchers.Main) {
            val msg = if (failed > 0) context.getString(R.string.notif_batch_done_with_failures, items.size - failed, failed)
            else successMessage ?: context.getString(R.string.applied_to_count, items.size)
            Toast.makeText(context, msg, if (failed > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
        return failed
    }
}

/** Renders the running [XmpBatch] as a small progress card - place once per surface that
 * triggers batch operations (RateAndTagSheet, TagBrowser). Renders nothing when idle. */
@Composable
fun XmpBatchIndicator(modifier: Modifier = Modifier) {
    val progress = XmpBatch.progress ?: return
    val (done, total) = progress
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Column(Modifier.padding(12.dp)) {
            LinearProgressIndicator(
                progress = { if (total > 0) done / total.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.batch_applying_progress, done, total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
