package com.b4kancs.rxredditdemo.ui.drawer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import androidx.core.view.isVisible
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.ui.follows.FollowsViewModel
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
import com.google.android.material.textview.MaterialTextView
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import logcat.LogPriority
import logcat.logcat

class FollowsDrawerListAdapter(
        private val c: Context,
        private val viewModel: FollowsViewModel
) : ArrayAdapter<UserFeed>(c, R.layout.list_item_drawer_follows) {

    private var disposables = CompositeDisposable()
    private var follows: List<UserFeed> = emptyList()
    private val yourFeedsHeaderPosition = 1

    private val userFeedComparator = Comparator<UserFeed> { a, b ->
        if (a.status == b.status) return@Comparator a.name.compareTo(b.name)
        if (a.status == UserFeed.Status.AGGREGATE) return@Comparator -1
        if (b.status == UserFeed.Status.AGGREGATE) return@Comparator 1
        if (a.status == UserFeed.Status.SUBSCRIBED) return@Comparator -1
        if (b.status == UserFeed.Status.SUBSCRIBED) return@Comparator 1
        if (a.status != UserFeed.Status.FOLLOWED && b.status == UserFeed.Status.FOLLOWED) return@Comparator -1
        if (b.status != UserFeed.Status.FOLLOWED) return@Comparator 1
        if (a.status != UserFeed.Status.NOT_IN_DB && b.status == UserFeed.Status.NOT_IN_DB) return@Comparator -1
        if (b.status != UserFeed.Status.NOT_IN_DB) return@Comparator 1
        return@Comparator a.name.compareTo(b.name)
    }

    init {
        logcat { "init" }
        viewModel.getFollowsChangedSubject()
            .observeOn(AndroidSchedulers.mainThread())
            .startWithItem(Unit)
            .subscribe {
                notifyDataSetChanged()
            }
            .addTo(disposables)
    }

    override fun notifyDataSetChanged() {
        logcat { "notifyDataSetChanged" }
        populateFollows()
            .blockingSubscribe {
                super.notifyDataSetChanged()
            }

        super.notifyDataSetChanged()
    }

    private fun populateFollows(): Completable {
        logcat { "populateFollows()" }
        return Completable.create { emitter ->
            viewModel.getAllUserFeeds()
                .map { feeds -> feeds.sortedWith(userFeedComparator) }
                .subscribe { feeds ->
                    follows = feeds
                    emitter.onComplete()
                }.addTo(disposables)
        }
    }

    override fun getCount(): Int {
        logcat { "getCount" }
        return follows.size + 2
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        logcat(LogPriority.VERBOSE) { "getView position = $position" }

        val inflater = LayoutInflater.from(c)

        // Let's figure out if the view needs to be a header
        if (position == 1) {    // yourFeedsHeaderPosition == 1
            val headerViewItem = inflater.inflate(R.layout.list_item_drawer_header, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.text_view_drawer_header_title)
            headerTextView.text = context.getString(R.string.drawer_header_your_followed_feeds)
            return headerViewItem
        }
        // .. or the Aggregate feed
        val feed: UserFeed =
            if (position == 0) {
                viewModel.getDefaultUserFeed()
            }
            // .. or a regular item
            else {    // Feed aggregate
                val pos = position - 2  // one for the aggregate feed, one for the header
                follows[pos]
            }

        val feedListItem = inflater.inflate(R.layout.list_item_drawer_follows, parent, false)
        val followsNameTextView = feedListItem.findViewById<MaterialTextView>(R.id.text_view_drawer_follows_name)
            .also { it.text = feed.name }
        val actionImageView = feedListItem.findViewById<ImageView>(R.id.image_view_drawer_follows_action)
            .also {
                when {
                    feed == viewModel.getDefaultUserFeed() -> it.isVisible = false
                    feed.status == UserFeed.Status.FOLLOWED -> it.setImageResource(R.drawable.ic_outline_notification_24)
                    feed.status == UserFeed.Status.SUBSCRIBED -> it.setImageResource(R.drawable.ic_baseline_notification_24)
                }
            }

        feedListItem.clicks()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                viewModel.setUserFeedTo(feed)
            }
            .addTo(disposables)

        return feedListItem
    }
}