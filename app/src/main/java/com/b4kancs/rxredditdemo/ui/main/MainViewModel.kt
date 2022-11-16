package com.b4kancs.rxredditdemo.ui.main

import androidx.lifecycle.ViewModel
import com.b4kancs.rxredditdemo.database.SubredditDatabase
import com.b4kancs.rxredditdemo.model.DefaultSubredditObject
import com.b4kancs.rxredditdemo.model.DefaultSubredditObject.DEFAULT_SUBREDDIT_PREFERENCE_KEY
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.networking.RedditJsonClient
import com.b4kancs.rxredditdemo.utils.toV3Observable
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.observables.ConnectableObservable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class MainViewModel : ViewModel() {
    val selectedSubredditPublishSubject: PublishSubject<Subreddit> = PublishSubject.create()
    val selectedSubredditReplayObservable: ConnectableObservable<Subreddit> = selectedSubredditPublishSubject.replay(1)
    val subredditsChangedSubject: PublishSubject<Unit> = PublishSubject.create()
    val searchResultsChangedSubject: PublishSubject<List<Subreddit>> = PublishSubject.create()
    var isActionBarShowing: Boolean = true
    var isNavBarShowing: Boolean = true
    private val subredditDatabase: SubredditDatabase by inject(SubredditDatabase::class.java)
    private val rxSharedPreferences: RxSharedPreferences by inject(RxSharedPreferences::class.java)
    private val disposables = CompositeDisposable()

    init {
        logcat { "init" }
        selectedSubredditReplayObservable.connect()
        selectedSubredditPublishSubject.doOnNext { logcat(LogPriority.INFO) { "selectedSubredditChangedSubject.onNext()" } }
        selectedSubredditPublishSubject.onNext(DefaultSubredditObject.defaultSubreddit)
        setUpDefaultSubredditObservable()
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
        return RedditJsonClient.getSubredditsByKeyword(keyword)
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

    fun changeSubredditStatus(subreddit: Subreddit): Subreddit {
        logcat { "changeSubredditStatus: subreddit = $subreddit" }
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

    fun goToSubredditByName(name: String) {
        logcat { "goToSubredditByName: name = $name" }

        fun goToNewSub() {
            selectedSubredditPublishSubject.onNext(
                Subreddit(
                    name,
                    "r/$name",
                    Subreddit.Status.NOT_IN_DB
                )
            )
        }
        // We first check if we already know that subreddit.
        getSubredditFromDbByName(name)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                // The sub was in the db. Let's work with that!
                onSuccess = {
                    selectedSubredditPublishSubject.onNext(it)
                },
                onComplete = {
                    // The sub was not in the db. No problem, let's go to that address anyway.
                    goToNewSub()
                },
                onError = { e ->
                    logcat(LogPriority.ERROR) { "Error during DB operation 'getSubredditFromDbByName($name)'! Message: ${e.message}" }
                    // Let's try going to that address anyway.
                    goToNewSub()
                }
            )
    }

    fun getSearchResultsFromDbAndNw(query: String): Observable<List<Subreddit>> {
        logcat { "getSearchResultsFromDbAndNw: query = $query" }
        if (query.isEmpty()) return Observable.just(emptyList())

        // Get the query results from both the DB and the network and combine them.
        val dbResultObservable = getSubredditsFromDbByNameLike(query)
            .toObservable()

        val nwResultObservable = getSubredditsFromNetworkByNameLike(query)
            .toObservable()
            .doOnError { logcat(LogPriority.ERROR) { "Did not receive a network response for query: $query" } }
            .onErrorComplete()
            .startWith(Single.just(emptyList()))

        return Observable.combineLatest(
            dbResultObservable,
            nwResultObservable
        ) { a: List<Subreddit>, b: List<Subreddit> -> a + b }
            .map { subs -> subs.distinctBy { it.address.lowercase() } }
    }

    // When the default subreddit preference changes, we immediately change it's representation in our model
    private fun setUpDefaultSubredditObservable() {
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