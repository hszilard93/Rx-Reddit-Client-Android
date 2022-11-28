package com.b4kancs.rxredditdemo.ui.main

import androidx.lifecycle.ViewModel
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.repository.SubredditRepository
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.observables.ConnectableObservable
import io.reactivex.rxjava3.subjects.PublishSubject
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

class MainViewModel : ViewModel() {
    private val subredditRepository: SubredditRepository by inject(SubredditRepository::class.java)

    val selectedSubredditPublishSubject: PublishSubject<Subreddit> = PublishSubject.create()
    val selectedSubredditReplayObservable: ConnectableObservable<Subreddit> = selectedSubredditPublishSubject.replay(1)
    val searchResultsChangedSubject: PublishSubject<List<Subreddit>> = PublishSubject.create()
    var isActionBarShowing: Boolean = true
    var isNavBarShowing: Boolean = true
    private val disposables = CompositeDisposable()

    init {
        logcat { "init" }
        selectedSubredditReplayObservable.connect()
        selectedSubredditPublishSubject.doOnNext { logcat(LogPriority.INFO) { "selectedSubredditChangedSubject.onNext" } }
        subredditRepository.loadDefaultSubreddit()
            .subscribe { selectedSubredditPublishSubject.onNext(subredditRepository.defaultSubreddit) }
            .addTo(disposables)
    }

    fun getAllSubreddits(): Single<List<Subreddit>> =
        subredditRepository.getAllSubredditsFromDb()

    fun getSubredditByAddress(address: String): Maybe<Subreddit> =
        subredditRepository.getSubredditFromDbByAddress(address)

    fun deleteSubreddit(subreddit: Subreddit): Completable =
        subredditRepository.deleteSubredditFromDb(subreddit)

    fun setAsDefaultSub(subreddit: Subreddit): Completable =
        subredditRepository.setAsDefaultSubreddit(subreddit)

    fun changeSubredditStatusByActionLogic(subreddit: Subreddit): Single<Subreddit> {
        logcat { "changeSubredditStatusByActionLogic: subreddit = $subreddit" }
        val newStatus = when (subreddit.status) {
            Subreddit.Status.NOT_IN_DB -> Subreddit.Status.IN_USER_LIST
            Subreddit.Status.IN_DEFAULTS_LIST -> Subreddit.Status.IN_USER_LIST
            Subreddit.Status.IN_USER_LIST -> Subreddit.Status.FAVORITED
            Subreddit.Status.FAVORITED -> Subreddit.Status.IN_USER_LIST
        }
        return changeSubredditStatusTo(subreddit, newStatus)
    }

    fun changeSubredditStatusTo(subreddit: Subreddit, status: Subreddit.Status): Single<Subreddit> {
        logcat { "changeSubredditStatusTo: subreddit = $subreddit, status = $status" }
        val newSub = Subreddit(subreddit.name, subreddit.address, status, subreddit.nsfw)
        return Single.create { emitter ->
            subredditRepository.saveSubredditToDb(newSub)
                .subscribeBy(
                    onComplete = { emitter.onSuccess(newSub) },
                    onError = { e -> emitter.onError(e) }
                ).addTo(disposables)
        }
    }

    fun goToSubredditByName(name: String) {
        logcat { "goToSubredditByName: name = $name" }

        val newSubNotInDb = Subreddit(name, "r/$name", Subreddit.Status.NOT_IN_DB)
        // We first check if we already know that subreddit.
        getSubredditByAddress(name)
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
        val dbResultObservable = subredditRepository.getSubredditsFromDbByNameLike(query)
            .toObservable()

        val nwResultObservable = subredditRepository.getSubredditsFromNetworkByNameLike(query)
            .doOnError { logcat(LogPriority.ERROR) { "Did not receive a network response for query: $query" } }
            .onErrorComplete()
            .startWith(Single.just(emptyList()))
            .toObservable()

        return Observable.combineLatest(
            dbResultObservable,
            nwResultObservable
        ) { a: List<Subreddit>, b: List<Subreddit> -> a + b }
            .map { subs -> subs.distinctBy { it.address.lowercase() } }
    }

    fun getSubredditsChangedSubject(): PublishSubject<Unit> =
        subredditRepository.subredditsChangedSubject

    fun getDefaultSubreddit() = subredditRepository.defaultSubreddit
}