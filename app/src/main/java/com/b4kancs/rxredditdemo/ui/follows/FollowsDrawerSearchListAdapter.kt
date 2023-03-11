package com.b4kancs.rxredditdemo.ui.follows

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.ListItemDrawerSearchBinding
import com.b4kancs.rxredditdemo.model.UserFeed
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import logcat.LogPriority
import logcat.logcat

class FollowsDrawerSearchListAdapter(
        private val c: Context,
        private val viewModel: FollowsViewModel
) : ArrayAdapter<UserFeed>(c, R.layout.list_item_drawer_search) {

    val disposables = CompositeDisposable()
    private var feeds = ArrayList<UserFeed>()

    init {
        logcat { "init" }
        viewModel.followsSearchResultsChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { searchResults ->
                logcat(LogPriority.INFO) { "Drawer searchResults: size = ${searchResults.size}; items = ${searchResults.map { it.name }}" }
                feeds.clear()
                feeds.addAll(searchResults)
                notifyDataSetChanged()
            }
            .addTo(disposables)
    }

    override fun getCount(): Int = feeds.size

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        logcat(LogPriority.VERBOSE) { "getView: position = $position" }

        val inflater = LayoutInflater.from(c)
        val binding: ListItemDrawerSearchBinding =
            if (convertView == null)
                ListItemDrawerSearchBinding.inflate(inflater, parent, false)
            else
                ListItemDrawerSearchBinding.bind(convertView)

        val feed = feeds[position]
        with(binding) {
            textViewDrawerListItemActionSearch.apply {
                text = feed.name
                clicks()
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext { logcat(LogPriority.INFO) { "textViewDrawerListItemActionSearch.clicks.onNext" } }
                    .subscribe {
                        viewModel.currentFeedBehaviorSubject.onNext(feed)
                    }
                    .addTo(disposables)
            }

            imageViewDrawerListItemActionSearch.apply {
                setImageResource(
                    when (feed.status) {
                        UserFeed.Status.NOT_IN_DB -> R.drawable.ic_outline_add_24
                        UserFeed.Status.FOLLOWED -> R.drawable.ic_outline_notification_24
                        UserFeed.Status.SUBSCRIBED -> R.drawable.ic_baseline_notification_24_enabled
                        else -> throw IllegalStateException("AGGREGATE feed in search results!")
                    }
                )
                clicks()
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext { logcat(LogPriority.INFO) { "imageViewDrawerListItemActionSearch.clicks.onNext" } }
                    .subscribe {
                        when (feed.status) {
                            UserFeed.Status.NOT_IN_DB -> viewModel.addUserFeed(feed)
                            UserFeed.Status.FOLLOWED -> viewModel.subscribeToFeed(feed)
                            UserFeed.Status.SUBSCRIBED -> viewModel.unsubscribeFromFeed(feed)
                            else -> throw java.lang.IllegalStateException(
                                "AGGREGATE or SUBSCRIPTIONS feeds should not show up in the search results."
                            )
                        }
                    }
                    .addTo(disposables)
            }
        }
        return binding.root
    }
}