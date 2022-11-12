package com.b4kancs.rxredditdemo.ui.main

import androidx.lifecycle.ViewModel
import com.b4kancs.rxredditdemo.model.DefaultSubredditObject
import com.b4kancs.rxredditdemo.model.DefaultSubredditObject.DEFAULT_SUBREDDIT_PREFERENCE_KEY
import com.b4kancs.rxredditdemo.database.SubredditDatabase
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.pagination.RedditJsonPagingSource
import com.b4kancs.rxredditdemo.utils.toV3Observable
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class MainViewModel : ViewModel() {
    val selectedSubredditChangedSubject: PublishSubject<Subreddit> = PublishSubject.create()
    val subredditsChangedSubject: PublishSubject<Unit> = PublishSubject.create()
    val searchResultsChangedSubject: PublishSubject<List<Subreddit>> = PublishSubject.create()
    private val subredditDatabase: SubredditDatabase by inject(SubredditDatabase::class.java)
    private val rxSharedPreferences: RxSharedPreferences by inject(RxSharedPreferences::class.java)
    private val disposables = CompositeDisposable()

    init {
        selectedSubredditChangedSubject.doOnNext { logcat(LogPriority.INFO) { "selectedSubredditChangedSubject.onNext()" } }
        setupDefaultSubredditObservable()
    }

    fun getSubredditFromDbByName(name: String): Maybe<Subreddit> {
        logcat { "getSubredditFromDbByName: name = $name" }
        return subredditDatabase.subredditDao()
            .getSubredditByAddress(name)
            .subscribeOn(Schedulers.io())
            .filter { it.name == name }
    }

    fun getSubredditsFromDbByNameLike(keyword: String): Single<List<Subreddit>> {
        logcat { "getSubredditsFromDbByNameLike: keyword = $keyword" }
        return subredditDatabase.subredditDao()
            .getSubredditsByNameLike("%${keyword}%")
            .subscribeOn(Schedulers.io())
    }

    fun getSubredditsFromNetworkByNameLike(keyword: String): Single<List<Subreddit>> {
        logcat { "getSubredditsFromNetworkByNameLike: keyword = $keyword" }
        return RedditJsonPagingSource.getSubredditsByKeyword(keyword)
            .subscribeOn(Schedulers.io())
    }

    fun saveSubredditToDb(subreddit: Subreddit) {
        logcat { "saveSubredditToDb: subreddit = $subreddit" }
        subredditDatabase.subredditDao().insertSubreddit(subreddit)
            .subscribeOn(Schedulers.io())
            .doOnError { logcat(LogPriority.ERROR) { "Unable to save subreddit ${subreddit.name} to db." } }
            .subscribe { count ->
                logcat(LogPriority.INFO) { "$count subreddit inserted into the database." }
                subredditsChangedSubject.onNext(Unit)
            }
            .addTo(disposables)
    }

    fun removeSubredditFromDb(subreddit: Subreddit) {
        logcat { "removeSubredditFromDb: subreddit = $subreddit" }
        subredditDatabase.subredditDao().deleteSubreddit(subreddit)
            .subscribeOn(Schedulers.io())
            .doOnComplete { logcat(LogPriority.INFO) { "Subreddit removed from db." } }
            .subscribe()
            .addTo(disposables)
    }

    fun makeThisTheDefaultSub(subreddit: Subreddit) {
        logcat { "makeThisTheDefaultSubreddit: subreddit = $subreddit" }
        rxSharedPreferences
            .getString(DEFAULT_SUBREDDIT_PREFERENCE_KEY)
            .set(subreddit.address)
    }

    fun handleActionOnSubredditInDrawer(subreddit: Subreddit): Subreddit {
        val newStatus = when (subreddit.status) {
            Subreddit.Status.NOT_IN_DB -> Subreddit.Status.IN_USER_LIST
            Subreddit.Status.IN_DEFAULTS_LIST -> Subreddit.Status.IN_USER_LIST
            Subreddit.Status.IN_USER_LIST -> Subreddit.Status.FAVORITED
            Subreddit.Status.FAVORITED -> Subreddit.Status.IN_USER_LIST
        }
        val newSub = Subreddit(subreddit.name, subreddit.address, newStatus, subreddit.nsfw)
        saveSubredditToDb(newSub)
        return newSub
    }

    // When the default subreddit preference changes, we immediately change it's representation in our model
    private fun setupDefaultSubredditObservable() {
        logcat { "getDefaultSubredditObservable" }
        val defaultSubredditAddress = rxSharedPreferences.getString(
            DEFAULT_SUBREDDIT_PREFERENCE_KEY,
            DefaultSubredditObject.defaultSubreddit.address
        )

        defaultSubredditAddress.asObservable().toV3Observable()
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe { address ->
                try {
                    DefaultSubredditObject.defaultSubreddit = subredditDatabase.subredditDao()
                        .getSubredditByAddress(address)
                        .blockingGet()
                    logcat(LogPriority.INFO) { "$address is made the default subreddit!" }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Could not get subreddit by address $address from database. error: ${e.message}" }
                }
            }
            .addTo(disposables)
    }

}