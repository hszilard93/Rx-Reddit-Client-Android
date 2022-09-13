package com.b4kancs.rxredditdemo.utils

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import io.reactivex.Observable
import java.time.Duration


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

fun animateViewLayoutHeightChange(view: View, oldHeight: Int, newHeight: Int, duration: Long) {

    val slideAnimator = ValueAnimator
        .ofInt(oldHeight, newHeight)
        .setDuration(duration)

    slideAnimator.addUpdateListener { animation ->
        // get the value the interpolator is at
        val value = animation.animatedValue as Int
        view.layoutParams.height = value
        // force all layouts to see which ones are affected by this layouts height change
        view.requestLayout()
    }

    val animatorSet = AnimatorSet()
    animatorSet.play(slideAnimator)
    animatorSet.interpolator = AccelerateDecelerateInterpolator()
    animatorSet.start()
}

fun View.resetOnTouchListener(context: Context) {
    this.setOnTouchListener(object : OnSwipeTouchListener(context) {})
}

fun <T : Any> Observable<T>.toV3Observable() : io.reactivex.rxjava3.core.Observable<T> =
    RxJavaBridge.toV3Observable(this)