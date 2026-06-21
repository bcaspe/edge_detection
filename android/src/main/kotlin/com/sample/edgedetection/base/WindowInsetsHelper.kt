package com.sample.edgedetection.base

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Adds system bar insets on top of the view's layout padding (e.g. nav gesture area). */
fun View.applySystemBarPadding(
    includeTop: Boolean = false,
    includeBottom: Boolean = true,
) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(
            initialLeft + bars.left,
            if (includeTop) initialTop + bars.top else initialTop,
            initialRight + bars.right,
            if (includeBottom) initialBottom + bars.bottom else initialBottom,
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
