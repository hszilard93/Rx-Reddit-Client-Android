package com.b4kancs.rxredditdemo.utils

import android.content.Context
import android.util.DisplayMetrics
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

// Custom layout manager for manipulating a RecyclerView's scroll ability for our own nefarious ends.
// Now it also comes with a slower default 'smoothscrolling' speed!
class CustomLinearLayoutManager(
    context: Context,
    orientation: Int,
    canScrollByDefault: Boolean = true
) : LinearLayoutManager(context, orientation, false) {

    var canScrollHorizontally = canScrollByDefault
    var canScrollVertically = canScrollByDefault
    var scrollSpeedInMillisecondsPerInch = 50f  // This provides a slower scrolling speed. Default is 25f.

    override fun canScrollHorizontally(): Boolean = canScrollHorizontally
    override fun canScrollVertically(): Boolean = canScrollVertically

    override fun smoothScrollToPosition(recyclerView: RecyclerView?, state: RecyclerView.State?, position: Int) {
        val customSmoothScroller = object : LinearSmoothScroller(recyclerView!!.context) {
            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics?): Float {
                return scrollSpeedInMillisecondsPerInch / displayMetrics!!.densityDpi
            }
        }
        customSmoothScroller.targetPosition = position
        startSmoothScroll(customSmoothScroller)
    }
}