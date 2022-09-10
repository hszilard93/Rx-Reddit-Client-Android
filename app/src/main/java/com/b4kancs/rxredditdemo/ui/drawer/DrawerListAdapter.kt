package com.b4kancs.rxredditdemo.ui.drawer

import android.content.Context
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.isVisible
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.database.SubredditDatabase
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.networking.RedditRssFeedPagingSource
import com.b4kancs.rxredditdemo.utils.dpToPixel
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.google.android.material.textview.MaterialTextView
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import org.koin.java.KoinJavaComponent.inject

class DrawerListAdapter(
    private val c: Context,
    val onClickCallback: (subreddit: Subreddit) -> Unit,
    val onActionClickedCallback: (subreddit: Subreddit) -> Subreddit,
    val onOptionRemoveClickedCallback: (subreddit: Subreddit) -> Unit,
    val onOptionDeleteClickedCallback: (subreddit: Subreddit) -> Unit,
    val onMakeDefaultSubClickedCallback: (subreddit: Subreddit) -> Unit
) :
    ArrayAdapter<Subreddit>(c, R.layout.drawer_subreddit_list_item) {

    private val database: SubredditDatabase by inject(SubredditDatabase::class.java)
    private val rxSharedPreferences: RxSharedPreferences by inject(RxSharedPreferences::class.java)
    private val disposables = CompositeDisposable()
    private lateinit var subreddits: List<Subreddit>
    private lateinit var homeSubreddit: Subreddit

    private val subredditComparator = Comparator<Subreddit> { a, b ->
        if (a == homeSubreddit) return@Comparator -1
        if (b == homeSubreddit) return@Comparator 1
        if (a.isFavorite && b.isFavorite) return@Comparator a.name.compareTo(b.name)
        if (a.isFavorite && !b.isFavorite) return@Comparator -1
        if (b.isFavorite) return@Comparator 1
        if (a.isInDefaultList && b.isInDefaultList) return@Comparator a.name.compareTo(b.name)
        if (!a.isInDefaultList && b.isInDefaultList) return@Comparator -1
        if (!b.isInDefaultList) return@Comparator 1
        return@Comparator a.name.compareTo(b.name)
    }

    init {
        setUpInitialState()
    }

    private fun setUpInitialState() {
        val homeSubredditAddress = rxSharedPreferences.getString(
            RedditRssFeedPagingSource.defaultSubredditPreferenceKey,
            RedditRssFeedPagingSource.defaultSubredditPreferenceValue
        ).get()
        homeSubreddit = database.subredditDao().getSubredditByAddress(homeSubredditAddress)
            .subscribeOn(Schedulers.io())
            .onErrorResumeWith { RedditRssFeedPagingSource.defaultSubreddit }
            .blockingGet()

        subreddits = database.subredditDao().getSubreddits()
            .subscribeOn(Schedulers.io())
            .blockingGet()
            .sortedWith(subredditComparator)
    }

    override fun notifyDataSetChanged() {
        setUpInitialState()
        super.notifyDataSetChanged()
    }

    // We have two headers for user added and default subreddits that we need to take into account
    override fun getCount(): Int {
        val shouldShowYourSubsHeader = true
        val shouldShowDefaultSubsHeader = subreddits.firstOrNull { it.isInDefaultList } != null
        return subreddits.size + if (shouldShowYourSubsHeader) 1 else 0 + if (shouldShowDefaultSubsHeader) 1 else 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(c)
        // Let's figure out if the view needs to be a header
        if (position == 0) {
            val headerViewItem = inflater.inflate(R.layout.drawer_header_list_view_item, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.subreddit_header_text_view)
            headerTextView.text = context.getString(R.string.your_subreddits_header)
            return headerViewItem
        }
        if (subreddits.count { !it.isInDefaultList || it.isFavorite || it == homeSubreddit } + 1 == position) {
            val headerViewItem = inflater.inflate(R.layout.drawer_header_list_view_item, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.subreddit_header_text_view)
            headerTextView.text = context.getString(R.string.default_subreddits_header)
            return headerViewItem
        }

        // The view needs to be a subreddit list item
        val pos =
            if (position in 1 .. subreddits.count { !it.isInDefaultList || it.isFavorite || it == homeSubreddit })
                position - 1
            else
                position - 2

        val sub = subreddits[pos]

        val listViewItem = inflater.inflate(R.layout.drawer_subreddit_list_item, parent, false)
        val viewHolder = ViewHolder()
        with(viewHolder) {
            actionImageView = listViewItem.findViewById<ImageView?>(R.id.subreddit_action_image_view)!!
                .also {
                    when {
                        sub == homeSubreddit -> {
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
                    onActionClickedCallback(sub)
//                    subreddits = (subreddits - sub + newSub).sortedWith(subredditComparator)
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
                            if (sub.isInDefaultList || sub === homeSubreddit)
                                visibility = View.GONE

                            clicks().subscribe {
                                onOptionRemoveClickedCallback(sub)
                                notifyDataSetChanged()
                                popupWindow.dismiss()
                            }.addTo(disposables)
                        }

                    val deleteFromSubredditsTextView = popupView.findViewById<MaterialTextView>(R.id.option_delete_sub_text_view)
                        .apply {
                            if (sub === homeSubreddit)
                                visibility = View.GONE

                            clicks().subscribe {
                                onOptionDeleteClickedCallback(sub)
                                notifyDataSetChanged()
                                popupWindow.dismiss()
                            }.addTo(disposables)
                        }

                    val setAsDefaultSubTextView = popupView.findViewById<MaterialTextView>(R.id.option_set_default_sub_text_view)
                        .apply {
                            clicks().subscribe {
                                homeSubreddit = Subreddit(sub.name, sub.address, isFavorite = true, isInDefaultList = false)
                                onMakeDefaultSubClickedCallback(homeSubreddit)
                                notifyDataSetChanged()
                                popupWindow.dismiss()
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
        }
        return listViewItem
    }

    inner class ViewHolder {
        lateinit var actionImageView: ImageView
        lateinit var subredditTextView: TextView
        lateinit var optionsImageView: ImageView
    }
}