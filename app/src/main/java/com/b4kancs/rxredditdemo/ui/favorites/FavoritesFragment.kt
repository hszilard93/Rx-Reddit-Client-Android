package com.b4kancs.rxredditdemo.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.b4kancs.rxredditdemo.databinding.FragmentFavoritesBinding
import com.b4kancs.rxredditdemo.ui.MainActivity
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerFragment
import com.b4kancs.rxredditdemo.ui.shared.PostVerticalRvAdapter
import com.b4kancs.rxredditdemo.utils.CustomLinearLayoutManager
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
import logcat.LogPriority
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.util.concurrent.TimeUnit

class FavoritesFragment : Fragment() {

    private val favoritesViewModel: FavoritesViewModel by sharedViewModel()
    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val disposables = CompositeDisposable()
    private var positionToReturnTo: Int? = null
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
            positionToReturnTo = this?.savedStateHandle?.get<Int>(PostViewerFragment.SAVED_STATE_POSITION_KEY)
            this?.savedStateHandle?.remove<Int>(PostViewerFragment.SAVED_STATE_POSITION_KEY)
            logcat(LogPriority.INFO) { "Recovered position from PostViewerFragment. positionToGoTo = $positionToReturnTo" }
        }

        _binding = FragmentFavoritesBinding.inflate(inflater,container, false)

        logcat(LogPriority.INFO) { "postponeEnterTransition()" }
        postponeEnterTransition()

        delayedTransitionTriggerDisposable = Observable.timer(350, TimeUnit.MILLISECONDS)
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
        val mainActivity = activity as MainActivity

        binding.apply {
            rvFavoritesPosts.layoutManager = CustomLinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL)
                .apply { canScrollHorizontally = false }

            if (rvFavoritesPosts.adapter == null) {
                // If positionToNavigateTo is not null, we need to disable glide transformations and some other stuff for the
                // shared element transition to work properly
                val shouldDisableTransformations = if (positionToReturnTo != null) {
                    logcat { "Disabling glide transformations" }
                    true
                } else false
                rvFavoritesPosts.adapter = PostVerticalRvAdapter(
                    mainActivity,
                    shouldDisableTransformations,
                    null
                )
            }
            val postsFavoritesAdapter = rvFavoritesPosts.adapter as PostVerticalRvAdapter

            favoritesViewModel.cachedPagingObservable
                .subscribe { pagingData ->
                    try {
                        postsFavoritesAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)

                        postsFavoritesAdapter.loadStateFlow
                            .filter { loadStates -> loadStates.refresh is LoadState.NotLoading }
                            .take(1)
                            .onEach {
                                // If the subreddit feed contains no displayable posts (images etc.), display a textview
                                if (postsFavoritesAdapter.itemCount == 1) {   // The 1 is because of the always-present bottom loading indicator
                                    binding.textViewFavoritesNoMedia.isVisible = true
                                } else {
                                    binding.textViewFavoritesNoMedia.isVisible = false
                                    (positionToReturnTo ?: 0).let { pos ->
                                        logcat(LogPriority.INFO) { "Scrolling to position: $pos" }
                                        rvFavoritesPosts.scrollToPosition(pos)
                                    }
                                }
                            }
                            .launchIn(MainScope())
                    } catch (e: Exception) {
                        // There might be a weird NullPointerException happening sometimes that doesn't really seem to affect anything
                        logcat(LogPriority.ERROR) { e.stackTrace.toString() }
                    }
                }.addTo(disposables)

            postsFavoritesAdapter.refresh()

            postsFavoritesAdapter.addLoadStateListener { combinedLoadStates ->
                progressBarFavoritesLarge.isVisible = combinedLoadStates.refresh is LoadState.Loading
            }

            srlFavorites.setOnRefreshListener {
                postsFavoritesAdapter.refresh()
                srlFavorites.isRefreshing = false
            }

            postsFavoritesAdapter.readyToBeDrawnSubject
                .observeOn(AndroidSchedulers.mainThread())
                .filter { it == (positionToReturnTo ?: 0) }      // When the target of the SharedElementTransition or the first element is ready
                .take(1)
                .doOnNext { logcat(LogPriority.INFO) { "readyToBeDrawnSubject.onNext: pos = $it" } }
                .subscribe {
                    // Fine scroll to better position the imageview
                    val toScrollY = binding.rvFavoritesPosts
                        .findViewHolderForLayoutPosition(positionToReturnTo ?: 0)
                        ?.itemView
                        ?.y
                        ?: 0f
                    logcat { "Scrolling by y = $toScrollY" }
                    binding.rvFavoritesPosts.scrollBy(0, toScrollY.toInt())

                    // We put these reveals here so that they will be synced with the SharedElementTransition.
                    mainActivity.animateShowActionBar()
                    mainActivity.animateShowBottomNavBar()

                    Observable.timer(50, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            val transitionName =
                                (rvFavoritesPosts.findViewHolderForLayoutPosition(positionToReturnTo ?: 0)
                                        as PostVerticalRvAdapter.PostSubredditViewHolder)
                                    .binding
                                    .postImageView
                                    .transitionName
                            logcat(LogPriority.INFO) { "Transition name = $transitionName" }
                            logcat { "startPostponedEnterTransition()" }
                            startPostponedEnterTransition()
                            logcat { "Disposing of delayedTransitionTriggerDisposable" }
                            delayedTransitionTriggerDisposable.dispose()

                            postsFavoritesAdapter.disableTransformations = false
                        }.addTo(disposables)
                }.addTo(disposables)

            postsFavoritesAdapter.postClickedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat(LogPriority.INFO) { "postClickedSubject.onNext: post = ${it.first}" } }
                .subscribe { (position, view) ->
                    createNewPostViewerFragment(position, view)
                    (rvFavoritesPosts.layoutManager as CustomLinearLayoutManager).canScrollVertically = false
                    postsFavoritesAdapter.disposables.dispose()
                }.addTo(disposables)
        }
    }

    private fun createNewPostViewerFragment(position: Int, sharedView: View) {
        logcat { "createNewPostViewerFragment" }
        val sharedElementExtras = FragmentNavigatorExtras(sharedView to sharedView.transitionName)
        val action = FavoritesFragmentDirections.actionFavoritesToPostViewer(position, favoritesViewModel::class.simpleName!!)
        findNavController().navigate(action, sharedElementExtras)
    }

    override fun onDestroyView() {
        logcat { "onDestroyView" }
        super.onDestroyView()
        _binding = null
    }
}