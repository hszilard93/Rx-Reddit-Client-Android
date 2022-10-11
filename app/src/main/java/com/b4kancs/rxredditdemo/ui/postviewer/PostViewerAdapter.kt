package com.b4kancs.rxredditdemo.ui.postviewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.*
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.PostViewerListItemBinding
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.ui.PostComparator
import com.b4kancs.rxredditdemo.utils.animateHideViewAlpha
import com.b4kancs.rxredditdemo.utils.animateShowViewAlpha
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.PublishSubject

class PostViewerAdapter(
    private val context: Context,
    // This is how the RecyclerView scrolls to the next/previous ViewHolder.
    private val onPositionChangedCallback: (Int) -> Unit
) : PagingDataAdapter<Post, PostViewerAdapter.PostViewerViewHolder>(PostComparator) {
    companion object {
        private const val LOG_TAG = "PostViewerAdapter"
    }

    val readyToBeDrawnSubject: PublishSubject<Int> = PublishSubject.create()
    val disposables = CompositeDisposable()
    private val positionSubject = PublishSubject.create<Int>()
    private val viewHolderMap = HashMap<PostViewerViewHolder, Int>()
    private var isRecentlyDisplayed = true

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        positionSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { nextPosition ->
                Log.i(LOG_TAG, "onPositionChangedCallback($nextPosition)")
                onPositionChangedCallback(nextPosition)
            }.addTo(disposables)
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewerViewHolder {
        return PostViewerViewHolder(
            PostViewerListItemBinding.inflate(LayoutInflater.from(context), parent, false)
        )
    }

    override fun onBindViewHolder(viewHolder: PostViewerViewHolder, position: Int) {
        val post = getItem(position)
        post?.let(viewHolder::bind)
        viewHolderMap[viewHolder] = position
        isRecentlyDisplayed = false
    }

    override fun onViewRecycled(holder: PostViewerViewHolder) {
        holder.hudElements.clear()
        super.onViewRecycled(holder)
    }

    fun getViewHolderForPosition(pos: Int): PostViewerViewHolder? {
        for (k in viewHolderMap.keys) {
            if (viewHolderMap[k] == pos) return k
        }
        return null
    }

    inner class PostViewerViewHolder(val binding: PostViewerListItemBinding) : RecyclerView.ViewHolder(binding.root) {

        val hudElements = ArrayList<View>()
        var isHudVisible = !isRecentlyDisplayed

        init {
            // For the imageview to fill the screen, I make it's height equal to the window's height minus the status bar's height.
            val statusBarId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeight = context.resources.getDimensionPixelSize(statusBarId)
            val navBarId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            val navBarHeight = context.resources.getDimension(navBarId)
            Log.d(LOG_TAG, "status bar height: $statusBarHeight; nav bar height: $navBarHeight")
            val imageViewNewHeight =
                context.resources.displayMetrics.heightPixels -
                        statusBarHeight +
                        if (navBarHeight > 0) 0 else 100
            Log.d(LOG_TAG, "The imageview's height will be set to: $imageViewNewHeight")
            binding.postLargeItemImageView.layoutParams.height = imageViewNewHeight
        }

        fun bind(post: Post) {
            setUpImageView(post, this)
            // Always start with the ScrollView scrolled to the top.
            binding.postLargeScrollView.apply {
                doOnLayout {
                    binding.postLargeScrollView.fling(-20000)
                }
            }
            binding.postLargeItemTitleTextView.text = post.title
            binding.postLargeItemScoreTextView.text = post.score.toString()
            binding.postLargeItemCommentsTextView.text = "${post.numOfComments} comments"

            hudElements.addAll(listOf<View>(binding.postLargeItemLeftHudConstraintLayout, binding.postLargeItemRightHudConstraintLayout))
            hudElements.forEach { it.isVisible = isHudVisible }
        }

        @SuppressLint("CheckResult", "ClickableViewAccessibility")
        private fun setUpImageView(post: Post, holder: PostViewerViewHolder) {
            binding.postLargeItemImageView.transitionName = post.links!!.first()

            val zoomableImageView = binding.postLargeItemImageView
            val link = post.links[0]
            Glide.with(context).load(link)
                .apply(
                    RequestOptions()
                        .error(R.drawable.ic_not_found_24)
                        .placeholder(R.drawable.ic_download)
                        .dontTransform()
                )
                .addListener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean) =
                        false

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        readyToBeDrawnSubject.onNext(layoutPosition)
                        return false
                    }
                })
                .into(zoomableImageView)

            binding.postLargeItemLeftHudConstraintLayout.clicks()
                .subscribe {
                    Log.i(LOG_TAG, "Left hud clicked, paging left.")
                    positionSubject.onNext(layoutPosition - 1)
                }.addTo(disposables)

            binding.postLargeItemRightHudConstraintLayout.clicks()
                .subscribe {
                    Log.i(LOG_TAG, "Right hud clicked, paging right.")
                    positionSubject.onNext(layoutPosition + 1)
                }.addTo(disposables)

            val onSingleTapSubject: PublishSubject<Unit> = PublishSubject.create()
            onSingleTapSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    Log.d(LOG_TAG, "isHudVisible = $isHudVisible")
                    if (!isHudVisible) {
                        Log.i(LOG_TAG, "Showing HUD. HUD elements size: ${hudElements.size}")
                        isHudVisible = true
                        hudElements.forEach(::animateShowViewAlpha)
                    } else {
                        Log.i(LOG_TAG, "Hiding HUD. HUD elements size: ${hudElements.size}")
                        isHudVisible = false
                        hudElements.forEach(::animateHideViewAlpha)
                    }
                }.addTo(disposables)

            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                    Log.i(LOG_TAG, "GestureDetector: onSingleTapConfirmed")
                    onSingleTapSubject.onNext(Unit)
                    return false
                }

                override fun onDoubleTap(e: MotionEvent?): Boolean {
                    Log.i(LOG_TAG, "GestureDetector: onDoubleTap")
                    return isHudVisible
                }
            })

            zoomableImageView.setOnTouchListener { _, e ->
                gestureDetector.onTouchEvent(e)
            }
        }
    }
}