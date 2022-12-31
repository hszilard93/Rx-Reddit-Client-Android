package com.b4kancs.rxredditdemo.ui.main

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.databinding.ActivityMainBinding
import com.b4kancs.rxredditdemo.ui.drawer.DrawerListAdapter
import com.b4kancs.rxredditdemo.ui.drawer.DrawerSearchListAdapter
import com.b4kancs.rxredditdemo.ui.follows.FollowsFragment
import com.b4kancs.rxredditdemo.ui.uiutils.ANIMATION_DURATION_LONG
import com.b4kancs.rxredditdemo.ui.uiutils.ANIMATION_DURATION_SHORT
import com.b4kancs.rxredditdemo.ui.uiutils.animateViewHeightChange
import com.b4kancs.rxredditdemo.ui.uiutils.dpToPixel
import com.jakewharton.rxbinding4.widget.queryTextChangeEvents
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import logcat.LogPriority
import logcat.logcat
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    val viewModel: MainViewModel by viewModel()
    private val disposables = CompositeDisposable()
    var menu: Menu? = null
    private lateinit var binding: ActivityMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        logcat { "onCreate viewModel = $viewModel" }
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.fragment_main_nav_host)
        setUpActionBar(navController)
        setUpActionBarAndNavigation(navController)
        setUpSubredditDrawer()
        setupSearchViewDrawerAndList()
    }

    private fun setUpActionBar(navController: NavController) {
        with(binding) {
            setSupportActionBar(toolbarMain)
            ActionBarDrawerToggle(this@MainActivity, drawerMain, toolbarMain, R.string.app_name, R.string.app_name)

            // Passing the subreddits and follows as top level nav destinations because we don't want to deal with the back arrow.
            val appBarConfiguration = AppBarConfiguration(
                setOf(R.id.navigation_subreddit, R.id.navigation_follows),
                drawerMain
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
        }
    }

    private fun setUpActionBarAndNavigation(navController: NavController) {
        with(binding) {
            bottomNavViewMain.setupWithNavController(navController)
            if (!viewModel.isActionBarShowing) supportActionBar?.hide()
            if (!viewModel.isNavBarShowing) bottomNavViewMain.isVisible = false

            // Set up bottom nav bar behaviour.
            bottomNavViewMain.setOnItemReselectedListener { }   // If a nav item is reselected, do nothing.

            bottomNavViewMain.setOnItemSelectedListener { menuItem ->
                val navOptionsWithFadeAnimation = navOptions { // Use the Kotlin DSL for building NavOptions
                    anim {
                        enter = android.R.animator.fade_in
                        exit = android.R.animator.fade_out
                    }
                }
                when (menuItem.itemId) {
                    R.id.navigation_subreddit -> {
                        // If the user clicks on a navigation button not belonging to the current fragment, reset the nav graph.
                        navController.setGraph(R.navigation.mobile_navigation)
                        // In this case, because this is the 'home fragment' and present in the graph by default,
                        // we need to pop it to get the expected behaviour (pressing back will exit the app).
                        navController.popBackStack()
                        navController.navigate(R.id.navigation_subreddit, null, navOptionsWithFadeAnimation)
                        true
                    }
                    R.id.navigation_favorites -> {
                        // If the user clicks on a navigation button not belonging to the current fragment, reset the nav graph.
                        navController.setGraph(R.navigation.mobile_navigation)
                        navController.navigate(R.id.navigation_favorites, null, navOptionsWithFadeAnimation)
                        true
                    }
                    R.id.navigation_follows -> {
                        // If the user clicks on a navigation button not belonging to the current fragment, reset the nav graph.
                        navController.setGraph(R.navigation.mobile_navigation)
                        navController.navigate(R.id.navigation_follows, null, navOptionsWithFadeAnimation)
                        true
                    }
                    else -> {
                        throw IllegalStateException("${menuItem.itemId} is not a valid menu item!")
                    }
                }
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar_options, menu)
        MenuCompat.setGroupDividerEnabled(menu!!, true)
        this.menu = menu
        return true
    }


    override fun onBackPressed() {
        logcat(LogPriority.INFO) { "onBackPressed" }

        if (binding.drawerMain.isDrawerOpen(GravityCompat.START)) {
            logcat(LogPriority.INFO) { "Closing drawer." }
            binding.drawerMain.closeDrawer(GravityCompat.START)
            return
        }

//        val navController = findNavController(R.id.fragment_main_nav_host)
//        val currentFragment = supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.fragments?.first()
//        if (currentFragment is FollowsFragment) {
//            navController.navigateUp()
//        }
//        else
            super.onBackPressed()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        logcat { "onSaveInstanceState" }
        super.onSaveInstanceState(outState)
    }


    private fun setUpSubredditDrawer() {
        logcat { "setUpSubredditDrawer" }
        val adapter = DrawerListAdapter(this, viewModel)
        binding.listViewDrawerSubreddits.adapter = adapter

        viewModel.getSubredditsChangedSubject()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { adapter.notifyDataSetChanged() }
            .addTo(disposables)

        viewModel.selectedSubredditPublishSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                // I think this small delay before triggering the closing animation of the drawer
                // gives a better "feel" that something is happening.
                Observable.timer(300, TimeUnit.MILLISECONDS)
                    .subscribe {
                        binding.drawerMain.closeDrawer(GravityCompat.START)
                    }
                    .addTo(disposables)
            }.addTo(disposables)
    }


    private fun setupSearchViewDrawerAndList() {
        logcat { "setupSearchViewDrawerAndList" }

        with(binding) {
            searchViewDrawer.queryTextChangeEvents()
                .observeOn(AndroidSchedulers.mainThread())
                .debounce(250, TimeUnit.MILLISECONDS)
                .map {
                    val query = it.queryText
                    if (it.isSubmitted) {
                        logcat(LogPriority.INFO) { "Subreddit query text submitted: $query" }
                        it.queryText.toString() to true
                    }
                    else {
                        logcat(LogPriority.INFO) { "Querying keyword: $query" }
                        it.queryText.toString() to false
                    }
                }
                .subscribe { (query, isSubmitted) ->
                    logcat { "queryTextChangeEvents.subscribe: query $query isSubmitted $isSubmitted" }
                    // If isSubmitted is true, it means that the Search button was clicked. In this case, we should go straight to the subreddit entered.
                    if (isSubmitted) {
                        viewModel.goToSubredditByName(query)
                    }
                    // Otherwise, show the query search results in a list.
                    else {
                        viewModel.getSearchResultsFromDbAndNw(query)
                            .subscribe { searchResultSubs ->
                                viewModel.searchResultsChangedSubject.onNext(searchResultSubs)
                            }
                            .addTo(disposables)
                    }
                }
                .addTo(disposables)

            val searchListAdapter = DrawerSearchListAdapter(this@MainActivity, viewModel)

            viewModel.searchResultsChangedSubject
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { subs ->
                    // Setting the height of the list of the search results according to the available and the desired height.
                    fun redrawSearchResultsList() {
                        logcat { "drawSearchResults" }
                        val listView = listViewDrawerSearchResults
                        val oldHeight = abs(listView.measuredHeight)
                        val desiredHeight = dpToPixel(40, this@MainActivity) * subs.size

                        val listLocationInWindowArray = IntArray(2)
                        listViewDrawerSearchResults.getLocationInWindow(listLocationInWindowArray)
                        val drawerLocationInWindowsArray = IntArray(2)
                        linearMainDrawerOuter.getLocationInWindow(drawerLocationInWindowsArray)
                        // For example, drawer height is 500, it's Y is 24 (because of the status bar), search list's Y is 150.
                        // The correct max height for the search results' list will be 500 + 24 - 150.
                        val maxHeight =
                            linearMainDrawerOuter.measuredHeight + drawerLocationInWindowsArray[1] - listLocationInWindowArray[1]

                        logcat { "Redrawing search results ListView. desiredHeight = $desiredHeight; maxHeight = $maxHeight" }
                        val newHeight = min(desiredHeight, maxHeight)
                        animateViewHeightChange(listView, oldHeight, newHeight, ANIMATION_DURATION_SHORT)
                    }
                    redrawSearchResultsList()
                    // Keyboard layout animation listener
                    ViewCompat.setWindowInsetsAnimationCallback(listViewDrawerSearchResults,
                        object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                            override fun onProgress(
                                    insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>
                            ): WindowInsetsCompat = insets

                            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                                super.onEnd(animation)
                                if (drawerMain.isDrawerVisible(linearMainDrawerOuter)) redrawSearchResultsList()
                            }
                        })
                }
                .addTo(disposables)

            listViewDrawerSearchResults.adapter = searchListAdapter

            drawerMain.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerClosed(drawerView: View) {
                    searchViewDrawer.setQuery("", true)
                }
            })
        }
    }


    fun lockDrawerClosed() {
        logcat { "lockDrawerClosed" }
        binding.drawerMain.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        setNavIconBackPressedBehaviour()
    }

    fun unlockDrawer() {
        logcat { "unlockDrawer" }
        binding.drawerMain.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        setNavIconDrawerBehaviour()
    }


    fun animateHideActionBar(viewToSynchronizeWith: View? = null) {
        logcat { "animateHideActionBar" }
        if (!viewModel.isActionBarShowing) return

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
                viewModel.isActionBarShowing = false
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
        if (viewModel.isActionBarShowing) return

        val actionBar = supportActionBar
        if (actionBar == null) {
            logcat(LogPriority.WARN) { "supportActionBar not found" }
            return
        }

        if (!viewModel.isActionBarShowing) {
            actionBar.show()
            viewModel.isActionBarShowing = true
            binding.toolbarMain.animate()
                .translationY(0f)
                .setDuration(ANIMATION_DURATION_LONG)
                .start()
        }
    }


    fun animateHideBottomNavBar(viewToSynchronizeWith: View? = null) {
        logcat { "animateHideBottomNavBar" }
        if (!viewModel.isNavBarShowing) return

        binding.bottomNavViewMain.let {
            it.animate()
                .translationYBy(it.height.toFloat())
                .setDuration(ANIMATION_DURATION_LONG)
                .withEndAction {
                    it.isVisible = false
                    viewModel.isNavBarShowing = false
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
        if (viewModel.isNavBarShowing) return

        binding.bottomNavViewMain.let {
            it.isVisible = true
            viewModel.isNavBarShowing = true
            it.animate()
                .translationYBy(it.height * -1f)
                .setDuration(ANIMATION_DURATION_LONG)
                .start()
        }
    }


    private fun setNavIconBackPressedBehaviour() {
        logcat { "setAlternativeNavigationBehaviour" }
//        binding.toolbarMain.navigationIcon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_arrow_back_24, theme)
        binding.toolbarMain.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setNavIconDrawerBehaviour() {
        logcat { "resetNavigationBehaviour" }
        binding.toolbarMain.setNavigationOnClickListener { binding.drawerMain.open() }
    }
}