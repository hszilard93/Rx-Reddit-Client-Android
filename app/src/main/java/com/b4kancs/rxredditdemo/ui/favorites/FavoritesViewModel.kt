package com.b4kancs.rxredditdemo.ui.favorites

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.data.database.PostFavoritesDbEntry
import com.b4kancs.rxredditdemo.data.networking.RedditJsonService
import com.b4kancs.rxredditdemo.repository.pagination.FavoritesPagingSource
import com.b4kancs.rxredditdemo.repository.pagination.SubredditsPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.repository.PostsPropertiesRepository
import com.b4kancs.rxredditdemo.ui.shared.BaseListingFragmentViewModel
import com.b4kancs.rxredditdemo.ui.shared.PostPagingDataObservableProvider
import com.f2prateek.rx.preferences2.RxSharedPreferences
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.logcat

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel(
    private val favoritePostsRepository: FavoritePostsRepository,
    private val jsonService: RedditJsonService,
    private val rxSharedPreferences: RxSharedPreferences,
    private val postsPropertiesRepository: PostsPropertiesRepository,
) : BaseListingFragmentViewModel(
    rxSharedPreferences,
    postsPropertiesRepository,
    favoritePostsRepository
), PostPagingDataObservableProvider {

//    enum class FavoritesUiStates { NORMAL, LOADING, ERROR_GENERIC, NO_CONTENT }

    override val postsCachedPagingObservable: Observable<PagingData<Post>>

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
        ) { FavoritesPagingSource(favoritePostsRepository, jsonService) }
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