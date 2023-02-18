package com.b4kancs.rxredditdemo.ui.postviewer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.databinding.PagerItemPostViewerBinding
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.ui.PostComparator
import com.b4kancs.rxredditdemo.ui.uiutils.*
import com.b4kancs.rxredditdemo.utils.executeTimedDisposable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.jakewharton.rxbinding4.view.clicks
import com.jakewharton.rxbinding4.view.focusChanges
import com.jakewharton.rxbinding4.widget.editorActionEvents
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.Observables
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import jp.wasabeef.glide.transformations.BlurTransformation
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

class PostViewerAdapter(
    private val context: Context,
    private val viewModel: PostViewerViewModel,
    // This is how the ViewPager scrolls to the next/previous ViewHolder.
    private val onPositionChangedCallback: (Int) -> Unit,
    isSlideShowOnGoing: Boolean = false,
    private val shouldShowNavigationToFollowsOption: Boolean = true
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
    private var resetScrollPosition = true

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        logcat { "onAttachedToRecyclerView" }
        positionSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { (currentPosition, nextPosition) ->
                logcat { "onPositionChangedCallback($nextPosition)" }
                onPositionChangedCallback(nextPosition)
                getViewHolderForPosition(currentPosition)?.noLongerShownSubject?.onNext(Unit)
                getViewHolderForPosition(nextPosition)?.shownSubject?.onNext(Unit)
                currentLayoutPosition = nextPosition
            }.addTo(disposables)

        // For recovering slideshow behaviour after configuration change
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
                }
                else {
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
        logcat { "The pagingDataObservable is ${viewModel.pagingDataObservable}" }

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
        logcat { "ViewHolder bound to Post. position = $position; post.name = ${post.name}" }

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

        val shownSubject: PublishSubject<Unit> = PublishSubject.create()
        val noLongerShownSubject: PublishSubject<Unit> = PublishSubject.create()
        private var wasAbleToLoadSubject: BehaviorSubject<Boolean> = BehaviorSubject.createDefault(false)
        val hudElements = ArrayList<View>()
        lateinit var post: Post
        var isGallery = false
        var currentGalleryPosition = 0
        private var hasAppliedBlur = false

        init {
            logcat { "PostViewerViewHolder.init" }

//             For the imageview to fill the screen, I make it's height equal to the window's height minus the status bar's height.
//            val statusBarId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
//            val statusBarHeight = context.resources.getDimensionPixelSize(statusBarId)
//            val navBarId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
//            val navBarHeight = context.resources.getDimension(navBarId)
//            logcat { "status bar height: $statusBarHeight; nav bar height: $navBarHeight" }
//            val imageViewNewHeight =
//                context.resources.displayMetrics.heightPixels -
//                        statusBarHeight +
//                        if (navBarHeight > 0) 0 else 100
//            logcat { "The imageview's height will be set to: $imageViewNewHeight" }
//            binding.imageViewPostMainImage.layoutParams.height = imageViewNewHeight


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = context.display
                val size = Point()
                display?.getSize(size)
                binding.imageViewPostMainImage.apply {
                    layoutParams.width = size.x
                    layoutParams.height = size.y
                    logcat { "The imageview's height will be set to: ${size.y}" }
                }
                (binding.imageViewPostMainImage.parent as View).apply {
                    layoutParams.width = size.x
                    layoutParams.height = size.y
                }
            }
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
                setUpOptionsAction()
                // Always start with the ScrollView scrolled to the top.
                scrollViewPostOuter.doOnPreDraw {
                    if (resetScrollPosition) {
                        // Let's disable the overscroll animation to get rid of the janky ripple effect on draw first.
                        scrollViewPostOuter.overScrollMode = View.OVER_SCROLL_NEVER

                        scrollViewPostOuter.fling(-20000)

                        // Reenable the overscroll animation after a short delay.
                        executeTimedDisposable(250) { scrollViewPostOuter.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }

                        resetScrollPosition = false
                    }
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
                .addTo(disposables)
        }

        fun showNextInGallery(): Boolean {
            logcat { "nextInGallery" }
            return if (currentGalleryPosition in 0 until post.links!!.size - 1) {
                changeGalleryPosition(currentGalleryPosition + 1)
                true
            }
            else false
        }

        fun showPreviousInGallery(): Boolean {
            logcat { "previousInGallery" }
            return if (currentGalleryPosition in 1 until post.links!!.size) {
                changeGalleryPosition(currentGalleryPosition - 1)
                true
            }
            else false
        }

        private fun changeGalleryPosition(position: Int) {
            logcat { "changeGalleryPosition" }
            logcat(LogPriority.INFO) { "New gallery position: $position" }
            loadImageWithGlide(binding.imageViewPostMainImage, post.links!![position], updateExisting = true, toBlur = false)
            currentGalleryPosition = position
            resetScrollPosition = true
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
                }
                else {
                    imageViewPostMainHudLeftPrevious.setColorFilter(colorNormalTypedValue.data)
                    constraintPostMainHudLeft.clicks()
                        .subscribe {
                            logcat(LogPriority.INFO) { "Left hud clicked, paging left." }
                            positionSubject.onNext(layoutPosition to layoutPosition - 1)
                        }.addTo(disposables)
                }

                if (layoutPosition == this@PostViewerAdapter.itemCount - 1) {
                    imageViewPostMainHudRightNext.setColorFilter(colorGreyTypedValue.data)
                }
                else {
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
                        }
                        else {
                            logcat (LogPriority.VERBOSE) { "isHudVisible = $isHudVisible" }
                            if (!isHudVisible)
                                showHud()
                            else
                                hideHud()
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
                    }
                    else {
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
                        wasAbleToLoadSubject.onNext(true)
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
                viewModel.addPostToFavorites(post)
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
                viewModel.removePostFromFavorites(post)
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
            viewModel.getFavoritePosts()
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
                    }
                    else {
                        favoriteAction()
                    }
                }.addTo(disposables)
        }

        private fun setUpOptionsAction() {
            logcat { "setUpOptions" }

            val optionsImageView = binding.imageViewPostMainHudRightOptions
            optionsImageView.clicks()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val popupView = LayoutInflater.from(context)
                        .inflate(R.layout.popup_post_viewer_options, binding.root, false)
                    val popupWindow = PopupWindow(
                        popupView,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        true)

                    val openLinkTextView = popupView.findViewById<MaterialTextView>(R.id.text_view_post_popup_option_open_link)
                        .apply {
                            clicks()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    popupWindow.dismiss()
                                    viewModel.openRedditLinkOfPost(post, context)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onError = {
                                                makeSnackBar(
                                                    view = optionsImageView,
                                                    stringId = R.string.string_common_error_something_went_wrong,
                                                    type = SnackType.ERROR
                                                ).show()
                                            }
                                        )
                                        .addTo(disposables)
                                }
                                .addTo(disposables)
                        }

                    val downloadImageTextView = popupView.findViewById<MaterialTextView>(R.id.text_view_post_popup_option_download)
                        .apply {
                            wasAbleToLoadSubject
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe { isVisible = it }
                                .addTo(disposables)

                            clicks()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    popupWindow.dismiss()
                                    viewModel.downloadImage(post.links!![currentGalleryPosition], (context as FragmentActivity))
                                        .subscribeBy(
                                            onComplete = {
                                                makeSnackBar(
                                                    view = optionsImageView,
                                                    stringId = null,
                                                    message = "Saved to Pictures!",
                                                ).show()
                                            },
                                            onError = {
                                                makeSnackBar(
                                                    view = optionsImageView,
                                                    stringId = R.string.string_common_error_something_went_wrong,
                                                    type = SnackType.ERROR
                                                ).show()
                                            }
                                        )
                                        .addTo(disposables)
                                }
                                .addTo(disposables)
                        }

                    val setAsBackgroundTextView = popupView.findViewById<MaterialTextView>(R.id.text_view_post_popup_option_set_as_wallpaper)
                        .apply {
                            clicks()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    popupWindow.dismiss()
                                    val workingOnItSnackbar = makeSnackBar(
                                        view = optionsImageView,
                                        stringId = null,
                                        message = "Working on it...",
                                        length = Snackbar.LENGTH_INDEFINITE
                                    )
                                    workingOnItSnackbar.show()
                                    viewModel.setImageAsBackground(post.links!![currentGalleryPosition], (context as FragmentActivity))
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onComplete = {
                                                workingOnItSnackbar.dismiss()
                                            },
                                            onError = {
                                                workingOnItSnackbar.dismiss()
                                                makeSnackBar(
                                                    view = optionsImageView,
                                                    stringId = R.string.string_options_wallpaper_error_something_went_wrong,
                                                    type = SnackType.ERROR,
                                                    length = Snackbar.LENGTH_LONG
                                                ).show()
                                            }
                                        )
                                        .addTo(disposables)
                                }.addTo(disposables)
                        }

                    val goToUserSubmissionTextView = popupView.findViewById<MaterialTextView>(R.id.text_view_post_popup_option_go_to_user)
                        .apply {
                            text = context.getString(R.string.string_post_popup_action_go_to_user, post.author)
                            isVisible = this@PostViewerAdapter.shouldShowNavigationToFollowsOption
                            clicks()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    popupWindow.dismiss()
                                    viewModel.goToUsersSubmissions(post)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onComplete = { },
                                            onError = {
                                                makeSnackBar(
                                                    view = optionsImageView,
                                                    stringId = R.string.string_common_error_something_went_wrong,
                                                    type = SnackType.ERROR
                                                ).show()
                                            }
                                        ).addTo(disposables)
                                }.addTo(disposables)
                        }

                    // The window y offset calculation is done because the popup window would get off the screen when the list item is on the bottom
                    val popupViewHeightPlusPadding = dpToPixel(48 * 4 + 24, context)
                    popupWindow.showAsDropDown(optionsImageView, 0, popupViewHeightPlusPadding * -1)

                }.addTo(disposables)

        }
    }
}