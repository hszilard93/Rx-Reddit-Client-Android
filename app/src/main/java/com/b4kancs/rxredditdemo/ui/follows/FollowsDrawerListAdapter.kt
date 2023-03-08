package com.b4kancs.rxredditdemo.ui.follows

import android.content.Context
import android.view.*
import android.widget.ArrayAdapter
import android.widget.PopupWindow
import androidx.core.view.children
import androidx.core.view.isVisible
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.ListItemDrawerFollowsBinding
import com.b4kancs.rxredditdemo.databinding.PopupDrawerFollowsOptionsBinding
import com.b4kancs.rxredditdemo.model.UserFeed
import com.b4kancs.rxredditdemo.model.UserFeed.Status
import com.b4kancs.rxredditdemo.ui.uiutils.SnackType
import com.b4kancs.rxredditdemo.ui.uiutils.dpToPixel
import com.b4kancs.rxredditdemo.ui.uiutils.makeSnackBar
import com.google.android.material.textview.MaterialTextView
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
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
        if (a.status == Status.AGGREGATE) return@Comparator -1
        if (b.status == Status.AGGREGATE) return@Comparator 1
        if (a.status == Status.SUBSCRIBED) return@Comparator -1
        if (b.status == Status.SUBSCRIBED) return@Comparator 1
        if (a.status != Status.FOLLOWED && b.status == Status.FOLLOWED) return@Comparator -1
        if (b.status != Status.FOLLOWED) return@Comparator 1
        if (a.status != Status.NOT_IN_DB && b.status == Status.NOT_IN_DB) return@Comparator -1
        if (b.status != Status.NOT_IN_DB) return@Comparator 1
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
        if (position == 1) {    // Your feeds header
            val headerViewItem = inflater.inflate(R.layout.list_item_drawer_header, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.text_view_drawer_header_title)
            headerTextView.text = context.getString(R.string.drawer_header_your_followed_feeds)
            return headerViewItem
        }
        // .. or the Aggregate feed
        val feed: UserFeed =
            if (position == 0) {
                viewModel.getAggregateUserFeed()
            }
            // .. or a regular item
            else {    // Feed aggregate
                val pos = position - 2  // one for the aggregate feed, one for the header
                follows[pos]
            }


        val listItemBinding: ListItemDrawerFollowsBinding =
