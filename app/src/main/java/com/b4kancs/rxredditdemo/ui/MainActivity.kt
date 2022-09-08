package com.b4kancs.rxredditdemo.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.b4kancs.rxredditdemo.R
import com.b4kancs.rxredditdemo.database.SubredditDatabase
import com.b4kancs.rxredditdemo.databinding.ActivityMainBinding
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.networking.RedditRssFeedPagingSource
import com.b4kancs.rxredditdemo.ui.drawer.DrawerListAdapter
import com.b4kancs.rxredditdemo.utils.toV3Observable
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOG_TAG = "MainActivity"
    }

    val subredditSelectedChangedSubject: PublishSubject<Subreddit> = PublishSubject.create()

    private val subredditDatabase: SubredditDatabase by inject()
    private val rxSharedPreferences: RxSharedPreferences by inject()
    private val disposables = CompositeDisposable()

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
        subredditSelectedChangedSubject
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                Observable.timer(500, TimeUnit.MILLISECONDS)
                    .subscribe {
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    .addTo(disposables)
            }
    }

    private fun setUpSubredditDrawer() {
        drawerListAdapter = DrawerListAdapter(
            this,
            onClickCallback = { sub ->
                subredditSelectedChangedSubject.onNext(sub)
            },
            onActionClickedCallback = { sub ->
                val newSub =
                    if (!sub.isInDefaultList && !sub.isFavorite)
                        Subreddit(sub.name, sub.address, isFavorite = true, isInDefaultList = false)
                    else
                        Subreddit(sub.name, sub.address, isFavorite = false, isInDefaultList = false)
                subredditDatabase.subredditDao().insertSubreddit(newSub)
                    .subscribeOn(Schedulers.io())
                    .subscribe()
                    .addTo(disposables)
                newSub
            },
            onOptionRemoveClickedCallback = { sub ->
                val newSub = Subreddit(sub.name, sub.address, false, isInDefaultList = true)
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
                    .getString(RedditRssFeedPagingSource.defaultSubredditPreferenceKey)
                    .set(sub.address)
                subredditDatabase.subredditDao().insertSubreddit(sub)
                    .subscribeOn(Schedulers.io())
                    .subscribe()
                    .addTo(disposables)
                Toast.makeText(this, "${sub.address} is set as the default subreddit!", Toast.LENGTH_SHORT).show()
            }
        )
        binding.drawerListView.adapter = drawerListAdapter
    }

    private fun setUpDefaultSubredditSharedPreferences() {
        val defaultSubredditPreference = rxSharedPreferences.getString(
            RedditRssFeedPagingSource.defaultSubredditPreferenceKey,
            RedditRssFeedPagingSource.defaultSubreddit.address
        )

        defaultSubredditPreference.asObservable().toV3Observable()
            .subscribe { address ->
                try {
                    RedditRssFeedPagingSource.defaultSubreddit = subredditDatabase.subredditDao().getSubredditByAddress(address)
                        .subscribeOn(Schedulers.io())
                        .blockingGet()
                    Log.d(LOG_TAG, "$address is made the default subreddit!")
                }
                catch (e: Exception) {
                    Log.e(LOG_TAG, "Could not get subreddit by address $address from database.")
                }
            }
            .addTo(disposables)

    }
}