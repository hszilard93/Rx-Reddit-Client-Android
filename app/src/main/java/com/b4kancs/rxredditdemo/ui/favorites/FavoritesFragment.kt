package com.b4kancs.rxredditdemo.ui.favorites

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
import com.b4kancs.rxredditdemo.databinding.FragmentFavoritesBinding
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerFragment
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragment
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragmentViewModel.UiState
import com.b4kancs.rxredditdemo.ui.shared.PostsVerticalRvAdapter
import com.b4kancs.rxredditdemo.ui.uiutils.CustomLinearLayoutManager
import com.b4kancs.rxredditdemo.ui.uiutils.SnackType
import com.b4kancs.rxredditdemo.ui.uiutils.makeConfirmationDialog
import com.b4kancs.rxredditdemo.ui.uiutils.makeSnackBar
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import logcat.LogPriority
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.util.concurrent.TimeUnit

class FavoritesFragment : BaseListingFragment() {

    override val viewModel: FavoritesViewModel by sharedViewModel()
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    override fun setUpBinding(inflater: LayoutInflater, container: ViewGroup?): ViewBinding {
        logcat { "setUpBinding" }
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding
    }


    override fun onViewCreatedDoAlso(view: View, savedInstanceState: Bundle?) {
        logcat { "onCreateViewDoExtras" }

        setUpLoadingStateAndErrorHandler(binding.rvFavoritesPosts.adapter as PostsVerticalRvAdapter)

        viewModel.getFavoritePostsBehaviorSubject()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat { "favoritesViewModel.favoritePostsBehaviorSubject.onNext" } }
            .filter { _binding != null }
            .distinctUntilChanged()
            .subscribe { postDbEntries ->
                val isEmpty = postDbEntries.isEmpty()
                (binding.rvFavoritesPosts.adapter as PostsVerticalRvAdapter).let { adapter ->
                    if (adapter.itemCount > 1 && isEmpty) adapter.refresh()
                    if (adapter.itemCount <= 1 && !isEmpty) adapter.refresh()
                }
            }.addTo(disposables)
    }

    override fun setUpActionBarAndRelated() {
        //
    }

    override fun setUpDrawer() {
        (activity as MainActivity).lockDrawerClosed()
    }

    override fun setUpUiStatesBehaviour() {
        logcat { "setUpUiStatesBehaviour" }
        viewModel.uiStateBehaviorSubject
            .observeOn(AndroidSchedulers.mainThread())
            .filter { _binding != null }
            .distinctUntilChanged()
            .doOnNext { logcat { "viewModel.uiStateBehaviorSubject.onNext" } }
            .subscribe { uiState ->
                with(binding) {
                    when (uiState) {
                        UiState.NORMAL -> {
                            rvFavoritesPosts.isVisible = true
                            linearLayoutFavoritesErrorContainer.isVisible = false
                            progressBarFavoritesLarge.isVisible = false
                        }
                        UiState.LOADING -> {
                            rvFavoritesPosts.isVisible = false
                            linearLayoutFavoritesErrorContainer.isVisible = false
                            progressBarFavoritesLarge.isVisible = true
                        }
                        UiState.ERROR_GENERIC -> {
                            val errorMessage = getString(R.string.string_common_network_error_message)
                            val errorImageId = R.drawable.im_error_network
                            textViewFavoritesError.text = errorMessage
                            imageViewFavoritesError.setImageResource(errorImageId)
                            rvFavoritesPosts.isVisible = false
                            linearLayoutFavoritesErrorContainer.isVisible = true
                            progressBarFavoritesLarge.isVisible = false
                        }
                        UiState.NO_CONTENT -> {
                            val errorMessage = getString(R.string.favorites_no_media)
                            val errorImageId = R.drawable.im_error_no_content_bird
                            textViewFavoritesError.text = errorMessage
                            imageViewFavoritesError.setImageResource(errorImageId)
                            rvFavoritesPosts.isVisible = false
                            linearLayoutFavoritesErrorContainer.isVisible = true
                            progressBarFavoritesLarge.isVisible = false
                        }
                        else -> {
                            logcat(LogPriority.ERROR) { "uiState error: state $uiState is illegal in FavoritesFragment." }
                            throw IllegalStateException("State $uiState is illegal in FavoritesFragment.")
                        }
                    }
                }
            }
            .addTo(disposables)
    }

    override fun setUpRecyclerView() {
        logcat { "setUpRecyclerView" }
        val mainActivity = (activity as MainActivity).also {
            it.animateShowActionBar()
            it.animateShowBottomNavBar()
        }

        with(binding) {
            rvFavoritesPosts.layoutManager = CustomLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL)
                .apply { canScrollHorizontally = false }

            if (rvFavoritesPosts.adapter == null) {
                // If positionToGoTo is not null, we need to disable glide transformations and some other stuff for the
                // shared element transition to work properly.
                val shouldDisableTransformations =
                    if (positionToGoTo != null) {
                        logcat { "Disabling glide transformations" }
                        true
                    }
                    else false

                rvFavoritesPosts.adapter = PostsVerticalRvAdapter(
                    mainActivity,
                    shouldDisableTransformations,
                    viewModel
                )
            }
            val postsFavoritesAdapter = rvFavoritesPosts.adapter as PostsVerticalRvAdapter

            viewModel.postsCachedPagingObservable
                .subscribe { pagingData ->
                    try {
                        postsFavoritesAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                    } catch (e: Exception) {
                        // There might be a weird NullPointerException happening sometimes that doesn't really seem to affect anything
                        logcat(LogPriority.ERROR) { e.stackTrace.toString() }
                    }
                }
                .addTo(disposables)

            postsFavoritesAdapter.readyToBeDrawnSubject
                .delay(200, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .filter {
                    if (positionToGoTo != null) it == positionToGoTo else true
                }      // When the target of the SharedElementTransition or any element is ready
                .take(1)
                .doOnNext { logcat { "readyToBeDrawnSubject.onNext: pos = $it" } }
                .subscribe {
                    // Fine scroll to better position the imageview
                    positionToGoTo?.let { position ->
                        val toScrollY = binding.rvFavoritesPosts
                            .findViewHolderForLayoutPosition(position)
                            ?.itemView
                            ?.y
                            ?.minus(20f)
                            ?: 0f
                        logcat { "Scrolling by y = $toScrollY" }
                        binding.rvFavoritesPosts.scrollBy(0, toScrollY.toInt())

                        try {   // FindViewHolderForLayoutPosition doesn't always return the correct ViewHolder, and the cast fails...
                            rvFavoritesPosts.findViewHolderForLayoutPosition(position)
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

                    postsFavoritesAdapter.disableTransformations = false
                }
                .addTo(disposables)

            postsFavoritesAdapter.postClickedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat(LogPriority.INFO) { "postClickedSubject.onNext: post = ${it.first}" } }
                .subscribe { (position, view) ->
                    createNewPostViewerFragment(position, view)
                    (rvFavoritesPosts.layoutManager as CustomLinearLayoutManager).canScrollVertically = false
                    postsFavoritesAdapter.disposables.dispose()
                }.addTo(disposables)

            postsFavoritesAdapter.addLoadStateListener { combinedLoadStates ->
                progressBarFavoritesLarge.isVisible = combinedLoadStates.refresh is LoadState.Loading
            }

            srlFavorites.isEnabled = true
            srlFavorites.setOnRefreshListener {
                viewModel.uiStateBehaviorSubject.onNext(UiState.LOADING)
                postsFavoritesAdapter.refresh()
                rvFavoritesPosts.scrollToPosition(0)
                srlFavorites.isRefreshing = false
            }
        }
    }

    private fun setUpLoadingStateAndErrorHandler(adapter: PostsVerticalRvAdapter) {
        logcat { "setUpLoadingStateAndErrorHandler" }
        adapter.loadStateFlow
            .distinctUntilChanged()
            .map { loadStates ->
                if (loadStates.refresh is LoadState.Error) {
                    logcat(LogPriority.WARN) { "LoadState.Error detected." }
                    viewModel.uiStateBehaviorSubject.onNext(UiState.ERROR_GENERIC)
                }
                else if (loadStates.refresh is LoadState.Loading) {
                    viewModel.uiStateBehaviorSubject.onNext(UiState.LOADING)
                }
                loadStates
            }
            .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
            .onEach {
                logcat(LogPriority.VERBOSE) { "postsFavoritesAdapter.loadStateFlow.onEach loadStates.refresh == LoadState.NotLoading" }
                if (adapter.itemCount > 1) {
                    viewModel.uiStateBehaviorSubject.onNext(UiState.NORMAL)

                    positionToGoTo?.let { pos ->
                        logcat(LogPriority.INFO) { "Scrolling to position: $pos" }
                        binding.rvFavoritesPosts.scrollToPosition(pos)
                        positionToGoTo = null
                    }
                }
                else {
                    viewModel.uiStateBehaviorSubject.onNext(UiState.NO_CONTENT)
                }
            }
            .launchIn(MainScope())
    }

    override fun setUpOptionsMenu() {
        logcat { "setUpOptionsMenu" }

        fun setUpClearAllFavoritesMenuItem(menuItems: Sequence<MenuItem>) {
            logcat(LogPriority.VERBOSE) { "setUpOptionsMenu:setUpClearAllFavoritesMenuItem" }
            val clearAllFavoritesMenuItem = menuItems
                .find { it.itemId == R.id.menu_item_toolbar_favorites_delete_all }

            viewModel.getFavoritePostsBehaviorSubject()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { postDbEntries ->
                    clearAllFavoritesMenuItem?.isVisible = postDbEntries.isNotEmpty()
                    clearAllFavoritesMenuItem?.clicks()
                        ?.doOnNext { logcat(LogPriority.INFO) { "clearAllFavoritesMenuItem.clicks.onNext" } }
                        ?.subscribe {
                            makeConfirmationDialog(
                                "Are you sure?",
                                "This will delete all your favorited posts.",
                                requireActivity()
                            ) {
                                viewModel.deleteAllFavoritePosts()
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribeBy(
                                        onComplete = {
                                            makeSnackBar(
                                                binding.root,
                                                null,
                                                "Favorites deleted!"
                                            ).show()
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
                                .show()
                        }?.addTo(disposables)
                }.addTo(disposables)
        }

        // If we don't wait an adequate amount, we might get a wrong reference to the options menu, or no reference at all.
        Observable.interval(250, TimeUnit.MILLISECONDS)
            .filter { (activity as MainActivity).menu != null }     // Wait until the menu is ready.
            .take(1)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat { "menu is ready .onNext" } }
            .subscribe {
                val menu = (activity as MainActivity).menu!!
                val menuItems = menu.children
                for (item in menuItems) {
                    when (item.groupId) {
                        R.id.menu_group_toolbar_favorites_actions -> item.isVisible = true
                        R.id.menu_group_toolbar_app_actions -> item.isVisible = true
                        else -> item.isVisible = false
                    }
                }
                setUpClearAllFavoritesMenuItem(menuItems)
            }
            .addTo(disposables)
    }

    override fun createNewPostViewerFragment(position: Int, sharedView: View) {
        logcat { "goToNewPostViewerFragment" }
        val sharedElementExtras = FragmentNavigatorExtras(sharedView to sharedView.transitionName)
        val action = FavoritesFragmentDirections.actionFavoritesToPostViewer(position, viewModel::class.simpleName!!)
        findNavController().navigate(action, sharedElementExtras)
    }

    override fun onDestroyViewRemoveBinding() {
        logcat { "onDestroyViewRemoveBinding" }
        _binding = null
    }

    override fun onDestroyViewDoAlso() {
        logcat { "onDestroyViewDoAlso" }
        (activity as MainActivity).unlockDrawer()
    }
}