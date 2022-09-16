package com.b4kancs.rxredditdemo.ui.postviewer

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.PostViewerListItemBinding
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.ui.PostComparator
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.PublishSubject

class PostViewerAdapter(private val context: Context) : PagingDataAdapter<Post, PostViewerAdapter.PostViewerViewHolder>(PostComparator) {
    companion object {
        private const val LOG_TAG = "PostViewerAdapter"
    }

    private val positionSubject = PublishSubject.create<Int>()
    private val disposables = CompositeDisposable()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        positionSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { nextPosition ->
                recyclerView.scrollToPosition(nextPosition)
            }.addTo(disposables)

        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewerViewHolder {
        return PostViewerViewHolder(PostViewerListItemBinding.inflate(LayoutInflater.from(context), parent, false))
    }

    override fun onBindViewHolder(holder: PostViewerViewHolder, position: Int) {
        val post = getItem(position)?.let(holder::bind)
    }

    override fun onViewRecycled(holder: PostViewerViewHolder) {
        super.onViewRecycled(holder)
    }

    inner class PostViewerViewHolder(val binding: PostViewerListItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.viewTreeObserver.addOnGlobalLayoutListener {
                // I was not able to get a reliable window measurement with any method I tried,
                // therefore this hack to make the imageview correctly fill the screen.
                val imageViewContainer = binding.postLargeItemImageViewConstraintLayout
                val imageViewNewHeight = imageViewContainer.height
                Log.d(LOG_TAG, "The imageview's height will be set to: $imageViewNewHeight")
                imageViewContainer.layoutParams.height = imageViewNewHeight
                Log.d(LOG_TAG, "The imageview's height is ${imageViewContainer.height}")
//                postView.findViewById<LinearLayout>(R.id.post_large_item_outer_linear_layout)
//                    .layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT

//                postView.findViewById<ConstraintLayout>(R.id.post_large_item_comments_constraint_layout).isVisible = true
//                postView.invalidate()
            }
        }

        fun bind(post: Post) {
            setUpImageView(post, this)
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
                .subscribe { positionSubject.onNext(layoutPosition + 1) }
        }
    }
}