package com.b4kancs.rxredditdemo

import android.app.Application
import androidx.preference.PreferenceManager
import com.b4kancs.rxredditdemo.data.database.FavoritesRoomDatabase
import com.b4kancs.rxredditdemo.data.database.FollowsRoomDatabase
import com.b4kancs.rxredditdemo.data.database.SubredditRoomDatabase
import com.b4kancs.rxredditdemo.data.networking.RedditJsonClient
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.repository.FollowsRepository
import com.b4kancs.rxredditdemo.repository.SubredditRepository
import com.b4kancs.rxredditdemo.ui.favorites.FavoritesViewModel
import com.b4kancs.rxredditdemo.ui.home.HomeViewModel
import com.b4kancs.rxredditdemo.ui.main.MainViewModel
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerViewModel
import com.b4kancs.rxredditdemo.ui.follows.FollowsViewModel
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class App : Application() {

    private val appModule = module {
        single {
            logcat { "Koin providing Single RedditJsonService instant." }
            createRedditJsonServiceInstant()
        }
        single {
            logcat { "Koin providing Single SubredditDatabase instant." }
            SubredditRoomDatabase.fetchDatabase(this@App)
        }
        single {
            logcat { "Koin providing Single FavoritesDatabase instant." }
            FavoritesRoomDatabase.fetchDatabase(this@App)
        }
        single {
            logcat { "Koin providing Single FollowsDatabase instant." }
            FollowsRoomDatabase.fetchDatabase(this@App)
        }
        single {
            logcat { "Koin providing Single RxSharedPreferences instant." }
            RxSharedPreferences.create(PreferenceManager.getDefaultSharedPreferences(this@App))
        }
        single {
            logcat { "Koin providing Single SubredditRepository instant." }
            SubredditRepository()
        }
        single {
            logcat { "Koin providing Single FavoritePostsRepository instant." }
            FavoritePostsRepository()
        }
        single {
            logcat { "Koin providing Single FollowsRepository instant." }
            FollowsRepository()
        }

        single {
            logcat { "Koin providing Single RedditJsonClient object." }
            return@single RedditJsonClient
        }
        viewModel {
            logcat { "Koin providing ViewModel MainViewModel instant." }
            MainViewModel()
        }
        viewModel {
            logcat { "Koin providing ViewModel HomeViewModel instant." }
            HomeViewModel(mainViewModel = it.get())
        }
        viewModel {
            logcat { "Koin providing ViewModel PostViewerViewModel instant." }
            PostViewerViewModel(get())
        }
        viewModel {
            logcat { "Koin providing ViewModel PostViewerViewModel instant." }
            FavoritesViewModel()
        }
        viewModel {
            logcat { "Koin providing ViewModel SubscriptionsViewModel instant." }
            FollowsViewModel()
        }
        single {
            logcat { "Koin providing single AssetManager instant." }
            assets
        }
    }

    override fun onCreate() {
        super.onCreate()

        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)

        logcat(LogPriority.DEBUG) { "onCreate" }

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(appModule)
        }
    }

    private fun createRedditJsonServiceInstant(): RedditJsonService {
        logcat { "createRedditJsonServiceInstant" }

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
}