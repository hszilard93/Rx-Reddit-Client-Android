package com.b4kancs.rxredditdemo.ui.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionInflater
import androidx.viewbinding.ViewBinding
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerFragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import logcat.LogPriority
import logcat.logcat
import java.util.concurrent.TimeUnit

/*
 * A base fragment for Home, Favorites and Follows.
 * Hopefully it will help cut down on bugs and will make changing shared behaviour easier
 * while making it easier to identify specific behaviour.
*/
abstract class BaseListingFragment : Fragment() {
    companion object {
        const val FLICKERING_DELAY = 200L
    }

    abstract val viewModel: BaseListingFragmentViewModel
    val disposables = CompositeDisposable()
    var positionToGoTo: Int? = null
    lateinit var delayedTransitionTriggerDisposable: Disposable

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        logcat {
            "onCreateView\n  Current nav backstack: ${
                findNavController().backQueue
                    .map { it.destination }
                    .joinToString("\n ", "\n ")
            }"
        }

        val genericBinding = setUpBinding(inflater, container)

        with(findNavController().currentBackStackEntry) {
            positionToGoTo = this?.savedStateHandle?.get<Int>(PostViewerFragment.SAVED_STATE_POSITION_KEY)
            this?.savedStateHandle?.remove<Int>(PostViewerFragment.SAVED_STATE_POSITION_KEY)
            positionToGoTo?.let { logcat(LogPriority.INFO) { "Recovered position from PostViewerFragment. positionToGoTo = $it" } }
        }

        if (positionToGoTo == null) positionToGoTo = 0
        logcat(LogPriority.INFO) { "positionToGoTo = $positionToGoTo" }

        // TODO move stuff here?

        onCreateViewDoAlso(inflater, container, savedInstanceState)

        return genericBinding.root
    }

    abstract fun setUpBinding(inflater: LayoutInflater, container: ViewGroup?): ViewBinding

    open fun onCreateViewDoAlso(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) {}


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logcat { "onViewCreated" }

        setUpActionBarAndRelated()
        setUpSharedElementTransition()
        setUpRecyclerView()
        setUpUiStatesBehaviour()
        onViewCreatedDoAlso(view, savedInstanceState)
    }

    open fun onViewCreatedDoAlso(view: View, savedInstanceState: Bundle?) {}

    abstract fun setUpActionBarAndRelated()

    open fun setUpSharedElementTransition() {
        logcat { "setUpSharedElementTransition" }

        sharedElementEnterTransition = TransitionInflater
            .from(requireContext())
            .inflateTransition(R.transition.shared_element_transition)

        postponeEnterTransition()

        // If, for some reason, the transition doesn't get triggered in time (the image is slow to load etc.), we force the transition.
        delayedTransitionTriggerDisposable = Observable.timer(500, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { logcat { "Starting delayed enter transition timer." } }
            .subscribe {
                logcat(LogPriority.INFO) { "Triggering delayed enter transition." }
                startPostponedEnterTransition()
            }
            .addTo(disposables)
    }

    abstract fun setUpRecyclerView()

    abstract fun setUpUiStatesBehaviour()


    override fun onStart() {
        logcat { "onStart" }
        super.onStart()

        setUpDrawer()
        onStartDoAlso()
    }

    abstract fun setUpDrawer()

    open fun onStartDoAlso() {}


    override fun onResume() {
        logcat { "onResume" }
        super.onResume()
        setUpOptionsMenu()
    }

    abstract fun setUpOptionsMenu()


    override fun onDestroyView() {
        logcat { "onDestroyView" }
        super.onDestroyView()
        onDestroyViewRemoveBinding()
        onDestroyViewDoAlso()
    }

    abstract fun onDestroyViewRemoveBinding()

    open fun onDestroyViewDoAlso() {}


    override fun onDestroy() {
        logcat { "onDestroy" }
        super.onDestroy()
        disposables.dispose()
        onDestroyDoAlso()
    }

    open fun onDestroyDoAlso() {}

    abstract fun createNewPostViewerFragment(position: Int, sharedView: View)
}