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
import com.b4kancs.rxredditdemo.ui.shared.PostPagingDataObservableProvider
import com.b4kancs.rxredditdemo.ui.favorites.FavoritesViewModel
import com.b4kancs.rxredditdemo.ui.follows.FollowsViewModel
import com.b4kancs.rxredditdemo.ui.home.HomeViewModel
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.uiutils.ANIMATION_DURATION_SHORT
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
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
    private var _binding: FragmentPostViewerBinding? = null
    private val binding: FragmentPostViewerBinding get() = _binding!!
    private lateinit var viewModel: PostViewerViewModel
    private var previousFragmentName: String? = null
    private lateinit var delayedEnterTransitionTriggerDisposable: Disposable
    private var currentPosition = 0     // This gets recovered when the Fragment gets restored from the BackStack

    init {
        logcat { "init $this" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logcat { "onCreate" }

        currentPosition = savedInstanceState?.getInt(SAVED_STATE_POSITION_KEY)
            ?: if (args.position != -1) args.position else 0

        logcat { "currentPosition = $currentPosition" }

        setUpViewModel()

        setUpOnBackPressedCallback()
        setUpNavigationToFollows()

        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        logcat {
            "onCreateView\n  Current nav backstack: ${
                findNavController().backQueue
                    .map { it.destination }
                    .joinToString("\n ", "\n ")
            }"
        }

        _binding = FragmentPostViewerBinding.inflate(inflater, container, false)

        sharedElementEnterTransition = AutoTransition()
        logcat { "postponeEnterTransition()." }
        postponeEnterTransition()

        // If the enter transition hasn't occurred by this time, force it.
        delayedEnterTransitionTriggerDisposable = Observable.timer(350, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .filter { this.isVisible }
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

        val isSlideShowOngoing = savedInstanceState?.getBoolean(SAVED_STATE_SLIDESHOW_KEY) ?: false
        previousFragmentName = findNavController().previousBackStackEntry?.destination?.displayName
        setUpViewPager(isSlideShowOngoing)
    }

    override fun onStart() {
        logcat { "onStart" }
        super.onStart()
        (activity as MainActivity).lockDrawerClosed()
    }


    private fun setUpViewModel() {
        logcat { "setUpViewModel" }
        if (this::viewModel.isInitialized) {
            logcat { "The viewModel has already been initialized. viewModel = $viewModel" }
            return
        }

        val pagingDataObservableProviderName = args.pagingDataObservableProvider
        val pagingDataObservableProvider: Lazy<PostPagingDataObservableProvider> =
            when (pagingDataObservableProviderName) {
                HomeViewModel::class.simpleName -> {
                    logcat { "The pagingDataObservableProvider is a HomeViewModel instance." }
                    sharedViewModel<HomeViewModel>()
                }
                FavoritesViewModel::class.simpleName -> {
                    logcat { "The pagingDataObservableProvider is a FavoritesViewModel instance." }
                    sharedViewModel<FavoritesViewModel>()
                }
                FollowsViewModel::class.simpleName -> {
                    logcat { "The pagingDataObservableProvider is a FollowsViewModel instance." }
                    sharedViewModel<FollowsViewModel>()
                }
                else -> {
                    logcat(LogPriority.ERROR) { "Provided class $pagingDataObservableProviderName is not a recognized pagingDataObservableProvider." }
                    throw IllegalArgumentException()
                }
            }
        viewModel = viewModel<PostViewerViewModel> { parametersOf(pagingDataObservableProvider.value) }.value

        logcat { "viewModel.pagingDataObservable = ${viewModel.pagingDataObservable}" }
    }

    private fun setUpViewPager(isSlideShowOnGoing: Boolean = false) {
        logcat { "setUpViewPager" }

        val onPositionChangedCallback = { nextPosition: Int ->
            logcat { "onPositionChangedCallback: nextPosition = $nextPosition" }
            binding.viewPagerPostViewer.customSetCurrentItem(nextPosition, ANIMATION_DURATION_SHORT)
            currentPosition = nextPosition
            // TODO: Make Observable
        }

        val postViewerAdapter = PostViewerAdapter(
            requireContext(),
            viewModel,
            onPositionChangedCallback,
            isSlideShowOnGoing,
            shouldShowNavigationToFollowsOption = previousFragmentName?.contains("follows", ignoreCase = true)?.not() ?: true
        )

        with(binding) {
            viewPagerPostViewer.isVisible = false
            viewPagerPostViewer.adapter = postViewerAdapter
            viewPagerPostViewer.isUserInputEnabled = false
            viewPagerPostViewer.offscreenPageLimit = 2

            viewModel.pagingDataObservable
                .filter {
                    // Because the paging data is being provided by a ViewModel belonging to another Fragment,
                    // we check if this fragment is active before reacting to it to avoid exceptions.
                    this@PostViewerFragment.isAdded
                    view != null
                }
                .doOnNext { logcat { "viewModel.pagingDataObservable.onNext" } }
                .subscribe { pagingData ->
                    postViewerAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                    // Initial setup. Let's scroll to the right position as soon as the pagingAdapter is done loading.
                    postViewerAdapter.loadStateFlow
                        .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                        .take(1)
                        .onEach {
                            logcat { "postViewerAdapter.loadStateFlow.onEach: loadState = NotLoading" }
                            viewPagerPostViewer.setCurrentItem(currentPosition, false)
                            viewPagerPostViewer.isVisible = true
                        }
                        .launchIn(MainScope())
                }
                .addTo(disposables)

            postViewerAdapter.readyToBeDrawnSubject
                .observeOn(AndroidSchedulers.mainThread())
                .filter { this@PostViewerFragment.isVisible }
                .filter { it == (currentPosition) }
                .take(1)
                .doOnNext { logcat { "postViewerAdapter.readyToBeDrawnSubject.onNext: position = $it" } }
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

    private fun setUpOnBackPressedCallback() {
        logcat { "setUpOnBackPressedCallback" }

        (activity as MainActivity).onBackPressedDispatcher.addCallback(this) {
            logcat(LogPriority.INFO) { "MainActivity back pressed" }

            // Stops the user from clicking on anything while the transition takes place.
            (binding.viewPagerPostViewer.adapter as PostViewerAdapter).disposables.dispose()

            val visibleViewHolder = (binding.viewPagerPostViewer.adapter as PostViewerAdapter).getViewHolderForPosition(currentPosition)
            val imageTransitionName =
                visibleViewHolder
                    ?.binding
                    ?.imageViewPostMainImage
                    ?.transitionName
            logcat(LogPriority.INFO) { "Transition name = $imageTransitionName" }
            imageTransitionName?.let { (sharedElementReturnTransition as Transition).addTarget(it) }

            findNavController().previousBackStackEntry?.savedStateHandle?.set(SAVED_STATE_POSITION_KEY, currentPosition)
            findNavController().popBackStack()
        }
    }

    private fun setUpNavigationToFollows() {
        logcat { "setUpNavigationToFollows" }
        viewModel.navigateToFollowsActionTriggerSubject
            .observeOn(AndroidSchedulers.mainThread())
            .filter { this.isVisible }
            .throttleFirst(250, TimeUnit.MILLISECONDS)
            .subscribe { (userName, resultSubject) ->
                try {
                    goToFollowsFragment(userName)
                    resultSubject.onNext(Completable.complete())
                }
                catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Navigation to FollowsFragment failed! Message = ${e.message}" }
                    resultSubject.onNext(Completable.error(e))
                }
            }.addTo(disposables)
    }

    private fun goToFollowsFragment(userName: String) {
        logcat { "goToFollowsFragment: userName = $userName" }


        val action = PostViewerFragmentDirections.actionPostViewerToFollows(userName)
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        logcat { "onDestroyView" }

        (binding.viewPagerPostViewer.adapter as PostViewerAdapter).disposables.dispose()

        super.onDestroyView()
        (activity as MainActivity).apply {
            supportActionBar?.show()
            findViewById<BottomNavigationView>(R.id.bottom_nav_view_main).isVisible = true
            unlockDrawer()
        }
    }

    override fun onDestroy() {
        logcat(LogPriority.ERROR) { "onDestroy" }
        disposables.dispose()

        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        logcat { "onSaveInstanceState" }
        outState.putInt(SAVED_STATE_POSITION_KEY, currentPosition)
        _binding?.let { binding ->
            outState.putBoolean(
                SAVED_STATE_SLIDESHOW_KEY,
                (binding.viewPagerPostViewer.adapter as PostViewerAdapter).slideShowOnOffSubject.value ?: return
            )
        }
//        viewModel.savedPosition = currentPosition
//        viewModel.savedSlideshowStateIsOn = (binding.viewPagerPostViewer.adapter as PostViewerAdapter).slideShowOnOffSubject.value ?: return

        super.onSaveInstanceState(outState)
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