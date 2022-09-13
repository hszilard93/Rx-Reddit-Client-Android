package com.b4kancs.rxredditdemo.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
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
import io.reactivex.rxjava3.kotlin.toObservable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOG_TAG = "MainActivity"
    }

    val selectedSubredditChangedSubject = PublishSubject.create<Subreddit>()
    val subredditsChangedSubject = PublishSubject.create<Unit>()

    private val subredditDatabase: SubredditDatabase by inject()
    private val rxSharedPreferences: RxSharedPreferences by inject()
    private val disposables = CompositeDisposable()
    private val searchResultsChangedSubject = PublishSubject.create<List<Subreddit>>()

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerListAdapter: DrawerListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUpDefaultSubredditSharedPreferences()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView = binding.navView

        setSupportActionBar(binding.toolbar)
        ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, R.string.app_name, R.string.app_name)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each  menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_subreddit, R.id.navigation_favorites, R.id.navigation_subscriptions
            ),
            binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        setUpSubredditDrawer()

        setUpDrawerSearchViewAndList()

        selectedSubredditChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                Observable.timer(400, TimeUnit.MILLISECONDS)
                    .subscribe {
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    .addTo(disposables)
            }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                binding.drawerSearchView.setQuery("", true)
            }
        })
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
            .map { it.queryText.toString() }
            .subscribe { keyword ->
                if (keyword.isEmpty()) {
                    searchResults = emptyList()
                    searchResultsChanged.onNext(Unit)
                }
                else {
                    val dbResultSingle = subredditDatabase.subredditDao().getSubredditsByNameLike("%${keyword}%")
                        .toObservable()
                        .subscribeOn(Schedulers.io())
                    val nwResultSingle = RedditJsonPagingSource.getSubredditsByKeyword(keyword)
                        .toObservable()
                        .subscribeOn(Schedulers.io())
                        .doOnError { Log.e(LOG_TAG, "Did not receive network response for query: $keyword") }
                        .onErrorComplete()
                        .startWith(Single.just(emptyList()))

                    Observable.combineLatest(dbResultSingle, nwResultSingle, BiFunction { a: List<Subreddit>, b: List<Subreddit> -> a + b })
                        .subscribe { subs ->
                            subs.distinctBy { it.address.toLowerCase() }.let { distinctSubs ->
                                searchResults = distinctSubs
                                searchResultsChanged.onNext(Unit)
                            }
                        }.addTo(disposables)
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
}