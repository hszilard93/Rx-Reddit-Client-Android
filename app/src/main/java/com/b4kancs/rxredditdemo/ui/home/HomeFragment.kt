package com.b4kancs.rxredditdemo.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentHomeBinding
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerFragment
import com.b4kancs.rxredditdemo.ui.shared.PostVerticalRvAdapter
import com.b4kancs.rxredditdemo.ui.uiutils.CustomLinearLayoutManager
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
import org.koin.core.parameter.parametersOf
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {
    companion object {
        const val FLICKERING_DELAY = 200L
    }

    private val mainViewModel: MainViewModel by sharedViewModel()
    private val homeViewModel: HomeViewModel by sharedViewModel { parametersOf(mainViewModel) }
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val disposables = CompositeDisposable()
    private var positionToGoTo: Int? = null
    private var justChangedSubreddits = false
    private lateinit var delayedTransitionTriggerDisposable: Disposable

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        logcat {
            "onCreateView\n  Current nav backstack: ${
                findNavController().backQueue
                    .map { it.destination }
                    .joinToString("\n ", "\n ")
            }"
        }

        with(findNavController().currentBackStackEntry) {
            positionToGoTo = this?.savedStateHandle?.get<Int>(PostViewerFragment.SAVED_STATE_POSITION_KEY)
            this?.savedStateHandle?.remove<Int>(PostViewerFragment.SAVED_STATE_POSITION_KEY)
            positionToGoTo?.let { logcat(LogPriority.INFO) { "Recovered position from PostViewerFragment. positionToGoTo = $it" } }
        }

        if (homeViewModel.isAppJustStarted) {
            logcat(LogPriority.INFO) { "Condition: the app just started." }
            positionToGoTo = 0
            homeViewModel.isAppJustStarted = false
        }

        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        if (justChangedSubreddits) positionToGoTo = 0
        logcat(LogPriority.INFO) { "positionToGoTo = $positionToGoTo" }

        logcat(LogPriority.INFO) { "postponeEnterTransition()" }
        postponeEnterTransition()

        // If, for some reason, the transition doesn't get triggered in time (the image is slow in loading, etc), we force it after a delay.
        delayedTransitionTriggerDisposable = Observable.timer(500, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { logcat(LogPriority.INFO) { "Starting delayed enter transition timer." } }
            .subscribe {
                logcat(LogPriority.INFO) { "Triggering delayed enter transition." }
                startPostponedEnterTransition()
            }
            .addTo(disposables)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logcat { "onViewCreated" }
        setUpRecyclerView()
        setUpOptionsMenu()
    }

    private fun setUpRecyclerView() {
        logcat { "setUpRecyclerView" }
        val mainActivity = activity as MainActivity
        binding.apply {
            rvHomePosts.layoutManager = CustomLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL)
                .apply { canScrollHorizontally = false }

            if (rvHomePosts.adapter == null) {
                // If positionToNavigateTo is not null, we need to disable glide transformations and some other stuff for the
                // shared element transition to work properly
                val shouldDisableTransformations = if (positionToGoTo != null) {
                    logcat { "Disabling glide transformations" }
                    true
                } else false
                rvHomePosts.adapter = PostVerticalRvAdapter(
                    mainActivity,
                    shouldDisableTransformations,
                    homeViewModel
                )
            }
            val postsHomeAdapter = rvHomePosts.adapter as PostVerticalRvAdapter

            homeViewModel.cachedPagingObservable
                .subscribe { pagingData ->
                    try {
                        postsHomeAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                        // Make the RecyclerView visible and scroll to the top only when the new data has been loaded!
                        postsHomeAdapter.loadStateFlow
                            .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                            .take(1)
                            .onEach {
                                logcat { "loadStateFlow.onEach" }
                                // If the subreddit feed contains no displayable posts (images etc.), display a textview
                                if (postsHomeAdapter.itemCount == 1) {   // The 1 is because of the always-present bottom loading indicator
                                    binding.textViewHomeNoMedia.isVisible = true
                                } else {
                                    binding.textViewHomeNoMedia.isVisible = false
                                    positionToGoTo?.let { pos ->
                                        logcat(LogPriority.INFO) { "Scrolling to position: $pos" }
                                        rvHomePosts.scrollToPosition(pos)
                                    }

                                    if (justChangedSubreddits) {
                                        logcat(LogPriority.INFO) { "justChangedSubreddits = true; scrolling to position 0" }
                                        rvHomePosts.scrollToPosition(0)
                                        rvHomePosts.visibility = View.INVISIBLE
                                        // This delay stops the flickering after a subreddit change
                                        Observable.timer(FLICKERING_DELAY, TimeUnit.MILLISECONDS)
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe {
                                                rvHomePosts.isVisible = true
                                                justChangedSubreddits = false
                                            }.addTo(disposables)
                                    }
                                }
                            }
                            .launchIn(MainScope())
                    } catch (e: Exception) {
                        // There might be a weird NullPointerException happening sometimes that doesn't really seem to affect anything
                        logcat(LogPriority.ERROR) { e.stackTrace.toString() }
                    }
                }
                .addTo(disposables)

            postsHomeAdapter.addLoadStateListener { combinedLoadStates ->
                progressBarHomeLarge.isVisible = combinedLoadStates.refresh is LoadState.Loading
            }

            mainViewModel.selectedSubredditPublishSubject
                .debounce(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat(LogPriority.INFO) { "selectedSubredditChangedSubject.doOnNext: ${it.name}" } }
                .subscribe { sub ->
                    rvHomePosts.isVisible = false
                    justChangedSubreddits = true
                    postsHomeAdapter.refresh()
                }
                .addTo(disposables)

            srlHome.setOnRefreshListener {
                postsHomeAdapter.refresh()
                rvHomePosts.scrollToPosition(0)
                srlHome.isRefreshing = false
            }

            postsHomeAdapter.postClickedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat(LogPriority.INFO) { "postClickedSubject.doOnNext: pos = ${it.first}" } }
                .subscribe { (position, view) ->
                    createNewPostViewerFragment(position, view)
                    (rvHomePosts.layoutManager as CustomLinearLayoutManager).canScrollVertically = false
                    // By disposing of the subscriptions here, we stop the user from accidentally clicking on a post
                    // while the transition takes place.
                    postsHomeAdapter.disposables.dispose()
                }.addTo(disposables)

            postsHomeAdapter.readyToBeDrawnSubject
                .observeOn(AndroidSchedulers.mainThread())
                .filter { if (positionToGoTo != null) it == positionToGoTo else true }
                .take(1)
                .doOnNext { logcat(LogPriority.INFO) { "readyToBeDrawnSubject.onNext: pos = $it" } }
                .subscribe {
                    // Fine scroll to better position the imageview
                    val toScrollY = binding.rvHomePosts
                        .findViewHolderForLayoutPosition(positionToGoTo ?: 0)
                        ?.itemView
                        ?.y
                        ?.minus(20f)
                        ?: 0f
                    logcat { "Scrolling by y = $toScrollY" }
                    binding.rvHomePosts.scrollBy(0, toScrollY.toInt())

                    // We put these reveals here so that they will be synced with the SharedElementTransition.
                    mainActivity.animateShowActionBar()
                    mainActivity.animateShowBottomNavBar()

                    Observable.timer(50, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            rvHomePosts.findViewHolderForLayoutPosition(positionToGoTo ?: 0)
                                ?.let { viewHolderAtPosition ->
                                    val transitionName =
                                        (viewHolderAtPosition as PostVerticalRvAdapter.PostViewHolder)
                                            .binding
                                            .postImageView
                                            .transitionName
                                    logcat(LogPriority.INFO) { "Transition name = $transitionName" }
                                    logcat { "startPostponedEnterTransition()" }
                                }
                            logcat { "startPostponedEnterTransition()" }
                            startPostponedEnterTransition()
                            logcat { "Disposing of delayedTransitionTriggerDisposable" }
                            delayedTransitionTriggerDisposable.dispose()
                            postsHomeAdapter.disableTransformations = false
                        }.addTo(disposables)
                }.addTo(disposables)
        }
    }

    private fun setUpOptionsMenu() {
        logcat { "setUpOptionsMenu" }
        Observable.interval(0, 200, TimeUnit.MILLISECONDS)
            .takeUntil { (activity as MainActivity).menu != null }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                val menu = (activity as MainActivity).menu
                if (menu != null) {
                    val menuItems = menu.children
                    for (item in menuItems) {
                        when (item.groupId) {
                            R.id.menu_group_toolbar_subreddit_actions -> item.isVisible = true
                            R.id.menu_group_toolbar_app_actions -> item.isVisible = true
                            else -> item.isVisible = false
                        }
                    }
                }
            }.addTo(disposables)
    }

    private fun createNewPostViewerFragment(position: Int, sharedView: View) {
        logcat { "createNewPostViewerFragment" }
        val sharedElementExtras = FragmentNavigatorExtras(sharedView to sharedView.transitionName)
        val action = HomeFragmentDirections.actionHomeToPostViewer(position, homeViewModel.javaClass.simpleName)
        findNavController().navigate(action, sharedElementExtras)
    }

    override fun onDestroyView() {
        logcat { "onDestroyView" }
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        logcat { "onDestroy" }
        super.onDestroy()
        disposables.dispose()
    }
}