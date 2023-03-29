package com.b4kancs.rxredditdemo.ui.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.transition.TransitionInflater
import androidx.viewbinding.ViewBinding
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.ui.favorites.FavoritesFragment
import com.b4kancs.rxredditdemo.ui.favorites.FavoritesFragmentDirections
import com.b4kancs.rxredditdemo.ui.follows.FollowsFragment
import com.b4kancs.rxredditdemo.ui.follows.FollowsFragmentDirections
import com.b4kancs.rxredditdemo.ui.home.HomeFragment
import com.b4kancs.rxredditdemo.ui.home.HomeFragmentDirections
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragmentViewModel.UiState
import com.b4kancs.rxredditdemo.ui.uiutils.CustomLinearLayoutManager
import com.b4kancs.rxredditdemo.utils.executeTimedDisposable
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import logcat.LogPriority
import logcat.logcat
import java.util.concurrent.TimeUnit

/*
 * A base fragment for Home, Favorites and Follows.
 * Hopefully it will help cut down on bugs and will make changing shared behaviour easier
 * while making it easier to identify specific behaviour.
*/
abstract class BaseListingFragment : Fragment() {

    companion object {
        const val AVOID_RV_FLICKER_DELAY_IN_MILLIS = 200L
    }

    abstract val viewModel: BaseListingFragmentViewModel
    protected val disposables = CompositeDisposable()
    protected val transientDisposables = CompositeDisposable()
    protected var enterAnimationInProgress = false
    protected lateinit var delayedTransitionTriggerDisposable: Disposable   // Used to trigger the shared element transition if it doesn't get triggered in time.


    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        logcat { "onCreateAnimation" }
        if (enter) {
            try {
                val anim = AnimationUtils.loadAnimation(activity, nextAnim)
                anim.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        enterAnimationInProgress = true
                    }

                    override fun onAnimationEnd(animation: Animation?) {
                        enterAnimationInProgress = false
                    }

                    override fun onAnimationRepeat(animation: Animation?) {
                        enterAnimationInProgress = false
                    }
                })
                return anim
            } catch (e: java.lang.Exception) {
                logcat(LogPriority.WARN) { "Enter animation exception: message = ${e.message}" }
            }
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        logcat {
            "onCreateView\n  Current nav backstack: ${
                findNavController().backQueue
                    .map { it.destination }
                    .joinToString("\n ", "\n ")
            }"
        }

        val genericBinding = setUpBinding(inflater, container)

        onCreateViewDoAlso(inflater, container, savedInstanceState)

        return genericBinding.root
    }

    abstract fun setUpBinding(inflater: LayoutInflater, container: ViewGroup?): ViewBinding

    open fun onCreateViewDoAlso(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) {}


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logcat { "onViewCreated" }

        setUpActionBarAndRelated()
        setUpSharedElementTransition()
        setUpRecyclerView()
        setUpUiStatesBehaviour()
        setUpLoadingStateAndErrorHandler()
        onViewCreatedDoAlso(view, savedInstanceState)
    }

    open fun onViewCreatedDoAlso(view: View, savedInstanceState: Bundle?) {}

    abstract fun setUpActionBarAndRelated()

    open fun setUpSharedElementTransition() {
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

    abstract fun setUpRecyclerView()

    abstract fun setUpUiStatesBehaviour()

    abstract fun setUpLoadingStateAndErrorHandler()


    override fun onStart() {
        logcat { "onStart" }
        super.onStart()

        onStartDoAlso()
    }

    open fun onStartDoAlso() {}


    override fun onResume() {
        logcat { "onResume" }
        super.onResume()
        setUpDrawer()
        setUpOptionsMenu()
    }

    abstract fun setUpDrawer()

    abstract fun setUpOptionsMenu()

    open fun setUpGoToSettingsMenuItem(menuItems: Sequence<MenuItem>) {
        logcat { "setUpGoToSettingsMenuItem" }
        menuItems
            .find { it.itemId == R.id.menu_item_toolbar_settings }
            ?.clicks()
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.doOnNext { logcat(LogPriority.INFO) { "Settings menu item clicked." } }
            ?.subscribe {
                goToNewSettingsFragment()
            }
            ?.addTo(disposables)
    }


    override fun onPause() {
        logcat { "onPause" }
        onPauseSavePosition()
        onPauseDoAlso()
        transientDisposables.clear()
        super.onPause()
    }

    abstract fun onPauseSavePosition()

    protected fun savePositionFromRv(recyclerView: RecyclerView) {
        logcat { "onSaveInstanceStateSavePositionForRv" }
        (recyclerView.layoutManager as CustomLinearLayoutManager).let {
            // currentPosition is the position of the first completely visible item,
            // or if no item is completely visible, the first visible item.
            var currentPosition = it.findFirstCompletelyVisibleItemPosition()
            if (currentPosition == RecyclerView.NO_POSITION)
                currentPosition = it.findFirstVisibleItemPosition()
            // Save the position in the ViewModel.
            if (currentPosition != RecyclerView.NO_POSITION)
                viewModel.saveRvPosition(currentPosition)
        }
    }

    open fun onPauseDoAlso() {}


    override fun onDestroyView() {
        logcat { "onDestroyView" }
        super.onDestroyView()
        onDestroyViewRemoveBinding()
        onDestroyViewDoAlso()
    }

    abstract fun onDestroyViewRemoveBinding()

    open fun onDestroyViewDoAlso() {}


    override fun onDestroy() {
        logcat { "onDestroy" }
        super.onDestroy()
        disposables.dispose()
        onDestroyDoAlso()
    }

    open fun onDestroyDoAlso() {}

    open fun goToNewPostViewerFragment(position: Int, sharedView: View) {
        logcat { "goToNewPostViewerFragment" }
        val sharedElementExtras = FragmentNavigatorExtras(sharedView to sharedView.transitionName)
        val action = when (this) {
            is HomeFragment -> HomeFragmentDirections.actionHomeToPostViewer(position, viewModel::class.simpleName)
            is FavoritesFragment -> FavoritesFragmentDirections.actionFavoritesToPostViewer(position, viewModel::class.simpleName)
            is FollowsFragment -> FollowsFragmentDirections.actionFollowsToPostViewer(position, viewModel::class.simpleName)
            else -> {
                throw IllegalStateException("WTF error: can't go to Settings from here: $this.")
            }
        }
        findNavController().navigate(action, sharedElementExtras)
    }

    open fun goToNewSettingsFragment() {
        logcat { "goToNewSettingsFragment" }
        val action = when (this) {
            is HomeFragment -> HomeFragmentDirections.actionHomeToSettings()
            is FavoritesFragment -> FavoritesFragmentDirections.actionFavoritesToSettings()
            is FollowsFragment -> FollowsFragmentDirections.actionFollowsToSettings()
            else -> {
                throw IllegalStateException("WTF error: can't go to Settings from here: $this.")
            }
        }
        findNavController().navigate(action)
    }

    protected fun setUpBaseRecyclerView(recyclerView: RecyclerView, viewModel: BaseListingFragmentViewModel) {
        logcat { "setUpBaseRecyclerView: owner = $" }

        val mainActivity = activity as MainActivity
        recyclerView.layoutManager = CustomLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL)
            .apply { canScrollHorizontally = false }

        if (recyclerView.adapter == null) {
//            // If positionToGoTo is not null, we need to disable glide transformations and some other stuff for the
//            // shared element transition to work properly
//            val shouldDisableTransformations = if (viewModel.rvPosition != 0) {
//                logcat { "Disabling glide transformations" }
//                true
//            }
//            else
//                false

            recyclerView.adapter = PostsVerticalRvAdapter(
                mainActivity,
//                shouldDisableTransformations,
                viewModel
            )
        }
        val postsAdapter = recyclerView.adapter as PostsVerticalRvAdapter

        viewModel.postsCachedPagingObservable
            .subscribe { pagingData ->
                try {
                    postsAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
//                    positionToGoTo?.let { recyclerView.scrollToPosition(it) }
                } catch (e: Exception) {
                    // There might be a weird NullPointerException happening sometimes that doesn't really seem to affect anything
                    logcat(LogPriority.ERROR) { e.stackTrace.toString() }
                }
            }.addTo(disposables)

        postsAdapter.postClickedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat(LogPriority.INFO) { "postClickedSubject.onNext: post = ${it.first}" } }
            .subscribe { (position, view) ->
                goToNewPostViewerFragment(position, view)
                (recyclerView.layoutManager as CustomLinearLayoutManager).canScrollVertically = false
                // By disposing of the subscriptions here, we stop the user from accidentally clicking on a post
                // while the transition takes place.
                postsAdapter.disposables.dispose()
            }
            .addTo(disposables)

        // This is only useful when returning from a PostViewerFragment, to trigger the delayed transition
        postsAdapter.readyForTransitionSubject
            .observeOn(AndroidSchedulers.mainThread())
            .filter { posOfTheReadyImage -> posOfTheReadyImage == viewModel.rvStoredPosition }
