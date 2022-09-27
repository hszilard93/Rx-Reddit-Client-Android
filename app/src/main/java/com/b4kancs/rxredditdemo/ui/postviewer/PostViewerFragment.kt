package com.b4kancs.rxredditdemo.ui.postviewer

import android.content.Context
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.*
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentPostViewerBinding
import com.b4kancs.rxredditdemo.ui.MainActivity
import com.b4kancs.rxredditdemo.ui.home.HomeViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.concurrent.TimeUnit

class PostViewerFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "PostViewerFragment"
    }

    private val args: PostViewerFragmentArgs by navArgs()
    private val disposables = CompositeDisposable()
    private lateinit var binding: FragmentPostViewerBinding
    private lateinit var viewModel: PostViewerViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPostViewerBinding.inflate(inflater, container, false)
        sharedElementEnterTransition = ChangeImageTransform()
        Log.d(LOG_TAG, "shared element transition: $sharedElementEnterTransition")
        Log.d(LOG_TAG, "Calling postponeEnterTransition().")
        postponeEnterTransition()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val position = args.position
        setUpViewModel()
        setUpRecyclerView(position)
        setUpOnBackPressedCallback()
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()

        (activity as MainActivity).apply {
            supportActionBar?.hide()
            findViewById<BottomNavigationView>(R.id.nav_view).isVisible = false
            window.decorView.apply { systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION }
        }
    }

    override fun onPause() {
        super.onPause()
        (activity as MainActivity).apply {
            supportActionBar?.show()
            findViewById<BottomNavigationView>(R.id.nav_view).isVisible = true
        }
    }

    // Lesson learned:
    // Using a RecyclerView here was a mistake.
    // I should have used a ViewPager instead.
    // I hate RecyclerViews now.
    private fun setUpRecyclerView(position: Int) {
        /* (⊙_◎) */
        // In order to achieve the desired behaviour of the recyclerview only scrolling on specific button clicks,
        // we disable scrollability of the rv in our CustomLinearLayoutManager. Just before requesting a smooth scroll, we
        // enable the scroll ability, then we disable it again just after the scroll has finished.
        val onPositionChangedCallback = { nextPosition: Int ->
            binding.recyclerViewPostViewer.let { recyclerView ->
                val recyclerViewLayoutManager = recyclerView.layoutManager as PostViewerFragment.CustomLinearLayoutManager

                /*
                // This should have stopped the user from interrupting the scroll from one 'page' to the next with a badly timed touch.
                // Unfortunately, it didn't work, so I was forced to find other solutions.
                val temporaryAllConsumingItemTouchListener = object : RecyclerView.SimpleOnItemTouchListener() {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                        Log.d(LOG_TAG, "Recyclerview item touch event blocked.")
                        return true
                    }
                }
                recyclerView.addOnItemTouchListener(temporaryAllConsumingItemTouchListener)
                */

                // Temporarily enable the LayoutManager to scroll, thus making 'smoothscrolling' possible.
                recyclerViewLayoutManager.canScrollHorizontally = true
                recyclerView.smoothScrollToPosition(nextPosition)
                var scrollStoppedTimedDisposable: Disposable? = null
                recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        // Disable scrolling again ASAP...
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            scrollStoppedTimedDisposable =
                                Observable.timer(100, TimeUnit.MILLISECONDS)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe {
                                    recyclerViewLayoutManager.canScrollHorizontally = false
                                }.addTo(disposables)

                        }
                        // ... but only when the view has snapped into place.
                        else {
                            scrollStoppedTimedDisposable?.dispose()
                        }
                    }
                })
            }
        }
        /* ¯\(°_o)/¯ */

        val pagingAdapter = PostViewerAdapter(requireContext(), onPositionChangedCallback)
        with(binding) {
            recyclerViewPostViewer.isVisible = false
            recyclerViewPostViewer.adapter = pagingAdapter
            recyclerViewPostViewer.layoutManager = CustomLinearLayoutManager(requireContext())
            LinearSnapHelper().attachToRecyclerView(recyclerViewPostViewer)
            viewModel.pagingDataObservable
                .subscribe { pagingData ->
                    pagingAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                    // Initial setup. Let's scroll to the right position as soon as the pagingAdapter has done loading.
                    pagingAdapter.loadStateFlow
                        .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                        .take(1)
                        .onEach {
                            recyclerViewPostViewer.scrollToPosition(position)
                            recyclerViewPostViewer.isVisible = true
                        }
                        .launchIn(MainScope())
                }
            if (sharedElementEnterTransition != null) {
                pagingAdapter.isReadyToBeDrawnSubject
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        Log.d(LOG_TAG, "Calling startPostponedEnterTransition().")
                        startPostponedEnterTransition()
                    }
                    .addTo(disposables)
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
            (binding.recyclerViewPostViewer.adapter as PostViewerAdapter).latestPosition?.let {
                findNavController().previousBackStackEntry?.savedStateHandle?.set("position", it)
            }
            findNavController().popBackStack()
        }
    }

    // Custom layout manager for manipulating the RecyclerView's scroll ability for our own nefarious ends.
    // Now it also comes with a slower default smoothscrolling speed!
    inner class CustomLinearLayoutManager(context: Context) : LinearLayoutManager(context, HORIZONTAL, false) {
        var canScrollHorizontally = false
        var scrollSpeedInMillisecondsPerInch = 50f  // This provides a slower scrolling speed. Default is 25f.

        override fun canScrollHorizontally(): Boolean = canScrollHorizontally

        override fun smoothScrollToPosition(recyclerView: RecyclerView?, state: RecyclerView.State?, position: Int) {
            val customSmoothScroller = object : LinearSmoothScroller(recyclerView!!.context) {
                override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics?): Float {
                    return scrollSpeedInMillisecondsPerInch / displayMetrics!!.densityDpi
                }
            }
            customSmoothScroller.targetPosition = position
            startSmoothScroll(customSmoothScroller)
        }
    }
}