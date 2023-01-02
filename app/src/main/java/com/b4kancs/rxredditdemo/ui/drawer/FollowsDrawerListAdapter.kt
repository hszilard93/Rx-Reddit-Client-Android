package com.b4kancs.rxredditdemo.ui.drawer

import android.content.Context
import android.widget.ArrayAdapter
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import logcat.logcat

class FollowsDrawerListAdapter(
        private val c: Context,
        private val viewModel: MainViewModel
): ArrayAdapter<UserFeed>(c, R.layout.list_item_drawer_follows) {

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
        logcat {"init"}
//        viewModel.getUserFeedByName()
    }

    

}