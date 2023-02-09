package com.b4kancs.rxredditdemo.ui.follows

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewbinding.ViewBinding
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentFollowsBinding
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragment
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragmentViewModel.UiState
import com.b4kancs.rxredditdemo.ui.shared.PostsVerticalRvAdapter
import com.b4kancs.rxredditdemo.ui.uiutils.CustomLinearLayoutManager
import com.b4kancs.rxredditdemo.ui.uiutils.SnackType
import com.b4kancs.rxredditdemo.ui.uiutils.makeSnackBar
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class FollowsFragment : BaseListingFragment() {

    override val viewModel: FollowsViewModel by sharedViewModel()
    private val args: FollowsFragmentArgs by navArgs()
    private var _binding: FragmentFollowsBinding? = null
    private val binding get() = _binding!!
    private var isJustCreated = false

    override fun setUpBinding(inflater: LayoutInflater, container: ViewGroup?): ViewBinding {
        logcat { "setUpBinding" }
        _binding = FragmentFollowsBinding.inflate(inflater, container, false)
        return binding
    }

    override fun onViewCreatedDoAlso(view: View, savedInstanceState: Bundle?) {
        logcat { "onCreateViewDoExtras" }
        setUpLoadingStateAndErrorHandler(binding.rvFollowsPosts.adapter as PostsVerticalRvAdapter)
        if (isJustCreated) {
            setUpBehaviourDisposables()
            isJustCreated = false
        }
    }

    override fun setUpActionBarAndRelated() {
        logcat { "setUpActionBarAndRelated" }
        // Every time the Fragment is recreated, we need to change the support action bar title.
        viewModel.feedChangedBehaviorSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { (activity as MainActivity).supportActionBar?.title = it.name }
            .addTo(disposables)
    }

    override fun setUpDrawer() {
        logcat { "setUpDrawer" }
        (activity as MainActivity).also {
            it.setUpFollowsDrawer(viewModel)
            it.setUpFollowsDrawerSearchView(viewModel)
        }
    }

    private fun setUpBehaviourDisposables() {
        // This subscription is for refreshing the feed. Does NOT need to immediately execute upon subscription,
        // hence the distinct until changed.
        viewModel.feedChangedBehaviorSubject
            .observeOn(AndroidSchedulers.mainThread())
            .filter { _binding != null && !isJustCreated }
            .distinctUntilChanged()
            .doOnNext { logcat { "followsViewModel.feedChangedBehaviorSubject.onNext" } }
            .subscribe { _ ->
                (binding.rvFollowsPosts.adapter as PostsVerticalRvAdapter).refresh()
            }
            .addTo(disposables)

        // This subscription is to change the Fragment title. It does need to immediately execute with a starting value.
        viewModel.feedChangedBehaviorSubject
            .observeOn(AndroidSchedulers.mainThread())
            .filter { _binding != null }
            .startWithItem(viewModel.getDefaultUserFeed())
            .subscribe { userFeed ->
                (activity as MainActivity).supportActionBar?.title =
                    if (userFeed.status == UserFeed.Status.AGGREGATE)
                        userFeed.name
                    else
                        getString(R.string.string_follows_title_feed_name_template, userFeed.name)
            }
            .addTo(disposables)

        // If we got here from another fragment, we need to recover the navigation argument and go to the specified user's feed.
        val userNameFromNavigation = args.userName
        if (viewModel.currentUserFeed.name != userNameFromNavigation) {
            userNameFromNavigation?.let { userName ->
                viewModel.setUserFeedTo(userName)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(
                        onError = { e ->
                            logcat(LogPriority.ERROR) { "Failed to set user feed! Message = ${e.message}" }
                            makeSnackBar(
                                view = binding.rvFollowsPosts,
                                stringId = R.string.string_common_error_something_went_wrong,
                                type = SnackType.ERROR
                            ).show()
                        }
                    ).addTo(disposables)
            }
        }
    }

    override fun setUpUiStatesBehaviour() {
        logcat { "setUpUiStatesBehaviour" }
        viewModel.uiStateBehaviorSubject
            .observeOn(AndroidSchedulers.mainThread())
            .filter { _binding != null }
            .doOnNext { logcat { "viewModel.uiStateBehaviorSubject.onNext" } }
            .subscribe { uiState ->
                with(binding) {
                    when (uiState) {
                        UiState.NORMAL -> {
                            rvFollowsPosts.isVisible = true
                            linearLayoutFollowsErrorContainer.isVisible = false
                            progressBarFollowsLarge.isVisible = false
                        }
                        UiState.LOADING -> {
                            rvFollowsPosts.isVisible = false
                            linearLayoutFollowsErrorContainer.isVisible = false
                            progressBarFollowsLarge.isVisible = true
                        }
                        UiState.ERROR_404 -> {
                            val errorMessage = getString(R.string.string_follows_http_404_error_message)
                            val errorImageId = R.drawable.im_error_404_resized
                            linearLayoutFollowsErrorContainer.isVisible = true
                            textViewFollowsError.text = errorMessage
                            imageViewFollowsError.setImageResource(errorImageId)
                            progressBarFollowsLarge.isVisible = false
                            rvFollowsPosts.isVisible = false
                        }
                        UiState.ERROR_GENERIC -> {
                            val errorMessage = getString(R.string.string_common_network_error_message)
                            val errorImageId = R.drawable.im_error_network
                            linearLayoutFollowsErrorContainer.isVisible = true
                            textViewFollowsError.text = errorMessage
                            imageViewFollowsError.setImageResource(errorImageId)
                            progressBarFollowsLarge.isVisible = false
                            rvFollowsPosts.isVisible = false
                        }
                        UiState.NO_CONTENT -> {
                            val errorMessage = getString(R.string.string_follows_no_posts_for_user)
                            val errorImageId = R.drawable.im_error_no_content_cat
                            linearLayoutFollowsErrorContainer.isVisible = true
                            textViewFollowsError.text = errorMessage
                            imageViewFollowsError.setImageResource(errorImageId)
                            progressBarFollowsLarge.isVisible = false
                            rvFollowsPosts.isVisible = false
                        }
                        UiState.NO_CONTENT_AGGREGATE -> {
                            val errorMessage = getString(R.string.string_follows_no_posts_aggregate)
                            val errorImageId = R.drawable.im_error_no_content_cat
                            linearLayoutFollowsErrorContainer.isVisible = true
                            textViewFollowsError.text = errorMessage
                            imageViewFollowsError.setImageResource(errorImageId)
                            progressBarFollowsLarge.isVisible = false
                            rvFollowsPosts.isVisible = false
                        }
                    }

                    if (uiState in setOf(
                            UiState.ERROR_GENERIC,
                            UiState.ERROR_404,
                            UiState.NO_CONTENT,
                            UiState.NO_CONTENT_AGGREGATE
                        )) {
                        (activity as MainActivity).expandAppBar()
                        (activity as MainActivity).expandBottomNavBar()
                    }
                }
            }.addTo(disposables)
    }

    override fun setUpRecyclerView() {
        logcat { "setUpRecyclerView" }
        val mainActivity = activity as MainActivity

        mainActivity.animateShowActionBar()
        mainActivity.animateShowBottomNavBar()

        with(binding) {
            rvFollowsPosts.layoutManager = CustomLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL)
                .apply { canScrollHorizontally = false }

            if (rvFollowsPosts.adapter == null) {
                // If positionToGoTo is not null, we need to disable glide transformations and some other stuff for the
                // shared element transition to work properly
                val shouldDisableTransformations =
                    if (positionToGoTo != null) {
                        logcat { "Disabling glide transformations" }
                        true
                    }
                    else false

                rvFollowsPosts.adapter = PostsVerticalRvAdapter(
                    mainActivity,
                    shouldDisableTransformations,
                    viewModel
                )
            }
            val postsFollowsAdapter = rvFollowsPosts.adapter as PostsVerticalRvAdapter

            viewModel.postsCachedPagingObservable
                .subscribe { pagingData ->
                    try {
                        postsFollowsAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                    } catch (e: Exception) {
                        // There might be a weird NullPointerException happening sometimes that doesn't really seem to affect anything
                        logcat(LogPriority.ERROR) { e.stackTrace.toString() }
                    }
                }.addTo(disposables)

            postsFollowsAdapter.readyToBeDrawnSubject
                .delay(200, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .filter {
                    if (positionToGoTo != null) it == positionToGoTo else true
                }
                .take(1)
                .doOnNext { logcat { "readyToBeDrawnSubject.onNext: pos = $it" } }
                .subscribe {
                    // Fine scroll to better position the imageview
                    positionToGoTo?.let { position ->
                        val toScrollY = binding.rvFollowsPosts
                            .findViewHolderForLayoutPosition(position)
                            ?.itemView
                            ?.y
                            ?.minus(20f)
                            ?: 0f
                        logcat { "Scrolling by y = $toScrollY" }
                        binding.rvFollowsPosts.scrollBy(0, toScrollY.toInt())

                        try {   // FindViewHolderForLayoutPosition doesn't always return the correct ViewHolder, and the cast fails...
                            rvFollowsPosts.findViewHolderForLayoutPosition(position)
                                ?.let { viewHolderAtPosition ->
                                    val transitionName =
                                        (viewHolderAtPosition as PostsVerticalRvAdapter.PostViewHolder)
                                            .binding
                                            .postImageView
                                            .transitionName
                                    logcat(LogPriority.INFO) { "Transition name = $transitionName" }
                                }
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN) { e.message.toString() }
                        }
                    }

                    // Getting these timings right is important for the UI to not glitch.
                    Observable.timer(50, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            logcat { "startPostponedEnterTransition()" }
                            startPostponedEnterTransition()
                            logcat { "Disposing of delayedTransitionTriggerDisposable" }
                            delayedTransitionTriggerDisposable.dispose()
                        }.addTo(disposables)

                    postsFollowsAdapter.disableTransformations = false
                }
                .addTo(disposables)

            postsFollowsAdapter.postClickedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat(LogPriority.INFO) { "postClickedSubject.onNext: post = ${it.first}" } }
                .subscribe { (position, view) ->
                    createNewPostViewerFragment(position, view)
                    (rvFollowsPosts.layoutManager as CustomLinearLayoutManager).canScrollVertically = false
                    postsFollowsAdapter.disposables.dispose()
                }.addTo(disposables)

            srlFollows.isEnabled = true
            srlFollows.setOnRefreshListener {
                viewModel.uiStateBehaviorSubject.onNext(UiState.LOADING)
                postsFollowsAdapter.refresh()
                rvFollowsPosts.scrollToPosition(0)
                srlFollows.isRefreshing = false
            }
        }
    }

    // This where we handle the errors coming from the feed and set the uiState.
    private fun setUpLoadingStateAndErrorHandler(adapter: PostsVerticalRvAdapter) {
        logcat { "setUpLoadingStateAndErrorHandler" }
        adapter.loadStateFlow
            .map { loadStates ->
                if (loadStates.refresh is LoadState.Error) {
                    logcat(LogPriority.WARN) { "LoadState.Error detected." }
                    val error = ((loadStates.refresh as LoadState.Error).error)
                    if (error is HttpException && error.code() == 404)
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
                logcat(LogPriority.INFO) { "postFollowsAdapter.loadStateFlow.onEach loadStates.refresh == LoadState.NotLoading" }
                if (adapter.itemCount > 1) {
                    viewModel.uiStateBehaviorSubject.onNext(UiState.NORMAL)

                    positionToGoTo?.let { pos ->
                        logcat(LogPriority.INFO) { "Scrolling to position: $pos" }
                        binding.rvFollowsPosts.scrollToPosition(pos)
                    }
                }
                else {
                    if (viewModel.currentUserFeed.status == UserFeed.Status.AGGREGATE)
                        viewModel.uiStateBehaviorSubject.onNext(UiState.NO_CONTENT_AGGREGATE)
                    else
                        viewModel.uiStateBehaviorSubject.onNext(UiState.NO_CONTENT)
                }
            }.launchIn(MainScope())
    }

    override fun setUpOptionsMenu() {
        logcat { "setUpOptionsMenu" }

        val mergedFeedUpdateObservable = Observable.merge(   // See setUpOptionsMenu() in HomeFragment.kt
            viewModel.getFollowsChangedSubject(),
            viewModel.feedChangedBehaviorSubject
        )
            .subscribeOn(Schedulers.io())
            .doOnNext { logcat { "mergedFeedUpdateObservable.onNext" } }
            .map { viewModel.feedChangedBehaviorSubject.blockingLatest().first() }
            .startWithItem(viewModel.getDefaultUserFeed())
            .replay(1)
            .apply { connect() }

        fun setUpAddToYourFollowsMenuItem(menuItems: Sequence<MenuItem>) {
            mergedFeedUpdateObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { currentFeed ->
                    val addToYourFollowsMenuItem = menuItems
                        .find { it.itemId == R.id.menu_item_toolbar_follows_add }
                    addToYourFollowsMenuItem?.isVisible = currentFeed.status == UserFeed.Status.NOT_IN_DB
                    addToYourFollowsMenuItem?.clicks()
                        ?.doOnNext { logcat { "addToYourFollowsMenuItem.clicks.onNext" } }
                        ?.subscribe {
                            viewModel.addUserFeed(currentFeed)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(
                                    onComplete = {
                                        makeSnackBar(binding.root, R.string.string_common_done).show()
                                    },
                                    onError = { _ ->
                                        makeSnackBar(
                                            binding.root,
                                            null,
                                            "Could not perform action :(",
                                            SnackType.ERROR
                                        ).show()
                                    }
                                ).addTo(disposables)
                        }?.addTo(disposables)
                }.addTo(disposables)
        }

        fun setUpDeleteFromYourFollowsMenuItem(menuItems: Sequence<MenuItem>) {
            mergedFeedUpdateObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { currentFeed ->
                    val deleteFromYourFollowsMenuItem = menuItems
                        .find { it.itemId == R.id.menu_item_toolbar_follows_delete }
                    deleteFromYourFollowsMenuItem?.isVisible =
                        currentFeed.status in setOf(UserFeed.Status.FOLLOWED, UserFeed.Status.SUBSCRIBED)
                    deleteFromYourFollowsMenuItem?.clicks()
                        ?.doOnNext { logcat { "deleteFromYourFollowsMenuItem.clicks.onNext" } }
                        ?.subscribe {
                            viewModel.deleteUserFeed(currentFeed)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(
                                    onComplete = {
                                        makeSnackBar(binding.root, null, "${currentFeed.name} has been deleted!").show()
                                    },
                                    onError = { _ ->
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

        fun subscribeToUserFeedMenuItem(menuItems: Sequence<MenuItem>) {
            mergedFeedUpdateObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { currentFeed ->
                    val subscribeToFeedMenuItem = menuItems
                        .find { it.itemId == R.id.menu_item_toolbar_follows_subscribe }
                    subscribeToFeedMenuItem?.isVisible =
                        currentFeed.status in setOf(UserFeed.Status.NOT_IN_DB, UserFeed.Status.FOLLOWED)
                    subscribeToFeedMenuItem?.clicks()
                        ?.doOnNext { logcat { "subscribeToFeedMenuItem.clicks.onNext" } }
                        ?.subscribe {
                            viewModel.subscribeToFeed(currentFeed)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(
                                    onComplete = {
                                        makeSnackBar(binding.root, null, "${currentFeed.name} has been deleted!").show()
                                    },
                                    onError = { _ ->
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

        fun unsubscribeFromUserFeedMenuItem(menuItems: Sequence<MenuItem>) {
            mergedFeedUpdateObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { currentFeed ->
                    val unsubscribeFromFeedMenuItem = menuItems
                        .find { it.itemId == R.id.menu_item_toolbar_follows_unsubscribe }
                    unsubscribeFromFeedMenuItem?.isVisible = currentFeed.status == UserFeed.Status.SUBSCRIBED
                    unsubscribeFromFeedMenuItem?.clicks()
                        ?.doOnNext { logcat { "unsubscribeFromFeedMenuItem.clicks.onNext" } }
                        ?.subscribe {
                            viewModel.unsubscribeFromFeed(currentFeed)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(
                                    onComplete = {
                                        makeSnackBar(binding.root, null, "${currentFeed.name} has been deleted!").show()
                                    },
                                    onError = { _ ->
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

        Observable.interval(250, TimeUnit.MILLISECONDS)
            .filter { (activity as MainActivity).menu != null }
            .take(1)    // Try until the menu is ready, then do once.
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat { "menu is ready .onNext" } }
            .subscribe { _ ->
                val menu = (activity as MainActivity).menu!!
                val menuItems = menu.children
                for (item in menuItems) {
                    when (item.groupId) {
                        R.id.menu_group_toolbar_follows_actions -> item.isVisible = true
                        R.id.menu_group_toolbar_app_actions -> item.isVisible = true
                        else -> item.isVisible = false
                    }
                }
                setUpAddToYourFollowsMenuItem(menuItems)
                setUpDeleteFromYourFollowsMenuItem(menuItems)
                subscribeToUserFeedMenuItem(menuItems)
                unsubscribeFromUserFeedMenuItem(menuItems)
            }
            .addTo(disposables)
    }

    override fun createNewPostViewerFragment(position: Int, sharedView: View) {
        logcat { "goToNewPostViewerFragment" }
        val sharedElementExtras = FragmentNavigatorExtras(sharedView to sharedView.transitionName)
        val action = FollowsFragmentDirections.actionFollowsToPostViewer(position, viewModel::class.simpleName!!)
        findNavController().navigate(action, sharedElementExtras)
    }

    override fun onDestroyViewRemoveBinding() {
        logcat { "onDestroyViewRemoveBinding" }
        _binding = null
    }
}