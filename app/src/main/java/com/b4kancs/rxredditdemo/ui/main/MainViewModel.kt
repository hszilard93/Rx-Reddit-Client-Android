package com.b4kancs.rxredditdemo.ui.main

import androidx.lifecycle.ViewModel
import com.b4kancs.rxredditdemo.database.SubredditDatabase
import com.b4kancs.rxredditdemo.model.DefaultSubredditObject.DEFAULT_SUBREDDIT_PREFERENCE_KEY
import com.b4kancs.rxredditdemo.model.DefaultSubredditObject.defaultSubreddit
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.networking.RedditJsonClient
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
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
        selectedSubredditPublishSubject.doOnNext { logcat(LogPriority.INFO) { "selectedSubredditChangedSubject.onNext" } }
        loadDefaultSubreddit()
            .subscribe { selectedSubredditPublishSubject.onNext(defaultSubreddit) }
            .addTo(disposables)
    }

    fun getAllSubredditsFromDb(): Single<List<Subreddit>> {
        logcat { "getAllSubredditsFromDb" }
        return subredditDatabase.subredditDao().getSubreddits()
            .subscribeOn(Schedulers.io())
            .retry(2)
            .doOnSuccess { subs -> logcat { "${subs.size} subreddits loaded from DB." } }
    }

    fun getSubredditFromDbByAddress(address: String): Maybe<Subreddit> {
        logcat { "getSubredditFromDbByName: name = $address" }
        return subredditDatabase.subredditDao()
            .getSubredditByAddress(address)
            .subscribeOn(Schedulers.io())
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

    fun deleteSubredditFromDb(subreddit: Subreddit): Completable {
        logcat { "removeSubredditFromDb: subreddit = $subreddit" }
        return subredditDatabase.subredditDao().deleteSubreddit(subreddit)
            .subscribeOn(Schedulers.io())
            .doOnComplete {
                logcat(LogPriority.INFO) { "Subreddit removed from DB." }
                subredditsChangedSubject.onNext(Unit)
            }
            .doOnError { e -> logcat(LogPriority.ERROR) { "Couldn't remove subreddit $subreddit from DB. Message: ${e.message}" } }
    }

    fun setAsDefaultSub(subreddit: Subreddit): Completable {
        logcat { "makeThisTheDefaultSubreddit: subreddit = $subreddit" }

        val newSub = Subreddit(subreddit.name, subreddit.address, Subreddit.Status.FAVORITED, subreddit.nsfw)

        try {
            rxSharedPreferences
                .getString(DEFAULT_SUBREDDIT_PREFERENCE_KEY)
                .set(newSub.address)
            defaultSubreddit = newSub
            logcat(LogPriority.INFO) { "${newSub.address} is made the default subreddit." }
            subredditsChangedSubject.onNext(Unit)
        } catch (e: Exception) {
            // I am not sure what error could occur here, but it's best to be safe!
            logcat(LogPriority.ERROR) {
                "Failed to save subreddit ${subreddit.address} as default subreddit to the shared preferences. Message: ${e.message} "
            }
            return Completable.error(e)
        }

        // Next, we save the new default sub's data to the DB.
        // Every result is a good result, since we have already saved the preference, and that's what matters most.
        return Completable.create { emitter ->
            saveSubredditToDb(newSub)
                .subscribeBy(
                    onComplete = { emitter.onComplete() },
                    onError = { e -> emitter.onComplete() } // This will still not cause any significant problems, so we still complete.
                ).addTo(disposables)
        }
    }

    fun changeSubredditStatusByActionLogic(subreddit: Subreddit): Single<Subreddit> {
        logcat { "changeSubredditStatusByActionLogic: subreddit = $subreddit" }
        val newStatus = when (subreddit.status) {
            Subreddit.Status.NOT_IN_DB -> Subreddit.Status.IN_USER_LIST
            Subreddit.Status.IN_DEFAULTS_LIST -> Subreddit.Status.IN_USER_LIST
            Subreddit.Status.IN_USER_LIST -> Subreddit.Status.FAVORITED
            Subreddit.Status.FAVORITED -> Subreddit.Status.IN_USER_LIST
        }
        val newSub = Subreddit(subreddit.name, subreddit.address, newStatus, subreddit.nsfw)
        return Single.create { emitter ->
            saveSubredditToDb(newSub)
                .subscribeBy(
                    onComplete = {
                        subredditsChangedSubject.onNext(Unit)
                        emitter.onSuccess(newSub)
                    },
                    onError = { e ->
                        emitter.onError(e)
                    }
                ).addTo(disposables)
        }
    }

    fun changeSubredditStatusTo(subreddit: Subreddit, status: Subreddit.Status): Single<Subreddit> {
        logcat { "changeSubredditStatusTo: subreddit = $subreddit, status = $status" }
        val newSub = Subreddit(subreddit.name, subreddit.address, status, subreddit.nsfw)
        return Single.create { emitter ->
            saveSubredditToDb(newSub)
                .subscribeBy(
                    onComplete = {
                        emitter.onSuccess(newSub)
                    },
                    onError = { e ->
                        emitter.onError(e)
                    }
                ).addTo(disposables)
        }
    }

    fun goToSubredditByName(name: String) {
        logcat { "goToSubredditByName: name = $name" }

        val newSubNotInDb = Subreddit(name, "r/$name", Subreddit.Status.NOT_IN_DB)
        // We first check if we already know that subreddit.
        getSubredditFromDbByAddress(name)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                // The sub was in the db. Let's work with that!
                onSuccess = {
                    selectedSubredditPublishSubject.onNext(it)
                },
                // The sub was not in the db. No problem, let's go to that address anyway.
                onComplete = {
                    selectedSubredditPublishSubject.onNext(newSubNotInDb)
                },
                // Oh well, let's try going to that address anyway.
                onError = { e ->
                    logcat(LogPriority.ERROR) { "Error during DB operation 'getSubredditFromDbByName($name)'! Message: ${e.message}" }
                    selectedSubredditPublishSubject.onNext(newSubNotInDb)
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

    private fun saveSubredditToDb(subreddit: Subreddit): Completable {
        logcat { "saveSubredditToDb: subreddit = $subreddit" }
        return Completable.create { emitter ->
            subredditDatabase.subredditDao().insertSubreddit(subreddit)
                .subscribeOn(Schedulers.io())
                .retry(2)
                .doOnError { e ->
                    logcat(LogPriority.ERROR) { "Unable to save subreddit ${subreddit.name} to db. Message: ${e.message}" }
                    emitter.onError(e)
                }
                .subscribe { count ->
                    logcat(LogPriority.INFO) { "$count subreddit inserted into the database." }
                    subredditsChangedSubject.onNext(Unit)
                    emitter.onComplete()
                }
                .addTo(disposables)
        }
    }

    private fun loadDefaultSubreddit(): Completable {
        logcat { "loadDefaultSubreddit" }
        rxSharedPreferences
            .getString(DEFAULT_SUBREDDIT_PREFERENCE_KEY)
            .takeIf { it.get().isNotEmpty() }
            ?.let { preference ->
                return Completable.create { emitter ->
                    subredditDatabase.subredditDao()
                        .getSubredditByAddress(preference.get())
                        .subscribeOn(Schedulers.io())
                        .subscribeBy(
                            onSuccess = { subreddit ->
                                defaultSubreddit = subreddit
                                logcat(LogPriority.INFO) { "Default subreddit loaded: ${subreddit.name}" }
                                emitter.onComplete()
                            },
                            onComplete = {
                                logcat(LogPriority.WARN) { "Did not find subreddit ${preference.get()} in DB." }
                                emitter.onComplete()
                            },
                            onError = { e ->
                                logcat(LogPriority.ERROR) {
                                    "Could not load default subreddit ${preference.get()} from DB. Message: ${e.message}"
                                }
                                emitter.onComplete()
                            }
                        ).addTo(disposables)
                }
            }
            ?: run {
                logcat(LogPriority.INFO) { "No default subreddit preference detected." }
                return Completable.complete()
            }
    }
}