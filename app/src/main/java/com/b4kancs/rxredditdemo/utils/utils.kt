package com.b4kancs.rxredditdemo.utils

import android.content.Context
import android.content.res.Configuration

enum class Orientation {
    LANDSCAPE, PORTRAIT;

    companion object {
        fun fromInt(value: Int): Orientation =
            when(value) {
                Configuration.ORIENTATION_LANDSCAPE -> LANDSCAPE
                Configuration.ORIENTATION_PORTRAIT -> PORTRAIT
                else -> throw IllegalStateException("ILLEGAL ORIENTATION ARGUMENT")
            }
    }
}

fun dpToPixel(dp: Int, context: Context): Int = (dp * context.resources.displayMetrics.density).toInt()

fun Int.dpToPx(context: Context): Int = dpToPixel(this, context)
