package com.b4kancs.rxredditdemo.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.viewbinding.ViewBinding
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentFavoritesBinding
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragment
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragmentViewModel.UiState
import com.b4kancs.rxredditdemo.ui.shared.PostsVerticalRvAdapter
import com.b4kancs.rxredditdemo.ui.uiutils.SnackType
import com.b4kancs.rxredditdemo.ui.uiutils.makeConfirmationDialog
import com.b4kancs.rxredditdemo.ui.uiutils.makeSnackBar
import com.b4kancs.rxredditdemo.utils.forwardLatestOnceTrue
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

        viewModel.getFavoritePostsBehaviorSubject()
            .doOnNext { logcat { "favoritesViewModel.favoritePostsBehaviorSubject.onNext" } }
            .forwardLatestOnceTrue { _binding != null }
            .observeOn(AndroidSchedulers.mainThread())
            .distinctUntilChanged()
            .subscribe { postDbEntries ->
                // TODO check this over
                val isEmpty = postDbEntries.isEmpty()
                (binding.rvFavoritesPosts.adapter as PostsVerticalRvAdapter).let { adapter ->
                    if (adapter.itemCount > 1 && isEmpty) adapter.refresh()
                    if (adapter.itemCount <= 1 && !isEmpty) adapter.refresh()
                }
            }.addTo(disposables)
    }

    override fun setUpActionBarAndRelated() {
        (activity as MainActivity).also {
            it.animateShowActionBar()
            it.animateShowBottomNavBar()
        }
    }

    override fun setUpDrawer() {
        (activity as MainActivity).lockDrawerClosed()
    }

    override fun setUpUiStatesBehaviour() {
        logcat { "setUpUiStatesBehaviour" }
        viewModel.uiStateBehaviorSubject
            .forwardLatestOnceTrue { _binding != null }
            .observeOn(AndroidSchedulers.mainThread())
            .distinctUntilChanged()
            .doOnNext { logcat { "viewModel.uiStateBehaviorSubject.onNext" } }
            .subscribe { uiState ->
                with(binding) {
                    when (uiState) {
                        UiState.NORMAL -> {
                            rvFavoritesPosts.isVisible = true
                            linearLayoutFavoritesErrorContainer.isVisible = false
                            progressBarFavoritesLarge.isVisible = false
                            logcat(LogPriority.INFO) { "Scrolling to position: ${viewModel.rvPosition}" }
                            binding.rvFavoritesPosts.scrollToPosition(viewModel.rvPosition)
                        }
                        UiState.LOADING -> {
                            rvFavoritesPosts.isVisible = false
                            linearLayoutFavoritesErrorContainer.isVisible = false
                            progressBarFavoritesLarge.isVisible = true
                        }
                        UiState.ERROR_GENERIC -> {
                            val errorMessage = getString(R.string.common_error_message_network)
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
        setUpBaseRecyclerView(binding.rvFavoritesPosts, viewModel)
    }

    override fun setUpLoadingStateAndErrorHandler() {
        logcat { "setUpLoadingStateAndErrorHandler" }
        val adapter = binding.rvFavoritesPosts.adapter as PostsVerticalRvAdapter
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
                    if (viewModel.uiStateBehaviorSubject.value != UiState.NORMAL) {
                        viewModel.uiStateBehaviorSubject.onNext(UiState.NORMAL)
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
                                                R.string.favorites_snack_deleted_all
                                            ).show()
                                        },
                                        onError = {
                                            makeSnackBar(
                                                view = binding.root,
                                                stringId = R.string.common_message_could_not_perform,
                                                type = SnackType.ERROR
                                            ).show()
                                        }
                                    ).addTo(transientDisposables)
                            }
                                .show()
                        }?.addTo(transientDisposables)
                }.addTo(transientDisposables)
        }

        val mainActivity = (activity as MainActivity)
        mainActivity.invalidateOptionsMenu()

        // If we don't wait an adequate amount, we might not get a reference to the options menu.
        Observable.interval(100, TimeUnit.MILLISECONDS)
            .filter { mainActivity.menu != null && !enterAnimationInProgress }     // Wait until the menu is ready.
            .take(1)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat { "menu is ready .onNext" } }
            .subscribe {
                val menu = mainActivity.menu!!
                val menuItems = menu.children
                for (item in menuItems) {
                    when (item.groupId) {
                        R.id.menu_group_toolbar_favorites_actions -> item.isVisible = true
                        R.id.menu_group_toolbar_app_actions -> item.isVisible = true
                        else -> item.isVisible = false
                    }
                }
                setUpClearAllFavoritesMenuItem(menuItems)
                setUpGoToSettingsMenuItem(menuItems)
            }
            .addTo(transientDisposables)
    }

    override fun onPauseSavePosition() {
        logcat { "onPauseSavePosition" }
        savePositionFromRv(binding.rvFavoritesPosts)
    }

    override fun onDestroyViewRemoveBinding() {
        logcat { "onDestroyViewRemoveBinding" }
        _binding = null
    }
}