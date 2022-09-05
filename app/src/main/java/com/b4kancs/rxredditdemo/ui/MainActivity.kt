package com.b4kancs.rxredditdemo.ui

import android.os.Bundle
import android.view.Gravity
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.database.SubredditDatabase
import com.b4kancs.rxredditdemo.databinding.ActivityMainBinding
import com.b4kancs.rxredditdemo.drawer.DrawerListAdapter
import com.b4kancs.rxredditdemo.model.Subreddit
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOG_TAG = "MainActivity"
    }

    val subredditSelectedChangedSubject = PublishSubject.create<Subreddit>()

    private val subredditDatabase: SubredditDatabase by inject()

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerListAdapter: DrawerListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        subredditSelectedChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                Observable.timer(500, TimeUnit.MILLISECONDS)
                    .subscribe {
                        binding.drawerLayout.closeDrawer(Gravity.LEFT)
                    }
            }
    }

    private fun setUpSubredditDrawer() {
        val listOfSubreddits = subredditDatabase.subredditDao().getSubreddits()
            .subscribeOn(Schedulers.io())
            .blockingGet()
        drawerListAdapter = DrawerListAdapter(this, listOfSubreddits) { sub ->
            subredditSelectedChangedSubject.onNext(sub)
        }
        binding.drawerListView.adapter = drawerListAdapter
    }
}