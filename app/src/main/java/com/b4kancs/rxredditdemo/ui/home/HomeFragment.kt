package com.b4kancs.rxredditdemo.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.b4kancs.rxredditdemo.databinding.FragmentHomeBinding
import com.b4kancs.rxredditdemo.ui.MainActivity
import com.b4kancs.rxredditdemo.utils.CustomLinearLayoutManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {
    companion object {
        const val FLICKERING_DELAY = 200L
        private const val LOG_TAG = "HomeFragment"
    }

    private val homeViewModel: HomeViewModel by sharedViewModel()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!  // This property is only valid between onCreateView and onDestroyView.
    private val disposables = CompositeDisposable()
    private var positionToGoTo: Int? = null
    private var justChangedSubreddits = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d(LOG_TAG, "Current backstack: ${
            findNavController().backQueue
                .map { it.destination }
                .joinToString("\n ", "\n ")
        }")
        positionToGoTo = findNavController().currentBackStackEntry?.savedStateHandle?.get<Int>("position")
        if (homeViewModel.isAppJustStarted) {
            positionToGoTo = 0
            homeViewModel.isAppJustStarted = false
        }

        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        Log.i(LOG_TAG, "postponeEnterTransition()")
        postponeEnterTransition()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity as MainActivity

        binding.apply {
            var postSubredditAdapter: PostSubredditAdapter? = null
            recyclerPosts.layoutManager = CustomLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL)
                .apply { canScrollHorizontally = false }
            homeViewModel.cachedPagingObservable
                .subscribe { pagingData ->
                    try {
                        if (recyclerPosts.adapter == null) {
                            val shouldDisableTransformations = positionToGoTo != null
                            // If positionToNavigateTo is not null, we need to disable glide transformations and some other stuff for the
                            // shared element transition to work properly
                            recyclerPosts.adapter = PostSubredditAdapter(activity, shouldDisableTransformations)
                        }
                        postSubredditAdapter = recyclerPosts.adapter as PostSubredditAdapter
                        postSubredditAdapter!!.submitData(viewLifecycleOwner.lifecycle, pagingData)
                        // Make the recyclerview visible and scroll to the top only when the new data has been loaded!
                        postSubredditAdapter!!.loadStateFlow
                            .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                            .take(1)
                            .onEach {
                                // If the subreddit feed contains no displayable posts (images etc.), display a textview
                                if (postSubredditAdapter!!.itemCount == 1)    // The 1 is because of the always present bottom loading indicator
                                    binding.noMediaInSubInfoTextView.isVisible = true
                                else {
                                    binding.noMediaInSubInfoTextView.isVisible = false
                                    positionToGoTo?.let { pos ->
                                        Log.i(LOG_TAG, "Scrolling to position: $pos")
                                        recyclerPosts.scrollToPosition(pos)
                                    }
                                    // This delay stops the flickering after a change of subreddits
                                    if (justChangedSubreddits) {
                                        recyclerPosts.scrollToPosition(0)
                                        recyclerPosts.visibility = View.INVISIBLE
                                        Observable.timer(FLICKERING_DELAY, TimeUnit.MILLISECONDS)
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe {
                                                recyclerPosts.isVisible = true
                                                justChangedSubreddits = false
                                            }.addTo(disposables)
                                    }
                                }
                            }
                            .launchIn(MainScope())
                    } catch (e: Exception) {
                        // There might be a weird NullPointerException happening sometimes that doesn't really seem to do anything
                        Log.e(LOG_TAG, e.stackTrace.toString())
                    }
                }
                .addTo(disposables)

            postSubredditAdapter!!.addLoadStateListener { combinedLoadStates ->
                largeProgressBar.isVisible = combinedLoadStates.refresh is LoadState.Loading
            }

            homeViewModel.subredditNameLiveData.observe(activity) { subredditName ->
                activity.supportActionBar?.title = subredditName
            }

            activity.selectedSubredditChangedSubject
                .debounce(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { sub ->
                    recyclerPosts.isVisible = false
                    homeViewModel.changeSubreddit(sub)
                    justChangedSubreddits = true
                    postSubredditAdapter!!.refresh()
                }
                .addTo(disposables)

            swipeRefreshLayout.apply {
                setOnRefreshListener {
                    postSubredditAdapter!!.refresh()
                    recyclerPosts.scrollToPosition(0)
                    isRefreshing = false
                }
            }

            postSubredditAdapter!!.postClickedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (position, view) ->
                    createNewPostViewerFragmentWithPost(position, view)
                    (recyclerPosts.layoutManager as CustomLinearLayoutManager).canScrollVertically = false
                    // By disposing of the subscriptions here, we stop the user from accidentally clicking on a post
                    // while the transition takes place.
                    postSubredditAdapter?.disposables?.dispose()
                }.addTo(disposables)

            postSubredditAdapter!!.readyToBeDrawnSubject
                .observeOn(AndroidSchedulers.mainThread())
                .filter { it == (positionToGoTo ?: 0) }
                .take(1)
                .subscribe {
                    // Fine scroll to better position the imageview
                    val toScrollY = binding.recyclerPosts
                        .findViewHolderForLayoutPosition(positionToGoTo ?: 0)
                        ?.itemView
                        ?.y
                        ?: 0f
                    Log.i(LOG_TAG, "Scrolling by y = $toScrollY")
                    binding.recyclerPosts.scrollBy(0, toScrollY.toInt())

                    // We put these reveals here so that they will be synced with the SharedElementTransition.
                    activity.animateShowActionBar()
                    activity.animateShowBottomNavBar()

                    Observable.timer(50, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            val transitionName =
                                (recyclerPosts.findViewHolderForLayoutPosition(positionToGoTo ?: 0)
                                        as PostSubredditAdapter.PostSubredditViewHolder)
                                    .binding
                                    .postImageView
                                    .transitionName
                            Log.i(LOG_TAG, "Transition name = $transitionName")
                            Log.i(LOG_TAG, "startPostponedEnterTransition()")
                            startPostponedEnterTransition()
                            postSubredditAdapter!!.disableTransformations = false
                        }.addTo(disposables)
                }.addTo(disposables)
        }
    }

    private fun createNewPostViewerFragmentWithPost(position: Int, sharedView: View) {
        val sharedElementExtras = FragmentNavigatorExtras(sharedView to sharedView.transitionName)
        val action = HomeFragmentDirections.actionOpenPostViewer(position, homeViewModel.javaClass.simpleName)
        findNavController().navigate(action, sharedElementExtras)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}