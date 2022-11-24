package com.b4kancs.rxredditdemo.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.database.FavoritesDatabase
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.pagination.FavoritesDbPagingSource
import com.b4kancs.rxredditdemo.pagination.RedditJsonPagingSource
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.LogPriority
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel : ViewModel(), PostPagingDataObservableProvider {

    val cachedPagingObservable: Observable<PagingData<Post>>
    val isFavoritePostsNotEmptyBehaviorSubject: BehaviorSubject<Boolean> = BehaviorSubject.create()
    val disposables = CompositeDisposable()
    private val favoritesDatabase: FavoritesDatabase by inject(FavoritesDatabase::class.java)

    init {
        logcat { "init" }
        val pager = Pager(
            PagingConfig(
                pageSize = FavoritesDbPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = RedditJsonPagingSource.PAGE_SIZE
            )
        ) { FavoritesDbPagingSource() }
        cachedPagingObservable = pager.observable
            .cachedIn(this.viewModelScope)

        cachedPagingObservable
            .subscribe {
                favoritesDatabase.favoritesDao().getFavorites()
                    .subscribeOn(Schedulers.io())
                    .subscribeBy(
                        onSuccess = { favoriteDbEntries ->
                            isFavoritePostsNotEmptyBehaviorSubject.onNext(favoriteDbEntries.isNotEmpty())
                        },
                        onError = { e ->
                            logcat (LogPriority.ERROR) { "Could not get favorite posts from DB! Message: ${e.message}" }
                        }
                    )
                    .addTo(disposables)
            }.addTo(disposables)
    }

    fun deleteAllFavoritePosts(): Completable {
        logcat { "deleteAllFavoritePosts" }
        return favoritesDatabase.favoritesDao().deleteAll()
            .subscribeOn(Schedulers.io())
            .doOnError { e -> logcat(LogPriority.ERROR) { "Could not delete posts from DB! Message: ${e.message}" } }
            .doOnComplete { isFavoritePostsNotEmptyBehaviorSubject.onNext(false) }
    }

    override fun cachedPagingObservable(): Observable<PagingData<Post>> = cachedPagingObservable
}