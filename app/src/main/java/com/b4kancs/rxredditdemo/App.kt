package com.b4kancs.rxredditdemo

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.b4kancs.rxredditdemo.data.database.FavoritesRoomDatabase
import com.b4kancs.rxredditdemo.data.database.FollowsRoomDatabase
import com.b4kancs.rxredditdemo.data.database.SubredditRoomDatabase
import com.b4kancs.rxredditdemo.data.networking.RedditJsonHelper
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.domain.notification.SubscriptionsNotificationManager
import com.b4kancs.rxredditdemo.domain.notification.SubscriptionsNotificationScheduler
import com.b4kancs.rxredditdemo.domain.pagination.AggregateFeedLoader
import com.b4kancs.rxredditdemo.domain.pagination.SubscriptionsFeedLoader
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import com.b4kancs.rxredditdemo.repository.PostsPropertiesRepository
import com.b4kancs.rxredditdemo.repository.SubredditRepository
import com.b4kancs.rxredditdemo.ui.favorites.FavoritesViewModel
import com.b4kancs.rxredditdemo.ui.follows.FollowsViewModel
import com.b4kancs.rxredditdemo.ui.home.HomeViewModel
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerViewModel
import com.b4kancs.rxredditdemo.utils.toV3Observable
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class App : Application() {

    private val disposables = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()

        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)

        logcat(LogPriority.DEBUG) { "onCreate" }

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(makeKoinAppModule())
        }

        setUpReactiveTheme()
        setUpNotificationService()
    }

    private fun createRedditJsonServiceInstance(): RedditJsonService {
        logcat { "createRedditJsonServiceInstance" }

        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .followRedirects(true)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.reddit.com")
            .client(client)
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder()
                        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                        .create()
                )
            )
            .build()

        return retrofit.create(RedditJsonService::class.java)
    }

    private fun setUpReactiveTheme() {
        logcat { "setUpAppReactiveTheme" }
        val rxPreferences: RxSharedPreferences by inject()

        rxPreferences.getString("pref_list_theme", "auto").asObservable().toV3Observable()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { logcat(LogPriority.INFO) { "Device theme preference set to: $it" } }
            .subscribe { themePreference ->
                AppCompatDelegate.setDefaultNightMode(
                    when (themePreference) {
                        "auto" -> {
                            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                        "light" -> {
                            AppCompatDelegate.MODE_NIGHT_NO
                        }
                        "dark" -> {
                            AppCompatDelegate.MODE_NIGHT_YES
                        }
                        else -> {
                            logcat(LogPriority.ERROR) {
                                "$themePreference is not a valid argument for the theme preference! Setting preference to 'follow system'."
                            }
                            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                    }
                )
            }.addTo(disposables)
    }

    private fun setUpNotificationService() {
        logcat { "setUpNotificationService" }

        val notificationScheduler: SubscriptionsNotificationScheduler by inject()
        notificationScheduler.scheduleImmediateNotification()
//        notificationScheduler.checkForScheduledNotificationAndRescheduleIfMissingDelayElse()
    }

    private fun makeKoinAppModule() = module {
        single {
            logcat { "Koin providing application Context." }
            applicationContext
        }
        single {
            logcat { "Koin providing Single RedditJsonService instance." }
            createRedditJsonServiceInstance()
        }
        single {
            logcat { "Koin providing Single SubredditDatabase instance." }
            SubredditRoomDatabase.fetchDatabase(this@App)
        }
        single {
            logcat { "Koin providing Single FavoritesDatabase instance." }
            FavoritesRoomDatabase.fetchDatabase(this@App)
        }
        single {
            logcat { "Koin providing Single FollowsDatabase instance." }
            FollowsRoomDatabase.fetchDatabase(this@App)
        }
        single {
            logcat { "Koin providing Single RxSharedPreferences instance." }
            RxSharedPreferences.create(PreferenceManager.getDefaultSharedPreferences(this@App))
        }
        single {
            logcat { "Koin providing Single SubredditRepository instance." }
            SubredditRepository(get(), get(), get())
        }
        single {
            logcat { "Koin providing Single FavoritePostsRepository instance." }
            FavoritePostsRepository(get())
        }
        single {
            logcat { "Koin providing Single FollowsRepository instance." }
            FollowsRepository(get())
        }
        single {
            logcat { "Koin providing Single PostsPropertiesRepository instance." }
            PostsPropertiesRepository()
        }
        single {
            logcat { "Koin providing Single RedditJsonClient instance." }
            RedditJsonHelper(get())
        }
        single {
            logcat { "Koin providing Single AggregateFeedLoader instance." }
            AggregateFeedLoader(get(), get())
        }

        single {
            logcat { "Koin providing Single AggregateFeedLoader instance." }
            SubscriptionsFeedLoader(get(), get())
        }

        viewModel {
            logcat { "Koin providing ViewModel MainViewModel instance." }
            MainViewModel()
        }
        viewModel {
            logcat { "Koin providing ViewModel HomeViewModel instance." }
            HomeViewModel(get(), get(), get(), get(), get())
        }
        viewModel {
            logcat { "Koin providing ViewModel FavoritesViewModel instance." }
            FavoritesViewModel(get(), get(), get(), get())
        }
        viewModel {
            logcat { "Koin providing ViewModel FollowsViewModel instance." }
            FollowsViewModel(get(), get(), get(), get(), get(), get(), get())
        }
        viewModel {
            logcat { "Koin providing ViewModel PostViewerViewModel instance." }
            PostViewerViewModel(get(), get(), get(), get())
        }
        single {
            logcat { "Koin providing single AssetManager instance." }
            assets
        }
        single {
            SubscriptionsNotificationScheduler(get(), get(), get())
        }
        single {
            logcat { "Koin providing SubscriptionsNotificationManager instance." }
            SubscriptionsNotificationManager(get(), get())
        }
    }
}