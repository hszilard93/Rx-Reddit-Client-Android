package com.b4kancs.rxredditdemo.ui.postviewer

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.PostViewerListItemBinding
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.ui.PostComparator
import com.b4kancs.rxredditdemo.utils.resetOnTouchListener
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.PublishSubject

class PostViewerAdapter(
    private val context: Context,
    private val onPositionChangedCallback: (Int) -> Unit
) : PagingDataAdapter<Post, PostViewerAdapter.PostViewerViewHolder>(PostComparator) {
    companion object {
        private const val LOG_TAG = "PostViewerAdapter"
    }

    var latestPosition: Int? = null

    private val positionSubject = PublishSubject.create<Int>()
    private val disposables = CompositeDisposable()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        positionSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { nextPosition ->
                onPositionChangedCallback(nextPosition)
            }.addTo(disposables)
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewerViewHolder {
        return PostViewerViewHolder(PostViewerListItemBinding.inflate(LayoutInflater.from(context), parent, false))
    }

    override fun onBindViewHolder(viewHolder: PostViewerViewHolder, position: Int) {
        val post = getItem(position)?.let(viewHolder::bind)
        latestPosition = position
    }

    inner class PostViewerViewHolder(val binding: PostViewerListItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            // In order to have the imageview fill the screen, I make it the size of the window's height minus the status bar height.
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
            // The ScrollView simply does not work. I've tried 20 different solutions.
            binding.postLargeScrollView.apply {
                doOnLayout {
                    fling(0)
                    fullScroll(View.FOCUS_UP)
                    scrollTo(0, 0)
                }
            }
        }

        @SuppressLint("CheckResult")
        private fun setUpImageView(post: Post, holder: PostViewerViewHolder) {
            var hasImageLoaded = false
            val imageView = binding.postLargeItemImageView
            val link = post.links?.get(0)
            Glide.with(context).load(link)
                .apply(
                    RequestOptions()
                        .error(R.drawable.ic_not_found_24)
                        .placeholder(R.drawable.ic_download)
                        .fitCenter()
                )
                .into(imageView)

            imageView.clicks()
                .subscribe {
                    positionSubject.onNext(layoutPosition + 1)
                }
        }
    }
}