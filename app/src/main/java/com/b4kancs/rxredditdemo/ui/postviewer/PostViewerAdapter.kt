package com.b4kancs.rxredditdemo.ui.postviewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.PostViewerListItemBinding
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
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import jp.wasabeef.glide.transformations.BlurTransformation
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

class PostViewerAdapter(
    private val context: Context,
    // This is how the ViewPager scrolls to the next/previous ViewHolder.
    private val onPositionChangedCallback: (Int) -> Unit,
    isSlideShowOnGoing: Boolean = false
) : PagingDataAdapter<Post, PostViewerAdapter.PostViewerViewHolder>(PostComparator) {
    companion object {
        private const val LOG_TAG = "PostViewerAdapter"
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
        positionSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { (currentPosition, nextPosition) ->
                Log.i(LOG_TAG, "onPositionChangedCallback($nextPosition)")
                onPositionChangedCallback(nextPosition)
                getViewHolderForPosition(currentPosition)?.noLongerShownSubject?.onNext(Unit)
                getViewHolderForPosition(nextPosition)?.shownSubject?.onNext(Unit)
                currentLayoutPosition = nextPosition
            }.addTo(disposables)

        slideShowOnOffSubject
            .observeOn(AndroidSchedulers.mainThread())
            .skipWhile { currentLayoutPosition == null }
            .subscribe { toShow ->
                val slideshowStartViewHolder = getViewHolderForPosition(currentLayoutPosition!!)!!
                if (toShow) {
                    slideshowStartViewHolder.binding.postLargeItemSlideshowImageView.setImageResource(R.drawable.ic_baseline_pause_slideshow_60)
                    slideshowStartViewHolder.showSlideShowControls()
                    startSlideShow()

                    makeSnackBar(
                        getViewHolderForPosition(currentLayoutPosition!!)!!.itemView,
                        R.string.slideshow_on
                    ).show()
                } else {
                    Log.i(LOG_TAG, "Stopping slideshow.")
                    slideshowStartViewHolder.binding.postLargeItemSlideshowImageView.setImageResource(R.drawable.ic_baseline_slideshow_60)
                    slideshowStartViewHolder.hideSlideShowControls()
                    slideshowIntervalPlayerDisposable?.dispose()

                    makeSnackBar(
                        getViewHolderForPosition(currentLayoutPosition!!)!!.itemView,
                        R.string.slideshow_off
                    ).show()
                }
            }.addTo(disposables)

        slideshowIntervalValueSubject
            .subscribe { newInterval ->
                rxSharedPreferences.getLong(SLIDESHOW_INTERVAL_KEY).set(newInterval)
            }.addTo(disposables)

        super.onAttachedToRecyclerView(recyclerView)
    }

    private fun startSlideShow() {
        val slideshowInterval = slideshowIntervalValueSubject.value!!
        Log.i(LOG_TAG, "Starting slideshow timer: $slideshowInterval seconds.")
        slideshowIntervalPlayerDisposable = Observable.interval(slideshowInterval, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                val currentViewHolder = getViewHolderForPosition(currentLayoutPosition!!)!!
                isHudVisible = false
                if (currentViewHolder.isGallery) {
                    Log.i(LOG_TAG, "Slideshow; gallery post. Trying to page withing gallery.")
                    if (currentViewHolder.nextInGallery()) return@subscribe
                }
                Log.i(LOG_TAG, "Slideshow; paging to next post.")
                positionSubject.onNext(currentLayoutPosition!! to currentLayoutPosition!! + 1)
            }
            .addTo(disposables)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewerViewHolder {
        return PostViewerViewHolder(
            PostViewerListItemBinding.inflate(LayoutInflater.from(context), parent, false)
        )
    }

    override fun onBindViewHolder(viewHolder: PostViewerViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val post = getItem(position)
        post?.let(viewHolder::bind)
        viewHolderMap[viewHolder] = position
        if (isRecentlyDisplayed) {
            viewHolder.shownSubject.onNext(Unit)
            if (slideShowOnOffSubject.value == true)
                slideShowOnOffSubject.onNext(true)
            isRecentlyDisplayed = false
        }
    }

    override fun onViewRecycled(holder: PostViewerViewHolder) {
        holder.hudElements.clear()
        with(holder.binding) {
            postLargeItemGalleryIndicatorImageView.isVisible = false
            postLargeItemGalleryItemsTextView.isVisible = false
        }
        super.onViewRecycled(holder)
    }

    fun getViewHolderForPosition(pos: Int): PostViewerViewHolder? {
        for (k in viewHolderMap.keys) {
            if (viewHolderMap[k] == pos) return k
        }
        Log.w(LOG_TAG, "Can't find PostViewerViewHolder for position $pos")
        return null
    }

    inner class PostViewerViewHolder(val binding: PostViewerListItemBinding) : RecyclerView.ViewHolder(binding.root) {

        val shownSubject = PublishSubject.create<Unit>()
        val noLongerShownSubject = PublishSubject.create<Unit>()
        val hudElements = ArrayList<View>()
        lateinit var post: Post
        var isGallery: Boolean = false
        var currentGalleryPosition = 0
        private var hasAppliedBlur: Boolean = false

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

        fun bind(post_: Post) {
            post = post_
            isGallery = post.links!!.size > 1
            hasAppliedBlur = false

            with(binding) {
                setUpImageViewAndHud(post, this@PostViewerViewHolder)
                setUpSlideshowAction()
                setUpSlideshowControls()
                // Always start with the ScrollView scrolled to the top.
                postLargeScrollView.apply {
                    doOnLayout {
                        binding.postLargeScrollView.fling(-20000)
                    }
                }
                postLargeItemTitleTextView.text = post.title
                postLargeItemScoreTextView.text = post.score.toString()
                postLargeItemCommentsTextView.text = "${post.numOfComments} comments"
                postLargeItemDateAuthorTextView.text = calculateDateAuthorSubredditText(post)

                hudElements.addAll(
                    listOf<View>(
                        postLargeItemLeftHudConstraintLayout,
                        postLargeItemRightHudConstraintLayout
                    )
                )
            }

            // Do these actions when the ViewHolder becomes actually visible.
            shownSubject.subscribe {
                if (isHudVisible) startAutoHideHudTimer()
                hudElements.forEach { it.isVisible = isHudVisible }
                currentLayoutPosition = layoutPosition

                val isSlideshowVisible = slideShowOnOffSubject.value!!
                binding.postLargeItemSlideshowImageView.setImageResource(
                    if (isSlideshowVisible)
                        R.drawable.ic_baseline_pause_slideshow_60
                    else
                        R.drawable.ic_baseline_slideshow_60
                )
                binding.postLargeItemSlideshowControlsConstraintLayout.isVisible = isSlideshowVisible
                binding.postLargeItemSlideshowDelayEditText.setText(slideshowIntervalValueSubject.value!!.toString())
            }.addTo(disposables)

            noLongerShownSubject.subscribe {
                autoHideHudTimerDisposable?.dispose()
            }
        }

        fun nextInGallery(): Boolean {
            return if (currentGalleryPosition in 0 until post.links!!.size - 1) {
                changeGalleryPosition(currentGalleryPosition + 1)
                true
            } else false
        }

        fun previousInGallery(): Boolean {
            return if (currentGalleryPosition in 1 until post.links!!.size) {
                changeGalleryPosition(currentGalleryPosition - 1)
                true
            } else false
        }

        private fun changeGalleryPosition(position: Int) {
            Log.i(LOG_TAG, "New gallery position: $position")
            loadImageWithGlide(binding.postLargeItemImageView, post.links!![position], updateExisting = true, toBlur = false)
            currentGalleryPosition = position
        }

        private fun startAutoHideHudTimer(delayInSeconds: Long = 3L) {
            if (isHudVisible) {
                autoHideHudTimerDisposable?.dispose()
                autoHideHudTimerDisposable = Observable.timer(delayInSeconds, TimeUnit.SECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { Log.i(LOG_TAG, "Subscribing to autoHideHudTimerDisposable.") }
                    .subscribe {
                        if (isHudVisible) {
                            Log.i(LOG_TAG, "Auto hiding HUD.")
                            hideHud()
                        }
                        cancelAutoHideHudTimer()
                    }
                    .addTo(disposables)
            }
        }

        private fun cancelAutoHideHudTimer() {
            autoHideHudTimerDisposable?.let {
                Log.i(LOG_TAG, "Disposing of autoHideHudTimerDisposable.")
                it.dispose()
            }
        }

        private fun hideHud() {
            isHudVisible = false
            hudElements.forEach(::animateHideViewAlpha)
            cancelAutoHideHudTimer()
        }

        private fun showHud() {
            isHudVisible = true
            hudElements.forEach(::animateShowViewAlpha)
            startAutoHideHudTimer()
        }

        fun showSlideShowControls() {
            animateShowViewAlpha(binding.postLargeItemSlideshowControlsConstraintLayout)
        }

        fun hideSlideShowControls() {
            animateHideViewAlpha(binding.postLargeItemSlideshowControlsConstraintLayout)
        }

        @SuppressLint("CheckResult", "ClickableViewAccessibility")
        private fun setUpImageViewAndHud(post: Post, holder: PostViewerViewHolder) {
            currentGalleryPosition = 0

            binding.postLargeItemImageView.transitionName = post.links!!.first()
            binding.nsfwTagTextView.isVisible = post.toBlur

            val zoomableImageView = binding.postLargeItemImageView
            loadImageWithGlide(zoomableImageView, post.links[0], false, post.toBlur)

            binding.postLargeItemLeftHudConstraintLayout.clicks()
                .subscribe {
                    Log.i(LOG_TAG, "Left hud clicked, paging left.")
                    positionSubject.onNext(layoutPosition to layoutPosition - 1)
                }.addTo(disposables)

            binding.postLargeItemRightHudConstraintLayout.clicks()
                .subscribe {
                    Log.i(LOG_TAG, "Right hud clicked, paging right.")
                    positionSubject.onNext(layoutPosition to layoutPosition + 1)
                }.addTo(disposables)

            val onSingleTapSubject: PublishSubject<Unit> = PublishSubject.create()
            onSingleTapSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    Log.d(LOG_TAG, "hasAppliedBlur: $hasAppliedBlur")
                    if (hasAppliedBlur) {
                        Log.i(LOG_TAG, "Unblurring image.")
                        post.toBlur = false
                        hasAppliedBlur = false
                        binding.nsfwTagTextView.isVisible = false
                        loadImageWithGlide(zoomableImageView, post.links[0], true, post.toBlur)
                    } else {
                        Log.d(LOG_TAG, "isHudVisible = $isHudVisible")
                        if (!isHudVisible) {
                            Log.i(LOG_TAG, "Showing HUD.")
                            showHud()
                        } else {
                            Log.i(LOG_TAG, "Hiding HUD.")
                            hideHud()
                        }
                    }
                }.addTo(disposables)

            // Setup for gallery
            if (isGallery) {
                binding.postLargeItemGalleryIndicatorImageView.visibility = View.VISIBLE
                binding.postLargeItemGalleryItemsTextView.visibility = View.VISIBLE
                binding.postLargeItemGalleryItemsTextView.text = post.links.size.toString()
            }

            zoomableImageView.setOnTouchListener(object : OnSwipeTouchListener(context) {

                override fun onSingleTap(): Boolean {
                    Log.i(LOG_TAG, "GestureDetector: onSingleTapConfirmed")
                    onSingleTapSubject.onNext(Unit)
                    return true
                }

                override fun onDoubleTap(): Boolean {
                    Log.i(LOG_TAG, "GestureDetector: onDoubleTap")
                    return false
                }

                override fun onSwipeRight() {
                    if (!isGallery) return
                    Log.i(LOG_TAG, "Gallery, right swipe detected.")
                    previousInGallery()
                }

                override fun onSwipeLeft() {
                    if (!isGallery) return
                    Log.i(LOG_TAG, "Gallery, left swipe detected.")
                    nextInGallery()
                }
            })
        }

        @SuppressLint("CheckResult")
        private fun loadImageWithGlide(imageView: ImageView, link: String, updateExisting: Boolean, toBlur: Boolean) {
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
                .into(imageView)
        }

        private fun setUpSlideshowAction() {
            with(binding) {
                postLargeItemSlideshowImageView.clicks()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        Log.i(LOG_TAG, "Slideshow button clicked.")
                        startAutoHideHudTimer(5L)

                        slideShowOnOffSubject.onNext(slideShowOnOffSubject.value!!.not())
                    }
                    .addTo(disposables)
            }
        }

        private fun setUpSlideshowControls() {
            binding.postLargeItemSlideshowDelayEditText.let { editText ->
                editText.focusChanges()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { isFocused ->
                        if (isFocused) {
                            Log.i(LOG_TAG, "Slideshow interval EditText has focus.")
                            slideshowIntervalPlayerDisposable?.dispose()
                            cancelAutoHideHudTimer()
                        }
                    }.addTo(disposables)

                editText.editorActionEvents()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { actionEvent ->
                        when (actionEvent.actionId) {
                            EditorInfo.IME_ACTION_DONE -> {
                                val value = (actionEvent.view as EditText).text.toString().toLong()
                                Log.i(LOG_TAG, "Slideshow interval EditText IME_ACTION_DONE. Value: $value")
                                if (value >= 1) {
                                    slideshowIntervalValueSubject.onNext(value)

                                    startSlideShow()
                                    hideHud()
                                    hideKeyboard(actionEvent.view)
                                }
                            }
                            else -> Log.i(LOG_TAG, "Slideshow interval EditText actionId: ${actionEvent.actionId}.")
                        }
                    }.addTo(disposables)
            }
        }
    }
}