package com.b4kancs.rxredditdemo.drawer

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.model.Subreddit
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo

class DrawerListAdapter(
    private val c: Context,
    val subreddits: List<Subreddit>,
    val onClickCallback: (subreddit: Subreddit) -> Unit
) :
    ArrayAdapter<Subreddit>(c, R.layout.drawer_subreddit_list_item, subreddits) {

    private val disposables = CompositeDisposable()

    @SuppressLint("ViewHolder")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val sub = subreddits[position]

        val viewHolder = ViewHolder()
        val inflater = LayoutInflater.from(c)
        val listViewItem = inflater.inflate(R.layout.drawer_subreddit_list_item, parent, false)
        viewHolder.actionImageView = listViewItem.findViewById(R.id.subreddit_action_image_view)
        viewHolder.subredditTextView = listViewItem.findViewById(R.id.subreddit_name_text_view)
        viewHolder.optionsImageView = listViewItem.findViewById(R.id.subreddit_options_image_view)

        viewHolder.subredditTextView.text = sub.name

        viewHolder.subredditTextView.clicks()
            .subscribe { onClickCallback(sub) }
            .addTo(disposables)

        return listViewItem
    }

    inner class ViewHolder {
        lateinit var actionImageView: ImageView
        lateinit var subredditTextView: TextView
        lateinit var optionsImageView: ImageView
    }
}