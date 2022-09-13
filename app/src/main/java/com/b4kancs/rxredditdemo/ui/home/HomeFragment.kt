package com.b4kancs.rxredditdemo.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.b4kancs.rxredditdemo.ui.MainActivity
import com.b4kancs.rxredditdemo.databinding.FragmentHomeBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "HomeFragment"
    }

    private val homeViewModel: HomeViewModel by viewModel()
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private val disposables = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)

//        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val activity = activity as MainActivity

        binding.apply {
            val pagingAdapter = PostSubredditAdapter(activity)

            homeViewModel.cachedPagingObservable
                .subscribe { pagingData ->
                    try {
                        if (recyclerPosts.adapter == null) {
                            recyclerPosts.adapter = pagingAdapter
                            recyclerPosts.layoutManager = LinearLayoutManager(context)
                        }
                        pagingAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                        // Make the recyclerview visible and scroll to the top only when the new data has been loaded!
                        pagingAdapter.loadStateFlow
                            .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                            .take(1)
                            .onEach {
                                // If the subreddit feed contains no displayable posts (images etc.), display a textview
                                if(pagingAdapter.itemCount == 1)    // The 1 is because of the always present bottom loading indicator
                                    binding.noMediaInSubInfoTextView.isVisible = true
                                else {
                                    binding.noMediaInSubInfoTextView.isVisible = false
                                    recyclerPosts.scrollToPosition(0)
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

            pagingAdapter.addLoadStateListener { combinedLoadStates ->
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
                    pagingAdapter.refresh()
                }
                .addTo(disposables)

            swipeRefreshLayout.apply {
                setOnRefreshListener {
                    pagingAdapter.refresh()
                    isRefreshing = false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}