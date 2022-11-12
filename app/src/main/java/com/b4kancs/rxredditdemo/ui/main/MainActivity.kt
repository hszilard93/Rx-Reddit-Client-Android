package com.b4kancs.rxredditdemo.ui.main

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.ActivityMainBinding
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.pagination.RedditJsonPagingSource
import com.b4kancs.rxredditdemo.ui.drawer.DrawerListAdapter
import com.b4kancs.rxredditdemo.ui.drawer.DrawerSearchListAdapter
import com.b4kancs.rxredditdemo.ui.uiutils.ANIMATION_DURATION_LONG
import com.b4kancs.rxredditdemo.ui.uiutils.animateViewHeightChange
import com.b4kancs.rxredditdemo.ui.uiutils.dpToPixel
import com.b4kancs.rxredditdemo.utils.*
import com.jakewharton.rxbinding4.widget.queryTextChangeEvents
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val IS_ACTION_BAR_SHOWING_KEY = "isActionBarShowing"
        private const val IS_NAV_BAR_SHOWING_KEY = "isNavBarShowing"
    }

    val viewModel: MainViewModel by viewModel()
    private val disposables = CompositeDisposable()
    var menu: Menu? = null
    private var isActionBarShowing = true   // The ActionBar's isShowing method didn't return the correct answer
    private var isNavBarShowing = true
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerListAdapter: DrawerListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        logcat { "onCreate" }
        super.onCreate(savedInstanceState)

        savedInstanceState?.let {
            isActionBarShowing = it.getBoolean(IS_ACTION_BAR_SHOWING_KEY)
            isNavBarShowing = it.getBoolean(IS_NAV_BAR_SHOWING_KEY)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        with(binding) {
            setContentView(root)

            setSupportActionBar(toolbarMain)
            ActionBarDrawerToggle(this@MainActivity, drawerMain, toolbarMain, R.string.app_name, R.string.app_name)

            val navController = findNavController(R.id.fragment_main_nav_host)
            // Passing each menu ID as a set of Ids because each  menu should be considered as top level destinations.
            val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.navigation_subreddit, R.id.navigation_favorites, R.id.navigation_subscriptions
                ),
                drawerMain
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            bottomNavViewMain.setupWithNavController(navController)

            if (!isActionBarShowing) supportActionBar?.hide()
            if (!isNavBarShowing) bottomNavViewMain.isVisible = false

            setUpSubredditDrawer()
            setupSearchViewDrawerAndList()

            viewModel.selectedSubredditChangedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    Observable.timer(300, TimeUnit.MILLISECONDS)
                        .subscribe {
                            drawerMain.closeDrawer(GravityCompat.START)
                        }
                        .addTo(disposables)
                }.addTo(disposables)

            drawerMain.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerClosed(drawerView: View) {
                    searchViewDrawer.setQuery("", true)
                }
            })
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar_options, menu)
        MenuCompat.setGroupDividerEnabled(menu!!, true)
        this.menu = menu
        return true
    }

    override fun onBackPressed() {
        logcat { "onBackPressed" }

        if (binding.drawerMain.isDrawerOpen(GravityCompat.START))
            binding.drawerMain.closeDrawer(GravityCompat.START)
        else
            super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        logcat { "onSaveInstanceState" }
        super.onSaveInstanceState(outState)

        outState.putBoolean(IS_ACTION_BAR_SHOWING_KEY, isActionBarShowing)
        outState.putBoolean(IS_NAV_BAR_SHOWING_KEY, isNavBarShowing)
    }

    private val genericOnClickCallback: (Subreddit) -> Unit = { sub ->
        logcat { "genericOnClickedCallback: ${sub.name}" }
    }

    private fun setUpSubredditDrawer() {
        logcat { "setUpSubredditDrawer" }
        drawerListAdapter = DrawerListAdapter(
            this,
            viewModel
        )

        binding.listViewDrawerSubreddits.adapter = drawerListAdapter

        viewModel.subredditsChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { drawerListAdapter.notifyDataSetChanged() }
            .addTo(disposables)
    }

    private fun setupSearchViewDrawerAndList() {
        logcat { "setupSearchViewDrawerAndList" }
        var searchResults: List<Subreddit> = emptyList()
        val searchResultsChanged = PublishSubject.create<Unit>()

        binding.searchViewDrawer.queryTextChangeEvents()
            .observeOn(AndroidSchedulers.mainThread())
            .debounce(250, TimeUnit.MILLISECONDS)
            .map {
                val keyword = it.queryText
                if (it.isSubmitted) {
                    logcat(LogPriority.INFO) { "Subreddit query text submitted: $keyword" }
                    it.queryText.toString() to true
                } else {
                    logcat(LogPriority.INFO) { "Querying keyword: $keyword" }
                    it.queryText.toString() to false
                }
            }
            .subscribe { (keyword, isSubmitted) ->
                logcat { "queryTextChangeEvents.subscribe: keyword $keyword isSubmitted $isSubmitted" }
                // If isSubmitted is true, it means that the Search button was clicked. In this case, we should go straight to the subreddit entered.
                if (isSubmitted) {
                    val subInDb = viewModel.getSubredditFromDbByName(keyword).blockingGet()
                    if (subInDb != null) {
                        viewModel.selectedSubredditChangedSubject.onNext(subInDb)
                    } else {
                        viewModel.selectedSubredditChangedSubject.onNext(
                            Subreddit(
                                keyword,
                                "r/$keyword",
                                Subreddit.Status.NOT_IN_DB
                            )
                        )
                    }
                }
                // Otherwise, show the query search results in a list.
                else {
                    if (keyword.isEmpty()) {
                        searchResults = emptyList()
                        searchResultsChanged.onNext(Unit)
                    } else {
                        val dbResultObservable = viewModel.getSubredditsFromDbByNameLike(keyword)
                            .toObservable()

                        val nwResultObservable = viewModel.getSubredditsFromNetworkByNameLike(keyword)
                            .toObservable()
                            .doOnError { logcat(LogPriority.ERROR) { "Did not receive a network response for query: $keyword" } }
                            .onErrorComplete()
                            .startWith(Single.just(emptyList()))

                        Observable.combineLatest(
                            dbResultObservable,
                            nwResultObservable
                        ) { a: List<Subreddit>, b: List<Subreddit> -> a + b }
                            .subscribe { subs ->
                                subs.distinctBy { it.address.lowercase() }.let { distinctSubs ->
                                    searchResults = distinctSubs
                                    searchResultsChanged.onNext(Unit)
                                }
                            }.addTo(disposables)
                    }
                }
            }
            .addTo(disposables)

        searchResultsChanged
            .subscribe {
                val results = searchResults.take(15)
                viewModel.searchResultsChangedSubject
                    .onNext(results)
                binding.listViewDrawerSearchResults
            }.addTo(disposables)

        val searchListAdapter = DrawerSearchListAdapter(
            this,
            viewModel
        )

        viewModel.subredditsChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { searchListAdapter.notifyDataSetChanged() }
            .addTo(disposables)

        viewModel.searchResultsChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { subs ->
                logcat { "searchResultsChanged: ${subs.map { it.name }}" }
                val listView = binding.listViewDrawerSearchResults
                val oldHeight = abs(listView.measuredHeight)
                val newHeight = dpToPixel(40, this) * subs.size
                animateViewHeightChange(listView, oldHeight, newHeight, 150)
            }
            .addTo(disposables)

        binding.listViewDrawerSearchResults.adapter = searchListAdapter
    }

    fun lockDrawerClosed() {
        logcat { "lockDrawerClosed" }
        binding.drawerMain.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        setAlternativeNavigationBehaviour()
    }

    fun unlockDrawer() {
        logcat { "unlockDrawer" }
        binding.drawerMain.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        resetNavigationBehaviour()
    }

    fun animateHideActionBar(viewToSynchronizeWith: View? = null) {
        logcat { "animateHideActionBar" }
        if (!isActionBarShowing) return

        val actionBar = supportActionBar
        if (actionBar == null) {
            logcat(LogPriority.WARN) { "supportActionBar not found" }
            return
        }

        binding.toolbarMain.animate()
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

    fun animateShowActionBar() {
        logcat { "animateShowActionBar" }
        if (isActionBarShowing) return

        val actionBar = supportActionBar
        if (actionBar == null) {
            logcat(LogPriority.WARN) { "supportActionBar not found" }
            return
        }

        if (!isActionBarShowing) {
            actionBar.show()
            isActionBarShowing = true
            binding.toolbarMain.animate()
                .translationY(0f)
                .setDuration(ANIMATION_DURATION_LONG)
                .start()
        }
    }

    fun animateHideBottomNavBar(viewToSynchronizeWith: View? = null) {
        logcat { "animateHideBottomNavBar" }
        if (!isNavBarShowing) return

        binding.bottomNavViewMain.let {
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
            animateViewHeightChange(
                it,
                it.height,
                it.height + binding.bottomNavViewMain.height,
                ANIMATION_DURATION_LONG,
                endWithThis = {
                    it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            )
        }
    }

    fun animateShowBottomNavBar() {
        logcat { "animateShowBottomNavBar" }
        if (isNavBarShowing) return

        binding.bottomNavViewMain.let {
            it.isVisible = true
            isNavBarShowing = true
            it.animate()
                .translationYBy(it.height * -1f)
                .setDuration(ANIMATION_DURATION_LONG)
                .start()
        }
    }

    private fun setAlternativeNavigationBehaviour() {
        logcat { "hideNavigationIcon" }
        binding.toolbarMain.navigationIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_arrow_back_24, theme)
        binding.toolbarMain.setNavigationOnClickListener { onBackPressed() }
    }

    private fun resetNavigationBehaviour() {
        logcat { "hideNavigationIcon" }
        binding.toolbarMain.setNavigationOnClickListener { binding.drawerMain.open() }
    }
}