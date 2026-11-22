package org.fossify.gallery.extensions

import android.view.View
import org.fossify.commons.extensions.toast

fun View.showContentDescriptionOnLongClick() {
    setOnLongClickListener {
        val contentDescription = contentDescription
        if (contentDescription != null) {
            context.toast(contentDescription.toString())
        }
        true
    }
}
