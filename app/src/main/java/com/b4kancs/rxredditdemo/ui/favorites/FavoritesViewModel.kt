package com.b4kancs.rxredditdemo.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.domain.pagination.FavoritesDbPagingSource
import com.b4kancs.rxredditdemo.domain.pagination.SubredditJsonPagingSource
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.repository.FavoritePostsRepository
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.logcat
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel : ViewModel(), PostPagingDataObservableProvider {

    private val favoritePostsRepository: FavoritePostsRepository by inject(FavoritePostsRepository::class.java)
    val favoritePostsCachedPagingObservable: Observable<PagingData<Post>>

    init {
        logcat { "init" }
        val pager = Pager(
            PagingConfig(
                pageSize = FavoritesDbPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = SubredditJsonPagingSource.PAGE_SIZE
            )
        ) { FavoritesDbPagingSource() }
        favoritePostsCachedPagingObservable = pager.observable
            .cachedIn(this.viewModelScope)
    }

    fun deleteAllFavoritePosts(): Completable =
        favoritePostsRepository.deleteAllFavoritePostsFromDb()

    fun getIsFavoritePostsNotEmptyBehaviorSubject(): BehaviorSubject<Boolean> =
        favoritePostsRepository.doesFavoritePostsHaveItemsBehaviorSubject

    override fun cachedPagingObservable(): Observable<PagingData<Post>> =
        favoritePostsCachedPagingObservable
}