//            .take(1)
            .doOnNext { logcat(LogPriority.INFO) { "readyToBeDrawnSubject.onNext: pos = $it" } }
            .subscribe { pos ->
                // Fine scroll to better position the imageview
                val toScrollY = recyclerView
                    .findViewHolderForLayoutPosition(pos)
                    ?.itemView
                    ?.y
                    ?.minus(20f)
                    ?: 0f
                logcat { "Scrolling by y = $toScrollY" }
                recyclerView.scrollBy(0, toScrollY.toInt())

                // This delay helps avoid flickering.
                executeTimedDisposable(50) {
                    recyclerView.findViewHolderForLayoutPosition(pos)
                        ?.let { viewHolderAtPosition ->
                            val transitionName =
                                (viewHolderAtPosition as PostsVerticalRvAdapter.PostViewHolder)
                                    .binding
                                    .postImageView
                                    .transitionName
                            logcat(LogPriority.INFO) { "Transition name = $transitionName" }
                            logcat { "startPostponedEnterTransition()" }
                        }
                    logcat { "Disposing of delayedTransitionTriggerDisposable" }
                    delayedTransitionTriggerDisposable.dispose()
//                    postsAdapter.disableTransformations = false
                    logcat { "startPostponedEnterTransition()" }
                    startPostponedEnterTransition()
                    viewModel.uiStateBehaviorSubject.onNext(UiState.NORMAL)
                }
            }.addTo(disposables)

        val swipeRefreshLayout = recyclerView.parent as SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.uiStateBehaviorSubject.onNext(UiState.LOADING) // This would get triggered later anyway but triggering it here helps with UI junk.
            postsAdapter.refresh()
            swipeRefreshLayout.isRefreshing = false
        }

        viewModel.refreshTriggerObservable
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                viewModel.uiStateBehaviorSubject.onNext(UiState.LOADING) // This would get triggered later anyway but triggering it here helps with UI junk.
                postsAdapter.refresh()
            }.addTo(disposables)
    }
}