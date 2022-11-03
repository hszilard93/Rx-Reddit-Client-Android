package com.b4kancs.rxredditdemo.ui.drawer

import android.content.Context
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.core.view.isVisible
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.database.SubredditDatabase
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.model.Subreddit.Status
import com.b4kancs.rxredditdemo.pagination.RedditJsonPagingSource
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
    private val onClickCallback: (subreddit: Subreddit) -> Unit,
    private val onActionClickedCallback: (subreddit: Subreddit) -> Subreddit,
    private val onOptionRemoveClickedCallback: (subreddit: Subreddit) -> Unit,
    private val onOptionDeleteClickedCallback: (subreddit: Subreddit) -> Unit,
    private val onMakeDefaultSubClickedCallback: (subreddit: Subreddit) -> Unit
) : ArrayAdapter<Subreddit>(c, R.layout.drawer_subreddit_list_item) {

    private val database: SubredditDatabase by inject(SubredditDatabase::class.java)
    private val rxSharedPreferences: RxSharedPreferences by inject(RxSharedPreferences::class.java)
    private val disposables = CompositeDisposable()
    private lateinit var subreddits: List<Subreddit>
    private lateinit var defaultSubreddit: Subreddit

    private val subredditComparator = Comparator<Subreddit> { a, b ->
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
        setUpInitialState()
    }

    private fun setUpInitialState() {
        val homeSubredditAddress = rxSharedPreferences.getString(
            RedditJsonPagingSource.DEFAULT_SUBREDDIT_PREFERENCE_KEY,
            RedditJsonPagingSource.DEFAULT_SUBREDDIT_PREFERENCE_VALUE
        ).get()
        defaultSubreddit = database.subredditDao().getSubredditByAddress(homeSubredditAddress)
            .subscribeOn(Schedulers.io())
            .onErrorResumeWith { RedditJsonPagingSource.defaultSubreddit }
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
        val shouldShowDefaultSubsHeader = subreddits.firstOrNull { it.status == Status.IN_DEFAULTS_LIST } != null
        return subreddits.size + if (shouldShowYourSubsHeader) 1 else 0 + if (shouldShowDefaultSubsHeader) 1 else 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(c)
        // Let's figure out if the view needs to be a header
        if (position == 0) {
            val headerViewItem = inflater.inflate(R.layout.drawer_header_list_view_item, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.subreddit_header_text_view)
            headerTextView.text = context.getString(R.string.drawer_header_your_subreddits)
            return headerViewItem
        }
        if (subreddits
                .count { it.status != Status.IN_DEFAULTS_LIST || it.status == Status.FAVORITED || it == defaultSubreddit } + 1 == position
        ) {
            val headerViewItem = inflater.inflate(R.layout.drawer_header_list_view_item, parent, false)
            val headerTextView = headerViewItem.findViewById<MaterialTextView>(R.id.subreddit_header_text_view)
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

        val listViewItem = inflater.inflate(R.layout.drawer_subreddit_list_item, parent, false)
        val actionImageView = listViewItem.findViewById<ImageView?>(R.id.subreddit_action_image_view)!!
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

        val subredditTextView: MaterialTextView = listViewItem.findViewById(R.id.subreddit_name_text_view)
        val optionsImageView: ImageView = listViewItem.findViewById(R.id.subreddit_options_image_view)

        subredditTextView.text = sub.name

        subredditTextView.clicks()
            .subscribe { onClickCallback(sub) }
            .addTo(disposables)

        actionImageView.clicks()
            .subscribe {
                onActionClickedCallback(sub)
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
                        if (sub.status == Status.IN_DEFAULTS_LIST || sub === defaultSubreddit)
                            visibility = View.GONE

                        clicks().subscribe {
                            onOptionRemoveClickedCallback(sub)
                            notifyDataSetChanged()
                            popupWindow.dismiss()
                        }.addTo(disposables)
                    }

                val deleteFromSubredditsTextView = popupView.findViewById<MaterialTextView>(R.id.option_delete_sub_text_view)
                    .apply {
                        if (sub === defaultSubreddit)
                            visibility = View.GONE

                        clicks().subscribe {
                            onOptionDeleteClickedCallback(sub)
                            notifyDataSetChanged()
                            popupWindow.dismiss()
                        }.addTo(disposables)
                    }

                val setAsDefaultSubTextView = popupView.findViewById<MaterialTextView>(R.id.option_set_default_sub_text_view)
                    .apply {
                        if (sub == defaultSubreddit) {
                            isVisible = false
                            return@apply
                        }
                        clicks().subscribe {
                            defaultSubreddit = Subreddit(sub.name, sub.address, Status.FAVORITED)
                            onMakeDefaultSubClickedCallback(defaultSubreddit)
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

        return listViewItem
    }
}