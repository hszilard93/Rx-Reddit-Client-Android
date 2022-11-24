package com.b4kancs.rxredditdemo.ui.shared

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.AdapterCommonLoadingListItemBinding
import com.b4kancs.rxredditdemo.databinding.RvItemCommonRedditPostLandscapeBinding
import com.b4kancs.rxredditdemo.databinding.RvItemCommonRedditPostPortraitBinding
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.ui.PostComparator
import com.b4kancs.rxredditdemo.ui.uiutils.*
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
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.PublishSubject
import jp.wasabeef.glide.transformations.BlurTransformation
import logcat.LogPriority
import logcat.logcat

class PostsVerticalRvAdapter(
    private val context: Context,
    var disableTransformations: Boolean,
    val favoritesProvider: FavoritesProvider?
) :
    PagingDataAdapter<Post, RecyclerView.ViewHolder>(PostComparator) {

    companion object {
        private const val ITEM_VIEW_TYPE_POST = 1
        private const val ITEM_VIEW_TYPE_LOAD = 2
    }

    val postClickedSubject: PublishSubject<Pair<Int, View>> = PublishSubject.create()
    val readyToBeDrawnSubject: PublishSubject<Int> = PublishSubject.create()
    val disposables = CompositeDisposable()
    private lateinit var orientation: Orientation
    private lateinit var postView: View

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        logcat { "onCreateViewHolder" }

        logcat { "itemType: $viewType" }
        if (viewType == ITEM_VIEW_TYPE_LOAD) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.adapter_common_loading_list_item, parent, false)
            return SmallBottomLoadingIndicatorViewHolder(view).apply { setIsRecyclable(false) }
        }

        orientation = Orientation.fromInt(context.resources.configuration.orientation)
        logcat { "orientation = $orientation" }
        val bindingReplacement =
            when (orientation) {
                Orientation.PORTRAIT ->
                    RvItemPostSubBindingReplacement.from(
                        RvItemCommonRedditPostPortraitBinding.inflate(LayoutInflater.from(context), parent, false)
                    )

                Orientation.LANDSCAPE ->
                    RvItemPostSubBindingReplacement.from(
                        RvItemCommonRedditPostLandscapeBinding.inflate(LayoutInflater.from(context), parent, false)
                    )
            }
        postView = bindingReplacement.root
        return PostViewHolder(bindingReplacement)
    }

    override fun getItemViewType(position: Int): Int = if (position == itemCount - 1) ITEM_VIEW_TYPE_LOAD else ITEM_VIEW_TYPE_POST

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        logcat { "onBindViewHolder: position = $position" }
        if (holder is PostViewHolder)
            getItem(position)?.let { holder.bind(it) }
        else
            (holder as SmallBottomLoadingIndicatorViewHolder).bind()
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        logcat { "onViewRecycled" }
        if (holder is PostViewHolder) {
            // Resetting views before the RecyclerView reuses the ViewHolder
            with(holder.binding) {
                postImageView.setImageDrawable(null)
                postImageView.resetOnTouchListener(context)
                postImageView.layoutParams.height = dpToPixel(250, context)
                galleryIndicatorImageView.isVisible = false
                galleryItemsTextView.isVisible = false
                nsfwTagTextView.isVisible = false
                favoriteIndicatorImageView.isVisible = false
            }
        }
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + 1     // Plus one is for the bottom loading indicator
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        disposables.dispose()
    }

    inner class PostViewHolder(val binding: RvItemPostSubBindingReplacement) : RecyclerView.ViewHolder(postView) {

        fun bind(post: Post) {
            logcat { "bind: post = ${post.name}" }
            with(binding) {
                titleTextView.text = post.title
                commentsTextView.text = "${post.numOfComments} comments"
                dateAuthorTextView.text = calculateDateAuthorSubredditText(post)
                scoreTextView.text = "${post.score}"

                favoritesProvider?.getFavoritePosts()
                    ?.apply {   // This is a nullable because it doesn't make sense to have it in the FavoritesFragment
                        observeOn(AndroidSchedulers.mainThread())
                            .doOnSubscribe { logcat { "getFavorites.onSubscribe" } }
                            .subscribe { favorites ->
                                if (post.name in favorites.map { it.name }) {
                                    animateShowViewAlpha(favoriteIndicatorImageView)
                                }
                            }.addTo(disposables)
                    }

                if (post.crossPostFrom != null) {
                    crossPostTextView.visibility = View.VISIBLE
                    crossPostTextView.text = "xpost from r/${post.crossPostFrom}"
                } else {
                    crossPostTextView.visibility = View.GONE
                }

                setUpImageView(post)
            }
        }

        @SuppressLint("ClickableViewAccessibility", "CheckResult")
        private fun setUpImageView(post: Post) {
            logcat { "setUpImageView: post = ${post.name}" }
            var hasImageLoaded = false
            var currentGalleryPosition: Int? = null
            val positionSubject = PublishSubject.create<Int>()

            with(binding) {
                postImageView.transitionName = post.links!!.first()
                logcat { "Transition name for home image view set: ${postImageView.transitionName}" }

                // 'Preloading' images
                post.links.forEach {
                    Glide.with(context).downloadOnly().load(it)
                }

                // Setting up various behaviours
                var nsfwClickObserver: Disposable? = null
                val subscribeForRegularClicks = {
                    nsfwClickObserver?.dispose()
                    postImageView.clicks()
                        .doOnSubscribe { logcat { "Subscribing for regular post imageview clicks." } }
                        .subscribe {
                            logcat { "Image clicked in post ${post.name}. Forwarding to postClickedSubject." }
                            postClickedSubject.onNext(layoutPosition to postImageView)
                        }.addTo(disposables)
                }

                if (post.toBlur) {
                    nsfwClickObserver = postImageView.clicks()
                        .doOnSubscribe { logcat { "Subscribing for nsfw imageview clicks." } }
                        .take(1)
                        .subscribe {
                            logcat { "Unblurring NSFW image." }
                            nsfwTagTextView.isVisible = false
                            post.toBlur = false
                            positionSubject.onNext(currentGalleryPosition)
                            subscribeForRegularClicks()
                        }
                        .addTo(disposables)

                    nsfwTagTextView.isVisible = true
                    nsfwTagTextView.clicks()
                        .doOnNext { logcat { "nsfwTagTextView clicked" } }
                        .subscribe {
                            postImageView.performClick()
                        }
                        .addTo(disposables)
                } else {
                    subscribeForRegularClicks()
                }

                // Set up gallery
                if (post.links.size > 1) {
                    logcat(LogPriority.INFO) { "Setting up gallery. size = ${post.links.size}" }
                    galleryIndicatorImageView.visibility = View.VISIBLE
                    galleryItemsTextView.visibility = View.VISIBLE
                    galleryItemsTextView.text = post.links.size.toString()

                    postImageView.setOnTouchListener(object : OnSwipeTouchListener(context) {

                        override fun onDown(): Boolean {
                            return true
                        }

                        override fun onSingleTap(): Boolean {
                            logcat(LogPriority.INFO) { "Single tap registered." }
                            postImageView.performClick()
                            return true
                        }

                        override fun onSwipeRight() {
                            logcat(LogPriority.INFO) { "Right swipe registered." }
                            // Load previous item in the gallery
                            if (currentGalleryPosition in 1 until post.links.size) {
                                positionSubject.onNext(currentGalleryPosition!! - 1)
                            }
                        }

                        override fun onSwipeLeft() {
                            logcat(LogPriority.INFO) { "Left swipe registered." }
                            // Load next item in the gallery
                            if (currentGalleryPosition in 0 until post.links.size - 1) {
                                positionSubject.onNext(currentGalleryPosition!! + 1)
                            }
//                            Toast.makeText(context, "Left", Toast.LENGTH_SHORT).show()
                        }
                    })
                }

                // This loads the image
                positionSubject
                    .doOnNext { logcat { "positionSubject.onNext: position = $it" } }
                    .subscribe { position ->
                        loadImageWithGlide(post.links[position], hasImageLoaded, post.toBlur)
                        currentGalleryPosition = position
                    }
                    .addTo(disposables)

                // Initiate logic
                positionSubject.onNext(0)
                hasImageLoaded = true
            }
        }

        @SuppressLint("CheckResult")
        private fun loadImageWithGlide(link: String, updateExisting: Boolean, toBlur: Boolean) {
            logcat { "loadImageWithGlide: link = $link, updateExisting = $updateExisting, toBlur = $toBlur" }
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
                        logcat { "Glide.onResourceReady" }
                        resource?.let {
                            val oldImageViewHeight = imageView.measuredHeight
                            val width = resource.intrinsicWidth
                            val height = resource.intrinsicHeight
                            val newImageViewHeight = ((imageView.width.toFloat() / width) * height).toInt()

                            if (disableTransformations)
                                imageView.layoutParams.height = newImageViewHeight
                            else
                                animateViewHeightChange(imageView, oldImageViewHeight, newImageViewHeight, 150)
                        }

                        readyToBeDrawnSubject.onNext(layoutPosition)
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
                    if (toBlur) {
                        apply(RequestOptions.bitmapTransform(BlurTransformation(25, 10)))
                    }
                    into(imageView)
                }

            imageView.adjustViewBounds = true
        }
    }

    // In the future, might use LoadStateFooter instead, but this solution is also satisfactory.
    // https://developer.android.com/topic/libraries/architecture/paging/load-state#kotlin
    inner class SmallBottomLoadingIndicatorViewHolder(val view: View) : RecyclerView.ViewHolder(view) {

        fun bind() {
            logcat { "SmallBottomLoadingIndicatorViewHolder.bind" }
            AdapterCommonLoadingListItemBinding.bind(view)

            this@PostsVerticalRvAdapter.addLoadStateListener { combinedLoadStates ->
                logcat(LogPriority.INFO) { "combinedLoadState = ${combinedLoadStates.refresh}, layoutPosition = $layoutPosition" }
                view.isVisible = combinedLoadStates.source.append is LoadState.Loading && layoutPosition >= 1
                view.layoutParams.height = if (view.isVisible) WindowManager.LayoutParams.WRAP_CONTENT else 0
            }
        }
    }

    // Since we have virtually identical Bindings for both the portrait and landscape layouts, I found this
    // 'replacement' data class to be the simplest solution to the problem of using them with the same ViewHolder
    // and avoiding replicating code.
    data class RvItemPostSubBindingReplacement(
        val root: ConstraintLayout,
        val postImageView: ShapeableImageView,
        val titleTextView: MaterialTextView,
        val scoreTextView: MaterialTextView,
        val galleryIndicatorImageView: ImageView,
        val galleryItemsTextView: MaterialTextView,
        val favoriteIndicatorImageView: ImageView,
        val nsfwTagTextView: MaterialTextView,
        val crossPostTextView: MaterialTextView,
        val commentsTextView: MaterialTextView,
        val dateAuthorTextView: MaterialTextView
    ) {
        companion object {

            fun from(binding: RvItemCommonRedditPostPortraitBinding) =
                RvItemPostSubBindingReplacement(
                    binding.root,
                    binding.imageViewRvMainImage,
                    binding.textViewRvTitle,
                    binding.textViewRvScore,
                    binding.imageViewRvGalleryIndicator,
                    binding.textViewRvGalleryItems,
                    binding.imageViewRvFavoriteIndicator,
                    binding.textViewRvNsfwTag,
                    binding.textViewRvCrossPost,
                    binding.textViewRvComments,
                    binding.textViewRvDateAuthor
                )

            fun from(binding: RvItemCommonRedditPostLandscapeBinding) =
                RvItemPostSubBindingReplacement(
                    binding.root,
                    binding.imageViewRvMainImage,
                    binding.textViewRvTitle,
                    binding.textViewRvScore,
                    binding.imageViewRvGalleryIndicator,
                    binding.textViewRvGalleryItems,
                    binding.imageViewRvFavoriteIndicator,
                    binding.textViewRvNsfwTag,
                    binding.textViewRvCrossPost,
                    binding.textViewRvComments,
                    binding.textViewRvDateAuthor
                )
        }
    }
}