package com.b4kancs.rxredditdemo.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.database.SubredditDatabase
import com.b4kancs.rxredditdemo.databinding.ActivityMainBinding
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.networking.RedditJsonPagingSource
import com.b4kancs.rxredditdemo.ui.drawer.DrawerListAdapter
import com.b4kancs.rxredditdemo.ui.drawer.DrawerSearchListAdapter
import com.b4kancs.rxredditdemo.utils.ANIMATION_DURATION_LONG
import com.b4kancs.rxredditdemo.utils.animateViewLayoutHeightChange
import com.b4kancs.rxredditdemo.utils.dpToPixel
import com.b4kancs.rxredditdemo.utils.toV3Observable
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.jakewharton.rxbinding4.widget.queryTextChangeEvents
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOG_TAG = "MainActivity"
        private const val IS_ACTION_BAR_SHOWING_KEY = "isActionBarShowing"
        private const val IS_NAV_BAR_SHOWING_KEY = "isNavBarShowing"
    }

    val selectedSubredditChangedSubject = PublishSubject.create<Subreddit>()
    val subredditsChangedSubject = PublishSubject.create<Unit>()

    private val subredditDatabase: SubredditDatabase by inject()
    private val rxSharedPreferences: RxSharedPreferences by inject()
    private val disposables = CompositeDisposable()
    private val searchResultsChangedSubject = PublishSubject.create<List<Subreddit>>()
    private var isActionBarShowing = true   // The ActionBar's isShowing method didn't return the correct answer
    private var isNavBarShowing = true
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerListAdapter: DrawerListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            isActionBarShowing = it.getBoolean(IS_ACTION_BAR_SHOWING_KEY)
            isNavBarShowing = it.getBoolean(IS_NAV_BAR_SHOWING_KEY)
        }

        setUpDefaultSubredditSharedPreferences()
        binding = ActivityMainBinding.inflate(layoutInflater)
        with(binding) {
            setContentView(root)

            setSupportActionBar(toolbar)
            ActionBarDrawerToggle(this@MainActivity, drawerLayout, toolbar, R.string.app_name, R.string.app_name)

            val navController = findNavController(R.id.nav_host_fragment_activity_main)
            // Passing each menu ID as a set of Ids because each  menu should be considered as top level destinations.
            val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.navigation_subreddit, R.id.navigation_favorites, R.id.navigation_subscriptions
                ),
                drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            navView.setupWithNavController(navController)

            if (!isActionBarShowing) supportActionBar?.hide()
            if (!isNavBarShowing) navView.isVisible = false

            setUpSubredditDrawer()
            setUpDrawerSearchViewAndList()

            selectedSubredditChangedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    Observable.timer(300, TimeUnit.MILLISECONDS)
                        .subscribe {
                            drawerLayout.closeDrawer(GravityCompat.START)
                        }
                        .addTo(disposables)
                }

            drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerClosed(drawerView: View) {
                    drawerSearchView.setQuery("", true)
                }
            })
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START))
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        else
            super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(IS_ACTION_BAR_SHOWING_KEY, isActionBarShowing)
        outState.putBoolean(IS_NAV_BAR_SHOWING_KEY, isNavBarShowing)
    }

    private val genericOnClickCallback: (Subreddit) -> Unit = { sub ->
        selectedSubredditChangedSubject.onNext(sub)
    }

    private val genericOnActionClickedCallback: (Subreddit) -> Subreddit = { sub ->
        val newStatus = when (sub.status) {
            Subreddit.Status.NOT_IN_DB -> Subreddit.Status.IN_USER_LIST
            Subreddit.Status.IN_DEFAULTS_LIST -> Subreddit.Status.IN_USER_LIST
            Subreddit.Status.IN_USER_LIST -> Subreddit.Status.FAVORITED
            Subreddit.Status.FAVORITED -> Subreddit.Status.IN_USER_LIST
        }
        val newSub = Subreddit(sub.name, sub.address, newStatus, sub.nsfw)

        subredditDatabase.subredditDao().insertSubreddit(newSub)
            .subscribeOn(Schedulers.io())
            .subscribe()
            .addTo(disposables)
        subredditsChangedSubject.onNext(Unit)
        newSub
    }

    private fun setUpSubredditDrawer() {
        drawerListAdapter = DrawerListAdapter(
            this,
            onClickCallback = genericOnClickCallback,
            onActionClickedCallback = genericOnActionClickedCallback,
            onOptionRemoveClickedCallback = { sub ->
                val newSub = Subreddit(sub.name, sub.address, Subreddit.Status.IN_DEFAULTS_LIST)
                subredditDatabase.subredditDao().insertSubreddit(newSub)
                    .subscribeOn(Schedulers.io())
                    .subscribe()
                    .addTo(disposables)
            },
            onOptionDeleteClickedCallback = { sub ->
                subredditDatabase.subredditDao().deleteSubreddit(sub)
                    .subscribeOn(Schedulers.io())
                    .subscribe()
                    .addTo(disposables)
            },
            onMakeDefaultSubClickedCallback = { sub ->
                rxSharedPreferences
                    .getString(RedditJsonPagingSource.defaultSubredditPreferenceKey)
                    .set(sub.address)
                subredditDatabase.subredditDao().insertSubreddit(sub)
                    .subscribeOn(Schedulers.io())
                    .subscribe()
                    .addTo(disposables)
                Toast.makeText(this, "${sub.address} is set as the default subreddit!", Toast.LENGTH_SHORT).show()
            },
            subredditsChangedSubject = subredditsChangedSubject
        )

        binding.drawerListView.adapter = drawerListAdapter
    }

    private fun setUpDrawerSearchViewAndList() {
        var searchResults: List<Subreddit> = emptyList()
        val searchResultsChanged = PublishSubject.create<Unit>()

        binding.drawerSearchView.queryTextChangeEvents()
            .observeOn(AndroidSchedulers.mainThread())
            .debounce(250, TimeUnit.MILLISECONDS)
            .map {
                val keyword = it.queryText
                if (it.isSubmitted) {
                    Log.i(LOG_TAG, "Subreddit query text submitted: $keyword")
                    it.queryText.toString() to true
                } else {
                    Log.i(LOG_TAG, "Querying keyword: $keyword")
                    it.queryText.toString() to false
                }
            }
            .subscribe { (keyword, isSubmitted) ->
                if (isSubmitted) {
                    val subInDb = subredditDatabase.subredditDao().getSubreddits().blockingGet().firstOrNull { it.name == keyword }
                    if (subInDb != null) {
                        selectedSubredditChangedSubject.onNext(subInDb)
                    }
                    else {
                        selectedSubredditChangedSubject.onNext(
                            Subreddit(
                                keyword,
                                "r/$keyword",
                                Subreddit.Status.NOT_IN_DB
                            )
                        )
                    }
                } else {
                    if (keyword.isEmpty()) {
                        searchResults = emptyList()
                        searchResultsChanged.onNext(Unit)
                    } else {
                        val dbResultSingle = subredditDatabase.subredditDao().getSubredditsByNameLike("%${keyword}%")
                            .toObservable()
                            .subscribeOn(Schedulers.io())
                        val nwResultSingle = RedditJsonPagingSource.getSubredditsByKeyword(keyword)
                            .toObservable()
                            .subscribeOn(Schedulers.io())
                            .doOnError { Log.e(LOG_TAG, "Did not receive network response for query: $keyword") }
                            .onErrorComplete()
                            .startWith(Single.just(emptyList()))

                        Observable.combineLatest(
                            dbResultSingle,
                            nwResultSingle
                        ) { a: List<Subreddit>, b: List<Subreddit> -> a + b }
                            .subscribe { subs ->
                                subs.distinctBy { it.address.toLowerCase() }.let { distinctSubs ->
                                    searchResults = distinctSubs
                                    searchResultsChanged.onNext(Unit)
                                }
                            }.addTo(disposables)
                    }
                }
            }

        searchResultsChanged
            .subscribe {
                val results = searchResults.take(15)
                searchResultsChangedSubject
                    .onNext(results)
                binding.drawerSearchResultsListView
            }.addTo(disposables)

        val searchListAdapter = DrawerSearchListAdapter(
            this,
            searchResultsChangedSubject,
            onClickCallback = genericOnClickCallback,
            onActionClickedCallback = genericOnActionClickedCallback
        )

        searchResultsChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { subs ->
                val listView = binding.drawerSearchResultsListView
                val oldHeight = abs(listView.measuredHeight)
                val newHeight = dpToPixel(40, this) * subs.size
                animateViewLayoutHeightChange(listView, oldHeight, newHeight, 150)
            }.addTo(disposables)

        binding.drawerSearchResultsListView.adapter = searchListAdapter
    }

    private fun setUpDefaultSubredditSharedPreferences() {
        val defaultSubredditPreference = rxSharedPreferences.getString(
            RedditJsonPagingSource.defaultSubredditPreferenceKey,
            RedditJsonPagingSource.defaultSubreddit.address
        )

        defaultSubredditPreference.asObservable().toV3Observable()
            .subscribe { address ->
                try {
                    RedditJsonPagingSource.defaultSubreddit = subredditDatabase.subredditDao().getSubredditByAddress(address)
                        .subscribeOn(Schedulers.io())
                        .blockingGet()
                    Log.d(LOG_TAG, "$address is made the default subreddit!")
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Could not get subreddit by address $address from database.")
                }
            }
            .addTo(disposables)
    }

    fun lockDrawerClosed() {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    fun unlockDrawer() {
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    fun animateHideActionBar(viewToSynchronizeWith: View? = null) {
        val actionBar = supportActionBar!!
        if (isActionBarShowing) {
            binding.toolbar.animate()
                .translationY(actionBar.height * -1f)
                .setDuration(ANIMATION_DURATION_LONG)
                .withEndAction {
                    actionBar.hide()
                    isActionBarShowing = false
                }.start()

            // We also animate the position of the view below the ActionBar so that the whole thing doesn't jerk upwards suddenly
            // when the ActionBar's visibility is gone
            viewToSynchronizeWith?.let {
                it.animate()
                    .translationY(actionBar.height * -1f)
                    .setDuration(ANIMATION_DURATION_LONG)
                    .withEndAction {
                        it.y = 0f
                    }
                    .start()
            }
        }
    }

    fun animateShowActionBar() {
        val actionBar = supportActionBar!!
        if (!isActionBarShowing) {
            actionBar.show()
            isActionBarShowing = true
            binding.toolbar.animate()
                .translationY(0f)
                .setDuration(ANIMATION_DURATION_LONG)
                .start()
        }
    }

    fun animateHideBottomNavBar(viewToSynchronizeWith: View? = null) {
        if (isNavBarShowing) {
            binding.navView.let {
                it.animate()
                    .translationYBy(it.height.toFloat())
                    .setDuration(ANIMATION_DURATION_LONG)
                    .withEndAction {
                        it.isVisible = false
                        isNavBarShowing = false
                    }
                    .start()
            }

            viewToSynchronizeWith?.let {
                animateViewLayoutHeightChange(
                    it,
                    it.height,
                    it.height + binding.navView.height,
                    ANIMATION_DURATION_LONG,
                    endWithThis = {
                        it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                )
            }
        }
    }

    fun animateShowBottomNavBar() {
        if (!isNavBarShowing) {
            binding.navView.let {
                it.isVisible = true
                isNavBarShowing = true
                it.animate()
                    .translationYBy(it.height * -1f)
                    .setDuration(ANIMATION_DURATION_LONG)
                    .start()
            }
        }
    }
}