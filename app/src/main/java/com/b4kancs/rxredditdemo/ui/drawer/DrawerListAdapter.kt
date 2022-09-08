package com.b4kancs.rxredditdemo.ui.drawer

import android.content.Context
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.isVisible
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.networking.RedditRssFeedPagingSource
import com.b4kancs.rxredditdemo.utils.dpToPixel
import com.google.android.material.textview.MaterialTextView
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo

class DrawerListAdapter(
    private val c: Context,
    val subreddits: List<Subreddit>,
    val onClickCallback: (subreddit: Subreddit) -> Unit,
    val onActionClickedCallback: (subreddit: Subreddit) -> Subreddit,
    val onOptionRemoveClickedCallback: (subreddit: Subreddit) -> Unit,
    val onOptionDeleteClickedCallback: (subreddit: Subreddit) -> Unit,
    val onMakeDefaultSubClickedCallback: (subreddit: Subreddit) -> Unit
) :
    ArrayAdapter<Subreddit>(c, R.layout.drawer_subreddit_list_item, subreddits) {

    private val disposables = CompositeDisposable()
    private lateinit var favoritedSubs: List<Subreddit>
    private lateinit var userPickedSubs: List<Subreddit>
    private lateinit var defaultSubs: List<Subreddit>
    private lateinit var homeSubreddit: Subreddit

    private val favoritedComparator = Comparator<Subreddit> { a, b ->
        if (a === homeSubreddit) return@Comparator 1
        if (b === homeSubreddit) return@Comparator 1
        else return@Comparator a.name.compareTo(b.name)
    }

    init {
        calculateInitialLists()
    }

    private fun calculateInitialLists() {
        homeSubreddit = RedditRssFeedPagingSource.defaultSubreddit
        favoritedSubs = (subreddits.filter { it.isFavorite } + homeSubreddit).sortedWith(favoritedComparator)
        userPickedSubs = subreddits.filter { !it.isInDefaultList && !it.isFavorite }
        defaultSubs = subreddits - favoritedSubs.toSet() - userPickedSubs.toSet()
    }

    // We have two headers for user added and default subreddits; the original list size is also not a valid source of truth
    override fun getCount(): Int = favoritedSubs.size + userPickedSubs.size + defaultSubs.size + 2

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(c)

        if (position == 0) {
            if (favoritedSubs.isEmpty() && userPickedSubs.isEmpty())
                return inflater.inflate(R.layout.drawer_empty_list_item, parent, false)
            val headerViewItem = inflater.inflate(R.layout.header_list_view_item, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.subreddit_header_text_view)
            headerTextView.text = "Your subreddits"
            return headerViewItem
        } else if (position == favoritedSubs.size + userPickedSubs.size + 1) {
            if (defaultSubs.isEmpty())
                return inflater.inflate(R.layout.drawer_empty_list_item, parent, false)
            val headerViewItem = inflater.inflate(R.layout.header_list_view_item, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.subreddit_header_text_view)
            headerTextView.text = "Default subreddits"
            return headerViewItem
        }

        val sub: Subreddit =
            when (position) {
                in 1..favoritedSubs.size ->
                    // The sub's been favorited
                    favoritedSubs[position - 1]
                in (favoritedSubs.size + 1)..(favoritedSubs.size + 1 + userPickedSubs.size) ->
                    // The sub's been not-defaulted (still appears under the 'your subs' header)
                    userPickedSubs[position - favoritedSubs.size - 1]
                else ->
                    // The sub's under the default header
                    defaultSubs[position - favoritedSubs.size - userPickedSubs.size - 2]
            }

        val listViewItem = inflater.inflate(R.layout.drawer_subreddit_list_item, parent, false)
        val viewHolder = ViewHolder()
        with(viewHolder) {
            actionImageView = listViewItem.findViewById<ImageView?>(R.id.subreddit_action_image_view)
                .also {
                    when {
                        sub === homeSubreddit -> {
                            it.setImageResource(R.drawable.ic_baseline_star_filled_gold_24)
                            it.isEnabled = false
                        }
                        sub.isFavorite -> {
                            it.setImageResource(R.drawable.ic_baseline_star_filled_24)
                            it.isEnabled = true
                        }
                        !sub.isInDefaultList -> {
                            it.setImageResource(R.drawable.ic_baseline_star_border_24)
                            it.isEnabled = true
                        }
                    }
                }

            subredditTextView = listViewItem.findViewById(R.id.subreddit_name_text_view)
            optionsImageView = listViewItem.findViewById(R.id.subreddit_options_image_view)

            subredditTextView.text = sub.name

            subredditTextView.clicks()
                .subscribe { onClickCallback(sub) }
                .addTo(disposables)

            actionImageView.clicks()
                .subscribe {
                    val newSub = onActionClickedCallback(sub)
                    when (sub) {
                        in defaultSubs -> {
                            defaultSubs = defaultSubs - sub
                            userPickedSubs = (userPickedSubs + newSub).sortedBy { it.name }
                        }
                        in userPickedSubs -> {
                            userPickedSubs = userPickedSubs - sub
                            favoritedSubs = (favoritedSubs + newSub).sortedWith(favoritedComparator)
                        }
                        in favoritedSubs -> {
                            favoritedSubs = favoritedSubs - sub
                            userPickedSubs = (userPickedSubs + newSub).sortedBy { it.name }
                        }
                    }
                    notifyDataSetChanged()
                }
                .addTo(disposables)

            optionsImageView.clicks()
                .subscribe {
                    val popupView = inflater.inflate(R.layout.popup_drawer_options_list, parent, false)
                    val popupWindow = PopupWindow(
                        popupView,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        true
                    )

                    val removeFromYourSubsTextView = popupView.findViewById<MaterialTextView>(R.id.option_remove_your_text_view)
                        .apply {
                            if (sub in defaultSubs) {
//                                findViewById<MaterialDivider>(R.id.option_remove_your_divider).visibility = View.GONE
                                visibility = View.GONE
                            }
                            clicks().subscribe {
                                onOptionRemoveClickedCallback(sub)

                                val removedSub = Subreddit(sub.name, sub.address, isFavorite = false, isInDefaultList = true)
                                if (sub in favoritedSubs) favoritedSubs = favoritedSubs - sub
                                if (sub in userPickedSubs) userPickedSubs = userPickedSubs - sub
                                defaultSubs = (defaultSubs + removedSub).sortedBy { it.name }

                                notifyDataSetChanged()
                                popupWindow.dismiss()
                            }.addTo(disposables)
                        }

                    val deleteFromSubredditsTextView = popupView.findViewById<MaterialTextView>(R.id.option_delete_sub_text_view)
                        .apply {
                            clicks().subscribe {
                                onOptionDeleteClickedCallback(sub)
                                when (sub) {
                                    in favoritedSubs -> favoritedSubs = favoritedSubs - sub
                                    in userPickedSubs -> userPickedSubs = userPickedSubs - sub
                                    in defaultSubs -> defaultSubs = defaultSubs - sub
                                }
                                notifyDataSetChanged()
                                popupWindow.dismiss()
                            }.addTo(disposables)
                        }

                    val setAsDefaultSubTextView = popupView.findViewById<MaterialTextView>(R.id.option_set_default_sub_text_view)
                        .apply {
                            clicks().subscribe {
                                homeSubreddit = Subreddit(sub.name, sub.address, isFavorite = true, isInDefaultList = false)
                                onMakeDefaultSubClickedCallback(homeSubreddit)
                                when (sub) {
                                    in userPickedSubs -> userPickedSubs = userPickedSubs - sub
                                    in defaultSubs -> defaultSubs = defaultSubs - sub
                                    in favoritedSubs -> favoritedSubs = favoritedSubs - sub
                                }
                                favoritedSubs = (favoritedSubs + homeSubreddit).sortedWith(favoritedComparator)
                                notifyDataSetChanged()
                                popupWindow.dismiss()
                            }.addTo(disposables)
                        }

                    // The window y offset calculation is done because the popup window would get off the screen when the list item is on the bottom.
                    val popupViewHeightPlusPadding =
                        if (removeFromYourSubsTextView.isVisible)
                            dpToPixel(3 * 36 + 40, c)
                        else
                            dpToPixel(2 * 36 + 40, c)
                    popupWindow.showAsDropDown(listViewItem, 0, popupViewHeightPlusPadding * -1, Gravity.END)
                }.addTo(disposables)
        }
        return listViewItem
    }

    inner class ViewHolder {
        lateinit var actionImageView: ImageView
        lateinit var subredditTextView: TextView
        lateinit var optionsImageView: ImageView
    }
}