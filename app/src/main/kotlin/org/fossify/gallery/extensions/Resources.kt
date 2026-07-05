package org.fossify.gallery.extensions

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import org.fossify.gallery.R

fun Resources.getActionBarHeight(context: Context): Int {
    val tv = TypedValue()
    return if (context.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
        TypedValue.complexToDimensionPixelSize(tv.data, displayMetrics)
    } else
        0
}

fun Resources.getBottomActionsHeight(): Int {
    return getDimensionPixelSize(R.dimen.bottom_actions_height) +
                getDimensionPixelSize(org.fossify.commons.R.dimen.normal_margin)
}
