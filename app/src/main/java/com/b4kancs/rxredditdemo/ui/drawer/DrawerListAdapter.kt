package com.b4kancs.rxredditdemo.ui.drawer

import android.content.Context
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.core.view.isVisible
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.model.DefaultSubredditObject
import com.b4kancs.rxredditdemo.model.DefaultSubredditObject.defaultSubreddit
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.model.Subreddit.Status
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
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

class DrawerListAdapter(
    private val c: Context,
    private val viewModel: MainViewModel
) : ArrayAdapter<Subreddit>(c, R.layout.list_item_drawer_subreddit) {

    private val disposables = CompositeDisposable()
    private var subreddits: List<Subreddit> = emptyList()

    private val subredditComparator = Comparator<Subreddit> { a, b ->
        val defaultSubreddit = DefaultSubredditObject.defaultSubreddit
        if (a == defaultSubreddit) return@Comparator -1
        if (b == defaultSubreddit) return@Comparator 1
        if (a.status == b.status) return@Comparator a.name.compareTo(b.name)
        if (a.status == Status.FAVORITED && b.status != Status.FAVORITED) return@Comparator -1
        if (b.status == Status.FAVORITED) return@Comparator 1
        if (a.status != Status.IN_DEFAULTS_LIST && b.status == Status.IN_DEFAULTS_LIST) return@Comparator -1
        if (b.status != Status.IN_DEFAULTS_LIST) return@Comparator 1
        return@Comparator a.name.compareTo(b.name)
    }

    init {
        logcat { "init" }
        viewModel.subredditsChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .startWithItem(Unit)
            .subscribe {
                notifyDataSetChanged()
            }
            .addTo(disposables)
    }

    override fun notifyDataSetChanged() {
        logcat { "notifyDataSetChanged" }
        populateSubreddits()
            .subscribe {
                super.notifyDataSetChanged()
            }
            .addTo(disposables)
    }

    private fun populateSubreddits(): Completable {
        logcat { "populateSubreddits" }
        return Completable.create { emitter ->
            viewModel.getAllSubredditsFromDb()
                .observeOn(AndroidSchedulers.mainThread())
                .map { subs -> subs.sortedWith(subredditComparator) }
                .subscribe { subs ->
                    subreddits = subs
                    emitter.onComplete()
                }
                .addTo(disposables)
        }
    }

    // We have two headers for user added and default subreddits that we need to take into account
    override fun getCount(): Int {
        logcat { "getCount" }
        val shouldShowYourSubsHeader = true
        val shouldShowDefaultSubsHeader = subreddits.firstOrNull { it.status == Status.IN_DEFAULTS_LIST } != null
        return subreddits.size + if (shouldShowYourSubsHeader) 1 else 0 + if (shouldShowDefaultSubsHeader) 1 else 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        logcat(LogPriority.VERBOSE) { "getView position = $position" }

        val inflater = LayoutInflater.from(c)
        // Let's figure out if the view needs to be a header
        if (position == 0) {
            val headerViewItem = inflater.inflate(R.layout.list_item_drawer_header, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.text_view_drawer_header_title)
            headerTextView.text = context.getString(R.string.drawer_header_your_subreddits)
            return headerViewItem
        }
        if (subreddits
                .count { it.status != Status.IN_DEFAULTS_LIST || it.status == Status.FAVORITED || it == defaultSubreddit } + 1 == position
        ) {
            val headerViewItem = inflater.inflate(R.layout.list_item_drawer_header, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.text_view_drawer_header_title)
            headerTextView.text = context.getString(R.string.drawer_header_recommended_subreddits)
            return headerViewItem
        }

        // The view needs to be a subreddit list item
        val pos =
            if (position in 1..subreddits
                    .count { it.status != Status.IN_DEFAULTS_LIST || it.status == Status.FAVORITED || it == defaultSubreddit }
            )
                position - 1
            else
                position - 2

        val sub = subreddits[pos]

        val listViewItem = inflater.inflate(R.layout.list_item_drawer_subreddit, parent, false)
        val actionImageView = listViewItem.findViewById<ImageView?>(R.id.image_view_drawer_subreddit_action)!!
            .also {
                when {
                    sub == defaultSubreddit -> {
                        it.setImageResource(R.drawable.ic_baseline_star_filled_gold_24)
                        it.isEnabled = false
                    }
                    sub.status == Status.FAVORITED -> {
                        it.setImageResource(R.drawable.ic_baseline_star_filled_24)
                        it.isEnabled = true
                    }
                    sub.status != Status.IN_DEFAULTS_LIST -> {
                        it.setImageResource(R.drawable.ic_baseline_star_border_24)
                        it.isEnabled = true
                    }
                }
            }

        val subredditTextView: MaterialTextView = listViewItem.findViewById(R.id.text_view_drawer_subreddit_name)
        val optionsImageView: ImageView = listViewItem.findViewById(R.id.image_view_drawer_subreddit_options)

        subredditTextView.text = sub.name

        subredditTextView.clicks()
            .subscribe {
                viewModel.selectedSubredditPublishSubject.onNext(sub)
            }
            .addTo(disposables)

        actionImageView.clicks()
            .subscribe {
                viewModel.changeSubredditStatusByActionLogic(sub)
                    .doOnError {
                        makeSnackBar(parent, null, "Uh oh, something went wrong :(", SnackType.ERROR).show()
                    }
                    .subscribe()
                    .addTo(disposables)
            }
            .addTo(disposables)

        optionsImageView.clicks()
            .subscribe {
                val popupView = inflater.inflate(R.layout.popup_drawer_list_options, parent, false)
                val popupWindow = PopupWindow(
                    popupView,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    true
                )

                val removeFromYourSubsTextView = popupView.findViewById<MaterialTextView>(R.id.text_view_drawer_popup_option_remove)
                    .apply {
                        if (sub.status == Status.IN_DEFAULTS_LIST || sub === defaultSubreddit) {
                            isVisible = false
                            return@apply
                        }

                        clicks().subscribe {
                            viewModel
                                .changeSubredditStatusTo(sub, Status.IN_DEFAULTS_LIST)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(
                                    onSuccess = {
                                        popupWindow.dismiss()
                                    },
                                    onError = {
                                        makeSnackBar(parent, null, "Uh oh, something went wrong :(", SnackType.ERROR).show()
                                    }
                                ).addTo(disposables)
                        }.addTo(disposables)
                    }

                val deleteFromSubredditsTextView = popupView.findViewById<MaterialTextView>(R.id.text_view_drawer_popup_option_delete)
                    .apply {
                        if (sub === defaultSubreddit) {
                            isVisible = false
                            return@apply
                        }

                        clicks().subscribe {
                            viewModel.deleteSubredditFromDb(sub)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(
                                    onComplete = {
                                        makeSnackBar(parent, null, "${sub.address} has been deleted!").show()
                                        popupWindow.dismiss()
                                    },
                                    onError = {
                                        makeSnackBar(
                                            parent,
                                            null,
                                            "Error: ${sub.address} could not be deleted :(",
                                            SnackType.ERROR
                                        ).show()
                                    }
                                )
                                .addTo(disposables)
                        }.addTo(disposables)
                    }

                val setAsDefaultSubTextView = popupView.findViewById<MaterialTextView>(R.id.text_view_drawer_option_set_default)
                    .apply {
                        if (sub == defaultSubreddit) {
                            isVisible = false
                            return@apply
                        }
                        clicks().subscribe {
                            viewModel.setAsDefaultSub(sub)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(
                                    onComplete = {
                                        makeSnackBar(parent, null, "${sub.address} is set as the default subreddit!").show()
                                        notifyDataSetChanged()
                                        popupWindow.dismiss()
                                    },
                                    onError = {
                                        makeSnackBar(
                                            parent,
                                            null,
                                            "Error: ${sub.address} could not be set as the default subreddit :(",
                                            SnackType.ERROR
                                        ).show()
                                    }
                                ).addTo(disposables)
                        }.addTo(disposables)
                    }

                // The window y offset calculation is done because the popup window would get off the screen when the list item is on the bottom
                val popupViewHeightPlusPadding =
                    if (removeFromYourSubsTextView.isVisible)
                        dpToPixel(3 * 36 + 40, c)
                    else
                        dpToPixel(2 * 36 + 40, c)
                popupWindow.showAsDropDown(listViewItem, 0, popupViewHeightPlusPadding * -1, Gravity.END)
            }.addTo(disposables)

        return listViewItem
    }
}