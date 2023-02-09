package com.b4kancs.rxredditdemo.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewbinding.ViewBinding
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentHomeBinding
import com.b4kancs.rxredditdemo.domain.pagination.SubredditJsonPagingSource
import com.b4kancs.rxredditdemo.model.Subreddit.Status
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragment
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragmentViewModel.UiState
import com.b4kancs.rxredditdemo.ui.shared.PostsVerticalRvAdapter
import com.b4kancs.rxredditdemo.ui.uiutils.CustomLinearLayoutManager
import com.b4kancs.rxredditdemo.ui.uiutils.SnackType
import com.b4kancs.rxredditdemo.ui.uiutils.makeSnackBar
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import logcat.LogPriority
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class HomeFragment : BaseListingFragment() {

    override val viewModel: HomeViewModel by sharedViewModel()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var justChangedSubreddits = false

    override fun setUpBinding(inflater: LayoutInflater, container: ViewGroup?): ViewBinding {
        logcat { "setUpBinding" }
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding
    }

    override fun onCreateViewDoAlso(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) {
        logcat { "onCreateViewDoExtras" }

        if (viewModel.isAppJustStarted) {
            logcat(LogPriority.INFO) { "Condition: the app just started." }
            positionToGoTo = 0
            viewModel.isAppJustStarted = false
        }
        else if (justChangedSubreddits) positionToGoTo = 0
        logcat(LogPriority.INFO) { "positionToGoTo = $positionToGoTo" }
    }

    override fun setUpActionBarAndRelated() {
        logcat { "setUpActionBarAndRelated" }
        // Every time the Fragment is recreated, we need to change the support action bar title.
        viewModel.selectedSubredditReplayObservable
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { (activity as MainActivity).supportActionBar?.title = it.name }
            .addTo(disposables)
    }

    override fun setUpDrawer() {
        logcat { "setUpDrawer" }
        (activity as MainActivity).also {
            it.setUpSubredditDrawer(viewModel)
            it.setUpSubredditDrawerSearchView(viewModel)
        }
    }

    override fun setUpRecyclerView() {
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
                }
                else false
                rvHomePosts.adapter = PostsVerticalRvAdapter(
                    mainActivity,
                    shouldDisableTransformations,
                    viewModel
                )
            }
            val postsHomeAdapter = rvHomePosts.adapter as PostsVerticalRvAdapter

            viewModel.postsCachedPagingObservable
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
                                if (postsHomeAdapter.itemCount <= 1) {   // The 1 is because of the always-present bottom loading indicator
                                    viewModel.uiStateBehaviorSubject.onNext(UiState.NO_CONTENT)
                                }
                                else {
                                    viewModel.uiStateBehaviorSubject.onNext(UiState.NORMAL)
                                    positionToGoTo?.let { pos ->
                                        logcat(LogPriority.INFO) { "Scrolling to position: $pos" }
                                        rvHomePosts.scrollToPosition(pos)
                                    }

                                    if (justChangedSubreddits) {
                                        logcat(LogPriority.INFO) { "justChangedSubreddits = true; scrolling to position 0" }
                                        rvHomePosts.scrollToPosition(0)
                                        rvHomePosts.visibility = View.INVISIBLE
                                        // This delay eliminates the flickering of the RV after a subreddit change
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

            postsHomeAdapter.loadStateFlow
                .map { loadStates ->
                    if (loadStates.refresh is LoadState.Error) {
                        logcat(LogPriority.WARN) { "LoadState.Error detected." }
                        val e = ((loadStates.refresh as LoadState.Error).error)
                        if ((e is HttpException && e.code() == 404)
                            || e is SubredditJsonPagingSource.NoSuchSubredditException)
                            viewModel.uiStateBehaviorSubject.onNext(UiState.ERROR_404)
                        else
                            viewModel.uiStateBehaviorSubject.onNext(UiState.ERROR_GENERIC)
                    }
                    else if (loadStates.refresh is LoadState.Loading) {
                        viewModel.uiStateBehaviorSubject.onNext(UiState.LOADING)
                    }
                    loadStates
                }
                .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                .onEach {
                    logcat(LogPriority.INFO) { "postsHomeAdapter.loadStateFlow.onEach loadStates.refresh == LoadState.NotLoading" }
                    if (postsHomeAdapter.itemCount != 1) {
                        viewModel.uiStateBehaviorSubject.onNext(UiState.NORMAL)

                        positionToGoTo?.let { pos ->
                            logcat(LogPriority.INFO) { "Scrolling to position: $pos" }
                            rvHomePosts.scrollToPosition(pos)
                        }
                    }
                    else {
                        viewModel.uiStateBehaviorSubject.onNext(UiState.NO_CONTENT)
                    }
                }.launchIn(MainScope())

            viewModel.selectedSubredditChangedPublishSubject
                .throttleFirst(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat(LogPriority.INFO) { "selectedSubredditChangedSubject.doOnNext: ${it.name}" } }
                .subscribe { _ ->
                    viewModel.uiStateBehaviorSubject.onNext(UiState.LOADING)
                    justChangedSubreddits = true
                    postsHomeAdapter.refresh()
                }
                .addTo(disposables)

            srlHome.setOnRefreshListener {
                viewModel.uiStateBehaviorSubject.onNext(UiState.LOADING)
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

    override fun setUpUiStatesBehaviour() {
        logcat { "setUpUiStatesBehaviour" }
        viewModel.uiStateBehaviorSubject
            .observeOn(AndroidSchedulers.mainThread())
            .filter { _binding != null }
            .doOnNext { logcat { "viewModel.uiStateBehaviorSubject.onNext" } }
            .subscribe { uiState ->
                logcat { "uiState = $uiState" }
                with(binding) {
                    when (uiState) {
                        UiState.NORMAL -> {
                            rvHomePosts.isVisible = true
                            linearLayoutHomeErrorContainer.isVisible = false
                            progressBarHomeLarge.isVisible = false
                        }
                        UiState.LOADING -> {
                            rvHomePosts.isVisible = false
                            linearLayoutHomeErrorContainer.isVisible = false
                            progressBarHomeLarge.isVisible = true
                        }
                        UiState.ERROR_404 -> {
                            val errorMessage = getString(R.string.string_home_http_404_error_message)
                            val errorImageId = R.drawable.im_error_404_resized
                            linearLayoutHomeErrorContainer.isVisible = true
                            textViewHomeError.text = errorMessage
                            imageViewHomeError.setImageResource(errorImageId)
                            progressBarHomeLarge.isVisible = false
                            rvHomePosts.isVisible = false
                        }
                        UiState.ERROR_GENERIC -> {
                            val errorMessage = getString(R.string.string_common_network_error_message)
                            val errorImageId = R.drawable.im_error_network
                            linearLayoutHomeErrorContainer.isVisible = true
                            textViewHomeError.text = errorMessage
                            imageViewHomeError.setImageResource(errorImageId)
                            progressBarHomeLarge.isVisible = false
                            rvHomePosts.isVisible = false
                        }
                        UiState.NO_CONTENT -> {
                            val errorMessage = getString(R.string.string_home_no_posts_in_sub)
                            val errorImageId = R.drawable.im_error_no_content_dog
                            linearLayoutHomeErrorContainer.isVisible = true
                            textViewHomeError.text = errorMessage
                            imageViewHomeError.setImageResource(errorImageId)
                            progressBarHomeLarge.isVisible = false
                            rvHomePosts.isVisible = false
                        }
                        else -> {
                            logcat(LogPriority.ERROR) { "uiState error: state $uiState is illegal in HomeFragment." }
                            throw IllegalStateException("State $uiState is illegal in HomeFragment.")
                        }
                    }

                    if (uiState in setOf(
                            UiState.ERROR_404,
                            UiState.ERROR_GENERIC,
                            UiState.NO_CONTENT
                        )) {
                        (activity as MainActivity).expandAppBar()
                        (activity as MainActivity).expandBottomNavBar()
                    }
                }
            }.addTo(disposables)
    }

    override fun setUpOptionsMenu() {
        logcat { "setUpOptionsMenu" }

        val mergedCurrentSubUpdateObservable = Observable.merge(    // We want to refresh the visibility of the menu item not only when the
            viewModel.getSubredditsChangedSubject(),         // subreddit is changed, but also when there is a modification of the subreddits
            viewModel.selectedSubredditReplayObservable // (e.g. a sub is set as default, so the option should no longer be visible)
        )
            .subscribeOn(Schedulers.io())
            .map { viewModel.selectedSubredditReplayObservable.blockingLatest().first() }
            .share()

        fun setUpSetAsDefaultMenuItem(menuItems: Sequence<MenuItem>) {
            mergedCurrentSubUpdateObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { currentSub ->
                    val setAsDefaultMenuItem = menuItems
                        .find { it.itemId == R.id.menu_item_toolbar_subreddit_set_default }
                    setAsDefaultMenuItem?.isVisible = currentSub.name != viewModel.getDefaultSubreddit().name
                    setAsDefaultMenuItem?.clicks()
                        ?.doOnNext { logcat(LogPriority.INFO) { "setAsDefaultMenuItem.clicks.onNext" } }
                        ?.subscribe {
                            viewModel.setAsDefaultSub(currentSub)
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
                                            R.string.string_common_could_not_perform,
                                            type = SnackType.ERROR
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
                    viewModel.getSubredditByAddress(currentSub.address)
                        .onErrorResumeWith { Maybe.just(currentSub) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { updatedSub ->
                            val removeFromYourMenuItem = menuItems
                                .find { it.itemId == R.id.menu_item_toolbar_subreddit_remove }

                            removeFromYourMenuItem?.isVisible = updatedSub.status in listOf(Status.IN_USER_LIST, Status.FAVORITED)
                                    && updatedSub.name != viewModel.getDefaultSubreddit().name
                            removeFromYourMenuItem?.clicks()
                                ?.doOnNext { logcat(LogPriority.INFO) { "removeFromYourMenuItem.clicks.onNext" } }
                                ?.subscribe {
                                    viewModel.changeSubredditStatusTo(updatedSub, Status.IN_DEFAULTS_LIST)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onSuccess = {
                                                makeSnackBar(binding.root, null, "Done!").show()
                                            },
                                            onError = {
                                                makeSnackBar(
                                                    binding.root,
                                                    R.string.string_common_could_not_perform,
                                                    type = SnackType.ERROR
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
                    viewModel.getSubredditByAddress(currentSub.address)
                        .onErrorResumeWith { Maybe.just(currentSub) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { updatedSub ->
                            val deleteSubMenuItem = menuItems
                                .find { it.itemId == R.id.menu_item_toolbar_subreddit_delete }

                            deleteSubMenuItem?.isVisible = updatedSub.status != Status.NOT_IN_DB
                                    && updatedSub != viewModel.getDefaultSubreddit()
                            deleteSubMenuItem?.clicks()
                                ?.doOnNext { logcat(LogPriority.INFO) { "deleteSubMenuItem.clicks.onNext" } }
                                ?.subscribe {
                                    viewModel.deleteSubreddit(updatedSub)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onComplete = {
                                                makeSnackBar(binding.root, null, "${updatedSub.address} has been deleted!")
                                                    .show()
                                            },
                                            onError = {
                                                makeSnackBar(
                                                    binding.root,
                                                    R.string.string_common_could_not_perform,
                                                    type = SnackType.ERROR
                                                ).show()
                                            }
                                        )
                                        .addTo(disposables)
                                }
                                ?.addTo(disposables)
                        }.addTo(disposables)
                }.addTo(disposables)
        }

        fun setUpAddToYourSubsMenuItem(menuItems: Sequence<MenuItem>) {
            mergedCurrentSubUpdateObservable
                .subscribe { currentSub ->
                    // The sub from the selectedSubredditReplayObservable may not reflect changes in Status, only the DB is always up to date.
                    viewModel.getSubredditByAddress(currentSub.address)
                        .onErrorResumeWith { Maybe.just(currentSub) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { updatedSub ->
                            val addToYourSubsMenuItem = menuItems
                                .find { it.itemId == R.id.menu_item_toolbar_subreddit_add_to_your }

                            addToYourSubsMenuItem?.isVisible = updatedSub.status in listOf(Status.NOT_IN_DB, Status.IN_DEFAULTS_LIST)
                            addToYourSubsMenuItem?.clicks()
                                ?.doOnNext { logcat(LogPriority.INFO) { "addToYourSubreddits.clicks.onNext" } }
                                ?.subscribe {
                                    viewModel.changeSubredditStatusTo(updatedSub, Status.IN_USER_LIST)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onSuccess = { _ ->
                                                makeSnackBar(binding.root, R.string.string_common_done).show()
                                            },
                                            onError = { _ ->
                                                makeSnackBar(
                                                    binding.root,
                                                    R.string.string_common_could_not_perform,
                                                    type = SnackType.ERROR
                                                ).show()
                                            }
                                        )
                                        .addTo(disposables)
                                }
                                ?.addTo(disposables)
                        }.addTo(disposables)
                }.addTo(disposables)
        }

        fun setUpAddToFavorites(menuItems: Sequence<MenuItem>) {
            mergedCurrentSubUpdateObservable
                .subscribe { currentSub ->
                    // The sub from the selectedSubredditReplayObservable may not reflect changes in Status, only the DB is always up to date.
                    viewModel.getSubredditByAddress(currentSub.address)
                        .onErrorResumeWith { Maybe.just(currentSub) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { updatedSub ->
                            val addToFavoritesMenuItem = menuItems
                                .find { it.itemId == R.id.menu_item_toolbar_subreddit_add_to_favorites }

                            addToFavoritesMenuItem?.isVisible = updatedSub.status != Status.FAVORITED
                            addToFavoritesMenuItem?.clicks()
                                ?.doOnNext { logcat(LogPriority.INFO) { "addToFavoriteSubreddits.clicks.onNext" } }
                                ?.subscribe {
                                    viewModel.changeSubredditStatusTo(updatedSub, Status.FAVORITED)
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
                        }.addTo(disposables)
                }.addTo(disposables)
        }

        fun setUpRemoveFromFavorites(menuItems: Sequence<MenuItem>) {
            mergedCurrentSubUpdateObservable
                .subscribe { currentSub ->
                    // The sub from the selectedSubredditReplayObservable may not reflect changes in Status, only the DB is always up to date.
                    viewModel.getSubredditByAddress(currentSub.address)
                        .onErrorResumeWith { Maybe.just(currentSub) }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { updatedSub ->
                            val removeFromFavoritesMenuItem = menuItems
                                .find { it.itemId == R.id.menu_item_toolbar_subreddit_remove_from_favorites }

                            removeFromFavoritesMenuItem?.isVisible = updatedSub.status == Status.FAVORITED
                                    && updatedSub.name != viewModel.getDefaultSubreddit().name
                            removeFromFavoritesMenuItem?.clicks()
                                ?.doOnNext { logcat(LogPriority.INFO) { "removeFromFavoriteSubreddits.clicks.onNext" } }
                                ?.subscribe {
                                    viewModel.changeSubredditStatusTo(updatedSub, Status.IN_USER_LIST)
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
                        }.addTo(disposables)
                }.addTo(disposables)
        }

        Observable.interval(250, TimeUnit.MILLISECONDS)
            .filter { (activity as MainActivity).menu != null }
            .take(1)    // Wait until the menu is ready.
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat { "menu is ready .onNext" } }
            .subscribe { _ ->
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

    override fun createNewPostViewerFragment(position: Int, sharedView: View) {
        logcat { "createNewPostViewerFragment" }
        val sharedElementExtras = FragmentNavigatorExtras(sharedView to sharedView.transitionName)
        val action = HomeFragmentDirections.actionHomeToPostViewer(position, viewModel.javaClass.simpleName)
        findNavController().navigate(action, sharedElementExtras)
    }

    override fun onDestroyViewRemoveBinding() {
        logcat { "onDestroyViewRemoveBinding" }
        _binding = null
    }
}