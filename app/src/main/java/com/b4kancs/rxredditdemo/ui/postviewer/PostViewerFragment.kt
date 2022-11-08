package com.b4kancs.rxredditdemo.ui.postviewer

import android.animation.Animator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.transition.AutoTransition
import androidx.transition.Transition
import androidx.viewpager2.widget.ViewPager2
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentPostViewerBinding
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.ui.MainActivity
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import com.b4kancs.rxredditdemo.ui.favorites.FavoritesViewModel
import com.b4kancs.rxredditdemo.ui.home.HomeViewModel
import com.b4kancs.rxredditdemo.utils.ANIMATION_DURATION_SHORT
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import logcat.LogPriority
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.concurrent.TimeUnit

class PostViewerFragment : Fragment() {
    companion object {
        const val SAVED_STATE_POSITION_KEY = "position"
        private const val SAVED_STATE_SLIDESHOW_KEY = "slideshow"
    }

    private val args: PostViewerFragmentArgs by navArgs()
    private val disposables = CompositeDisposable()
    private lateinit var binding: FragmentPostViewerBinding
    private lateinit var viewModel: PostViewerViewModel
    private lateinit var delayedEnterTransitionTriggerDisposable: Disposable

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        logcat { "onCreateView" }

        binding = FragmentPostViewerBinding.inflate(inflater, container, false)

        sharedElementEnterTransition = AutoTransition()
        sharedElementReturnTransition = AutoTransition().apply {
            addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) {
                    logcat(LogPriority.INFO) { "onTransitionStart" }
                }

                override fun onTransitionEnd(transition: Transition) {
                    logcat(LogPriority.INFO) { "onTransitionEnd" }
                }

                override fun onTransitionCancel(transition: Transition) {
                    logcat(LogPriority.INFO) { "onTransitionCancel" }
                }

                override fun onTransitionPause(transition: Transition) {
                    logcat(LogPriority.INFO) { "onTransitionPause" }
                }

