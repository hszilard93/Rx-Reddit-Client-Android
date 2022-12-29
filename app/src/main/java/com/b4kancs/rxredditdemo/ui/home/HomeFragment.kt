package com.b4kancs.rxredditdemo.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionInflater
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentHomeBinding
import com.b4kancs.rxredditdemo.model.Subreddit.Status
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerFragment
import com.b4kancs.rxredditdemo.ui.shared.PostsVerticalRvAdapter
import com.b4kancs.rxredditdemo.ui.uiutils.CustomLinearLayoutManager
import com.b4kancs.rxredditdemo.ui.uiutils.SnackType
import com.b4kancs.rxredditdemo.ui.uiutils.makeSnackBar
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
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

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logcat { "onViewCreated" }

        // Every time the Fragment is recreated, we need to change the support action bar title.
        mainViewModel.selectedSubredditReplayObservable
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { (activity as MainActivity).supportActionBar?.title = it.name }
            .addTo(disposables)

        setUpSharedElementTransition()
        setUpRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        setUpOptionsMenu()
    }

    private fun setUpSharedElementTransition() {
        logcat { "setUpSharedElementTransition" }
        sharedElementEnterTransition = TransitionInflater
            .from(requireContext())
            .inflateTransition(R.transition.shared_element_transition)

        postponeEnterTransition()

        // If, for some reason, the transition doesn't get triggered in time (the image is slow to load etc.), we force the transition.
        delayedTransitionTriggerDisposable = Observable.timer(500, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { logcat { "Starting delayed enter transition timer." } }
            .subscribe {
                logcat(LogPriority.INFO) { "Triggering delayed enter transition." }
                startPostponedEnterTransition()
            }
            .addTo(disposables)
    }

    private fun setUpRecyclerView() {
        logcat { "setUpRecyclerView" }
        val mainActivity = activity as MainActivity
        binding.apply {
            rvHomePosts.layoutManager = CustomLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL)
                .apply { canScrollHorizontally = false }

            if (rvHomePosts.adapter == null) {
                // If positionToGoTo is not null, we need to disable glide transformations and some other stuff for the
                // shared element transition to work properly
                val shouldDisableTransformations = if (positionToGoTo != null) {
                    logcat { "Disabling glide transformations" }
                    true
                } else false
                rvHomePosts.adapter = PostsVerticalRvAdapter(
                    mainActivity,
                    shouldDisableTransformations,
                    homeViewModel
                )
            }
            val postsHomeAdapter = rvHomePosts.adapter as PostsVerticalRvAdapter

            homeViewModel.subredditPostsCachedPagingObservable
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
                .throttleFirst(1, TimeUnit.SECONDS)
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
                                        (viewHolderAtPosition as PostsVerticalRvAdapter.PostViewHolder)
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

        val mergedCurrentSubUpdateObservable = Observable.merge(    // We want to refresh the visibility of the menu item not only when the
            mainViewModel.getSubredditsChangedSubject(),         // subreddit is changed, but also when there is a modification of the subreddits
            mainViewModel.selectedSubredditReplayObservable // (e.g. a sub is set as default, so the option should no longer be visible)
        )
            .subscribeOn(Schedulers.io())
            .map { mainViewModel.selectedSubredditReplayObservable.blockingLatest().first() }
            .share()

        fun setUpSetAsDefaultMenuItem(menuItems: Sequence<MenuItem>) {
            mergedCurrentSubUpdateObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { currentSub ->
                    val setAsDefaultMenuItem = menuItems
                        .find { it.itemId == R.id.menu_item_toolbar_subreddit_set_default }
                    setAsDefaultMenuItem?.isVisible = currentSub.name != homeViewModel.getDefaultSubreddit().name
                    setAsDefaultMenuItem?.clicks()
                        ?.doOnNext { logcat(LogPriority.INFO) { "setAsDefaultMenuItem.clicks.onNext" } }
                        ?.subscribe {
                            mainViewModel.setAsDefaultSub(currentSub)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(
                                    onComplete = {
                                        makeSnackBar(
                                            binding.root,
                                            null,
                                            "${currentSub.address} is set as the default subreddit!"
                                        ).show()
                                    },
                                    onError = {
                                        makeSnackBar(
                                            binding.root,
                                            null,
                                            "Error: ${currentSub.address} could not be set as the default subreddit :(",
                                            SnackType.ERROR
                                        ).show()
                                    }
                                ).addTo(disposables)
                        }?.addTo(disposables)
                }.addTo(disposables)
        }

        fun setUpRemoveFromYourSubsMenuItem(menuItems: Sequence<MenuItem>) {
            mergedCurrentSubUpdateObservable
                .subscribe { currentSub ->
                    // The sub from the selectedSubredditReplayObservable may not reflect changes in Status, only the DB is always up to date.
                    mainViewModel.getSubredditByAddress(currentSub.address)
                        .onErrorResumeWith { Single.just(currentSub) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { updatedSub ->
                            val removeFromYourMenuItem = menuItems
                                .find { it.itemId == R.id.menu_item_toolbar_subreddit_remove }

                            removeFromYourMenuItem?.isVisible = updatedSub.status in listOf(Status.IN_USER_LIST, Status.FAVORITED)
                                    && updatedSub.name != homeViewModel.getDefaultSubreddit().name
                            removeFromYourMenuItem?.clicks()
                                ?.doOnNext { logcat(LogPriority.INFO) { "removeFromYourMenuItem.clicks.onNext" } }
                                ?.subscribe {
                                    mainViewModel.changeSubredditStatusTo(updatedSub, Status.IN_DEFAULTS_LIST)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onSuccess = {
                                                makeSnackBar(binding.root, null, "Done!").show()
                                            },
                                            onError = {
                                                makeSnackBar(
                                                    binding.root,
                                                    null,
                                                    "Could not perform action :(",
                                                    SnackType.ERROR
                                                ).show()
                                            }
                                        ).addTo(disposables)
                                }
                                ?.addTo(disposables)
                        }.addTo(disposables)
                }.addTo(disposables)
        }

        fun setUpDeleteFromSubsMenuItem(menuItems: Sequence<MenuItem>) {
            mergedCurrentSubUpdateObservable
                .subscribe { currentSub ->
                    // The sub from the selectedSubredditReplayObservable may not reflect changes in Status, only the DB is always up to date.
                    mainViewModel.getSubredditByAddress(currentSub.address)
                        .onErrorResumeWith { Single.just(currentSub) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { updatedSub ->
                            val deleteSubMenuItem = menuItems
                                .find { it.itemId == R.id.menu_item_toolbar_subreddit_delete }

                            deleteSubMenuItem?.isVisible = updatedSub.status != Status.NOT_IN_DB
                                    && updatedSub != homeViewModel.getDefaultSubreddit()
                            deleteSubMenuItem?.clicks()
                                ?.doOnNext { logcat(LogPriority.INFO) { "deleteSubMenuItem.clicks.onNext" } }
                                ?.subscribe {
                                    mainViewModel.deleteSubreddit(updatedSub)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onComplete = {
                                                makeSnackBar(binding.root, null, "${updatedSub.address} has been deleted!")
                                                    .show()
                                            },
                                            onError = {
                                                makeSnackBar(
                                                    binding.root,
                                                    null,
                                                    "Could not delete ${updatedSub.address} :(",
                                                    SnackType.ERROR
                                                ).show()
                                            }
                                        )
                                        .addTo(disposables)
                                }
                                ?.addTo(disposables)
                        }
                }.addTo(disposables)
        }

        fun setUpAddToYourSubsMenuItem(menuItems: Sequence<MenuItem>) {
            mergedCurrentSubUpdateObservable
                .subscribe { currentSub ->
                    // The sub from the selectedSubredditReplayObservable may not reflect changes in Status, only the DB is always up to date.
                    mainViewModel.getSubredditByAddress(currentSub.address)
                        .onErrorResumeWith { Single.just(currentSub) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { updatedSub ->
                            val addToYourSubsMenuItem = menuItems
                                .find { it.itemId == R.id.menu_item_toolbar_subreddit_add_to_your }

                            addToYourSubsMenuItem?.isVisible = updatedSub.status in listOf(Status.NOT_IN_DB, Status.IN_DEFAULTS_LIST)
                            addToYourSubsMenuItem?.clicks()
                                ?.doOnNext { logcat(LogPriority.INFO) { "addToYourSubreddits.clicks.onNext" } }
                                ?.subscribe {
                                    mainViewModel.changeSubredditStatusTo(updatedSub, Status.IN_USER_LIST)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onSuccess = {
                                                makeSnackBar(binding.root, null, "Done!").show()
                                            },
                                            onError = {
                                                makeSnackBar(
                                                    binding.root,
                                                    null,
                                                    "Could not perform action :(",
                                                    SnackType.ERROR
                                                ).show()
                                            }
                                        )
                                        .addTo(disposables)
                                }
                                ?.addTo(disposables)
                        }
                }.addTo(disposables)
        }

        fun setUpAddToFavorites(menuItems: Sequence<MenuItem>) {
            mergedCurrentSubUpdateObservable
                .subscribe { currentSub ->
                    // The sub from the selectedSubredditReplayObservable may not reflect changes in Status, only the DB is always up to date.
                    mainViewModel.getSubredditByAddress(currentSub.address)
                        .onErrorResumeWith { Single.just(currentSub) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { updatedSub ->
                            val addToFavoritesMenuItem = menuItems
                                .find { it.itemId == R.id.menu_item_toolbar_subreddit_add_to_favorites }

                            addToFavoritesMenuItem?.isVisible = updatedSub.status != Status.FAVORITED
                            addToFavoritesMenuItem?.clicks()
                                ?.doOnNext { logcat(LogPriority.INFO) { "addToFavoriteSubreddits.clicks.onNext" } }
                                ?.subscribe {
                                    mainViewModel.changeSubredditStatusTo(updatedSub, Status.FAVORITED)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onSuccess = {
                                                makeSnackBar(binding.root, null, "Done!").show()
                                            },
                                            onError = {
                                                makeSnackBar(
                                                    binding.root,
                                                    null,
                                                    "Could not perform action :(",
                                                    SnackType.ERROR
                                                ).show()
                                            }
                                        )
                                        .addTo(disposables)
                                }
                                ?.addTo(disposables)
                        }
                }.addTo(disposables)
        }

        fun setUpRemoveFromFavorites(menuItems: Sequence<MenuItem>) {
            mergedCurrentSubUpdateObservable
                .subscribe { currentSub ->
                    // The sub from the selectedSubredditReplayObservable may not reflect changes in Status, only the DB is always up to date.
                    mainViewModel.getSubredditByAddress(currentSub.address)
                        .onErrorResumeWith { Single.just(currentSub) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { updatedSub ->
                            val removeFromFavoritesMenuItem = menuItems
                                .find { it.itemId == R.id.menu_item_toolbar_subreddit_remove_from_favorites }

                            removeFromFavoritesMenuItem?.isVisible = updatedSub.status == Status.FAVORITED
                                    && updatedSub.name != homeViewModel.getDefaultSubreddit().name
                            removeFromFavoritesMenuItem?.clicks()
                                ?.doOnNext { logcat(LogPriority.INFO) { "removeFromFavoriteSubreddits.clicks.onNext" } }
                                ?.subscribe {
                                    mainViewModel.changeSubredditStatusTo(updatedSub, Status.IN_USER_LIST)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onSuccess = {
                                                makeSnackBar(binding.root, null, "Done!").show()
                                            },
                                            onError = {
                                                makeSnackBar(
                                                    binding.root,
                                                    null,
                                                    "Could not perform action :(",
                                                    SnackType.ERROR
                                                ).show()
                                            }
                                        )
                                        .addTo(disposables)
                                }
                                ?.addTo(disposables)
                        }
                }.addTo(disposables)
        }

        Observable.interval(250, TimeUnit.MILLISECONDS)
            .filter { (activity as MainActivity).menu != null }
            .take(1)    // Wait until the menu is ready.
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat { "menu is ready .onNext" } }
            .subscribe {
                val menu = (activity as MainActivity).menu!!
                val menuItems = menu.children
                for (item in menuItems) {
                    when (item.groupId) {
                        R.id.menu_group_toolbar_subreddit_actions -> item.isVisible = true
                        R.id.menu_group_toolbar_app_actions -> item.isVisible = true
                        else -> item.isVisible = false
                    }
                }
                setUpSetAsDefaultMenuItem(menuItems)
                setUpRemoveFromYourSubsMenuItem(menuItems)
                setUpDeleteFromSubsMenuItem(menuItems)
                setUpAddToYourSubsMenuItem(menuItems)
                setUpAddToFavorites(menuItems)
                setUpRemoveFromFavorites(menuItems)
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