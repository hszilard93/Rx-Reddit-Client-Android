package com.b4kancs.rxredditdemo

import android.app.Application
import android.os.StrictMode
import androidx.preference.PreferenceManager
import com.b4kancs.rxredditdemo.database.SubredditRoomDatabase
import com.b4kancs.rxredditdemo.networking.RedditJsonPagingSource
import com.b4kancs.rxredditdemo.networking.RedditJsonService
import com.b4kancs.rxredditdemo.ui.home.HomeViewModel
import com.b4kancs.rxredditdemo.ui.postviewer.PostViewerViewModel
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
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
        single { createRedditRssServiceInstant() }
        single { SubredditRoomDatabase.fetchDatabase(this@App) }
        single { RxSharedPreferences.create(PreferenceManager.getDefaultSharedPreferences(this@App)) }
        viewModel { HomeViewModel() }
        viewModel  { PostViewerViewModel(get()) }
        single { assets }
    }

    override fun onCreate() {
        super.onCreate()

        if(BuildConfig.DEBUG)
            StrictMode.enableDefaults()

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(appModule)
        }
    }

    private fun createRedditRssServiceInstant(): RedditJsonService {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .followRedirects(true)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(RedditJsonPagingSource.FEED_URL)
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