                override fun onTransitionResume(transition: Transition) {
                    logcat(LogPriority.INFO) { "onTransitionResume" }
                }
            })
        }

        logcat { "postponeEnterTransition()." }
        postponeEnterTransition()

        // If the enter transition hasn't occurred by this time, force it.
        delayedEnterTransitionTriggerDisposable = Observable.timer(350, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { logcat { "Starting delayed enter transition timer." } }
            .subscribe {
                logcat(LogPriority.INFO) { "Triggering delayed enter transition." }
                startPostponedEnterTransition()
            }
            .addTo(disposables)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logcat { "onViewCreated" }

        val initialPosition = savedInstanceState?.getInt(SAVED_STATE_POSITION_KEY) ?: args.position
        val isSlideShowOngoing = savedInstanceState?.getBoolean(SAVED_STATE_SLIDESHOW_KEY) ?: false

        setUpViewModel()
        setUpViewPager(initialPosition, isSlideShowOngoing)
        setUpOnBackPressedCallback()
    }

    override fun onResume() {
        logcat { "onResume" }
        super.onResume()
        (activity as MainActivity).lockDrawerClosed()
    }

    override fun onDestroy() {
        logcat { "onDestroy" }
        super.onDestroy()
        disposables.dispose()
        (activity as MainActivity).apply {
            supportActionBar?.show()
            findViewById<BottomNavigationView>(R.id.bottom_nav_view_main).isVisible = true
            unlockDrawer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        logcat { "onSaveInstanceState" }
        outState.putInt(SAVED_STATE_POSITION_KEY, binding.viewPagerPostViewer.currentItem)
        outState.putBoolean(
            SAVED_STATE_SLIDESHOW_KEY,
            (binding.viewPagerPostViewer.adapter as PostViewerAdapter).slideShowOnOffSubject.value ?: return
        )
        super.onSaveInstanceState(outState)
    }

    private fun setUpViewPager(initialPosition: Int, isSlideShowOngoing: Boolean) {
        logcat { "setUpViewPager" }

        val onPositionChangedCallback = { nextPosition: Int ->
            binding.viewPagerPostViewer.customSetCurrentItem(nextPosition, ANIMATION_DURATION_SHORT)
        }

        val onFavoritesActionCallback = { toFavorite: Boolean, post: Post ->
            if (toFavorite) {
                viewModel.addPostToFavorites(post)
            } else {
                viewModel.removePostFromFavorites(post)
            }
        }

        val postViewerAdapter = PostViewerAdapter(
            requireContext(),
            onPositionChangedCallback,
            onFavoritesActionCallback,
            isSlideShowOngoing,
            { viewModel.getFavoritePosts() }
        )
        with(binding) {
            viewPagerPostViewer.isVisible = false
            viewPagerPostViewer.adapter = postViewerAdapter
            viewPagerPostViewer.isUserInputEnabled = false

            // This fixed unexpected behaviour where the next ViewHolder was not immediately available upon paging.
            viewPagerPostViewer.offscreenPageLimit = 2

            viewModel.pagingDataObservable
                .filter {
                    // We do this, because the pagingDataObservable's owner is the HomeViewModel, and updating the paging data in the
                    // HomeFragment would trigger this observer and cause an exception here.
                    this@PostViewerFragment.isAdded
                }
                .subscribe { pagingData ->
                    postViewerAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                    // Initial setup. Let's scroll to the right position as soon as the pagingAdapter has done loading.
                    postViewerAdapter.loadStateFlow
                        .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                        .take(1)
                        .onEach {
                            viewPagerPostViewer.setCurrentItem(initialPosition, false)
                            viewPagerPostViewer.isVisible = true
                        }
                        .launchIn(MainScope())
                }

            postViewerAdapter.readyToBeDrawnSubject
                .observeOn(AndroidSchedulers.mainThread())
                .filter { it == (initialPosition) }
                .take(1)
                .subscribe {
                    (activity as MainActivity).apply {
                        animateHideActionBar(root)
                        animateHideBottomNavBar(root)
                    }
                    logcat(LogPriority.INFO) { "startPostponedEnterTransition()" }
                    startPostponedEnterTransition()
                    logcat(LogPriority.INFO) { "Disposing of delayedTransitionTriggerDisposable" }
                    delayedEnterTransitionTriggerDisposable.dispose()
                }.addTo(disposables)
        }
    }

    private fun setUpViewModel() {
        logcat { "setUpViewModel" }

        val pagingDataObservableProviderName = args.pagingDataObservableProvider
        val pagingDataObservableProvider: Lazy<PostPagingDataObservableProvider> =
            when (pagingDataObservableProviderName) {
                HomeViewModel::class.simpleName -> {
                    logcat { "The pagingDataObservableProvider is a HomeViewModel class." }
                    sharedViewModel<HomeViewModel>()
                }
                FavoritesViewModel::class.simpleName -> {
                    logcat { "The pagingDataObservableProvider is a FavoritesViewModel class." }
                    sharedViewModel<FavoritesViewModel>()
                }
                else -> {
                    logcat(LogPriority.ERROR) { "Provided class $pagingDataObservableProviderName is not a pagingDataObservableProvider." }
                    throw IllegalArgumentException()
                }
            }
        viewModel = viewModel<PostViewerViewModel> { parametersOf(pagingDataObservableProvider.value) }.value
    }

    private fun setUpOnBackPressedCallback() {
        logcat { "setUpOnBackPressedCallback" }

        (activity as MainActivity).onBackPressedDispatcher.addCallback(this) {
            logcat(LogPriority.INFO) { "MainActivity back pressed" }

//          Stops the user from clicking on anything while the transition takes place.
            (binding.viewPagerPostViewer.adapter as PostViewerAdapter).disposables.dispose()

            val visiblePosition = binding.viewPagerPostViewer.currentItem
            val visibleViewHolder = (binding.viewPagerPostViewer.adapter as PostViewerAdapter).getViewHolderForPosition(visiblePosition)
            val imageTransitionName =
                visibleViewHolder
                    ?.binding
                    ?.imageViewPostMainImage
                    ?.transitionName
            logcat(LogPriority.INFO) { "Transition name = $imageTransitionName" }
            imageTransitionName?.let { (sharedElementReturnTransition as Transition).addTarget(it) }

            findNavController().previousBackStackEntry?.savedStateHandle?.set("position", visiblePosition)
            findNavController().popBackStack()
        }
    }

    // Apparently, this is the simplest way to customize the scrolling speed of a ViewPager2...
    // Code based on https://stackoverflow.com/a/73318028.
    private fun ViewPager2.customSetCurrentItem(
        item: Int,
        duration: Long,
        interpolator: TimeInterpolator = AccelerateDecelerateInterpolator(),
        pagePxWidth: Int = width
    ) {
        logcat { "customSetCurrentItem" }
        val pxToDrag: Int = pagePxWidth * (item - currentItem)
        val animator = ValueAnimator.ofInt(0, pxToDrag)
        var previousValue = 0
        animator.addUpdateListener { valueAnimator ->
            val currentValue = valueAnimator.animatedValue as Int
            val currentPxToDrag = (currentValue - previousValue).toFloat()
            fakeDragBy(-currentPxToDrag)
            previousValue = currentValue
        }
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator?) {
                beginFakeDrag()
            }

            override fun onAnimationEnd(animation: Animator?) {
                endFakeDrag()
            }

            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationRepeat(animation: Animator?) {}
        })
        animator.interpolator = interpolator
        animator.duration = duration
        animator.start()
    }
}