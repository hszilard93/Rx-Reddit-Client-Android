package com.b4kancs.rxredditdemo.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.b4kancs.rxredditdemo.ui.MainActivity
import com.b4kancs.rxredditdemo.databinding.FragmentHomeBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
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
            val pagingAdapter = PostSubredditAdapter()

            homeViewModel.cachedPagingObservable
                .subscribe { pagingData ->
                    try {
                        pagingAdapter.let {
                            if (recyclerPosts.adapter == null) {
                                recyclerPosts.adapter = it
                                recyclerPosts.layoutManager = LinearLayoutManager(context)
                            }
                            it.submitData(viewLifecycleOwner.lifecycle, pagingData)
                        }
                        recyclerPosts.isVisible = true
                    } catch (e: Exception) {
                        // There might be a weird NullPointerException happening sometimes that doesn't really seem to do anything
                        Log.e(LOG_TAG, e.stackTraceToString())
                    }
                }
                .addTo(disposables)

            pagingAdapter.addLoadStateListener { combinedLoadStates ->
                progressBar.isVisible = combinedLoadStates.refresh is LoadState.Loading
            }

            homeViewModel.subredditNameLiveData.observe(activity) { subredditName ->
                activity.supportActionBar?.title = subredditName
            }

            activity.subredditSelectedChangedSubject
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