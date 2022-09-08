package com.b4kancs.rxredditdemo.utils

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import io.reactivex.Observable


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

fun View.resetOnTouchListener() {
    this.setOnTouchListener(object : OnSwipeTouchListener() {})
}

fun <T : Any> Observable<T>.toV3Observable() : io.reactivex.rxjava3.core.Observable<T> =
    RxJavaBridge.toV3Observable(this)