package com.b4kancs.rxredditdemo

import android.app.Application
import com.b4kancs.rxredditdemo.database.SubredditRoomDatabase
import com.b4kancs.rxredditdemo.networking.RedditRssPagingSource
import com.b4kancs.rxredditdemo.networking.RedditRssService
import com.b4kancs.rxredditdemo.ui.home.HomeViewModel
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
        viewModel { HomeViewModel() }
        single { assets }
    }

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(appModule)
        }
    }

    private fun createRedditRssServiceInstant(): RedditRssService {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .followRedirects(true)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(RedditRssPagingSource.FEED_URL)
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

        return retrofit.create(RedditRssService::class.java)
    }
}