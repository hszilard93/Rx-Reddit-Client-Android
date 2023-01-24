package com.b4kancs.rxredditdemo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.domain.pagination.SubredditJsonPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.model.Subreddit
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.repository.SubredditRepository
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import com.b4kancs.rxredditdemo.ui.shared.FavoritesProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.observables.ConnectableObservable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel : ViewModel(), PostPagingDataObservableProvider, FavoritesProvider {

    enum class HomeUiStates { NORMAL, LOADING, ERROR_404, ERROR_GENERIC, NO_CONTENT }

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)
    private val subredditRepository: SubredditRepository by inject(SubredditRepository::class.java)
    private lateinit var subredditAddress: String
    val disposables = CompositeDisposable()

    val selectedSubredditChangedPublishSubject: PublishSubject<Subreddit> = PublishSubject.create()
    val selectedSubredditReplayObservable: ConnectableObservable<Subreddit> = selectedSubredditChangedPublishSubject.replay(1)
    val subredditSearchResultsChangedSubject: PublishSubject<List<Subreddit>> = PublishSubject.create()
    val uiStateBehaviorSubject: BehaviorSubject<HomeUiStates> = BehaviorSubject.createDefault(HomeUiStates.LOADING)

    val subredditPostsCachedPagingObservable: Observable<PagingData<Post>>
    var isAppJustStarted = true

    init {
        logcat { "init" }
        selectedSubredditReplayObservable.connect()
        selectedSubredditChangedPublishSubject
            .subscribe { sub -> logcat(LogPriority.INFO) { "selectedSubredditChangedSubject.onNext: sub = ${sub.name}" } }
            .addTo(disposables)

        subredditRepository.loadDefaultSubreddit()
            .subscribe { selectedSubredditChangedPublishSubject.onNext(subredditRepository.defaultSubreddit) }
            .addTo(disposables)

        selectedSubredditReplayObservable
            .subscribe { subredditAddress = it.address }
            .addTo(disposables)

        val pager = Pager(
            PagingConfig(
                pageSize = SubredditJsonPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = SubredditJsonPagingSource.PAGE_SIZE
            )
        ) {
            SubredditJsonPagingSource(subredditAddress)
        }
        subredditPostsCachedPagingObservable = pager.observable
            .cachedIn(viewModelScope)
    }



    override fun getFavoritePosts(): Single<List<PostFavoritesDbEntry>> =
        favoritePostsRepository.getAllFavoritePostsFromDb()

    fun getDefaultSubreddit() = subredditRepository.defaultSubreddit

    override fun cachedPagingObservable(): Observable<PagingData<Post>> = subredditPostsCachedPagingObservable

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
        getSubredditByAddress("r/$name")
            .subscribeBy(
                // The sub was in the db. Let's work with that!
                onSuccess = {
                    selectedSubredditChangedPublishSubject.onNext(it)
                },
                // The sub was not in the db. No problem, let's go to that address anyway.
                onComplete = {
                    selectedSubredditChangedPublishSubject.onNext(newSubNotInDb)
                },
                // Oh well, let's try going to that address anyway.
                onError = { e ->
                    logcat(LogPriority.ERROR) { "Error during DB operation 'getSubredditFromDbByName($name)'! Message: ${e.message}" }
                    selectedSubredditChangedPublishSubject.onNext(newSubNotInDb)
                }
            )
            .addTo(disposables)
    }

    fun getSubredditsSearchResultsFromDbAndNw(query: String): Observable<List<Subreddit>> {
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

    override fun onCleared() {
        logcat { "onCleared" }
        disposables.dispose()
        super.onCleared()
    }
}