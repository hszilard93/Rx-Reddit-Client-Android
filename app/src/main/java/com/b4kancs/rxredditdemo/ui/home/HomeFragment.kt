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
import androidx.transition.ChangeImageTransform
import com.b4kancs.rxredditdemo.databinding.FragmentHomeBinding
import com.b4kancs.rxredditdemo.ui.MainActivity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "HomeFragment"
    }

    private val homeViewModel: HomeViewModel by viewModel()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!  // This property is only valid between onCreateView and onDestroyView.
    private val disposables = CompositeDisposable()
    private var positionToNavigateTo: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d(LOG_TAG, "Current backstack: ${
            findNavController().backQueue
                .map { it.destination }
                .joinToString("\n ", "\n ")
        }")
        positionToNavigateTo = findNavController().currentBackStackEntry?.savedStateHandle?.get<Int>("position")

        _binding = FragmentHomeBinding.inflate(inflater, container, false)

//        sharedElementEnterTransition = ChangeImageTransform()
        sharedElementReturnTransition = ChangeImageTransform()
        Log.i(LOG_TAG, "Calling postponeEnterTransition().")
        postponeEnterTransition()

//        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val activity = activity as MainActivity

        binding.apply {
            var pagingAdapter: PostSubredditAdapter? = null
            recyclerPosts.layoutManager = LinearLayoutManager(context)
            homeViewModel.cachedPagingObservable
                .subscribe { pagingData ->
                    try {
                        if (recyclerPosts.adapter == null) {
                            recyclerPosts.adapter = PostSubredditAdapter(activity)
                        }
                        pagingAdapter = recyclerPosts.adapter as PostSubredditAdapter
                        pagingAdapter!!.submitData(viewLifecycleOwner.lifecycle, pagingData)
                        // Make the recyclerview visible and scroll to the top only when the new data has been loaded!
                        pagingAdapter!!.loadStateFlow
                            .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                            .take(1)
                            .onEach {
                                // If the subreddit feed contains no displayable posts (images etc.), display a textview
                                if (pagingAdapter!!.itemCount == 1)    // The 1 is because of the always present bottom loading indicator
                                    binding.noMediaInSubInfoTextView.isVisible = true
                                else {
                                    binding.noMediaInSubInfoTextView.isVisible = false
                                    recyclerPosts.scrollToPosition(positionToNavigateTo ?: 0)
                                    // This stops the flickering after a change
                                    recyclerPosts.visibility = View.INVISIBLE
                                    Observable.timer(150, TimeUnit.MILLISECONDS)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe {
                                            recyclerPosts.isVisible = true
                                        }.addTo(disposables)
                                }
                            }
                            .launchIn(MainScope())
                    } catch (e: Exception) {
                        // There might be a weird NullPointerException happening sometimes that doesn't really seem to do anything
                        Log.e(LOG_TAG, e.stackTrace.toString())
                    }
                }
                .addTo(disposables)

            pagingAdapter!!.addLoadStateListener { combinedLoadStates ->
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
                    pagingAdapter!!.refresh()
                }
                .addTo(disposables)

            swipeRefreshLayout.apply {
                setOnRefreshListener {
                    pagingAdapter!!.refresh()
                    isRefreshing = false
                }
            }

            pagingAdapter!!.postClickedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (position, view) ->
                    createNewPostViewerFragmentWithPost(position, view)

                    // By disposing of the subscriptions here, we stop the user from accidentally clicking on a post
                    // while the transition takes place.
                    pagingAdapter?.disposables?.dispose()
                }.addTo(disposables)

            pagingAdapter!!.readyToBeDrawnSubject
                .observeOn(AndroidSchedulers.mainThread())
                .take(1)
                .subscribe {
                    Log.i(LOG_TAG, "Calling startPostponedEnterTransition().")
                    startPostponedEnterTransition()
                    activity.animateShowActionBar()
                    activity.animateShowBottomNavBar()
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