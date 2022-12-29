package com.b4kancs.rxredditdemo.ui.postviewer

import android.animation.Animator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.transition.AutoTransition
import androidx.transition.Transition
import androidx.viewpager2.widget.ViewPager2
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentPostViewerBinding
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
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
    private lateinit var delayedEnterTransitionTriggerDisposable: Disposable
    private var currentPosition = 0     // This gets recovered when the Fragment gets restored from the BackStack

    init {
        logcat { "init $this" }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        logcat { "onConfigurationChanged" }
        // TODO
        super.onConfigurationChanged(newConfig)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        currentPosition = savedInstanceState?.getInt(SAVED_STATE_POSITION_KEY)
            ?: if (args.position != -1) args.position else 0
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
//        sharedElementReturnTransition = AutoTransition().apply {
//            addListener(object : Transition.TransitionListener {
//                override fun onTransitionStart(transition: Transition) {
//                    logcat(LogPriority.INFO) { "onTransitionStart" }
//                }
//
//                override fun onTransitionEnd(transition: Transition) {
//                    logcat(LogPriority.INFO) { "onTransitionEnd" }
//                }
//
//                override fun onTransitionCancel(transition: Transition) {
//                    logcat(LogPriority.INFO) { "onTransitionCancel" }
//                }
//
//                override fun onTransitionPause(transition: Transition) {
//                    logcat(LogPriority.INFO) { "onTransitionPause" }
//                }
//
//                override fun onTransitionResume(transition: Transition) {
//                    logcat(LogPriority.INFO) { "onTransitionResume" }
//                }
//            })
//        }

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
        logcat { "Recovered from savedInstanceState: currentPosition = $currentPosition, isSlideShowOngoing = $isSlideShowOngoing" }

        setUpViewModel()
        setUpViewPager(isSlideShowOngoing)
        setUpOnBackPressedCallback()
        setUpNavigationToFollows()
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

        // If the fragment is newly created, the viewModel hasn't been initialized.
//        val viewModelProvider: PostViewerViewModelProviderInterface by inject()
//        val fragmentBackStackId = findNavController().currentBackStackEntry!!.id
//        viewModelProvider.getViewModel(fragmentBackStackId = fragmentBackStackId)?.let {
        // If we have an instance stored with the key fragmentBackStackId, return that.
//            viewModel = it
//            return
//        }

        // Else, get a new instance with the provided pagingDataObservable, if any.
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
                FollowsViewModel::class.simpleName -> {
                    logcat { "The pagingDataObservableProvider is a FollowsViewModel class." }
                    sharedViewModel<FollowsViewModel>()
                }
                else -> {
                    logcat(LogPriority.ERROR) { "Provided class $pagingDataObservableProviderName is not a recognized pagingDataObservableProvider." }
                    throw IllegalArgumentException()
                }
            }
//        viewModel = viewModelProvider.getViewModel(pagingDataObservableProvider = pagingDataObservableProvider.value)!!
        viewModel = viewModel<PostViewerViewModel> { parametersOf(pagingDataObservableProvider.value) }.value
    }

    private fun setUpViewPager(isSlideShowOngoing: Boolean) {
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
            isSlideShowOngoing
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
                    this@PostViewerFragment.isVisible
//                    view != null
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

            findNavController().previousBackStackEntry?.savedStateHandle?.set("position", currentPosition)
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

        // Save the instance of the PostViewerViewModel so that in case this fragment is popped from the back stack, it can be recovered.
//        val viewModelProvider: PostViewerViewModelProviderInterface by inject()
//        val fragmentId = findNavController().currentBackStackEntry!!.id
//        viewModelProvider.persistViewModelForFragmentOnBackStack(fragmentId, viewModel)

        logcat(LogPriority.ERROR) { "The current destination is ${findNavController().currentDestination?.displayName}" }

        val action = PostViewerFragmentDirections.actionPostViewerToFollows(userName)
//        val navController = Navigation.findNavController(requireActivity().findViewById(R.id.fragment_main_nav_host))

        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        logcat { "onDestroyView" }
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
        _binding?.let { binding ->
            outState.putInt(SAVED_STATE_POSITION_KEY, binding.viewPagerPostViewer.currentItem)
            outState.putBoolean(
                SAVED_STATE_SLIDESHOW_KEY,
                (binding.viewPagerPostViewer.adapter as PostViewerAdapter).slideShowOnOffSubject.value ?: return
            )
        }
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