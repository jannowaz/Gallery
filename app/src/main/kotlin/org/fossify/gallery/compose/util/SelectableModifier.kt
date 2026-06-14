package org.fossify.gallery.compose.util

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.selectableItem(
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToSelect: () -> Unit = {},
): Modifier = composed {
    val view = LocalView.current

    this.combinedClickable(
        onClick = {
            if (isSelectionMode) onClick()
            else onClick()
        },
        onLongClick = {
            onLongClick()
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        },
    )
}
