package org.fossify.gallery.compose.util

import android.app.Activity
import android.app.PendingIntent
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges MediaStore consent dialogs (createTrashRequest/createDeleteRequest/createWriteRequest)
 * into a suspendable call. Obtain one with [rememberMediaStoreConsent] inside any composable hosted
 * by a ComponentActivity, then `consent.request(pendingIntent)` suspends until the user accepts or
 * dismisses the system dialog and returns whether it was granted.
 */
class MediaStoreConsent(
    private val pending: MutableState<CompletableDeferred<Boolean>?>,
    private val launch: (IntentSenderRequest) -> Unit,
) {
    suspend fun request(sender: IntentSender): Boolean {
        pending.value?.complete(false)
        val deferred = CompletableDeferred<Boolean>()
        pending.value = deferred
        launch(IntentSenderRequest.Builder(sender).build())
        return deferred.await()
    }

    suspend fun request(pendingIntent: PendingIntent): Boolean = request(pendingIntent.intentSender)
}

@Composable
fun rememberMediaStoreConsent(): MediaStoreConsent {
    val pending = remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
        pending.value?.complete(result.resultCode == Activity.RESULT_OK)
        pending.value = null
    }
    return remember(launcher) { MediaStoreConsent(pending) { req -> launcher.launch(req) } }
}
