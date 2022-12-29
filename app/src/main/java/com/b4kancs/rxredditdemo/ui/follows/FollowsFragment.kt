package com.b4kancs.rxredditdemo.ui.follows

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentFollowsBinding
import com.b4kancs.rxredditdemo.ui.main.MainActivity
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerFragment
import com.b4kancs.rxredditdemo.ui.shared.PostsVerticalRvAdapter
import com.b4kancs.rxredditdemo.ui.uiutils.CustomLinearLayoutManager
import com.b4kancs.rxredditdemo.ui.uiutils.SnackType
import com.b4kancs.rxredditdemo.ui.uiutils.makeSnackBar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import logcat.LogPriority
import logcat.logcat
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.util.concurrent.TimeUnit

class FollowsFragment : Fragment() {

    private val followsViewModel: FollowsViewModel by sharedViewModel()
    private val args: FollowsFragmentArgs by navArgs()
    private var _binding: FragmentFollowsBinding? = null
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
            positionToGoTo?.let { logcat(LogPriority.INFO) { "Recovered position from PostViewerFragment. positionToGoTo = $it" } }
        }

        isBeingReconstructed = savedInstanceState != null
        logcat { "isBeingReconstructed = $isBeingReconstructed" }
        if (!isBeingReconstructed) {
            // If not returning from PVF and not recovering from rotation etc. positionToGoTo remains null, else 0.
            // When positionToGoTo is null, we should let the RecyclerView recover its previous position.
            if (positionToGoTo == null) positionToGoTo = 0
        }

        _binding = FragmentFollowsBinding.inflate(inflater, container, false)

        logcat(LogPriority.INFO) { "postponeEnterTransition()" }
        postponeEnterTransition()

        // If, for some reason, the transition doesn't get triggered in time (the image is slow to load, etc), we force it after a delay.
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

        followsViewModel.getAreThereFollowedUsersBehaviourSubject()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat { "followsViewModel.getAreThereFollowedUsersBehaviourSubject.onNext" } }
            .filter { _binding != null }
            .subscribe { isNotEmpty ->
                binding.textViewFollowsNoMedia.isVisible = !isNotEmpty
                (binding.rvFollowsPosts.adapter as PostsVerticalRvAdapter).let { adapter ->
                    if (adapter.itemCount > 1 && !isNotEmpty) adapter.refresh()
                    if (adapter.itemCount <= 1 && isNotEmpty) adapter.refresh()
                }
            }.addTo(disposables)

        followsViewModel.feedChangedBehaviorSubject
            .observeOn(AndroidSchedulers.mainThread())
            .filter { _binding != null }
            .subscribe {
                (binding.rvFollowsPosts.adapter as PostsVerticalRvAdapter).refresh()
            }
            .addTo(disposables)

        // If we got here from another fragment, we need to recover the navigation argument and go to the specified user's feed.
        if (!isBeingReconstructed) {
            val userNameFromNavigation = args.userName
            userNameFromNavigation?.let { userName ->
                followsViewModel.setUserFeedTo(userName)
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

    override fun onStart() {
        logcat { "onStart" }
        super.onStart()
        (activity as MainActivity)  // TODO: Implement fragment specific drawer

        setUpOptionsMenu()
    }

    private fun goToNewPostViewerFragment(position: Int, sharedView: View) {
        logcat { "goToNewPostViewerFragment" }
        val sharedElementExtras = FragmentNavigatorExtras(sharedView to sharedView.transitionName)
        val action = FollowsFragmentDirections.actionFollowsToPostViewer(position, followsViewModel::class.simpleName!!)
        findNavController().navigate(action, sharedElementExtras)
    }

    private fun setUpRecyclerView() {
        logcat { "setUpRecyclerView" }
        val mainActivity = activity as MainActivity

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
                    null
                )
            }
            val postsFollowsAdapter = rvFollowsPosts.adapter as PostsVerticalRvAdapter

            followsViewModel.postsCachedPagingObservable
                .subscribe { pagingData ->
                    try {
                        postsFollowsAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                    }
                    catch (e: Exception) {
                        // There might be a weird NullPointerException happening sometimes that doesn't really seem to affect anything
                        logcat(LogPriority.ERROR) { e.stackTrace.toString() }
                    }
                }.addTo(disposables)

            postsFollowsAdapter.loadStateFlow
                .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                .take(1)
                .onEach {
                    logcat(LogPriority.INFO) { "postFollowsAdapter.loadStateFlow.onEach loadStates.refresh == LoadState.NotLoading" }
                    if (postsFollowsAdapter.itemCount != 1) {
                        positionToGoTo?.let { pos ->
                            logcat(LogPriority.INFO) { "Scrolling to position: $pos" }
                            rvFollowsPosts.scrollToPosition(pos)
                        }
                    }
                }.launchIn(MainScope())

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
                        }
                        catch (e: Exception) {
                            logcat(LogPriority.WARN) { e.message.toString() }
                        }
                    }

                    // We put these reveals here so that they will be synced with the SharedElementTransition.
                    mainActivity.animateShowActionBar()
                    mainActivity.animateShowBottomNavBar()

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
                    goToNewPostViewerFragment(position, view)
                    (rvFollowsPosts.layoutManager as CustomLinearLayoutManager).canScrollVertically = false
                    postsFollowsAdapter.disposables.dispose()
                }.addTo(disposables)

            postsFollowsAdapter.addLoadStateListener { combinedLoadStates ->
                progressBarFollowsLarge.isVisible = combinedLoadStates.refresh is LoadState.Loading
            }

            srlFollows.isEnabled = false
        }
    }

    private fun setUpOptionsMenu() {
        logcat { "setUpOptionsMenu" }
        // TODO
    }

    override fun onDestroy() {
        logcat { "onDestroy" }
        super.onDestroy()
        logcat { "Disposing of disposables." }
        disposables.dispose()
    }

    override fun onDestroyView() {
        logcat { "onDestroyView" }
        super.onDestroyView()
        _binding = null
    }
}