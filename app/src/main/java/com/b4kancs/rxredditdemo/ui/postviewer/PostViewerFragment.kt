package com.b4kancs.rxredditdemo.ui.postviewer

import android.animation.Animator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
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
import com.b4kancs.rxredditdemo.ui.MainActivity
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
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.concurrent.TimeUnit

class PostViewerFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "PostViewerFragment"
        private const val SAVED_STATE_POSITION_KEY = "position"
        private const val SAVED_STATE_SLIDESHOW_KEY = "slideShow"
    }

    private val args: PostViewerFragmentArgs by navArgs()
    private val disposables = CompositeDisposable()
    private lateinit var binding: FragmentPostViewerBinding
    private lateinit var viewModel: PostViewerViewModel
    private lateinit var delayedTransitionTriggerDisposable: Disposable

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPostViewerBinding.inflate(inflater, container, false)

        sharedElementEnterTransition = AutoTransition()
        sharedElementReturnTransition = AutoTransition().apply {
            addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) {
                    Log.i(LOG_TAG, "onTransitionStart")
                }

                override fun onTransitionEnd(transition: Transition) {
                    Log.i(LOG_TAG, "onTransitionEnd")
                }

                override fun onTransitionCancel(transition: Transition) {
                    Log.i(LOG_TAG, "onTransitionCancel")
                }

                override fun onTransitionPause(transition: Transition) {
                    Log.i(LOG_TAG, "onTransitionPause")
                }

                override fun onTransitionResume(transition: Transition) {
                    Log.i(LOG_TAG, "onTransitionResume")
                }
            })
        }

        Log.i(LOG_TAG, "Calling postponeEnterTransition().")
        postponeEnterTransition()

        delayedTransitionTriggerDisposable = Observable.timer(350, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { Log.i(LOG_TAG, "Starting delayed enter transition timer.") }
            .subscribe {
                Log.i(LOG_TAG, "Triggering delayed enter transition.")
                startPostponedEnterTransition()
            }
            .addTo(disposables)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val initialPosition = savedInstanceState?.getInt(SAVED_STATE_POSITION_KEY) ?: args.position
        val isSlideShowOngoing = savedInstanceState?.getBoolean(SAVED_STATE_SLIDESHOW_KEY) ?: false

        setUpViewModel()
        setUpViewPager(initialPosition, isSlideShowOngoing)
        setUpOnBackPressedCallback()
    }

    override fun onResume() {
        super.onResume()
        (activity as MainActivity).lockDrawerClosed()
    }

    override fun onDestroy() {
        super.onDestroy()
        (binding.viewPagerPostViewer.adapter as PostViewerAdapter).disposables.dispose()
        (activity as MainActivity).apply {
            supportActionBar?.show()
            findViewById<BottomNavigationView>(R.id.nav_view).isVisible = true
            unlockDrawer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.i(LOG_TAG, "onSaveInstanceState: Saving variables.")
        outState.putInt(SAVED_STATE_POSITION_KEY, binding.viewPagerPostViewer.currentItem)
        outState.putBoolean(
            SAVED_STATE_SLIDESHOW_KEY,
            (binding.viewPagerPostViewer.adapter as PostViewerAdapter).slideShowOnOffSubject.value!!
        )
        super.onSaveInstanceState(outState)
    }

    private fun setUpViewPager(initialPosition: Int, isSlideShowOngoing: Boolean) {
        val onPositionChangedCallback = { nextPosition: Int ->
            binding.viewPagerPostViewer.customSetCurrentItem(nextPosition, ANIMATION_DURATION_SHORT)
        }

        val postViewerAdapter = PostViewerAdapter(requireContext(), onPositionChangedCallback, isSlideShowOngoing)
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
                .filter { it == (initialPosition ?: 0) }
                .take(1)
                .subscribe {
                    (activity as MainActivity).apply {
                        animateHideActionBar(root)
                        animateHideBottomNavBar(root)
                    }
                    Log.i(LOG_TAG, "startPostponedEnterTransition()")
                    startPostponedEnterTransition()
                    Log.i(LOG_TAG, "Disposing of delayedTransitionTriggerDisposable")
                    delayedTransitionTriggerDisposable.dispose()
                }.addTo(disposables)
        }
    }

    private fun setUpViewModel() {
        val pagingDataObservableProviderName = args.pagingDataObservableProvider
        val pagingDataObservableProvider: Lazy<PostPagingDataObservableProvider> =
            if (pagingDataObservableProviderName == HomeViewModel::class.java.simpleName)
                sharedViewModel<HomeViewModel>()
            else
                throw IllegalArgumentException()
        viewModel = viewModel<PostViewerViewModel> { parametersOf(pagingDataObservableProvider.value) }.value
    }

    private fun setUpOnBackPressedCallback() {
        (activity as MainActivity).onBackPressedDispatcher.addCallback(this) {
//          Stops the user from clicking on anything while the transition takes place .
            (binding.viewPagerPostViewer.adapter as PostViewerAdapter).disposables.dispose()

            val visiblePosition = binding.viewPagerPostViewer.currentItem
            val transitionName =
                (binding.viewPagerPostViewer.adapter as PostViewerAdapter).getViewHolderForPosition(visiblePosition)
                    ?.binding
                    ?.postLargeItemImageView
                    ?.transitionName
            Log.i(LOG_TAG, "Transition name = $transitionName")
            transitionName?.let { (sharedElementReturnTransition as Transition).addTarget(it) }

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