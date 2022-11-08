package com.b4kancs.rxredditdemo.ui.postviewer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.databinding.PagerItemPostViewerBinding
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
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.jakewharton.rxbinding4.view.clicks
import com.jakewharton.rxbinding4.view.focusChanges
import com.jakewharton.rxbinding4.widget.editorActionEvents
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import jp.wasabeef.glide.transformations.BlurTransformation
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

class PostViewerAdapter(
    private val context: Context,
    // This is how the ViewPager scrolls to the next/previous ViewHolder.
    private val onPositionChangedCallback: (Int) -> Unit,
    private val onFavoritesActionCallback: (Boolean, Post) -> Completable,
    isSlideShowOnGoing: Boolean = false,
    private val getFavoritePosts: () -> Single<List<PostFavoritesDbEntry>>
) : PagingDataAdapter<Post, PostViewerAdapter.PostViewerViewHolder>(PostComparator) {
    companion object {
        private const val SLIDESHOW_INTERVAL_KEY = "slideshowInterval"
    }

    val disposables = CompositeDisposable()
    val readyToBeDrawnSubject: PublishSubject<Int> = PublishSubject.create()
    val slideShowOnOffSubject: BehaviorSubject<Boolean> = BehaviorSubject.createDefault(isSlideShowOnGoing)
    private val rxSharedPreferences: RxSharedPreferences by inject(RxSharedPreferences::class.java)
    private val positionSubject: PublishSubject<Pair<Int, Int>> = PublishSubject.create()
    private val slideshowIntervalValueSubject: BehaviorSubject<Long> =
        BehaviorSubject.createDefault(
            rxSharedPreferences.getLong(SLIDESHOW_INTERVAL_KEY, 5L).get()
        )
    private val viewHolderMap = HashMap<PostViewerViewHolder, Int>()
    private var slideshowIntervalPlayerDisposable: Disposable? = null
    private var autoHideHudTimerDisposable: Disposable? = null
    private var isRecentlyDisplayed = true
    private var isHudVisible = !isRecentlyDisplayed
    private var currentLayoutPosition: Int? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        logcat { "onAttachedToRecyclerView" }
        positionSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { (currentPosition, nextPosition) ->
                logcat(LogPriority.INFO) { "onPositionChangedCallback($nextPosition)" }
                onPositionChangedCallback(nextPosition)
                getViewHolderForPosition(currentPosition)?.noLongerShownSubject?.onNext(Unit)
                getViewHolderForPosition(nextPosition)?.shownSubject?.onNext(Unit)
                currentLayoutPosition = nextPosition
            }.addTo(disposables)

        slideShowOnOffSubject
            .observeOn(AndroidSchedulers.mainThread())
            .skipWhile { currentLayoutPosition == null }
            .doOnNext { logcat(LogPriority.INFO) { "slideShowOnOffSubject.onNext: $it" } }
            .subscribe { toShow ->
                val slideshowStartViewHolder = getViewHolderForPosition(currentLayoutPosition!!)!!
                if (toShow) {
                    logcat(LogPriority.INFO) { "Starting slideshow." }

                    slideshowStartViewHolder.binding.imageViewPostMainHudLeftSlideshow.setImageResource(R.drawable.ic_baseline_pause_slideshow_60)
                    slideshowStartViewHolder.showSlideShowControls()
                    startSlideShow()

                    makeSnackBar(
                        getViewHolderForPosition(currentLayoutPosition!!)!!.itemView,
                        R.string.post_viewer_slideshow_on
                    ).show()
                } else {
                    logcat(LogPriority.INFO) { "Stopping slideshow." }
                    slideshowStartViewHolder.binding.imageViewPostMainHudLeftSlideshow.setImageResource(R.drawable.ic_baseline_slideshow_60)
                    slideshowStartViewHolder.hideSlideShowControls()
                    cancelSlideshow()

                    makeSnackBar(
                        getViewHolderForPosition(currentLayoutPosition!!)!!.itemView,
                        R.string.post_viewer_slideshow_off
                    ).show()
                }
            }.addTo(disposables)

        slideshowIntervalValueSubject
            .subscribe { newInterval ->
                rxSharedPreferences.getLong(SLIDESHOW_INTERVAL_KEY).set(newInterval)
            }.addTo(disposables)

        // For to debug ye olde code
        val favoritePosts = getFavoritePosts().blockingGet()
        logcat { "Favorite database size = ${favoritePosts.size}, entries: ${favoritePosts.map { it.name }}" }

        super.onAttachedToRecyclerView(recyclerView)
    }

    private fun startSlideShow() {
        logcat { "startSlideShow" }
        val slideshowInterval = slideshowIntervalValueSubject.value ?: 5L
        logcat(LogPriority.INFO) { "Keeping screen awake." }
        (context as Activity).window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        logcat(LogPriority.INFO) { "Starting slideshow timer: $slideshowInterval seconds." }
        slideshowIntervalPlayerDisposable = Observable.interval(slideshowInterval, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                val currentViewHolder = getViewHolderForPosition(currentLayoutPosition!!)!!
                isHudVisible = false
                if (currentViewHolder.isGallery) {
                    logcat(LogPriority.INFO) { "Slideshow; gallery post. Trying to page withing gallery." }
                    if (currentViewHolder.showNextInGallery()) return@subscribe
                }
                logcat(LogPriority.INFO) { "Slideshow; paging to next post." }
                positionSubject.onNext(currentLayoutPosition!! to currentLayoutPosition!! + 1)
            }
            .addTo(disposables)
    }

    private fun cancelSlideshow() {
        logcat { "cancelSlideshow" }
        logcat(LogPriority.INFO) { "No longer keeping screen awake." }
        (context as Activity).window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        slideshowIntervalPlayerDisposable?.dispose()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewerViewHolder {
        logcat { "onCreateViewHolder" }
        return PostViewerViewHolder(
            PagerItemPostViewerBinding.inflate(LayoutInflater.from(context), parent, false)
        )
    }

    override fun onBindViewHolder(viewHolder: PostViewerViewHolder, @SuppressLint("RecyclerView") position: Int) {
        logcat { "onBindViewHolder" }
        val post = getItem(position)
        post?.let(viewHolder::bind) ?: return
        logcat(LogPriority.INFO) { "ViewHolder bound to Post. position = $position; post.name = ${post.name}" }

        viewHolderMap[viewHolder] = position
        if (isRecentlyDisplayed) {
            viewHolder.shownSubject.onNext(Unit)
            if (slideShowOnOffSubject.value == true)
                slideShowOnOffSubject.onNext(true)
            isRecentlyDisplayed = false
            logcat { "isRecentlyDisplayed = false" }
        }
    }

    override fun onViewRecycled(holder: PostViewerViewHolder) {
        logcat { "onViewRecycled" }
        holder.hudElements.clear()
        with(holder.binding) {
            imageViewPostMainGalleryIndicator.isVisible = false
            textViewPostMainGalleryItems.isVisible = false
        }
        super.onViewRecycled(holder)
    }

    fun getViewHolderForPosition(pos: Int): PostViewerViewHolder? {
        logcat { "getViewHolderForPosition: position = $pos" }

        viewHolderMap.keys.find { keyViewHolder -> viewHolderMap[keyViewHolder] == pos }?.let { return it }

        logcat(LogPriority.WARN) { "Can't find PostViewerViewHolder for position $pos" }
        return null
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        disposables.dispose()
    }

    inner class PostViewerViewHolder(val binding: PagerItemPostViewerBinding) : RecyclerView.ViewHolder(binding.root) {

        val shownSubject = PublishSubject.create<Unit>()
        val noLongerShownSubject = PublishSubject.create<Unit>()
        val hudElements = ArrayList<View>()
        lateinit var post: Post
        var isGallery = false
        var currentGalleryPosition = 0
        private var hasAppliedBlur = false
        private var isFavorited = false

        init {
            logcat { "PostViewerViewHolder.init" }

            // For the imageview to fill the screen, I make it's height equal to the window's height minus the status bar's height.
            val statusBarId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeight = context.resources.getDimensionPixelSize(statusBarId)
            val navBarId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            val navBarHeight = context.resources.getDimension(navBarId)
            logcat { "status bar height: $statusBarHeight; nav bar height: $navBarHeight" }
            val imageViewNewHeight =
                context.resources.displayMetrics.heightPixels -
                        statusBarHeight +
                        if (navBarHeight > 0) 0 else 100
            logcat { "The imageview's height will be set to: $imageViewNewHeight" }
            binding.imageViewPostMainImage.layoutParams.height = imageViewNewHeight
        }

        fun bind(post_: Post) {
            logcat { "bind" }

            post = post_
            isGallery = post.links!!.size > 1
            hasAppliedBlur = false

            with(binding) {
                setUpImageViewAndHud(post, this@PostViewerViewHolder)
                setUpSlideshowAction()
                setUpSlideshowControls()
                setUpFavoritesAction()
                // Always start with the ScrollView scrolled to the top.
                scrollViewPostOuter.doOnLayout {
                    scrollViewPostOuter.fling(-20000)
                }

                textViewPostLowerTitle.text = post.title
                textViewPostLowerScore.text = post.score.toString()
                textViewPostLowerComments.text = "${post.numOfComments} comments"
                textViewPostLowerDateAuthor.text = calculateDateAuthorSubredditText(post)

                hudElements.addAll(
                    listOf<View>(
                        constraintPostMainHudLeft,
                        constraintPostMainHudRight
                    )
                )
            }

            // Do these actions when the ViewHolder becomes actually visible.
            shownSubject
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { logcat { "shownSubject.onNext" } }
                .subscribe {
                    if (isHudVisible) startAutoHideHudTimer()
                    hudElements.forEach { it.isVisible = isHudVisible }
                    currentLayoutPosition = layoutPosition

                    val isSlideshowVisible = slideShowOnOffSubject.value!!
                    binding.imageViewPostMainHudLeftSlideshow.setImageResource(
                        if (isSlideshowVisible)
                            R.drawable.ic_baseline_pause_slideshow_60
                        else
                            R.drawable.ic_baseline_slideshow_60
                    )
                    binding.constraintPostMainHudLeftSlideshowControls.isVisible = isSlideshowVisible
                    binding.editTextPostMainHudLeftSlideshowDelay.setText(slideshowIntervalValueSubject.value!!.toString())
                }.addTo(disposables)

            noLongerShownSubject
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat { "noLongerShownSubject.onNext" } }
                .subscribe {
                    autoHideHudTimerDisposable?.dispose()
                }
        }

        fun showNextInGallery(): Boolean {
            logcat { "nextInGallery" }
            return if (currentGalleryPosition in 0 until post.links!!.size - 1) {
                changeGalleryPosition(currentGalleryPosition + 1)
                true
            } else false
        }

        fun showPreviousInGallery(): Boolean {
            logcat { "previousInGallery" }
            return if (currentGalleryPosition in 1 until post.links!!.size) {
                changeGalleryPosition(currentGalleryPosition - 1)
                true
            } else false
        }

        private fun changeGalleryPosition(position: Int) {
            logcat { "changeGalleryPosition" }
            logcat(LogPriority.INFO) { "New gallery position: $position" }
            loadImageWithGlide(binding.imageViewPostMainImage, post.links!![position], updateExisting = true, toBlur = false)
            currentGalleryPosition = position
        }

        private fun startAutoHideHudTimer(delayInSeconds: Long = 3L) {
            logcat { "startAutoHideHudTimer" }

            if (isHudVisible) {
                autoHideHudTimerDisposable?.dispose()
                autoHideHudTimerDisposable = Observable.timer(delayInSeconds, TimeUnit.SECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { logcat(LogPriority.INFO) { "Subscribing to autoHideHudTimerDisposable." } }
                    .subscribe {
                        if (isHudVisible) {
                            logcat(LogPriority.INFO) { "Auto hiding HUD." }
                            hideHud()
                        }
                        // Timer subscriber gets disposed of automatically.
                    }
                    .addTo(disposables)
            }
        }

        private fun cancelAutoHideHudTimer() {
            logcat { "cancelAutoHideHudTimer" }
            autoHideHudTimerDisposable?.let {
                logcat(LogPriority.INFO) { "Disposing of autoHideHudTimerDisposable." }
                it.dispose()
            }
        }

        private fun hideHud() {
            logcat { "hideHud" }
            isHudVisible = false
            hudElements.forEach(::animateHideViewAlpha)
            cancelAutoHideHudTimer()
        }

        private fun showHud() {
            logcat { "showHud" }
            isHudVisible = true
            hudElements.forEach(::animateShowViewAlpha)
            startAutoHideHudTimer()
        }

        fun showSlideShowControls() {
            logcat { "showSlideShowControls" }
            animateShowViewAlpha(binding.constraintPostMainHudLeftSlideshowControls)
        }

        fun hideSlideShowControls() {
            logcat { "hideSlideShowControls" }
            animateHideViewAlpha(binding.constraintPostMainHudLeftSlideshowControls)
        }

        @SuppressLint("CheckResult", "ClickableViewAccessibility")
        private fun setUpImageViewAndHud(post: Post, holder: PostViewerViewHolder) {
            logcat { "setUpImageViewAndHud" }
            with(binding) {
                currentGalleryPosition = 0
                imageViewPostMainImage.transitionName = post.links!!.first()
                textViewRvNsfwTag.isVisible = post.toBlur

                val zoomableImageView = imageViewPostMainImage
                loadImageWithGlide(zoomableImageView, post.links[0], false, post.toBlur)

                val colorGreyTypedValue = TypedValue()
                context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorSurfaceVariant,
                    colorGreyTypedValue,
                    true
                )
                val colorNormalTypedValue = TypedValue()
                context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorPrimary,
                    colorNormalTypedValue,
                    true
                )

                if (layoutPosition == 0) {
                    imageViewPostMainHudLeftPrevious.setColorFilter(colorGreyTypedValue.data)
                } else {
                    imageViewPostMainHudLeftPrevious.setColorFilter(colorNormalTypedValue.data)
                    constraintPostMainHudLeft.clicks()
                        .subscribe {
                            logcat(LogPriority.INFO) { "Left hud clicked, paging left." }
                            positionSubject.onNext(layoutPosition to layoutPosition - 1)
                        }.addTo(disposables)
                }

                if (layoutPosition == this@PostViewerAdapter.itemCount - 1) {
                    imageViewPostMainHudRightNext.setColorFilter(colorGreyTypedValue.data)
                } else {
                    imageViewPostMainHudRightNext.setColorFilter(colorNormalTypedValue.data)
                    constraintPostMainHudRight.clicks()
                        .subscribe {
                            logcat(LogPriority.INFO) { "Right hud clicked, paging right." }
                            positionSubject.onNext(layoutPosition to layoutPosition + 1)
                        }.addTo(disposables)
                }

                val singleTapSubject: PublishSubject<Unit> = PublishSubject.create()
                singleTapSubject
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext { logcat { "onSingleTapSubject.onNext" } }
                    .subscribe {
                        logcat { "hasAppliedBlur: $hasAppliedBlur" }
                        if (hasAppliedBlur) {
                            logcat(LogPriority.INFO) { "Unblurring image." }
                            post.toBlur = false
                            hasAppliedBlur = false
                            textViewRvNsfwTag.isVisible = false
                            loadImageWithGlide(zoomableImageView, post.links[0], true, post.toBlur)
                        } else {
                            logcat { "isHudVisible = $isHudVisible" }
                            if (!isHudVisible) {
                                logcat(LogPriority.INFO) { "Showing HUD." }
                                showHud()
                            } else {
                                logcat(LogPriority.INFO) { "Hiding HUD." }
                                hideHud()
                            }
                        }
                    }.addTo(disposables)

                // Setup for gallery
                if (isGallery) {
                    imageViewPostMainGalleryIndicator.visibility = View.VISIBLE
                    textViewPostMainGalleryItems.visibility = View.VISIBLE
                    textViewPostMainGalleryItems.text = post.links.size.toString()
                }

                zoomableImageView.setOnTouchListener(object : OnSwipeTouchListener(context) {

                    override fun onSingleTap(): Boolean {
                        logcat(LogPriority.INFO) { "GestureDetector: onSingleTapConfirmed" }
                        singleTapSubject.onNext(Unit)
                        return true
                    }

                    override fun onDoubleTap(): Boolean {
                        logcat(LogPriority.INFO) { "GestureDetector: onDoubleTap" }
                        return false
                    }

                    override fun onSwipeRight() {
                        if (!isGallery) return
                        logcat(LogPriority.INFO) { "Gallery, right swipe detected." }
                        showPreviousInGallery()
                    }

                    override fun onSwipeLeft() {
                        if (!isGallery) return
                        logcat(LogPriority.INFO) { "Gallery, left swipe detected." }
                        showNextInGallery()
                    }
                })
            }
        }

        @SuppressLint("CheckResult")
        private fun loadImageWithGlide(imageView: ImageView, link: String, updateExisting: Boolean, toBlur: Boolean) {
            logcat { "loadImageWithGlide" }
            Glide.with(context).load(link)
                .apply {
                    error(R.drawable.ic_not_found_24)
                    if (updateExisting) {
                        transition(
                            DrawableTransitionOptions.withCrossFade(
                                DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(false).build()
                            )
                        )
                    } else {
                        placeholder(R.drawable.ic_download)
                        dontTransform()
                    }
                    if (toBlur) {
                        apply(RequestOptions.bitmapTransform(BlurTransformation(25, 10)))
                        hasAppliedBlur = true
                    }
                }
                .addListener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        logcat { "Glide.onLoadFailed" }
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        logcat { "Glide.onResourceReady" }
                        readyToBeDrawnSubject.onNext(layoutPosition)
                        return false
                    }
                })
                .into(imageView)
        }

        private fun setUpSlideshowAction() {
            logcat { "setUpSlideShowAction" }
            with(binding) {
                imageViewPostMainHudLeftSlideshow.clicks()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        logcat(LogPriority.INFO) { "Slideshow button clicked." }
                        startAutoHideHudTimer(5L)

                        slideShowOnOffSubject.onNext(slideShowOnOffSubject.value!!.not())
                    }
                    .addTo(disposables)
            }
        }

        private fun setUpSlideshowControls() {
            logcat { "setUpSlideShowControls" }
            binding.editTextPostMainHudLeftSlideshowDelay.also { editText ->
                editText.focusChanges()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { isFocused ->
                        if (isFocused) {
                            logcat(LogPriority.INFO) { "Slideshow interval EditText has focus." }
                            cancelSlideshow()
                            cancelAutoHideHudTimer()
                        }
                    }.addTo(disposables)

                editText.editorActionEvents()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { actionEvent ->
                        when (actionEvent.actionId) {
                            EditorInfo.IME_ACTION_DONE -> {
                                val value = (actionEvent.view as EditText).text.toString().toLong()
                                logcat(LogPriority.INFO) { "Slideshow interval EditText IME_ACTION_DONE. Value: $value" }
                                if (value >= 1) {
                                    slideshowIntervalValueSubject.onNext(value)

                                    startSlideShow()
                                    hideHud()
                                    hideKeyboard(actionEvent.view)
                                }
                            }
                            else -> logcat(LogPriority.INFO) { "Slideshow interval EditText actionId: ${actionEvent.actionId}." }
                        }
                    }.addTo(disposables)
            }
        }

        private fun setUpFavoritesAction() {
            logcat { "setUpFavoritesAction" }
            val favView = binding.imageViewPostMainHudRightFavorite
            var isFavorite = false

            // Setup
            val favoriteAction: () -> Unit = {
                favView.setImageResource(R.drawable.ic_baseline_favorite_filled_60)
                onFavoritesActionCallback(true, post)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            logcat(LogPriority.INFO) { "Post ${post.name} was favorited." }
                            makeSnackBar(view = favView, stringId = null, message = "Post favorited!")
                                .show()
                            isFavorite = true
                        },
                        {
                            logcat(LogPriority.ERROR) { "Could not favorite post ${post.name} !\tMessage: ${it.message}" }
                            makeSnackBar(view = favView, stringId = null, message = "Failed to favorite post :(", type = SnackType.ERROR)
                                .show()
                            favView.setImageResource(R.drawable.ic_baseline_favorite_border_60)
                        }
                    )
                    .addTo(disposables)
            }

            val unfavoriteAction: () -> Unit = {
                favView.setImageResource(R.drawable.ic_baseline_favorite_border_60)
                onFavoritesActionCallback(false, post)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            logcat(LogPriority.INFO) { "Post ${post.name} was deleted from favorites." }
                            makeSnackBar(view = favView, stringId = null, message = "Post no longer in Favorites!")
                                .show()
                            isFavorite = false
                        },
                        {
                            logcat(LogPriority.ERROR) { "Error deleting post ${post.name} from favorites!\tMessage: ${it.message}" }
                            makeSnackBar(
                                view = favView,
                                stringId = null,
                                message = "Failed to remove post from favorites :(",
                                type = SnackType.ERROR
                            ).show()
                            favView.setImageResource(R.drawable.ic_baseline_favorite_filled_60)
                        }
                    )
                    .addTo(disposables)
            }

            // Initialization
            getFavoritePosts()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { logcat { "getFavoritePosts.onSubscribe" } }
                .subscribe { favorites: List<PostFavoritesDbEntry> ->
                    isFavorite = post.name in favorites.map { it.name }
                    logcat { "Post ${post.name} isFavorite = $isFavorite" }

                    if (isFavorite) favView.setImageResource(R.drawable.ic_baseline_favorite_filled_60)
                    else favView.setImageResource(R.drawable.ic_baseline_favorite_border_60)
                }.addTo(disposables)

            favView.clicks()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat { "Favorites ImageView clicked." } }
                .subscribe {
                    if (isFavorite) {
                        unfavoriteAction()
                    } else {
                        favoriteAction()
                    }
                }.addTo(disposables)
        }
    }
}