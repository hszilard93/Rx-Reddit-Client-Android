package com.b4kancs.rxredditdemo.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.rxjava3.cachedIn
import androidx.paging.rxjava3.observable
import com.b4kancs.rxredditdemo.model.Post
import com.b4kancs.rxredditdemo.pagination.FavoritesDbPagingSource
import com.b4kancs.rxredditdemo.pagination.RedditJsonPagingSource
import com.b4kancs.rxredditdemo.ui.PostPagingDataObservableProvider
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import logcat.logcat

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel : ViewModel(), PostPagingDataObservableProvider {

    val cachedPagingObservable: Observable<PagingData<Post>>

    init {
        logcat { "init"}
        val pager = Pager(
            PagingConfig(
                pageSize = FavoritesDbPagingSource.PAGE_SIZE,
                prefetchDistance = 5,
                initialLoadSize = RedditJsonPagingSource.PAGE_SIZE
            )
        ) { FavoritesDbPagingSource() }
        cachedPagingObservable = pager.observable.cachedIn(this.viewModelScope)
    }

    override fun cachedPagingObservable(): Observable<PagingData<Post>> = cachedPagingObservable
}