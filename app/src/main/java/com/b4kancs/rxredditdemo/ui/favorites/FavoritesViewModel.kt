package com.b4kancs.rxredditdemo.ui.favorites

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.domain.pagination.FavoritesPagingSource
import com.b4kancs.rxredditdemo.domain.pagination.SubredditsPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.ui.shared.PostPagingDataObservableProvider
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragmentViewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel : BaseListingFragmentViewModel(), PostPagingDataObservableProvider {

//    enum class FavoritesUiStates { NORMAL, LOADING, ERROR_GENERIC, NO_CONTENT }

    override val postsCachedPagingObservable: Observable<PagingData<Post>>

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)
    private val disposables = CompositeDisposable()

    init {
        logcat { "init" }

        uiStateBehaviorSubject
            .subscribe { uiState -> logcat { "uiStateBehaviorSubject.onNext: $uiState" } }
            .addTo(disposables)

        val pager = Pager(
            PagingConfig(
                pageSize = FavoritesPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = SubredditsPagingSource.PAGE_SIZE
            )
        ) { FavoritesPagingSource() }
        postsCachedPagingObservable = pager.observable
            .cachedIn(this.viewModelScope)
    }

    fun deleteAllFavoritePosts(): Completable =
        favoritePostsRepository.deleteAllFavoritePostsFromDb()

    fun getFavoritePostsBehaviorSubject(): BehaviorSubject<List<PostFavoritesDbEntry>> =
        favoritePostsRepository.favoritePostEntriesBehaviorSubject

    override fun getCachedPagingObservable(): Observable<PagingData<Post>> =
        postsCachedPagingObservable

    override fun onCleared() {
        logcat { "onCleared" }
        disposables.clear()
        super.onCleared()
    }
}