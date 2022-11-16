package com.b4kancs.rxredditdemo.ui.drawer

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
import com.google.android.material.textview.MaterialTextView
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import logcat.LogPriority
import logcat.logcat

class DrawerSearchListAdapter(
    private val c: Context,
    private val viewModel: MainViewModel
) : ArrayAdapter<Subreddit>(c, R.layout.list_item_drawer_search) {

    val disposables = CompositeDisposable()
    private var subreddits = ArrayList<Subreddit>()

    init {
        logcat { "init" }
        viewModel.searchResultsChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { searchResults ->
                logcat(LogPriority.INFO) { "New searchresults: size = ${searchResults.size}; items = ${searchResults.map { it.name }}" }
                subreddits = ArrayList()
                subreddits.addAll(searchResults)
                notifyDataSetChanged()
            }.addTo(disposables)
    }

    override fun getCount(): Int = subreddits.size

    @SuppressLint("ViewHolder")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        logcat(LogPriority.VERBOSE) { "getView: position = $position" }

        val inflater = LayoutInflater.from(c)
        val container = inflater.inflate(R.layout.list_item_drawer_search, parent, false)
        val subredditNameTextView: MaterialTextView = container.findViewById(R.id.text_view_drawer_list_item_action_search)
        val actionImageView: ImageView = container.findViewById(R.id.image_view_drawer_list_item_action_search)

        val sub: Subreddit = subreddits[position]
        subredditNameTextView.text = sub.address
        subredditNameTextView.clicks()
            .doOnNext { "subredditNameTextView.clicks.onNext" }
            .subscribe { viewModel.selectedSubredditPublishSubject.onNext(sub) }
            .addTo(disposables)

        if (sub.status == Subreddit.Status.FAVORITED)
            actionImageView.setImageResource(R.drawable.ic_baseline_star_filled_24)
        if (sub.status == Subreddit.Status.IN_USER_LIST)
            actionImageView.setImageResource(R.drawable.ic_baseline_star_border_24)

        actionImageView.clicks()
            .doOnNext { "actionImageView.clicks.onNext" }
            .subscribe {
                val newSub = viewModel.changeSubredditStatus(sub)
                subreddits[subreddits.indexOf(sub)] = newSub
                notifyDataSetChanged()
            }
            .addTo(disposables)

        return container
    }
}