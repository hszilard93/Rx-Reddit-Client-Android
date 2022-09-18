package com.b4kancs.rxredditdemo.ui.postviewer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentPostViewerBinding
import com.b4kancs.rxredditdemo.ui.MainActivity
import com.b4kancs.rxredditdemo.ui.home.HomeViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class PostViewerFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "PostViewerFragment"
    }

    private val args: PostViewerFragmentArgs by navArgs()
    private lateinit var binding: FragmentPostViewerBinding
    private lateinit var viewModel: PostViewerViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentPostViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()

        (activity as MainActivity).apply {
            supportActionBar?.hide()
            findViewById<BottomNavigationView>(R.id.nav_view).isVisible = false
            window.decorView.apply { systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION }
        }

        val position = args.position
        setUpViewModel()
        setUpRecyclerView(position)
        setUpOnBackPressedCallback()
    }

    override fun onPause() {
        super.onPause()
        (activity as MainActivity).apply {
            supportActionBar?.show()
            findViewById<BottomNavigationView>(R.id.nav_view).isVisible = true
        }
    }

    private fun setUpRecyclerView(position: Int) {
        val activity = activity as MainActivity
        val pagingAdapter = PostViewerAdapter(activity)
        with(binding) {
            recyclerPostViewer.isVisible = false
            viewModel.pagingDataObservable
                .subscribe { pagingData ->
                    if (recyclerPostViewer.adapter == null) {
                        recyclerPostViewer.adapter = pagingAdapter
                        recyclerPostViewer.layoutManager = object : LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {
                            override fun canScrollHorizontally(): Boolean = false
                        }
                    }
                    pagingAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                    // Let's scroll to the right position as soon as the pagingAdapter has done loading.
                    pagingAdapter.loadStateFlow
                        .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                        .take(1)
                        .onEach {
                            recyclerPostViewer.scrollToPosition(position)
                            recyclerPostViewer.isVisible = true
                        }
                        .launchIn(MainScope())
                }
        }
    }

    private fun setUpViewModel() {
        val pagingDataObservableProviderName = args.pagingDataObservableProvider
        val pagingDataObservableProvider: Lazy<PostPagingDataObservableProvider> =
            if (pagingDataObservableProviderName == HomeViewModel::class.java.simpleName)
                inject<HomeViewModel>()
            else
                throw IllegalArgumentException()
        viewModel = viewModel<PostViewerViewModel> { parametersOf(pagingDataObservableProvider.value) }.value
        viewModel.hello()
    }

    private fun setUpOnBackPressedCallback() {
        (activity as MainActivity).onBackPressedDispatcher.addCallback {
            (binding.recyclerPostViewer.adapter as PostViewerAdapter).latestPosition?.let {
                findNavController().previousBackStackEntry?.savedStateHandle?.set("position", it)
            }
            findNavController().popBackStack()
        }
    }
}