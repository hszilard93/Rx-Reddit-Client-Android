package com.b4kancs.rxredditdemo.ui.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.transition.TransitionInflater
import androidx.viewbinding.ViewBinding
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerFragment
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragmentViewModel.UiState
import com.b4kancs.rxredditdemo.ui.uiutils.CustomLinearLayoutManager
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
        const val FLICKERING_DELAY = 200L
    }

    abstract val viewModel: BaseListingFragmentViewModel
    val disposables = CompositeDisposable()
    var positionToGoTo: Int? = null
    lateinit var delayedTransitionTriggerDisposable: Disposable

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        logcat {
            "onCreateView\n  Current nav backstack: ${
                findNavController().backQueue
                    .map { it.destination }
                    .joinToString("\n ", "\n ")
            }"
        }

        val genericBinding = setUpBinding(inflater, container)

        // Recover position from PostViewerFragment, if applicable
        findNavController().currentBackStackEntry?.let { backStackEntry ->
            positionToGoTo = backStackEntry.savedStateHandle.get<Int>(PostViewerFragment.SAVED_STATE_POSITION_KEY)
            backStackEntry.savedStateHandle.remove<Int>(PostViewerFragment.SAVED_STATE_POSITION_KEY)
            positionToGoTo?.let { logcat(LogPriority.INFO) { "Recovered position from PostViewerFragment. positionToGoTo = $it" } }
        }

        // Else, recover position after config change or when coming from another Home|Favorites|Follows fragment, if applicable
        if (positionToGoTo == null) {
            positionToGoTo = viewModel.savedPosition ?: 0
        }

        logcat(LogPriority.INFO) { "positionToGoTo = $positionToGoTo" }

        // TODO move stuff here?

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

        setUpDrawer()
        onStartDoAlso()
    }

    abstract fun setUpDrawer()

    open fun onStartDoAlso() {}


    override fun onResume() {
        logcat { "onResume" }
        super.onResume()
        setUpOptionsMenu()
    }

    abstract fun setUpOptionsMenu()


    override fun onPause() {
        logcat { "onPause" }
        onPauseSavePosition()
        onPauseDoAlso()
        super.onPause()
    }

    abstract fun onPauseSavePosition()

    protected fun savePositionFromRv(recyclerView: RecyclerView) {
        logcat { "onSaveInstanceStateSavePositionForRv" }
        (recyclerView.layoutManager as CustomLinearLayoutManager).let {
            var currentPosition = it.findFirstCompletelyVisibleItemPosition()
            if (currentPosition == RecyclerView.NO_POSITION)
                currentPosition = it.findFirstVisibleItemPosition()
            if (currentPosition != RecyclerView.NO_POSITION)
                viewModel.updateSavedPosition(currentPosition)
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

    abstract fun createNewPostViewerFragment(position: Int, sharedView: View)

    protected fun setUpBaseRecyclerView(recyclerView: RecyclerView, viewModel: BaseListingFragmentViewModel) {
        logcat { "setUpBaseRecyclerView: owner = $" }

        val mainActivity = activity as MainActivity
        recyclerView.layoutManager = CustomLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL)
            .apply { canScrollHorizontally = false }

        if (recyclerView.adapter == null) {
            // If positionToGoTo is not null, we need to disable glide transformations and some other stuff for the
            // shared element transition to work properly
            val shouldDisableTransformations = if (positionToGoTo != null) {
                logcat { "Disabling glide transformations" }
                true
            }
            else
                false

            recyclerView.adapter = PostsVerticalRvAdapter(
                mainActivity,
                shouldDisableTransformations,
                viewModel
            )
        }
        val postsAdapter = recyclerView.adapter as PostsVerticalRvAdapter

        viewModel.postsCachedPagingObservable
            .subscribe { pagingData ->
                try {
                    postsAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                } catch (e: Exception) {
                    // There might be a weird NullPointerException happening sometimes that doesn't really seem to affect anything
                    logcat(LogPriority.ERROR) { e.stackTrace.toString() }
                }
            }.addTo(disposables)

        postsAdapter.postClickedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat(LogPriority.INFO) { "postClickedSubject.onNext: post = ${it.first}" } }
            .subscribe { (position, view) ->
                createNewPostViewerFragment(position, view)
                (recyclerView.layoutManager as CustomLinearLayoutManager).canScrollVertically = false
                // By disposing of the subscriptions here, we stop the user from accidentally clicking on a post
                // while the transition takes place.
                postsAdapter.disposables.dispose()
            }
            .addTo(disposables)

        // This is only useful when returning from a PostViewerFragment, to trigger the delayed transition
        postsAdapter.readyForTransitionSubject
            .observeOn(AndroidSchedulers.mainThread())
            .filter { pos ->
                if (positionToGoTo != null)
                    pos == positionToGoTo
                else
                    pos == 0
            }
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

                Observable.timer(50, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
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
                        postsAdapter.disableTransformations = false
                        logcat { "startPostponedEnterTransition()" }
                        startPostponedEnterTransition()
                        viewModel.uiStateBehaviorSubject.onNext(UiState.NORMAL)
                    }
                    .addTo(disposables)
            }.addTo(disposables)

        val swipeRefreshLayout = recyclerView.parent as SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener {
            viewModel.uiStateBehaviorSubject.onNext(UiState.LOADING)
            postsAdapter.refresh()
            recyclerView.scrollToPosition(0)
            swipeRefreshLayout.isRefreshing = false
        }

    }
}