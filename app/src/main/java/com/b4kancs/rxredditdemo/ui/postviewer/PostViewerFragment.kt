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
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.Transition
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.FragmentPostViewerBinding
import com.b4kancs.rxredditdemo.ui.MainActivity
import com.b4kancs.rxredditdemo.ui.home.HomeViewModel
import com.b4kancs.rxredditdemo.utils.CustomLinearLayoutManager
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
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.util.concurrent.TimeUnit

class PostViewerFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "PostViewerFragment"
        private const val SAVED_STATE_POSITION_KEY = "position"
    }

    private val args: PostViewerFragmentArgs by navArgs()
    private val disposables = CompositeDisposable()
    private lateinit var binding: FragmentPostViewerBinding
    private lateinit var viewModel: PostViewerViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPostViewerBinding.inflate(inflater, container, false)

        sharedElementEnterTransition = AutoTransition()
        sharedElementReturnTransition = AutoTransition().apply {
            addListener(object : Transition.TransitionListener {
                override fun onTransitionStart(transition: Transition) {
                    Log.i(LOG_TAG, "onTransitionStart")
                }

                override fun onTransitionEnd(transition: Transition) {
                    Log.i(LOG_TAG, "onTransitionEnd")
                }

                override fun onTransitionCancel(transition: Transition) {
                    Log.i(LOG_TAG, "onTransitionCancel")
                }

                override fun onTransitionPause(transition: Transition) {
                    Log.i(LOG_TAG, "onTransitionPause")
                }

                override fun onTransitionResume(transition: Transition) {
                    Log.i(LOG_TAG, "onTransitionResume")
                }
            })
        }

        Log.i(LOG_TAG, "Calling postponeEnterTransition().")
        postponeEnterTransition()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val initialPosition: Int? =
            if (savedInstanceState != null)
                null
            else
                savedInstanceState?.getInt("position") ?: args.position

        setUpViewModel()
        setUpRecyclerView(initialPosition)
        setUpOnBackPressedCallback()
    }

    override fun onPause() {
        super.onPause()
        (activity as MainActivity).apply {
            supportActionBar?.show()
            findViewById<BottomNavigationView>(R.id.nav_view).isVisible = true
        }
//        (binding.recyclerViewPostViewer.adapter as PostViewerAdapter)
    }

    // Lesson learned:
    // Using a RecyclerView here was a mistake.
    // I should have used a ViewPager instead.
    // I hate RecyclerViews now.
    private fun setUpRecyclerView(initialPosition: Int?) {
        /* (⊙_◎) */
        // In order to achieve the desired behaviour of the recyclerview only scrolling on specific button clicks,
        // we disable scrollability of the rv in our CustomLinearLayoutManager. Just before requesting a smooth scroll, we
        // enable the scroll ability, then we disable it again just after the scroll has finished.
        val onPositionChangedCallback = { nextPosition: Int ->
            binding.recyclerViewPostViewer.let { recyclerView ->
                val recyclerViewLayoutManager = recyclerView.layoutManager as CustomLinearLayoutManager

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

        val postViewerAdapter = PostViewerAdapter(requireContext(), onPositionChangedCallback)
        with(binding) {
            recyclerViewPostViewer.isVisible = false
            recyclerViewPostViewer.adapter = postViewerAdapter
            recyclerViewPostViewer.layoutManager = CustomLinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            LinearSnapHelper().attachToRecyclerView(recyclerViewPostViewer)
            viewModel.pagingDataObservable
                .filter {
                    // We do this, because the pagingDataObservable's owner is the HomeViewModel, updating the paging data in the
                    // HomeFragment would trigger this observer and cause an exception here
                    this@PostViewerFragment.isAdded
                }
                .subscribe { pagingData ->
                    postViewerAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
                    // Initial setup. Let's scroll to the right position as soon as the pagingAdapter has done loading.
                    postViewerAdapter.loadStateFlow
                        .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                        .take(1)
                        .onEach {
                            initialPosition?.let { pos -> recyclerViewPostViewer.scrollToPosition(pos) }

                            // After a rotation, the RecyclerView could sometimes get stuck scrolling between two view.
                            // The below block stops that behaviour.
                            postViewerAdapter.readyToBeDrawnSubject
                                .observeOn(AndroidSchedulers.mainThread())
                                .take(1)
                                .subscribe {
                                    val layoutManager = recyclerViewPostViewer.layoutManager as CustomLinearLayoutManager
                                    val visiblePosition = layoutManager.findLastVisibleItemPosition()
                                    val toScrollX =
                                        (recyclerViewPostViewer
                                            .findViewHolderForLayoutPosition(visiblePosition) as PostViewerAdapter.PostViewerViewHolder)
                                            .binding.postLargeItemImageView.x.toInt()
                                    recyclerViewPostViewer.scrollBy(toScrollX, 0)
                                }
                            this.recyclerViewPostViewer.isVisible = true
                        }
                        .launchIn(MainScope())
                }

            postViewerAdapter.readyToBeDrawnSubject
                .observeOn(AndroidSchedulers.mainThread())
                .filter { it == (initialPosition ?: 0) }
                .take(1)
                .subscribe {
                    (activity as MainActivity).apply {
                        animateHideActionBar(root)
                        animateHideBottomNavBar(root)
                    }
                    Log.i(LOG_TAG, "startPostponedEnterTransition()")
                    startPostponedEnterTransition()
                }.addTo(disposables)
        }
    }

    private fun setUpViewModel() {
        val pagingDataObservableProviderName = args.pagingDataObservableProvider
        val pagingDataObservableProvider: Lazy<PostPagingDataObservableProvider> =
            if (pagingDataObservableProviderName == HomeViewModel::class.java.simpleName)
                sharedViewModel<HomeViewModel>()
            else
                throw IllegalArgumentException()
        viewModel = viewModel<PostViewerViewModel> { parametersOf(pagingDataObservableProvider.value) }.value
    }

    private fun setUpOnBackPressedCallback() {
        (activity as MainActivity).onBackPressedDispatcher.addCallback(this) {
            // Stops the user from clicking on anything while the transition takes place.
            (binding.recyclerViewPostViewer.adapter as PostViewerAdapter).disposables.dispose()

            val visiblePosition = (binding.recyclerViewPostViewer.layoutManager as LinearLayoutManager)
                .findLastVisibleItemPosition()
            val transitionName =
                (binding.recyclerViewPostViewer.findViewHolderForLayoutPosition(visiblePosition) as PostViewerAdapter.PostViewerViewHolder)
                    .binding
                    .postLargeItemImageView
                    .transitionName
            Log.i(LOG_TAG, "Transition name = $transitionName")
            (sharedElementReturnTransition as Transition).addTarget(transitionName)

            findNavController().previousBackStackEntry?.savedStateHandle?.set("position", visiblePosition)
            findNavController().popBackStack()
        }
    }
}