package com.b4kancs.rxredditdemo.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.distinctUntilChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.b4kancs.rxredditdemo.MainActivity
import com.b4kancs.rxredditdemo.databinding.FragmentHomeBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
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

        val adapter = PostSubredditAdapter()
        homeViewModel.cachedFlowable
            .subscribe { pagingData ->
                try {
                    adapter.let {
                        binding.recyclerPosts.adapter = it
                        it.submitData(viewLifecycleOwner.lifecycle, pagingData)
                    }
                    binding.recyclerPosts.layoutManager = LinearLayoutManager(context)
                    binding.progressCircular.visibility = View.GONE
                } catch (e: Exception) {
                    // There might be a weird NullPointerException happening sometimes that doesn't really seem to do anything
                    Log.e(LOG_TAG, e.stackTraceToString())
                }
            }
            .addTo(disposables)

        homeViewModel.subredditNameLiveData.observe(activity) { subredditName ->
            activity.supportActionBar?.title = subredditName
        }

        homeViewModel.subredditChangedSubject
            .subscribe {
                adapter.refresh()
            }

        binding.swipeRefreshLayout.apply {
            setOnRefreshListener {
                adapter.refresh()
                isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}