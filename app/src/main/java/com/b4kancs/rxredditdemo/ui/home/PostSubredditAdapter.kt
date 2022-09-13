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
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.utils.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
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

    private lateinit var orientation: Orientation
    private val disposables = CompositeDisposable()
    private lateinit var postView: View

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == ITEM_VIEW_TYPE_LOAD) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.adapter_post_loading_list_item, parent, false)
            return SmallBottomLoadingIndicatorViewHolder(view).apply { setIsRecyclable(false) }
        }

        orientation = Orientation.fromInt(context.resources.configuration.orientation)
        val view =
            when (orientation) {
                Orientation.PORTRAIT ->
                    LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.adapter_post_sub_row_large, parent, false)

                Orientation.LANDSCAPE ->
                    LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.adapter_post_sub_row, parent, false)
            }
        postView = view
        return PostSubredditViewHolder(view)
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
            with(holder) {
                imageView.setImageDrawable(null)
                imageView.resetOnTouchListener(context)
                imageView.layoutParams.height = dpToPixel(250, context)
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

    inner class PostSubredditViewHolder(postView: View) : RecyclerView.ViewHolder(postView) {

        val titleTextView: TextView = postView.findViewById(R.id.post_title_text_view)
        val imageView: ImageView = postView.findViewById(R.id.post_image_view)
        val dateAuthorTextView: TextView = postView.findViewById(R.id.post_date_author_text_view)
        val crossPostTextView: TextView = postView.findViewById(R.id.post_cross_text_view)
        val commentsTextView: TextView = postView.findViewById(R.id.post_comments_text_view)
        val galleryIndicatorImageView: ImageView = postView.findViewById(R.id.gallery_indicator_image_view)
        val galleryItemsTextView: TextView = postView.findViewById(R.id.gallery_items_text_view)
        val nsfwTagTextView: TextView = postView.findViewById(R.id.post_nsfw_tag_text_view)

        fun bind(post: Post) {
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

            setUpImageView(post, this)
        }


        @SuppressLint("ClickableViewAccessibility", "CheckResult")
        private fun setUpImageView(post: Post, holder: PostSubredditViewHolder) {
            var hasImageLoaded = false
            var currentPos: Int? = null
            val positionSubject = PublishSubject.create<Int>()
            var isNsfw = post.nsfw

            positionSubject
                .subscribe { position ->
                    loadWithGlideInto(post.links!![position], imageView, hasImageLoaded, isNsfw)
                    currentPos = position
                }
                .addTo(disposables)

            if (isNsfw) {
                imageView.clicks()
                    .doOnSubscribe { Log.d(LOG_TAG, "Subscribing for imageView clicks.") }
                    .subscribe {
                        Log.d(LOG_TAG, "Unblurring.")
                        isNsfw = false
                        nsfwTagTextView.isVisible = false
                        positionSubject.onNext(currentPos)
                    }
                    .addTo(disposables)
                nsfwTagTextView.clicks()
                    .subscribe {
                        imageView.performClick()
                    }
                    .addTo(disposables)
            }

            positionSubject.onNext(0)
            hasImageLoaded = true

            if (post.links?.size!! > 1) {
                post.links.forEach {
                    Glide.with(context).downloadOnly().load(it)
                }

                holder.galleryIndicatorImageView.visibility = View.VISIBLE
                holder.galleryItemsTextView.visibility = View.VISIBLE
                holder.galleryItemsTextView.text = post.links.size.toString()

                holder.imageView.setOnTouchListener(object : OnSwipeTouchListener(context) {

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

        @SuppressLint("CheckResult")
        private fun loadWithGlideInto(link: String, imageView: ImageView, updateExisting: Boolean, nsfw: Boolean) {
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
                        nsfwTagTextView.isVisible = true
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

    object PostComparator : DiffUtil.ItemCallback<Post>() {

        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean = oldItem == newItem
    }
}