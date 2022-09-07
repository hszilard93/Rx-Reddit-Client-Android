package com.b4kancs.rxredditdemo.ui.drawer

import android.annotation.SuppressLint
import android.content.Context
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.model.Subreddit
import com.google.android.material.textview.MaterialTextView
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo

class DrawerListAdapter(
    private val c: Context,
    val subreddits: List<Subreddit>,
    val onClickCallback: (subreddit: Subreddit) -> Unit,
    val onActionCallback: (subreddit: Subreddit) -> Subreddit
) :
    ArrayAdapter<Subreddit>(c, R.layout.drawer_subreddit_list_item, subreddits) {

    private val disposables = CompositeDisposable()
    private lateinit var favorited: List<Subreddit>
    private lateinit var notDefault: List<Subreddit>
    private lateinit var rest: List<Subreddit>

    init {
        calculateLists()
    }

    private fun calculateLists() {
        favorited = subreddits.filter { it.isFavorite }
        notDefault = subreddits.filter { !it.isDefault && !it.isFavorite }
        rest = subreddits - favorited.toSet() - notDefault.toSet()
    }

    // We have to headers for user added and default subreddits
    override fun getCount(): Int = subreddits.size + 2

    @SuppressLint("ViewHolder")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(c)

        if (position == 0) {
            if (favorited.isEmpty() && notDefault.isEmpty())
                return inflater.inflate(R.layout.drawer_empty_list_item, parent, false)
            val headerViewItem = inflater.inflate(R.layout.header_list_view_item, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.subreddit_header_text_view)
            headerTextView.text = "Your subreddits"
            return headerViewItem
        } else if (position == favorited.size + notDefault.size + 1) {
            if (rest.isEmpty())
                return inflater.inflate(R.layout.drawer_empty_list_item, parent, false)
            val headerViewItem = inflater.inflate(R.layout.header_list_view_item, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.subreddit_header_text_view)
            headerTextView.text = "Default subreddits"
            return headerViewItem
        }

        val sub: Subreddit =
            when (position) {
                in 1..favorited.size ->
                    // The sub's been favorited
                    favorited[position - 1]
                in (favorited.size + 1)..(favorited.size + 1 + notDefault.size) ->
                    // The sub's been not-defaulted (still appears under the 'your subs' header)
                    notDefault[position - favorited.size - 1]
                else ->
                    // The sub's under the default header
                    rest[position - favorited.size - notDefault.size - 2]
            }

        val viewHolder = ViewHolder()
        val listViewItem = inflater.inflate(R.layout.drawer_subreddit_list_item, parent, false)

        viewHolder.actionImageView = listViewItem.findViewById(R.id.subreddit_action_image_view)
        if (sub.isFavorite)
            viewHolder.actionImageView.setImageResource(R.drawable.ic_baseline_star_filled_24)
        else if (!sub.isDefault)
            viewHolder.actionImageView.setImageResource(R.drawable.ic_baseline_star_border_24)

        viewHolder.subredditTextView = listViewItem.findViewById(R.id.subreddit_name_text_view)
        viewHolder.optionsImageView = listViewItem.findViewById(R.id.subreddit_options_image_view)

        viewHolder.subredditTextView.text = sub.name

        viewHolder.subredditTextView.clicks()
            .subscribe { onClickCallback(sub) }
            .addTo(disposables)

        viewHolder.actionImageView.clicks()
            .subscribe {
                val newSub = onActionCallback(sub)
                when (sub) {
                    in rest -> {
                        rest = rest - sub
                        notDefault = (notDefault + newSub).sortedBy { it.name }
                    }
                    in notDefault -> {
                        notDefault = notDefault - sub
                        favorited = (favorited + newSub).sortedBy { it.name }
                    }
                    in favorited -> {
                        favorited = favorited - sub
                        notDefault = (notDefault + newSub).sortedBy { it.name }
                    }
                }
                notifyDataSetChanged()
            }
            .addTo(disposables)

        viewHolder.optionsImageView.clicks()
            .subscribe {
                val popupView = inflater.inflate(R.layout.popup_drawer_options_list, parent, false)
                val popupWindow = PopupWindow(
                    popupView,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    true
                )
                popupWindow.showAsDropDown(listViewItem, 0, 0, Gravity.RIGHT)
            }

        return listViewItem
    }

    inner class ViewHolder {
        lateinit var actionImageView: ImageView
        lateinit var subredditTextView: TextView
        lateinit var optionsImageView: ImageView
    }
}