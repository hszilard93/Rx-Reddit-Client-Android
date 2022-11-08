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
import com.google.android.material.textview.MaterialTextView
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.Subject

class DrawerSearchListAdapter(
    private val c: Context,
    searchResultsChangedSubject: Subject<List<Subreddit>>,
    private val onClickCallback: (subreddit: Subreddit) -> Unit,
    private val onActionClickedCallback: (subreddit: Subreddit) -> Subreddit
) : ArrayAdapter<Subreddit>(c, R.layout.list_item_drawer_search) {

    val disposables = CompositeDisposable()
    private var subreddits = ArrayList<Subreddit>()

    init {
        searchResultsChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                subreddits = ArrayList()
                subreddits.addAll(it)
                notifyDataSetChanged()
            }.addTo(disposables)
    }

    override fun getCount(): Int = subreddits.size

    @SuppressLint("ViewHolder")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(c)
        val container = inflater.inflate(R.layout.list_item_drawer_search, parent, false)
        val subredditNameTextView: MaterialTextView = container.findViewById(R.id.text_view_drawer_list_item_action_search)
        val actionImageView: ImageView = container.findViewById(R.id.image_view_drawer_list_item_action_search)

        val sub: Subreddit = subreddits[position]
        subredditNameTextView.text = sub.address
        subredditNameTextView.clicks()
            .subscribe { onClickCallback(sub) }
            .addTo(disposables)

        if (sub.status == Subreddit.Status.FAVORITED)
            actionImageView.setImageResource(R.drawable.ic_baseline_star_filled_24)
        if (sub.status == Subreddit.Status.IN_USER_LIST)
            actionImageView.setImageResource(R.drawable.ic_baseline_star_border_24)

        actionImageView.clicks()
            .subscribe {
                val newSub = onActionClickedCallback(sub)
                subreddits[subreddits.indexOf(sub)] = newSub
                notifyDataSetChanged()
            }
            .addTo(disposables)

        return container
    }
}