//            if (convertView?.id == R.id.constraint_layout_follows_drawer_list_item)
//                ListItemDrawerFollowsBinding.bind(convertView)
//            else
            ListItemDrawerFollowsBinding.inflate(inflater, parent, false)

        with(listItemBinding) {
            textViewDrawerFollowsName.text = feed.name
            imageViewDrawerFollowsAction
                .also {
                    when (feed.status) {
                        Status.AGGREGATE -> it.setImageResource(R.drawable.ic_follows_aggregate_zipper_24)
                        Status.FOLLOWED -> it.setImageResource(R.drawable.ic_outline_notification_24)
                        Status.SUBSCRIBED -> {
                            val isNotificationPermissionGrantedOrNotNeeded = viewModel.checkIsNotificationPermissionDenied()
                            if (isNotificationPermissionGrantedOrNotNeeded)
                                it.setImageResource(R.drawable.ic_baseline_notification_24_enabled)
                            else
                                it.setImageResource(R.drawable.ic_baseline_notification_24_disabled)
                        }
                        else -> throw java.lang.IllegalStateException(
                            "No feed with status ${feed.status} should be in the side drawer."
                        )
                    }
                }

            imageViewDrawerFollowsAction.clicks()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat(LogPriority.INFO) { "imageViewDrawerFollowsAction.clicks.onNext" } }
                .subscribe {
                    when (feed.status) {
                        Status.FOLLOWED -> viewModel.subscribeToFeed(feed).subscribe()
                        Status.SUBSCRIBED -> viewModel.unsubscribeFromFeed(feed).subscribe()
                        else -> { logcat(LogPriority.WARN) { "Illegal state for UserFeed in drawer! feed = $feed" } }
                    }
                }
                .addTo(disposables)

            textViewDrawerFollowsName.clicks()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { logcat(LogPriority.INFO) { "textViewDrawerFollowsName.clicks.onNext" } }
                .subscribe {
                    viewModel.setUserFeedTo(feed)
                }
                .addTo(disposables)

            imageViewDrawerFollowsOptions.apply {
                isVisible = feed.status != Status.AGGREGATE
                clicks()
                    .doOnNext { logcat(LogPriority.INFO) { "imageViewDrawerFollowsOptions.clicks.onNext" } }
                    .subscribe {
                        val popupBinding = PopupDrawerFollowsOptionsBinding.inflate(inflater, parent, false)
                        val popupWindow = PopupWindow(
                            popupBinding.root,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            true
                        )

                        popupBinding.textViewDrawerFollowsOptionDelete.apply {
                            isVisible = feed.status in listOf(Status.FOLLOWED, Status.SUBSCRIBED)
                            clicks()
                                .doOnNext { logcat(LogPriority.INFO) { "textViewDrawerFollowsOptionDelete.clicks.onNext" } }
                                .subscribe {
                                    viewModel.deleteUserFeed(feed)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onComplete = {
                                                val message = c.getString(R.string.follows_snack_deleted, feed.name)
                                                makeSnackBar(parent, null, message).show()
                                                popupWindow.dismiss()
                                            },
                                            onError = { _ ->
                                                makeSnackBar(
                                                    parent,
                                                    R.string.common_message_could_not_perform,
                                                    type = SnackType.ERROR
                                                ).show()
                                                popupWindow.dismiss()
                                            }
                                        ).addTo(disposables)
                                }.addTo(disposables)
                        }

                        popupBinding.textViewDrawerFollowsOptionSubscribe.apply {
                            isVisible = feed.status == Status.FOLLOWED
                            clicks()
                                .doOnNext { logcat(LogPriority.INFO) { "textViewDrawerFollowsOptionSubscribe.clicks.onNext" } }
                                .subscribe {
                                    viewModel.subscribeToFeed(feed)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onComplete = {
                                                val message = c.getString(R.string.follows_snack_subscribed, feed.name)
                                                makeSnackBar(parent, null, message).show()
                                                popupWindow.dismiss()
                                            },
                                            onError = { _ ->
                                                makeSnackBar(
                                                    parent,
                                                    R.string.common_message_could_not_perform,
                                                    type = SnackType.ERROR
                                                ).show()
                                                popupWindow.dismiss()
                                            }
                                        ).addTo(disposables)
                                }.addTo(disposables)
                        }

                        popupBinding.textViewDrawerFollowsOptionUnsubscribe.apply {
                            isVisible = feed.status == Status.SUBSCRIBED
                            clicks()
                                .doOnNext { logcat(LogPriority.INFO) { "textViewDrawerFollowsOptionUnsubscribe.clicks.onNext" } }
                                .subscribe {
                                    viewModel.unsubscribeFromFeed(feed)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribeBy(
                                            onComplete = {
                                                val message = c.getString(R.string.follows_snack_unsubscribed, feed.name)
                                                makeSnackBar(parent, null, message).show()
                                                popupWindow.dismiss()
                                            },
                                            onError = { _ ->
                                                makeSnackBar(
                                                    parent,
                                                    R.string.common_message_could_not_perform,
                                                    type = SnackType.ERROR
                                                ).show()
                                                popupWindow.dismiss()
                                            }
                                        ).addTo(disposables)
                                }.addTo(disposables)
                        }

                        val numberOfVisibleOptions = popupBinding.root.children.count { it.isVisible }
                        val popupViewHeightPlusPadding =
                            dpToPixel(numberOfVisibleOptions * 36 + 40, c)

                        popupWindow.showAsDropDown(listItemBinding.root, 0, popupViewHeightPlusPadding * -1, Gravity.END)
                    }
                    .addTo(disposables)
            }
        }

        return listItemBinding.root
    }
}