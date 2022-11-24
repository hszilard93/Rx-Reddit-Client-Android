package com.b4kancs.rxredditdemo.ui.favorites

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
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentFavoritesBinding
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerFragment
import com.b4kancs.rxredditdemo.ui.shared.PostsVerticalRvAdapter
import com.b4kancs.rxredditdemo.ui.uiutils.CustomLinearLayoutManager
import com.b4kancs.rxredditdemo.ui.uiutils.SnackType
import com.b4kancs.rxredditdemo.ui.uiutils.makeConfirmationDialog
import com.b4kancs.rxredditdemo.ui.uiutils.makeSnackBar
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.util.concurrent.TimeUnit

class FavoritesFragment : Fragment() {

    private val favoritesViewModel: FavoritesViewModel by sharedViewModel()
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val disposables = CompositeDisposable()
    private var positionToGoTo: Int? = null
    private var isBeingReconstructed = false
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
            logcat(LogPriority.INFO) { "Recovered position from PostViewerFragment. positionToGoTo = $positionToGoTo" }
        }

        isBeingReconstructed = savedInstanceState != null
        logcat { "isBeingReconstructed = $isBeingReconstructed" }
        // If not returning from PVF and not recovering from rotation etc. positionToGoTo remains null, else 0.
        // When positionToGoTo is null, we should let the RecyclerView recover its previous position.
        if (positionToGoTo == null && !isBeingReconstructed) positionToGoTo = 0

        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)

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

        favoritesViewModel.isFavoritePostsNotEmptyBehaviorSubject
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat { "favoritesViewModel.favoritePostsBehaviorSubject.onNext" } }
            .subscribe {
                (binding.rvFavoritesPosts.adapter as PostsVerticalRvAdapter).refresh()
            }
            .addTo(disposables)

    }

    override fun onResume() {
        logcat { "onResume" }
        super.onResume()
        (activity as MainActivity).apply {
            lockDrawerClosed()
        }
        if (positionToGoTo == null) (binding.rvFavoritesPosts.adapter as PostsVerticalRvAdapter).refresh()
        setUpOptionsMenu()
    }

    private fun goToNewPostViewerFragment(position: Int, sharedView: View) {
        logcat { "createNewPostViewerFragment" }
        val sharedElementExtras = FragmentNavigatorExtras(sharedView to sharedView.transitionName)
        val action = FavoritesFragmentDirections.actionFavoritesToPostViewer(position, favoritesViewModel::class.simpleName!!)
        findNavController().navigate(action, sharedElementExtras)
    }

    private fun setUpRecyclerView() {
        logcat { "setUpPostsRecyclerView" }
        val mainActivity = activity as MainActivity

        with(binding) {
            rvFavoritesPosts.layoutManager = CustomLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL)
                .apply { canScrollHorizontally = false }

            if (rvFavoritesPosts.adapter == null) {
                // If positionToNavigateTo is not null, we need to disable glide transformations and some other stuff for the
                // shared element transition to work properly
                val shouldDisableTransformations = if (positionToGoTo != null) {
                    logcat { "Disabling glide transformations" }
                    true
                } else false
                rvFavoritesPosts.adapter = PostsVerticalRvAdapter(
                    mainActivity,
                    shouldDisableTransformations,
                    null
                )
            }
            val postsFavoritesAdapter = rvFavoritesPosts.adapter as PostsVerticalRvAdapter

            favoritesViewModel.cachedPagingObservable
                .subscribe { pagingData ->
                    try {
                        postsFavoritesAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                    } catch (e: Exception) {
                        // There might be a weird NullPointerException happening sometimes that doesn't really seem to affect anything
                        logcat(LogPriority.ERROR) { e.stackTrace.toString() }
                    }
                }.addTo(disposables)

            postsFavoritesAdapter.loadStateFlow
                .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                .distinctUntilChanged()
                .onEach {
                    logcat(LogPriority.VERBOSE) { "postsFavoritesAdapter.loadStateFlow.onEach loadStates.refresh == LoadState.NotLoading" }
                }
                .onEach {
                    // If the subreddit feed contains no displayable posts (images etc.), display a textview
                    if (postsFavoritesAdapter.itemCount == 1) {   // The 1 is because of the always-present bottom loading indicator
                        binding.textViewFavoritesNoMedia.isVisible = true
                    } else {
                        binding.textViewFavoritesNoMedia.isVisible = false
                        positionToGoTo?.let { pos ->
                            logcat(LogPriority.INFO) { "Scrolling to position: $pos" }
                            rvFavoritesPosts.scrollToPosition(pos)
                        }
                    }
                }
                .launchIn(MainScope())

            postsFavoritesAdapter.readyToBeDrawnSubject
                .delay(200, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .filter {
                    if (positionToGoTo != null) it == positionToGoTo else true
                }      // When the target of the SharedElementTransition or any element is ready
                .take(1)
                .doOnNext { logcat(LogPriority.INFO) { "readyToBeDrawnSubject.onNext: pos = $it" } }
                .subscribe {
                    // Fine scroll to better position the imageview
                    positionToGoTo?.let {
                        val toScrollY = binding.rvFavoritesPosts
                            .findViewHolderForLayoutPosition(it)
                            ?.itemView
                            ?.y
                            ?.minus(20f)
                            ?: 0f
                        logcat { "Scrolling by y = $toScrollY" }
                        binding.rvFavoritesPosts.scrollBy(0, toScrollY.toInt())
                    }

                    // We put these reveals here so that they will be synced with the SharedElementTransition.
                    mainActivity.animateShowActionBar()
                    mainActivity.animateShowBottomNavBar()

                    positionToGoTo?.let {
                        try {   // It turns out, findViewHolderForLayoutPosition doesn't always return the correct ViewHolder, and the cast fails...
                            rvFavoritesPosts.findViewHolderForLayoutPosition(it)
                                ?.let { viewHolderAtPosition ->
                                    val transitionName =
                                        (viewHolderAtPosition as PostsVerticalRvAdapter.PostViewHolder)
                                            .binding
                                            .postImageView
                                            .transitionName
                                    logcat(LogPriority.INFO) { "Transition name = $transitionName" }
                                    logcat { "startPostponedEnterTransition()" }
                                }
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN) { e.message.toString() }
                        }

                        // Getting these timings right is important for the UI to not glitch.
                        Observable.timer(50, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                startPostponedEnterTransition()
                                logcat { "Disposing of delayedTransitionTriggerDisposable" }
                                delayedTransitionTriggerDisposable.dispose()
                            }.addTo(disposables)

                        postsFavoritesAdapter.disableTransformations = false
                    }
                }.addTo(disposables)

            postsFavoritesAdapter.postClickedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat(LogPriority.INFO) { "postClickedSubject.onNext: post = ${it.first}" } }
                .subscribe { (position, view) ->
                    goToNewPostViewerFragment(position, view)
                    (rvFavoritesPosts.layoutManager as CustomLinearLayoutManager).canScrollVertically = false
                    postsFavoritesAdapter.disposables.dispose()
                }.addTo(disposables)

            postsFavoritesAdapter.addLoadStateListener { combinedLoadStates ->
                progressBarFavoritesLarge.isVisible = combinedLoadStates.refresh is LoadState.Loading
            }

            srlFavorites.isEnabled = false
        }
    }

    private fun setUpOptionsMenu() {
        logcat { "setUpOptionsMenu" }

        fun setUpClearAllFavoritesMenuItem(menuItems: Sequence<MenuItem>) {
            logcat(LogPriority.VERBOSE) { "setUpOptionsMenu:setUpClearAllFavoritesMenuItem" }
            val clearAllFavoritesMenuItem = menuItems
                .find { it.itemId == R.id.menu_item_toolbar_favorites_delete_all }

            favoritesViewModel.isFavoritePostsNotEmptyBehaviorSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { isNotEmpty ->
                    clearAllFavoritesMenuItem?.isVisible = isNotEmpty
                    clearAllFavoritesMenuItem?.clicks()
                        ?.doOnNext { logcat(LogPriority.INFO) { "clearAllFavoritesMenuItem.clicks.onNext" } }
                        ?.subscribe {
                            makeConfirmationDialog(
                                "Are you sure?",
                                "This will delete all your favorited posts.",
                                requireActivity()
                            ) {
                                favoritesViewModel.deleteAllFavoritePosts()
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
    }

    override fun onDestroy() {
        logcat { "onDestroy" }
        super.onDestroy()
        (activity as MainActivity).unlockDrawer()
        disposables.dispose()
    }

    override fun onDestroyView() {
        logcat { "onDestroyView" }
        super.onDestroyView()
        _binding = null
    }
}