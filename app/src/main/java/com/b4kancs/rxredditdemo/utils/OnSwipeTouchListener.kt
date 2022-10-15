package com.b4kancs.rxredditdemo.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import kotlin.math.abs

/* Credit to github.com/nesquena and github.com/fernandospr at https://gist.github.com/nesquena/ed58f34791da00da9751

MIT License
Copyright (c) 2019 Nathan Esquenazi
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
(the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:
The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

* */

/*
Usage:
    myView.setOnTouchListener(object: OnSwipeTouchListener(this@MainActivity) {
      override fun onSwipeDown() {
        Toast.makeText(this@MainActivity, "Down", Toast.LENGTH_SHORT).show();
      }
    })
*/

// I have modified the above implementation somewhat, mostly to support single tap and double tap events
open class OnSwipeTouchListener(context: Context) : OnTouchListener {

    private lateinit var view: View

    companion object {
        private const val SWIPE_THRESHOLD = 20
        private const val SWIPE_VELOCITY_THRESHOLD = 0
    }

    private val gestureDetector: GestureDetector

    init {
        gestureDetector = GestureDetector(context, GestureListener())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        this.view = view
        return gestureDetector.onTouchEvent(motionEvent)
    }

    inner class GestureListener : SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent?): Boolean {
            return onDown()
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return onSingleTap()
        }

        override fun onDoubleTap(e: MotionEvent?): Boolean {
            return onDoubleTap()
        }

        // Determines the fling velocity and then fires the appropriate swipe event accordingly
        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val result = false
            try {
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                    }
                } else {
                    if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            onSwipeDown()
                        } else {
                            onSwipeUp()
                        }
                    }
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }

            return result
        }
    }

    open fun onSwipeRight() {}

    open fun onSwipeLeft() {}

    open fun onSwipeUp() {}

    open fun onSwipeDown() {}

    open fun onDown(): Boolean { return false }

    open fun onSingleTap(): Boolean { return false }

    open fun onDoubleTap(): Boolean { return false }
}