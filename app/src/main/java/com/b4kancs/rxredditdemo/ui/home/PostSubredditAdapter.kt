package com.b4kancs.rxredditdemo.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.RvItemPostSubLandscapeBinding
import com.b4kancs.rxredditdemo.databinding.RvItemPostSubPortraitBinding
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.ui.PostComparator
import com.b4kancs.rxredditdemo.utils.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.PublishSubject
import jp.wasabeef.glide.transformations.BlurTransformation
import java.util.*

class PostSubredditAdapter(private val context: Context) :
    PagingDataAdapter<Post, RecyclerView.ViewHolder>(PostComparator) {

    companion object {
        private const val LOG_TAG = "PostSubredditAdapter"
        private const val ITEM_VIEW_TYPE_POST = 1
        private const val ITEM_VIEW_TYPE_LOAD = 2
    }

    val postClickedSubject = PublishSubject.create<Pair<Int, View>>()
    val readyToBeDrawnSubject = PublishSubject.create<Unit>()
    val disposables = CompositeDisposable()
    private lateinit var orientation: Orientation
    private lateinit var postView: View

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == ITEM_VIEW_TYPE_LOAD) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.adapter_post_loading_list_item, parent, false)
            return SmallBottomLoadingIndicatorViewHolder(view).apply { setIsRecyclable(false) }
        }

        orientation = Orientation.fromInt(context.resources.configuration.orientation)
        val bindingReplacement =
            when (orientation) {
                Orientation.PORTRAIT ->
                    RvItemPostSubBindingReplacement.from(
                        RvItemPostSubPortraitBinding.inflate(LayoutInflater.from(context), parent, false)
                    )

                Orientation.LANDSCAPE ->
                    RvItemPostSubBindingReplacement.from(
                        RvItemPostSubLandscapeBinding.inflate(LayoutInflater.from(context), parent, false)
                    )
            }
        postView = bindingReplacement.root
        return PostSubredditViewHolder(bindingReplacement)
    }

    override fun getItemViewType(position: Int): Int = if (position == itemCount - 1) ITEM_VIEW_TYPE_LOAD else ITEM_VIEW_TYPE_POST

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is PostSubredditViewHolder)
            getItem(position)?.let { holder.bind(it) }
        else
            (holder as SmallBottomLoadingIndicatorViewHolder).bind(itemCount > 1)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is PostSubredditViewHolder) {
            // Resetting views before recyclerview reuses the ViewHolder
            with(holder.binding) {
                postImageView.setImageDrawable(null)
                postImageView.resetOnTouchListener(context)
                postImageView.layoutParams.height = dpToPixel(250, context)
                galleryIndicatorImageView.isVisible = false
                galleryItemsTextView.isVisible = false
                nsfwTagTextView.isVisible = false
            }
        }
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + 1     // Plus one for the bottom loading indicator
    }

    inner class PostSubredditViewHolder(val binding: RvItemPostSubBindingReplacement) : RecyclerView.ViewHolder(postView) {

        fun bind(post: Post) {
            with(binding) {
                titleTextView.text = post.title

                if (post.crossPostFrom != null) {
                    crossPostTextView.visibility = View.VISIBLE
                    crossPostTextView.text = "xpost from r/${post.crossPostFrom}"
                } else {
                    crossPostTextView.visibility = View.GONE
                }
                commentsTextView.text = "${post.numOfComments} comments"

                val postAgeInMinutes = (Date().time - (post.createdAt * 1000L)) / (60 * 1000L) // time difference in ms divided by a minute
                val postAge = when (postAgeInMinutes) {
                    in 0 until 60 -> postAgeInMinutes to "minute(s)"
                    in 60 until 1440 -> postAgeInMinutes / 60 to "hour(s)"
                    in 1440 until 525600 -> postAgeInMinutes / 1440 to "day(s)"
                    in 525600 until Long.MAX_VALUE -> postAgeInMinutes / 525600 to "year(s)"
                    else -> postAgeInMinutes to "ms"
                }
                dateAuthorTextView.text = "posted ${postAge.first} ${postAge.second} ago by ${post.author} to r/${post.subreddit}"

                setUpImageView(post)
            }
        }

        @SuppressLint("ClickableViewAccessibility", "CheckResult")
        private fun setUpImageView(post: Post) {
            with(binding) {
                postImageView.transitionName = post.links!!.first()
                Log.d(LOG_TAG, "Transition name for home image view set: ${postImageView.transitionName}")

                var hasImageLoaded = false
                var currentPos: Int? = null
                val positionSubject = PublishSubject.create<Int>()
                var isNsfw = post.nsfw

                post.links.forEach {
                    Glide.with(context).downloadOnly().load(it)
                }

                positionSubject
                    .subscribe { position ->
                        loadWithGlideInto(post.links[position], hasImageLoaded, isNsfw)
                        currentPos = position
                    }
                    .addTo(disposables)

                if (isNsfw) {
                    postImageView.clicks()
                        .doOnSubscribe { Log.d(LOG_TAG, "Subscribing for nsfw imageview clicks.") }
                        .subscribe {
                            Log.d(LOG_TAG, "Unblurring.")
                            isNsfw = false
                            nsfwTagTextView.isVisible = false
                            positionSubject.onNext(currentPos)
                        }
                        .addTo(disposables)
                    nsfwTagTextView.clicks()
                        .subscribe {
                            postImageView.performClick()
                        }
                        .addTo(disposables)
                } else {
                    postImageView.clicks()
                        .doOnSubscribe { Log.d(LOG_TAG, "Subscribing for regular post imageview clicks.") }
                        .subscribe {
                            Log.d(LOG_TAG, "Image clicked in post ${post.toString()}. Forwarding to postClickedSubject.")
                            postClickedSubject.onNext(position to postImageView)
                        }.addTo(disposables)
                }

                positionSubject.onNext(0)
                hasImageLoaded = true

                if (post.links.size > 1) {
                    galleryIndicatorImageView.visibility = View.VISIBLE
                    galleryItemsTextView.visibility = View.VISIBLE
                    galleryItemsTextView.text = post.links.size.toString()

                    postImageView.setOnTouchListener(object : OnSwipeTouchListener(context) {

                        override fun onSwipeRight() {
                            // Load previous item in the gallery
                            if (currentPos in 1..post.links.size + 1) {
                                positionSubject.onNext(currentPos!! - 1)
                            }
                            Toast.makeText(context, "Right", Toast.LENGTH_SHORT).show()
                        }

                        override fun onSwipeLeft() {
                            // Load next item in the gallery
                            if (currentPos in 0 until post.links.size - 1) {
                                positionSubject.onNext(currentPos!! + 1)
                            }
                            Toast.makeText(context, "Left", Toast.LENGTH_SHORT).show()
                        }

                    })
                }
            }
        }

        @SuppressLint("CheckResult")
        private fun loadWithGlideInto(link: String, updateExisting: Boolean, nsfw: Boolean) {
            val imageView = binding.postImageView
            Glide.with(context).load(link)
                .error(R.drawable.ic_not_found_24)
                .override(imageView.width.dpToPx(context), 0)
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
                        resource?.let {
                            val oldImageViewWidth = imageView.measuredWidth
                            val oldImageViewHeight = imageView.measuredHeight
                            val width = resource.intrinsicWidth
                            val height = resource.intrinsicHeight
                            val newImageViewHeight = ((imageView.width.toFloat() / width) * height).toInt()

                            animateViewLayoutHeightChange(imageView, oldImageViewHeight, newImageViewHeight, 150)
                        }
                        readyToBeDrawnSubject.onNext(Unit)
                        return false
                    }
                }).apply {
                    if (updateExisting) {
                        transition(
                            DrawableTransitionOptions.withCrossFade(
                                DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(false).build()
                            )
                        )
                    } else {
                        placeholder(R.drawable.ic_download)
                    }
                    if (nsfw) {
                        apply(RequestOptions.bitmapTransform(BlurTransformation(25, 10)))
                        binding.nsfwTagTextView.isVisible = true
                    }
                    into(imageView)
                }

            imageView.adjustViewBounds = true
        }
    }

    inner class SmallBottomLoadingIndicatorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val smallBottomLoadingProgressBar: ProgressBar = view.findViewById(R.id.small_progress_bar)

        fun bind(makeVisible: Boolean) {
            smallBottomLoadingProgressBar.isVisible = makeVisible
        }
    }

    // Since we have virtually identical bindings for both the portrait and landscape layouts, I found this
    // 'replacement' data class to be the simplest solution to the problem of using them with the same ViewHolder
    // and avoiding replicating code.
    data class RvItemPostSubBindingReplacement(
        val root: ConstraintLayout,
        val postImageView: ShapeableImageView,
        val titleTextView: MaterialTextView,
        val galleryIndicatorImageView: ImageView,
        val galleryItemsTextView: MaterialTextView,
        val nsfwTagTextView: MaterialTextView,
        val crossPostTextView: MaterialTextView,
        val commentsTextView: MaterialTextView,
        val dateAuthorTextView: MaterialTextView
    ) {
        companion object {

            fun from(binding: RvItemPostSubPortraitBinding) =
                RvItemPostSubBindingReplacement(
                    binding.root,
                    binding.postImageView,
                    binding.titleTextView,
                    binding.galleryIndicatorImageView,
                    binding.galleryItemsTextView,
                    binding.nsfwTagTextView,
                    binding.crossPostTextView,
                    binding.commentsTextView,
                    binding.dateAuthorTextView
                )

            fun from(binding: RvItemPostSubLandscapeBinding) =
                RvItemPostSubBindingReplacement(
                    binding.root,
                    binding.postImageView,
                    binding.titleTextView,
                    binding.galleryIndicatorImageView,
                    binding.galleryItemsTextView,
                    binding.nsfwTagTextView,
                    binding.crossPostTextView,
                    binding.commentsTextView,
                    binding.dateAuthorTextView
                )
        }
    }

}