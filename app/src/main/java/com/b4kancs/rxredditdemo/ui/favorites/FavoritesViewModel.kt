package com.b4kancs.rxredditdemo.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.domain.pagination.FavoritesPagingSource
import com.b4kancs.rxredditdemo.domain.pagination.SubredditJsonPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel : ViewModel(), PostPagingDataObservableProvider {

    enum class FavoritesUiStates { NORMAL, LOADING, ERROR_GENERIC, NO_CONTENT }

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)
    private val disposables = CompositeDisposable()
    val favoritePostsCachedPagingObservable: Observable<PagingData<Post>>
    val uiStateBehaviorSubject = BehaviorSubject.createDefault(FavoritesUiStates.LOADING)

    init {
        logcat { "init" }

        uiStateBehaviorSubject
            .subscribe { uiState -> logcat { "uiStateBehaviorSubject.onNext: $uiState" } }
            .addTo(disposables)

        val pager = Pager(
            PagingConfig(
                pageSize = FavoritesPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = SubredditJsonPagingSource.PAGE_SIZE
            )
        ) { FavoritesPagingSource() }
        favoritePostsCachedPagingObservable = pager.observable
            .cachedIn(this.viewModelScope)
    }

    fun deleteAllFavoritePosts(): Completable =
        favoritePostsRepository.deleteAllFavoritePostsFromDb()

    fun getFavoritePostsBehaviorSubject(): BehaviorSubject<List<PostFavoritesDbEntry>> =
        favoritePostsRepository.favoritePostEntriesBehaviorSubject

    override fun cachedPagingObservable(): Observable<PagingData<Post>> =
        favoritePostsCachedPagingObservable

    override fun onCleared() {
        logcat { "onCleared" }
        disposables.clear()
        super.onCleared()
    }
}