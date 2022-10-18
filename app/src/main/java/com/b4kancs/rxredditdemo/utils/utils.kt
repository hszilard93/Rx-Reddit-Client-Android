package com.b4kancs.rxredditdemo.utils

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.model.Post
import com.google.android.material.snackbar.Snackbar
import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import io.reactivex.Observable
import java.util.*

const val ANIMATION_DURATION_LONG = 500L
const val ANIMATION_DURATION_SHORT = 300L

enum class Orientation {
    LANDSCAPE, PORTRAIT;

    companion object {
        fun fromInt(value: Int): Orientation =
            when (value) {
                Configuration.ORIENTATION_LANDSCAPE -> LANDSCAPE
                Configuration.ORIENTATION_PORTRAIT -> PORTRAIT
                else -> throw IllegalStateException("ILLEGAL ORIENTATION ARGUMENT")
            }
    }
}

fun calculateDateAuthorSubredditText(post: Post): String {
    val postAgeInMinutes = (Date().time - (post.createdAt * 1000L)) / (60 * 1000L) // time difference in ms divided by a minute
    val postAge = when (postAgeInMinutes) {
        in 0 until 60 -> postAgeInMinutes to "minute(s)"
        in 60 until 1440 -> postAgeInMinutes / 60 to "hour(s)"
        in 1440 until 525600 -> postAgeInMinutes / 1440 to "day(s)"
        in 525600 until Long.MAX_VALUE -> postAgeInMinutes / 525600 to "year(s)"
        else -> postAgeInMinutes to "ms"
    }
    return "posted ${postAge.first} ${postAge.second} ago by ${post.author} to r/${post.subreddit}"

}

fun dpToPixel(dp: Int, context: Context): Int = (dp * context.resources.displayMetrics.density).toInt()

fun Int.dpToPx(context: Context): Int = dpToPixel(this, context)

fun animateViewLayoutHeightChange(
    view: View,
    oldHeight: Int,
    newHeight: Int,
    duration: Long,
    endWithThis: () -> Unit = {}
) {
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
    animatorSet.addListener(object : Animator.AnimatorListener {
        override fun onAnimationEnd(animation: Animator?) {
            endWithThis()
        }

        override fun onAnimationStart(animation: Animator?) {}
        override fun onAnimationCancel(animation: Animator?) {}
        override fun onAnimationRepeat(animation: Animator?) {}

    })
    animatorSet.start()
}

fun animateShowViewAlpha(view: View) {
    view.alpha = 0f
    view.isVisible = true
    view.animate()
        .alpha(1f)
        .setDuration(ANIMATION_DURATION_LONG)
        .start()
}

fun animateHideViewAlpha(view: View) {
    view.animate()
        .alpha(0f)
        .setDuration(ANIMATION_DURATION_LONG)
        .withEndAction { view.isVisible = false }
        .start()
}

fun hideKeyboard(view: View) {
    val inputMethodManager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun makeSnackBar(view: View, stringId: Int): Snackbar {
    val typedValue = TypedValue()
    val theme = view.context.theme
    theme.resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValue, true)
    val backgroundColor = typedValue.data
    theme.resolveAttribute(com.google.android.material.R.attr.colorTertiary, typedValue, true)
    val textColor = typedValue.data

    return Snackbar
        .make(view, stringId, Snackbar.LENGTH_SHORT)
        .setBackgroundTint(backgroundColor)
        .setTextColor(textColor)
}

fun View.resetOnTouchListener(context: Context) {
    // TODO
    this.setOnTouchListener(object : OnSwipeTouchListener(context) {})
}

fun <T : Any> Observable<T>.toV3Observable(): io.reactivex.rxjava3.core.Observable<T> =
    RxJavaBridge.toV3Observable(this)